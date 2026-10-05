@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-019`'s iOS half — over content, a failed load and a stale result each produce the glass
/// banner of `UI_SPEC.md` §8 with a Retry (`ERROR_FLOW.md` §4, §9, `DEC-124`, `TASK-111`).
///
/// The decision of what the banner says lives in `DiscoveryNotice`, so it is asserted here without
/// rendering; the view draws the notice it is given and sends `CharacterListIntent.Retry` from its
/// button.
@MainActor
final class DiscoveryNoticeTests: XCTestCase {
    func test_TEST_UI_019_given_a_failed_append_over_content_when_the_notice_is_built_then_it_names_the_failure() {
        let notice = DiscoveryNotice.make(for: state(contentFailure: ApiFailureOffline.shared))

        XCTAssertEqual(notice?.message, "You're offline. Reconnect to continue exploring the multiverse.")
        XCTAssertEqual(notice?.actionLabel, "Retry")
    }

    func test_TEST_UI_019_given_a_rate_limited_append_when_the_notice_is_built_then_its_countdown_is_substituted() {
        let failure = ApiFailureRateLimited(retryAfterSeconds: KotlinLong(longLong: 30))

        let notice = DiscoveryNotice.make(for: state(contentFailure: failure))

        XCTAssertEqual(notice?.message, "Too many jumps. Try again in 30 s.")
    }

    func test_TEST_UI_019_given_stale_content_when_the_notice_is_built_then_it_is_the_stale_banner_with_retry() {
        let notice = DiscoveryNotice.make(for: state(isStale: true))

        XCTAssertEqual(notice?.message, "Showing saved results")
        XCTAssertEqual(notice?.actionLabel, "Retry")
    }

    func test_TEST_UI_019_given_a_refresh_in_flight_when_the_notice_is_built_then_there_is_none() {
        XCTAssertNil(DiscoveryNotice.make(for: state(isStale: true, isRefreshing: true)))
    }

    func test_TEST_UI_019_given_fresh_content_or_a_full_surface_error_when_the_notice_is_built_then_there_is_none() {
        XCTAssertNil(DiscoveryNotice.make(for: state()), "fresh content needs no banner")
        XCTAssertNil(
            DiscoveryNotice.make(
                for: state(items: [], loadState: LoadStateError(failure: ApiFailureOffline.shared))
            ),
            "a failure with nothing displayable is the full-surface error, not a banner"
        )
    }

    private func state(
        items: [CharacterCardUi]? = nil,
        loadState: any LoadState = LoadStateContent.shared,
        isStale: Bool = false,
        contentFailure: ApiFailure? = nil,
        isRefreshing: Bool = false
    ) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: items ?? [card()],
            totalCount: KotlinInt(int: 826),
            loadState: loadState,
            isAppending: false,
            isStale: isStale,
            contentFailure: contentFailure,
            isRefreshing: isRefreshing
        )
    }

    private func card() -> CharacterCardUi {
        CharacterCardUi(
            id: "1",
            name: "Rick Sanchez",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
    }
}
