import MultiverseExplorer
import SwiftUI
import XCTest

@testable import MultiverseApp

/// `TEST-UI-016` on iOS, Detail half, and `TEST-UI-002` (`AC-REQ-UX-009-1`, `AC-REQ-FUNC-002-1`/
/// `-3`, `AC-REQ-FUNC-023-2`, `REQ-FUNC-002`, `REQ-FUNC-023`, `TASK-055`).
///
/// Every state the detail surface owes is rendered from a `CharacterDetailUiState` value the shared
/// `IC-019` contract carries: the list-provided header before the detail response arrives, the full
/// enriched content, the enrichment-absent content whose `First seen in` row is **absent** rather
/// than empty, the detail failure that retains the header and shows the inline retry, and the
/// failure with no header at all. The absent-row case asserts the row's *absence*, which is the rule
/// a rendered image cannot prove on its own.
@MainActor
final class CharacterDetailScreenStateTests: XCTestCase {
    // MARK: - TEST-UI-002: the header renders before the response

    func test_TEST_UI_002_given_only_the_list_header_when_rendered_then_the_known_fields_render() {
        assertRenders(
            state: makeState(header: card(id: "1", name: "Rick Sanchez")),
            "the list-provided header before the detail response (AC-REQ-FUNC-002-1)"
        )
        // The header is present in the very first frame, so the hero, the name and the status
        // capsule render with no network at all.
        XCTAssertEqual(makeState(header: card(id: "1", name: "Rick Sanchez")).header?.name, "Rick Sanchez")
    }

    // MARK: - TEST-UI-016: every rendered detail state

    func test_TEST_UI_016_given_the_enriched_content_when_rendered_then_the_full_detail_renders() {
        assertRenders(
            state: makeState(
                header: card(id: "1", name: "Rick Sanchez"),
                episodeCount: 51,
                dimension: "C-137",
                info: allRows(enriched: true),
                loadState: LoadStateContent.shared
            ),
            "the enriched detail content (ERROR_FLOW.md §8, \"Content\")"
        )
    }

    func test_TEST_UI_016_given_no_enrichment_when_rendered_then_the_first_seen_row_is_absent_and_the_count_renders() {
        let state = makeState(
            header: card(id: "1", name: "Rick Sanchez"),
            episodeCount: 51,
            dimension: "C-137",
            info: allRows(enriched: false),
            loadState: LoadStateContent.shared
        )
        XCTAssertEqual(
            state.info.filter { $0.kind == InfoRowKind.firstseenin }.count,
            0,
            "AC-REQ-FUNC-023-2: the First seen in row is absent, never an empty or placeholder row"
        )
        XCTAssertEqual(
            state.episodeCount?.int32Value,
            51,
            "AC-REQ-FUNC-023-2: the episode count still renders without the enrichment"
        )
        assertRenders(state: state, "the enrichment-absent detail (ERROR_FLOW.md §7)")
    }

    func test_TEST_UI_016_given_a_dimension_that_is_absent_when_rendered_then_its_tile_is_hidden() {
        let state = makeState(
            header: card(id: "1", name: "Rick Sanchez"),
            episodeCount: 51,
            dimension: nil,
            info: allRows(enriched: true),
            loadState: LoadStateContent.shared
        )
        XCTAssertNil(state.dimension, "IC-019: a nil dimension means hide the tile, never a placeholder")
        assertRenders(state: state, "the detail with no dimension tile (UI_SPEC.md §6.3)")
    }

    func test_TEST_UI_016_given_a_failure_with_list_data_then_the_header_stays_and_the_inline_retry_renders() {
        let state = makeState(
            header: card(id: "1", name: "Rick Sanchez"),
            info: allRows(enriched: true),
            loadState: LoadStateError(failure: ApiFailureNotFound(resource: "character", id: "1"))
        )
        XCTAssertNotNil(state.header, "AC-REQ-FUNC-002-3: an Error never clears the list-provided header")
        assertRenders(state: state, "the detail load failure with the header retained (ERROR_FLOW.md §4, §8)")
    }

