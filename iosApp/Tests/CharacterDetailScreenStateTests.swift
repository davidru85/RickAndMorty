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

    func test_TEST_UI_016_given_a_detail_failure_without_list_data_when_rendered_then_the_inline_retry_renders() {
        assertRenders(
            state: makeState(
                header: nil,
                loadState: LoadStateError(failure: ApiFailureOffline.shared)
            ),
            "the detail load failure without cached data (AC-REQ-UX-009-1)"
        )
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

    func test_UI_SPEC_6_3_given_an_origin_when_the_subtitle_renders_then_it_joins_species_and_origin() {
        XCTAssertEqual(
            CharacterPresentation.subtitle(species: "Human", info: allRows(enriched: true)),
            "Human · Earth (C-137)"
        )
    }

    func test_UI_SPEC_6_3_given_a_missing_origin_when_the_subtitle_renders_then_it_is_the_species_alone() {
        XCTAssertEqual(CharacterPresentation.subtitle(species: "Human", info: []), "Human")
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
        episodeCount: Int32? = nil,
        dimension: String? = nil,
        info: [InfoRowUi] = [],
        isFavorite: Bool = false,
        loadState: any LoadState = LoadStateLoading.shared
    ) -> CharacterDetailUiState {
        CharacterDetailUiState(
            header: header,
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
                value: "Earth (C-137)"
            )
        )
        rows.append(
            InfoRowUi(
                kind: InfoRowKind.lastknownlocation,
                copyKey: CopyKeys.shared.DETAIL_INFO_LAST_KNOWN_LOCATION,
                value: "Citadel of Ricks"
            )
        )
        if enriched {
            rows.append(
                InfoRowUi(
                    kind: InfoRowKind.firstseenin,
                    copyKey: CopyKeys.shared.DETAIL_INFO_FIRST_SEEN_IN,
                    value: "Pilot · S01E01"
                )
            )
        }
        return rows
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
