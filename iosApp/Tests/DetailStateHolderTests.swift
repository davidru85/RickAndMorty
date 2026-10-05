@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-070` — the iOS Detail holder starts the shared Detail holder (`IC-019`, `TASK-111`,
/// `REQ-FUNC-002`, `REQ-FUNC-006`, `AC-REQ-FUNC-006-1`).
///
/// The shared `CharacterDetailStateHolder` requests nothing and observes nothing until its platform
/// holder calls `start` once, as the Android `CharacterDetailViewModel` does. A holder that never calls
/// it leaves the detail unloaded and the favourite flag at its initial `false`, so a tap on a stored
/// favourite *removes* it while the screen shows it as marked. The repositories here are in-memory
/// fakes, so no case touches the network or the shared graph (`REQ-REL-004`).
@MainActor
final class DetailStateHolderTests: XCTestCase {
    func test_TEST_UNIT_070_given_a_new_holder_when_it_initialises_then_the_detail_is_requested_once_and_rendered() async {
        let repository = RecordingDetailRepository()
        let holder = makeHolder(repository: repository, favorites: InMemoryFavorites(stored: []))

        await waitUntil { holder.state.loadState is LoadStateContent }

        XCTAssertEqual(repository.detailRequests, 1, "the holder must start the shared holder, which loads the detail once")
        XCTAssertTrue(holder.state.loadState is LoadStateContent, "the loaded detail renders")
        XCTAssertEqual(holder.state.episodeCount?.intValue, 3, "the episode count comes from the loaded detail")
    }

    func test_TEST_UNIT_070_given_a_stored_favourite_when_the_holder_initialises_then_it_reads_marked_without_a_tap() async {
        let holder = makeHolder(repository: RecordingDetailRepository(), favorites: InMemoryFavorites(stored: ["1"]))

        await waitUntil { holder.state.isFavorite }

        XCTAssertTrue(holder.state.isFavorite, "the stored set is observed, so a stored favourite reads marked")
    }

    func test_TEST_UNIT_070_given_a_stored_favourite_when_it_is_toggled_once_then_it_leaves_the_store_and_reads_unmarked() async {
        let favorites = InMemoryFavorites(stored: ["1"])
        let holder = makeHolder(repository: RecordingDetailRepository(), favorites: favorites)
        await waitUntil { holder.state.isFavorite }

        holder.onIntent(CharacterDetailIntentToggleFavorite.shared)
        await waitUntil { !favorites.stored.contains("1") && !holder.state.isFavorite }

        XCTAssertFalse(favorites.stored.contains("1"), "one toggle on a stored favourite removes it")
        XCTAssertFalse(holder.state.isFavorite, "and the screen agrees with the store")
    }

    private func makeHolder(repository: RecordingDetailRepository, favorites: InMemoryFavorites) -> DetailStateHolder {
        DetailStateHolder(
            id: "1",
            header: nil,
            getDetails: GetCharacterDetails(repository: repository),
            toggleFavorite: ToggleFavorite(repository: favorites),
            observeFavoriteIds: ObserveFavoriteIds(repository: favorites),
            enrich: false
        )
    }

    /// The holder republishes on the main actor, so a change lands after a few yields; the bound keeps
    /// a case that never converges from hanging.
    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<50 where !condition() {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
    }
}

/// An `IC-007` double that answers every detail request with one fixed character and counts the calls.
private final class RecordingDetailRepository: CharacterRepository {
    private(set) var detailRequests = 0

    func details(id: Any, enrich: Bool, completionHandler: @escaping ((any DataResult)?, (any Error)?) -> Void) {
        detailRequests += 1
        let origin = LocationSummary(id: nil, name: "Earth (C-137)", type: nil, dimension: nil)
        let rick = CharacterDetails(
            id: "1",
            name: "Rick Sanchez",
            status: CharacterStatusAlive.shared,
            species: "Human",
            type: nil,
            gender: CharacterGenderMale.shared,
            origin: origin,
            lastKnownLocation: origin,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
            episodeIds: ["1", "2", "3"],
            episodeSummaries: nil,
            createdAt: nil
        )
        completionHandler(DataResultSuccess(value: rick, source: DataSource.network, isStale: false, warnings: []), nil)
    }

    func page(
        filter: CharacterFilter,
        page: Int32,
        policy: PageLoadPolicy,
        completionHandler: @escaping ((any DataResult)?, (any Error)?) -> Void
    ) {
        preconditionFailure("the Detail holder never requests a page")
    }
}

/// An `IC-008` double over an in-memory set: `observe` replays the current set to each collector and
/// re-emits after every write, as the real store does.
private final class InMemoryFavorites: FavoritesRepository {
    private(set) var stored: Set<String>
    private var collectors: [any Kotlinx_coroutines_coreFlowCollector] = []

    init(stored: Set<String>) {
        self.stored = stored
    }

    func observe() -> any Kotlinx_coroutines_coreFlow {
        FavoritesFlow(owner: self)
    }

    func toggle(id: Any, completionHandler: @escaping ((any Error)?) -> Void) {
        guard let key = id as? String else { preconditionFailure("a CharacterId crosses as its string value") }
        if stored.contains(key) { stored.remove(key) } else { stored.insert(key) }
        publish()
        completionHandler(nil)
    }

    func clear(completionHandler: @escaping ((any Error)?) -> Void) {
        stored.removeAll()
        publish()
        completionHandler(nil)
    }

    fileprivate func attach(_ collector: any Kotlinx_coroutines_coreFlowCollector) {
        collectors.append(collector)
        collector.emit(value: stored, completionHandler: { _ in })
    }

    private func publish() {
        for collector in collectors {
            collector.emit(value: stored, completionHandler: { _ in })
        }
    }
}

/// The hot, never-completing flow `InMemoryFavorites.observe` returns.
private final class FavoritesFlow: Kotlinx_coroutines_coreFlow {
    private let owner: InMemoryFavorites

    init(owner: InMemoryFavorites) {
        self.owner = owner
    }

    func collect(collector: any Kotlinx_coroutines_coreFlowCollector, completionHandler: @escaping ((any Error)?) -> Void) {
        // Never completing is what a hot store stream does; the holder's scope owns the collection.
        owner.attach(collector)
    }
}
