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
///
/// What this adapter owns is the start: the load and the subscription to the stored set. How an
/// observed set reconciles the flag is the shared holder's rule, asserted by `TEST-UNIT-004` in the
/// shared suite. It cannot be asserted from Swift: Kotlin boxes a `CharacterId` inside a collection, a
/// boxed value class reaches Swift as an opaque object rather than its string (observed 2026-10-05:
/// `CopyKeys.all` yields `CopyKey(value=…)` elements, and `contains("status_alive")` is `false`), so a
/// set built in Swift never contains the id Kotlin looks up.
@MainActor
final class DetailStateHolderTests: XCTestCase {
    func test_TEST_UNIT_070_given_a_new_holder_when_it_initialises_then_the_detail_loads_once() async {
        let repository = RecordingDetailRepository()
        let holder = makeHolder(repository: repository, favorites: InMemoryFavorites(stored: []))

        await waitUntil { holder.state.loadState is LoadStateContent }

        XCTAssertEqual(repository.detailRequests, 1, "the started shared holder loads the detail once")
        XCTAssertTrue(holder.state.loadState is LoadStateContent, "the loaded detail renders")
        XCTAssertEqual(holder.state.episodeCount?.intValue, 3, "the episode count comes from the loaded detail")
    }

    func test_TEST_UNIT_070_given_a_new_holder_when_it_initialises_then_it_observes_the_stored_set() async {
        let favorites = InMemoryFavorites(stored: [])
        let holder = makeHolder(repository: RecordingDetailRepository(), favorites: favorites)

        await waitUntil { favorites.subscriptions == 1 }

        XCTAssertEqual(favorites.subscriptions, 1, "the started holder observes the stored set once")
        XCTAssertFalse(holder.state.isFavorite, "an empty stored set reads unmarked")
    }

    func test_TEST_UNIT_070_given_a_started_holder_when_the_favourite_is_toggled_then_the_store_gets_one_write() async {
        let favorites = InMemoryFavorites(stored: [])
        let holder = makeHolder(repository: RecordingDetailRepository(), favorites: favorites)
        await waitUntil { favorites.subscriptions == 1 }

        holder.onIntent(CharacterDetailIntentToggleFavorite.shared)
        await waitUntil { favorites.toggles == 1 }

        XCTAssertEqual(favorites.toggles, 1, "one tap is one write")
        XCTAssertTrue(favorites.stored.contains("1"), "the toggled id reaches the store")
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
    private(set) var toggles = 0
    private var collectors: [any Kotlinx_coroutines_coreFlowCollector] = []

    /// How many collectors are observing the set: the started holder's one.
    var subscriptions: Int { collectors.count }

    init(stored: Set<String>) {
        self.stored = stored
    }

    func observe() -> any Kotlinx_coroutines_coreFlow {
        FavoritesFlow(owner: self)
    }

    func toggle(id: Any, completionHandler: @escaping ((any Error)?) -> Void) {
        guard let key = id as? String else { preconditionFailure("a CharacterId crosses as its string value") }
        toggles += 1
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

    func collect(
        collector: any Kotlinx_coroutines_coreFlowCollector,
        completionHandler: @escaping ((any Error)?) -> Void
    ) {
        // Never completing is what a hot store stream does; the holder's scope owns the collection.
        owner.attach(collector)
    }
}
