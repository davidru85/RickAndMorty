@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-095` — one scope per iOS screen (`IC-014`, `DEC-143`, `TASK-115`).
///
/// `DiscoveryHost` built the pager on one scope and the holder built its reducer on a second, and only
/// the reducer's was ever cancelled, so the pager's protocol observer and its loads outlived the
/// screen; the host's initialiser also made a new scope on every struct re-init. The holder now builds
/// its pager from its own scope, which ends with it.
@MainActor
final class ScreenScopeTests: XCTestCase {
    func test_TEST_UNIT_095_given_a_discovery_holder_when_it_goes_then_its_pager_scope_ends_with_it() throws {
        var pagerScope: Kotlinx_coroutines_coreCoroutineScope?
        var holder: DiscoveryStateHolder? = DiscoveryStateHolder(
            pagerFactory: { scope in
                pagerScope = scope
                return RecordingCharacterPager()
            },
            initialFilter: CharacterFilter(query: "", status: StatusFilter.all)
        )
        let scope = try XCTUnwrap(pagerScope, "the holder builds its pager from its own scope")
        XCTAssertNotNil(holder)
        XCTAssertTrue(MultiverseBootstrap.shared.isScopeActive(scope: scope), "the scope lives with the holder")

        holder = nil

        XCTAssertFalse(MultiverseBootstrap.shared.isScopeActive(scope: scope), "the pager's scope ends with the holder")
    }
}
