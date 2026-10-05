@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-038` — the iOS Detail as Figma `26:452` draws it (`UI_SPEC.md` §4.2, §5.3, §6.3,
/// `TASK-114`).
///
/// The title block sat below a sharp 402 × 520 hero instead of over its lower part; the stats were
/// Android's coloured tiles instead of the frosted panel's plain row with the Episodes value in Portal
/// Glow; and the info rows' symbol wells were rounded squares instead of 38 pt circles. The portrait
/// stub is solid green, so near-white pixels inside the hero can only be the title's.
@MainActor
final class DetailFidelityTests: XCTestCase {
    func test_TEST_UI_038_given_detail_content_when_laid_out_then_the_title_is_over_the_hero() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: detail()))

        // The hero's lower part (Figma: the title block spans y 358–479 of the 520 pt hero), clear of
        // the top controls.
        let title = try HostedRendering.matchingPixels(in: image, region: CGRect(x: 0, y: 300, width: 402, height: 220)) {
            red, green, blue in red > 200 && green > 200 && blue > 200
        }
        XCTAssertGreaterThan(title.count, 2_000, "the name is painted over the hero, not below it")
    }

    func test_TEST_UI_038_given_detail_content_when_laid_out_then_the_stats_are_a_row_not_tiles() throws {
        // No info rows, whose symbols are Portal Glow too, so the colour can only be the Episodes value's.
        let image = try HostedRendering.render(UIHostingController(rootView: detail(withRows: false)))

        // Portal Glow #C6FF6B.
        let glow = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            green > 235 && (170...225).contains(red) && (70...140).contains(blue)
        }
        XCTAssertGreaterThan(glow.count, 150, "the Episodes value is set in Portal Glow")
        // Primary Container #497401 was the Episodes tile's fill.
        let tile = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            abs(red - 0x49) < 12 && abs(green - 0x74) < 12 && blue < 14
        }
        XCTAssertLessThan(tile.count, 30, "no Android stat tile is drawn on iOS")
    }

    func test_TEST_UI_038_given_an_info_row_when_rendered_then_its_symbol_well_is_a_circle() throws {
        let row = GlassInfoRow(symbol: "globe.americas.fill", label: "Origin", value: "Earth (C-137)")
            .padding(40)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
            .environment(\.multiverseGlassPath, .material)
        let image = try HostedRendering.render(UIHostingController(rootView: row))

        // The well is the only green-tinted shape; its bounds are the 38 pt square it is drawn in.
        let tinted = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            green > red + 10 && green > blue + 15
        }
        let well = try XCTUnwrap(tinted.bounds, "the symbol well must paint")
        XCTAssertEqual(well.width, MultiverseDimensions.infoRowSymbolWell, accuracy: 2)

        // 4 pt in from the well's top-leading corner: inside a 12 pt rounded square, outside a circle.
        let corner = try HostedRendering.color(in: image, at: CGPoint(x: well.minX + 4, y: well.minY + 4))
        let inside = try HostedRendering.color(in: image, at: CGPoint(x: well.minX + 4, y: well.midY))
        let outside = try HostedRendering.color(in: image, at: CGPoint(x: well.minX - 6, y: well.midY))
        XCTAssertLessThan(
            distance(corner, outside),
            distance(corner, inside),
            "the well's corner is the background, so the well is a circle"
        )
    }

    private func distance(_ lhs: (red: Int, green: Int, blue: Int), _ rhs: (red: Int, green: Int, blue: Int)) -> Int {
        abs(lhs.red - rhs.red) + abs(lhs.green - rhs.green) + abs(lhs.blue - rhs.blue)
    }

    private func detail(withRows: Bool = true) -> some View {
        let header = CharacterCardUi(
            id: "1",
            name: "Rick Sanchez",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        var rows: [InfoRowUi] = []
        if withRows {
            rows.append(
            InfoRowUi(
                kind: InfoRowKind.origin,
                copyKey: CopyKeys.shared.DETAIL_INFO_ORIGIN,
                value: DisplayTextData(value: "Earth (C-137)")
            )
            )
        }
        let state = CharacterDetailUiState(
            header: header,
            gender: CopyKeys.shared.GENDER_MALE,
            episodeCount: KotlinInt(int: 51),
            dimension: "C-137",
            info: rows,
            isFavorite: false,
            loadState: LoadStateContent.shared
        )
        return CharacterDetailScreen(state: state, loader: PortraitImageStub(), onIntent: { _ in }, onBack: {}, onShare: {})
            .environment(\.multiverseGlassPath, .material)
    }
}
