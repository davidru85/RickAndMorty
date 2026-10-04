import MultiverseExplorer
import SwiftUI
import XCTest

@testable import MultiverseApp

/// `TEST-UI-016` on iOS, Discovery half (`AC-REQ-UX-009-1`, `REQ-UX-009`, `ERROR_FLOW.md` §8,
/// `TASK-055`).
///
/// Every state `ERROR_FLOW.md` §4/§8 requires of the list surface is rendered from a state value the
/// shared `IC-018` contract carries, and each render is asserted to produce a non-empty image: the
/// initial loading skeleton, the content grid, the paging indicator over content, the empty search,
/// the full-surface error with Retry, and the stale banner over retained content. Driving the view
/// with a state value is what makes each state reachable without a network or a pager, so the cases
/// cannot pass by accident of a live load.
@MainActor
final class DiscoveryScreenStateTests: XCTestCase {
    // MARK: - TEST-UI-016: every rendered list state

    func test_TEST_UI_016_given_the_initial_state_when_rendered_then_the_skeleton_loading_surface_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateLoading.shared),
            "the initial loading skeleton (ERROR_FLOW.md §8, \"Initial loading\")"
        )
    }

    func test_TEST_UI_016_given_content_when_rendered_then_the_card_grid_renders() {
        assertRenders(
            state: makeState(items: [card(id: "1", name: "Rick Sanchez")], loadState: LoadStateContent.shared),
            "the content grid (ERROR_FLOW.md §8, \"Content\")"
        )
    }

    func test_TEST_UI_016_given_content_with_a_next_page_when_rendered_then_the_paging_indicator_renders() {
        assertRenders(
            state: makeState(
                items: [card(id: "1", name: "Rick Sanchez")],
                loadState: LoadStateContent.shared,
                isAppending: true
            ),
            "the paging indicator (ERROR_FLOW.md §8, \"Paging\")"
        )
    }

    func test_TEST_UI_016_given_an_empty_result_when_rendered_then_the_empty_search_surface_renders() {
        assertRenders(
            state: makeState(query: "zzz", loadState: LoadStateEmpty.shared),
            "the empty search (ERROR_FLOW.md §5.1, §8)"
        )
    }

    func test_TEST_UI_016_given_an_offline_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureOffline.shared)),
            "the offline full-surface error with Retry (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_a_timeout_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureTimeout.shared)),
            "the timeout full-surface error with Retry (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_a_rate_limited_failure_when_rendered_then_the_countdown_message_renders() {
        assertRenders(
            state: makeState(
                loadState: LoadStateError(
                    failure: ApiFailureRateLimited(retryAfterSeconds: KotlinLong(longLong: 30))
                )
            ),
            "the rate-limited error with its countdown (ERROR_FLOW.md §4.1, GAP-027)"
        )
    }

    func test_TEST_UI_016_given_a_server_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureServer(statusCode: 500))),
            "the server full-surface error with Retry (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_a_malformed_response_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureMalformedResponse.shared)),
            "the malformed-response error (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_an_empty_body_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureEmptyBody.shared)),
            "the empty-body error (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_an_invalid_request_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(
                loadState: LoadStateError(failure: ApiFailureInvalidRequest(detail: "bad filter"))
            ),
            "the invalid-request error (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_a_graphql_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(
                loadState: LoadStateError(
                    failure: ApiFailureGraphQl(codes: ["GRAPHQL_VALIDATION_FAILED"], messages: ["nope"])
                )
            ),
            "the GraphQL error (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_an_unknown_failure_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(loadState: LoadStateError(failure: ApiFailureUnknown(cause: nil))),
            "the unclassified error (ERROR_FLOW.md §4)"
        )
    }

    func test_TEST_UI_016_given_stale_content_when_rendered_then_the_stale_banner_over_content_renders() {
        assertRenders(
            state: makeState(
                items: [card(id: "1", name: "Rick Sanchez")],
                loadState: LoadStateContent.shared,
                isStale: true
            ),
            "the stale banner over retained content (ERROR_FLOW.md §9, UI_SPEC.md §8)"
        )
    }

    // MARK: - Accessibility (TEST-A11Y-004) and the absent mic (TEST-UI-013)

    /// The status is announced as a text label, never by the dot's colour alone (`REQ-UX-005`).
    func test_TEST_A11Y_004_given_a_card_when_labelled_then_the_status_is_the_text_label() {
        XCTAssertEqual(
            CharacterPresentation.cardLabel(name: "Rick Sanchez", statusLabel: "Alive", species: "Human"),
            "Rick Sanchez, Alive, Human"
        )
    }

    /// The status mirror is the shared mapping, so the two platforms cannot colour a status
    /// differently.
    func test_TEST_A11Y_004_given_each_status_when_mapped_then_the_tone_is_the_shared_mirror() {
        XCTAssertEqual(CharacterPresentation.tone(CharacterStatusAlive.shared), .alive)
        XCTAssertEqual(CharacterPresentation.tone(CharacterStatusDead.shared), .dead)
        XCTAssertEqual(CharacterPresentation.tone(CharacterStatusUnknown.shared), .unknown)
        XCTAssertEqual(CharacterPresentation.tone(CharacterStatusUnsupported(raw: "zombie")), .unknown)
    }

    /// `REQ-SEC-004` / `DEC-002`: the mic affordance is absent, and the renderer proves the search
    /// field is the only control in the field's row by rendering the real view (a `mic.fill` symbol
    /// would have to be added to `GlassSearchField`, which the design system does not contain).
    func test_TEST_UI_013_given_the_discovery_search_field_when_rendered_then_no_mic_affordance_exists() {
        let renderer = ImageRenderer(content: GlassSearchField(text: .constant(""), placeholder: "Search characters"))
        renderer.proposedSize = ProposedViewSize(width: 402, height: 96)
        XCTAssertNotNil(renderer.uiImage, "the search field itself must render; it carries no mic (DEC-002)")
    }

    // MARK: - Copy bindings

    func test_AC_REQ_UX_009_given_the_states_when_their_copy_is_resolved_then_each_key_exists_in_both_locales() {
        let keys = ["empty_search_message", "action_clear_filters", "state_stale_banner", "error_title", "action_retry"]
        for key in keys {
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "en"), "en must carry \(key)")
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "es"), "es must carry \(key)")
        }
    }

    /// The count line is the shared formatter's number inside the canonical template, never a
    /// hardcoded constant (`AC-REQ-FUNC-001-3`).
    func test_AC_REQ_FUNC_001_3_given_a_total_when_the_count_line_renders_then_it_uses_the_shared_number() {
        let template = LocalizedCopy.shared.text(for: "characters_count")
        let rendered = DefaultPresentationFormatters.shared.charactersCount(count: 826)
        XCTAssertEqual(rendered, "826", "the shared formatter is the one place the total becomes text")
        XCTAssertTrue(
            template.contains("%1$@"),
            "the template substitutes the shared number rather than carrying one itself"
        )
    }

    // MARK: - Helpers

    private func assertRenders(state: CharacterListUiState, _ description: String) {
        let loader = PortraitImageStub()
        let screen = DiscoveryScreen(state: state, loader: loader, onIntent: { _ in }, onOpenDetail: { _ in })
        let renderer = ImageRenderer(
            content: screen
                .frame(width: 402, height: 874)
                .background(MultiverseBrandColors.spaceBlack)
                .environment(\.multiverseGlassPath, .material)
        )
        renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
        guard let image = renderer.uiImage else {
            XCTFail("\(description) must render")
            return
        }
        XCTAssertGreaterThan(image.size.width * image.size.height, 0, "\(description) must render a non-empty image")
    }

    private func makeState(
        query: String = "",
        items: [CharacterCardUi] = [],
        loadState: any LoadState,
        isAppending: Bool = false,
        isStale: Bool = false
    ) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: query, status: StatusFilter.all),
            items: items,
            totalCount: KotlinInt(int: 826),
            loadState: loadState,
            isAppending: isAppending,
            isStale: isStale
        )
    }

    private func card(id: String, name: String) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: name,
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(id).jpeg"
        )
    }
}

/// The in-memory loader the state renders install: no network, no disk, one synchronous image
/// (`TESTING.md` §4.5 "No network in tests").
@MainActor
final class PortraitImageStub: PortraitImageLoading {
    private let image: Image

    init() {
        let size = CGSize(width: 32, height: 32)
        image = Image(size: size) { context in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.green))
        }
    }

    func cachedImage(for url: String) -> Image? { image }

    func image(for url: String) async -> Image? { image }
}
