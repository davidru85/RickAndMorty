@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// The Discovery state holder starts the screen's first load (`TASK-053`, `TASK-055`, `GAP-031`).
///
/// The shared `DiscoveryReducer` requests nothing until its platform holder calls `start` once, from
/// its own initialisation, exactly as the Android `DiscoveryViewModel` does. A holder that never
/// calls it leaves the screen on its initial `Loading` state for ever: the skeleton grid with no
/// count, which is what the running app showed. The pager here is a recording fake, so the case needs
/// no network and no graph (`REQ-REL-004`).
@MainActor
final class DiscoveryStateHolderTests: XCTestCase {
    func test_GAP_031_given_a_new_holder_when_it_initialises_then_page_one_of_the_initial_filter_is_requested() async {
        let pager = RecordingCharacterPager()
        let initial = CharacterFilter(query: "", status: StatusFilter.alive)

        let holder = DiscoveryStateHolder(pager: pager, initialFilter: initial)

        // The reducer runs on the main dispatcher, so the request lands after this frame yields.
        for _ in 0..<50 where pager.requestedFilters.isEmpty {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertEqual(
            pager.requestedFilters,
            [initial],
            "the holder must start the shared reducer, which requests page 1 of the initial filter once"
        )
        XCTAssertTrue(holder.state.loadState is LoadStateLoading, "nothing has loaded yet, so the state stays Loading")
    }
}

/// A `CharacterPager` that records the filters it is asked for and never publishes a page.
private final class RecordingCharacterPager: CharacterPager {
    private(set) var requestedFilters: [CharacterFilter] = []

    /// A flow that never emits: the case is about the request, not about rendering a result.
    let state: any Kotlinx_coroutines_coreFlow = SilentFlow()

    func setFilter(filter: CharacterFilter, completionHandler: @escaping (Error?) -> Void) {
        requestedFilters.append(filter)
        completionHandler(nil)
    }

    func next(completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }

    func refresh(completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }

    func retry(completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }
}

/// A Kotlin `Flow` that suspends its collector without emitting, as a pager does before any load.
private final class SilentFlow: Kotlinx_coroutines_coreFlow {
    func collect(collector: any Kotlinx_coroutines_coreFlowCollector, completionHandler: @escaping (Error?) -> Void) {
        // Never completing is what "no value yet" means for a hot flow; the reducer's scope owns it.
    }
}