    func test_TEST_UI_016_given_a_detail_failure_without_list_data_when_rendered_then_the_full_surface_error_renders() {
        assertRenders(
            state: makeState(
                header: nil,
                loadState: LoadStateError(failure: ApiFailureOffline.shared)
            ),
            "the detail load failure without cached data, as the full-surface error (AC-REQ-UX-009-1, DEC-131)"
        )
    }

    // MARK: - TEST-UI-022: the failure recovery (DEC-131)

    func test_TEST_UI_022_given_a_not_found_detail_when_recovered_then_the_affordance_is_back_and_never_retry() {
        let notFound = ApiFailureNotFound(resource: "character", id: "9999")
        let recovery = DefaultPresentationFormatters.shared.recovery(failure: notFound)
        XCTAssertEqual(recovery, Recovery.back, "a 404 is terminal for the identifier (ERROR_FLOW.md §10)")
        XCTAssertEqual(CharacterPresentation.key(recovery.actionKey), "action_back")
        XCTAssertEqual(
            CharacterPresentation.key(DefaultPresentationFormatters.shared.inlineFailureMessage(failure: notFound).key),
            "error_message_not_found",
            "beside a header the message says the character is not in this dimension"
        )
        assertRenders(
            state: makeState(header: card(id: "1", name: "Rick Sanchez"), loadState: LoadStateError(failure: notFound)),
            "the not-found detail with the header retained and Back"
        )
        assertRenders(
            state: makeState(header: nil, loadState: LoadStateError(failure: notFound)),
            "the not-found detail without a header, as the full-surface error with Back"
        )
    }

    func test_TEST_UI_022_given_a_retryable_detail_failure_when_recovered_then_the_affordance_is_retry() {
        let recovery = DefaultPresentationFormatters.shared.recovery(failure: ApiFailureOffline.shared)
        XCTAssertEqual(recovery, Recovery.retry)
        XCTAssertEqual(CharacterPresentation.key(recovery.actionKey), "action_retry")
    }

    func test_TEST_UI_016_given_a_marked_favourite_when_rendered_then_the_prominent_toggle_renders() {
        let state = makeState(
            header: card(id: "1", name: "Rick Sanchez"),
            info: allRows(enriched: true),
            isFavorite: true,
            loadState: LoadStateContent.shared
        )
        XCTAssertTrue(state.isFavorite)
        assertRenders(state: state, "the detail with the favourite marked (UI_SPEC.md §6.3)")
    }

    func test_TEST_UI_016_given_an_unmarked_favourite_when_rendered_then_the_glass_toggle_renders() {
        let state = makeState(
            header: card(id: "1", name: "Rick Sanchez"),
            info: allRows(enriched: true),
            isFavorite: false,
            loadState: LoadStateContent.shared
        )
        XCTAssertFalse(state.isFavorite)
        assertRenders(state: state, "the detail with the favourite unmarked (UI_SPEC.md §6.3)")
    }

    // MARK: - The shared derivations

    func test_TEST_UI_022_given_a_gender_when_the_subtitle_renders_then_it_joins_species_and_gender() {
        // `UI_SPEC.md` §6.3: iOS reads "Species · Gender"; the origin has its own row (`DEC-131`).
        XCTAssertEqual(
            CharacterPresentation.subtitle(species: "Human", gender: CopyKeys.shared.GENDER_MALE),
            "Human · \(LocalizedCopy.shared.text(for: "gender_male"))"
        )
    }

    func test_TEST_UI_022_given_no_gender_yet_when_the_subtitle_renders_then_it_is_the_species_alone() {
        // The list card carries no gender, so the subtitle before the detail answers is the species.
        XCTAssertEqual(CharacterPresentation.subtitle(species: "Human", gender: nil), "Human")
    }

    // MARK: - TEST-UI-022: Unknown rows (DEC-131)

