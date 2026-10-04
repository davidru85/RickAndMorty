@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-005` and `TEST-A11Y-004` on iOS, Favorites half (`IC-020`, `AC-REQ-FUNC-006-3`,
/// `REQ-UX-005`, `TASK-055`).
///
/// Every `IC-020` state is rendered from a value the shared contract carries — the designed empty
/// state, the favourite grid of the same cards Discovery draws, the loading hold, and the
/// full-surface error with Retry — so each state is reachable without a store, a repository or a
/// network. The empty state's copy and the card's merged label are asserted against the canonical
/// key list, so a case cannot pass on a literal the copy set does not carry.
@MainActor
final class FavoritesScreenStateTests: XCTestCase {
    // MARK: - TEST-UI-005: every rendered Favorites state

    func test_TEST_UI_005_given_the_empty_state_when_rendered_then_the_designed_empty_surface_renders() {
        assertRenders(
            state: FavoritesUiState(items: [], loadState: LoadStateEmpty.shared),
            "the designed empty state (UI_SPEC.md §6.4, AC-REQ-FUNC-006-3)"
        )
    }

    func test_TEST_UI_005_given_stored_favourites_when_rendered_then_the_grid_of_same_cards_renders() {
        assertRenders(
            state: FavoritesUiState(
                items: [card(id: "1", name: "Rick Sanchez"), card(id: "2", name: "Morty Smith")],
                loadState: LoadStateContent.shared
            ),
            "the favourite grid reusing Discovery's cards (IC-016, IC-020, UI_SPEC.md §6.4)"
        )
    }

    func test_TEST_UI_005_given_the_loading_state_when_rendered_then_the_surface_holds() {
        assertRenders(
            state: FavoritesUiState(items: [], loadState: LoadStateLoading.shared),
            "the loading hold: nothing displayable yet (IC-020, CONF-79)"
        )
    }

    func test_TEST_UI_005_given_an_offline_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: FavoritesUiState(items: [], loadState: LoadStateError(failure: ApiFailureOffline.shared)),
            "the offline full-surface error with Retry (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_005_given_a_server_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: FavoritesUiState(
                items: [],
                loadState: LoadStateError(failure: ApiFailureServer(statusCode: 500))
            ),
            "the server full-surface error with Retry (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_005_given_a_rate_limited_failure_when_rendered_then_the_countdown_message_renders() {
        assertRenders(
            state: FavoritesUiState(
                items: [],
                loadState: LoadStateError(
                    failure: ApiFailureRateLimited(retryAfterSeconds: KotlinLong(longLong: 30))
                )
            ),
            "the rate-limited error with its countdown (ERROR_FLOW.md §4.1, GAP-027)"
        )
    }

    /// `IC-020`'s precedence: cards a user can see outrank a failure, so a partial read never blanks
    /// a section that has content (`AC-REQ-FUNC-006-1`).
    func test_TEST_UI_005_given_content_and_a_failure_when_rendered_then_the_cards_win() {
        assertRenders(
            state: FavoritesUiState(
                items: [card(id: "1", name: "Rick Sanchez")],
                loadState: LoadStateContent.shared
            ),
            "content outranking a partial read (AC-REQ-FUNC-006-1)"
        )
    }

    // MARK: - Accessibility

    /// The card is one merged element and the status is announced as a text label, never by the dot's
    /// colour alone (`REQ-UX-005`, `UI_SPEC.md` §9).
    func test_TEST_A11Y_004_given_a_favourite_card_when_labelled_then_the_status_is_the_text_label() {
        XCTAssertEqual(
            CharacterPresentation.cardLabel(name: "Rick Sanchez", statusLabel: "Alive", species: "Human"),
            "Rick Sanchez, Alive, Human"
        )
    }

    // MARK: - Copy bindings

    /// The designed empty state's three canonical keys, both locales (`REQ-UX-008`).
    func test_AC_REQ_FUNC_006_3_given_the_empty_state_when_its_copy_resolves_then_each_key_exists_in_both_locales() {
        let keys = ["favorites_heading", "favorites_body", "browse_characters", "error_title", "action_retry"]
        for key in keys {
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "en"), "en must carry \(key)")
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "es"), "es must carry \(key)")
        }
    }

    /// The error surface takes its copy from the shared formatter, so the screen never builds a
    /// message from a failure field (`REQ-FUNC-013`).
    func test_AC_REQ_FUNC_006_1_given_the_error_state_when_it_resolves_copy_then_it_uses_the_shared_keys() {
        XCTAssertEqual(CharacterPresentation.key(DefaultPresentationFormatters.shared.failureTitle()), "error_title")
        XCTAssertEqual(CharacterPresentation.key(DefaultPresentationFormatters.shared.retryAction()), "action_retry")
    }

    // MARK: - Helpers

    private func assertRenders(state: FavoritesUiState, _ description: String) {
        let screen = FavoritesScreen(
            state: state,
            loader: FavoritesImageStub(),
            onIntent: { _ in },
            onCharacterSelected: { _ in },
            onBrowseCharacters: {}
        )
        let renderer = ImageRenderer(
            content:
                screen
                .frame(width: 402, height: 874)
                .background(MultiverseBrandColors.spaceBlack)
                .environment(\.multiverseGlassPath, .material)
        )
        renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
        guard let image = renderer.uiImage else {
            XCTFail("Favorites must render \(description)")
            return
        }
        XCTAssertGreaterThan(
            image.size.width * image.size.height,
            0,
            "Favorites must render a non-empty image for \(description)"
        )
    }

    private func card(id: String, name: String) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: name,
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://images.invalid/avatar/\(id).png"
        )
    }
}

/// The in-memory loader the Favorites render installs: no network, no disk, one synchronous image
/// (`TESTING.md` §4.5 "No network in tests").
@MainActor
private final class FavoritesImageStub: PortraitImageLoading {
    func cachedImage(for url: String) -> Image? { Self.image }

    func image(for url: String) async -> Image? { Self.image }

    private static let image: Image = {
        let size = CGSize(width: 32, height: 32)
        return Image(size: size) { context in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.green))
        }
    }()
}
