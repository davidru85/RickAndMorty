@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-024`, iOS half — the manual refresh gesture (`REQ-FUNC-012`, `AC-REQ-FUNC-012-1`,
/// `DEC-134`, `TASK-112`).
///
/// `.refreshable` holds its spinner for as long as its action runs, so the holder's refresh returns
/// only when the shared reducer's refresh ends — the same moment `isRefreshing` clears. The pager is a
/// fake that holds the refresh until the case releases it, so no network is involved (`REQ-REL-004`).
@MainActor
final class DiscoveryRefreshTests: XCTestCase {
    func test_TEST_UI_024_given_a_refresh_when_it_is_awaited_then_it_returns_only_after_the_revalidation_ends() async {
        let pager = HeldRefreshPager()
        let holder = DiscoveryStateHolder(
            pager: pager,
            initialFilter: CharacterFilter(query: "", status: StatusFilter.all)
        )
        let probe = RefreshProbe()

        let refresh = Task { @MainActor in
            await holder.refresh()
            probe.finished = true
        }
        for _ in 0..<50 where pager.pendingRefresh == nil {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertNotNil(pager.pendingRefresh, "the gesture reaches the shared pager's refresh")
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertFalse(probe.finished, "the spinner holds while the network answers")

        pager.pendingRefresh?(nil)
        await refresh.value

        XCTAssertTrue(probe.finished, "the refresh returns once the revalidation ends")
    }
}

/// Whether the awaited refresh has returned, read on the main actor.
@MainActor
private final class RefreshProbe {
    var finished = false
}

/// A `CharacterPager` whose refresh stays in flight until the case completes it.
private final class HeldRefreshPager: CharacterPager {
    private(set) var pendingRefresh: ((Error?) -> Void)?

    let state: any Kotlinx_coroutines_coreFlow = NeverEmittingFlow()

    func setFilter(filter: CharacterFilter, completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }

    func next(completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }

    func refresh(completionHandler: @escaping (Error?) -> Void) { pendingRefresh = completionHandler }

    func retry(completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }
}

/// A Kotlin `Flow` that suspends its collector without emitting, as a pager does before any load.
private final class NeverEmittingFlow: Kotlinx_coroutines_coreFlow {
    func collect(collector: any Kotlinx_coroutines_coreFlowCollector, completionHandler: @escaping (Error?) -> Void) {}
}