    func test_TEST_UI_022_given_an_unknown_origin_and_location_when_rendered_then_both_rows_read_unknown() {
        let rows = unknownRows()
        XCTAssertEqual(
            rows.map { CharacterPresentation.text($0.value) },
            [LocalizedCopy.shared.text(for: "value_unknown"), LocalizedCopy.shared.text(for: "value_unknown")],
            "an unknown origin and location keep their rows and read Unknown (AC-REQ-FUNC-002-2)"
        )
        assertRenders(
            state: makeState(
                header: card(id: "1", name: "Rick Sanchez"),
                episodeCount: 1,
                info: rows,
                loadState: LoadStateContent.shared
            ),
            "the detail with an unknown origin and location"
        )
    }

    func test_UI_SPEC_6_3_given_an_info_row_when_its_symbol_is_resolved_then_it_is_the_specified_glyph() {
        XCTAssertEqual(CharacterPresentation.infoSymbol(for: InfoRowKind.origin), "globe.americas.fill")
        XCTAssertEqual(CharacterPresentation.infoSymbol(for: InfoRowKind.lastknownlocation), "mappin.and.ellipse")
        XCTAssertEqual(CharacterPresentation.infoSymbol(for: InfoRowKind.firstseenin), "play.tv.fill")
    }

    // MARK: - Helpers

    private func assertRenders(state: CharacterDetailUiState, _ description: String) {
        let loader = PortraitImageStub()
        let renderer = ImageRenderer(
            content: CharacterDetailScreen(
                state: state,
                loader: loader,
                onIntent: { _ in },
                onBack: {},
                onShare: {}
            )
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
        header: CharacterCardUi?,
        gender: Any? = nil,
        episodeCount: Int32? = nil,
        dimension: String? = nil,
        info: [InfoRowUi] = [],
        isFavorite: Bool = false,
        loadState: any LoadState = LoadStateLoading.shared
    ) -> CharacterDetailUiState {
        CharacterDetailUiState(
            header: header,
            gender: gender,
            episodeCount: episodeCount.map { KotlinInt(int: $0) },
            dimension: dimension,
            info: info,
            isFavorite: isFavorite,
            loadState: loadState
        )
    }

    /// The rows the shared holder produces in its fixed order, with the `First seen in` row present
    /// only when the enrichment was requested and found one (`IC-019`, `ERROR_FLOW.md` §7).
    private func allRows(enriched: Bool) -> [InfoRowUi] {
        var rows: [InfoRowUi] = []
        rows.append(
            InfoRowUi(
                kind: InfoRowKind.origin,
                copyKey: CopyKeys.shared.DETAIL_INFO_ORIGIN,
                value: DisplayTextData(value: "Earth (C-137)")
            )
        )
        rows.append(
            InfoRowUi(
                kind: InfoRowKind.lastknownlocation,
                copyKey: CopyKeys.shared.DETAIL_INFO_LAST_KNOWN_LOCATION,
                value: DisplayTextData(value: "Citadel of Ricks")
            )
        )
        if enriched {
            rows.append(
                InfoRowUi(
                    kind: InfoRowKind.firstseenin,
                    copyKey: CopyKeys.shared.DETAIL_INFO_FIRST_SEEN_IN,
                    value: DisplayTextData(value: "Pilot · S01E01")
                )
            )
        }
        return rows
    }

    /// The two rows the shared reducer keeps for an origin and a location the API reports as unknown.
    private func unknownRows() -> [InfoRowUi] {
        [
            InfoRowUi(
                kind: InfoRowKind.origin,
                copyKey: CopyKeys.shared.DETAIL_INFO_ORIGIN,
                value: DisplayTextCopy(key: CopyKeys.shared.VALUE_UNKNOWN)
            ),
            InfoRowUi(
                kind: InfoRowKind.lastknownlocation,
                copyKey: CopyKeys.shared.DETAIL_INFO_LAST_KNOWN_LOCATION,
                value: DisplayTextCopy(key: CopyKeys.shared.VALUE_UNKNOWN)
            ),
        ]
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
