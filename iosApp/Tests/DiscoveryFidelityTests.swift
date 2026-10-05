@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-037` — Discovery's grid geometry and the brand mark on its empty and failed surfaces
/// (`UI_SPEC.md` §4.2, §5.1, §8, Figma `29:381`, `DEC-139`, `TASK-114`).
///
/// The grid sat 12 pt in from each edge where Figma places the cards at x = 16 and x = 209, 177 pt
/// wide; the empty search showed `person.2.fill` and a failed portrait the SF Symbol `circle.circle`,
/// where the specification shows the brand's portal logo at 40 %.
@MainActor
final class DiscoveryFidelityTests: XCTestCase {
    func test_TEST_UI_037_given_content_when_laid_out_then_the_cards_sit_on_16_pt_margins() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: discovery(cards: 4, load: LoadStateContent.shared)))

        // The stub portraits are solid system green; the cards' outer edges are the grid's margins.
        let cards = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            green > 150 && green - red > 60 && green - blue > 60
        }
        let bounds = try XCTUnwrap(cards.bounds, "the cards' portraits must paint")
        XCTAssertEqual(bounds.minX, 16, accuracy: 1, "the leading column starts 16 pt in (Figma x = 16)")
        XCTAssertEqual(bounds.maxX, 402 - 16, accuracy: 1, "the trailing column ends 16 pt in (Figma x = 209 + 177)")
    }

    func test_TEST_UI_037_given_favourites_when_laid_out_then_the_cards_sit_on_16_pt_margins() throws {
        let cards = (1...4).map { index in
            CharacterCardUi(
                id: "\(index)",
                name: "Rick Sanchez",
                species: DisplayTextData(value: "Human"),
                status: CharacterStatusAlive.shared,
                statusLabel: CopyKeys.shared.STATUS_ALIVE,
                imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(index).jpeg"
            )
        }
        let favorites = FavoritesScreen(
            state: FavoritesUiState(items: cards, loadState: LoadStateContent.shared),
            loader: PortraitImageStub(),
            onIntent: { _ in },
            onCharacterSelected: { _ in },
            onBrowseCharacters: {}
        )
        let image = try HostedRendering.render(UIHostingController(rootView: favorites))

        let portraits = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            green > 150 && green - red > 60 && green - blue > 60
        }
        let bounds = try XCTUnwrap(portraits.bounds, "the favourites' portraits must paint")
        XCTAssertEqual(bounds.minX, 16, accuracy: 1, "Favorites shares Discovery's 16 pt margins (Figma 102:375)")
        XCTAssertEqual(bounds.maxX, 402 - 16, accuracy: 1)
    }

    func test_TEST_UI_037_given_an_empty_search_when_rendered_then_the_portal_logo_is_its_illustration() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: discovery(cards: 0, load: LoadStateEmpty.shared)))

        // The centre column below the filters, clear of the green glow off the trailing edge; only the
        // dimmed greens of a mark at 40 % count, so the bright "Clear filters" button does not.
        let logo = try HostedRendering.matchingPixels(in: image, region: CGRect(x: 120, y: 300, width: 162, height: 500)) {
            red, green, blue in Self.isPortalGreen(red, green, blue) && green < 140
        }
        XCTAssertGreaterThan(logo.count, 8_000, "the empty search shows the portal logo, not a person symbol")
    }

    func test_TEST_UI_037_given_a_failed_portrait_when_rendered_then_the_portal_logo_is_centred_on_it() throws {
        let portrait = CharacterPortrait(url: "", loader: PortraitImageStub())
            .frame(width: 177, height: 236)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
        let image = try HostedRendering.render(UIHostingController(rootView: portrait))

        let logo = try HostedRendering.matchingPixels(in: image) { red, green, blue in Self.isPortalGreen(red, green, blue) }
        let bounds = try XCTUnwrap(logo.bounds, "a failed portrait shows a mark")
        XCTAssertEqual(bounds.midX, 201, accuracy: 4, "the mark is centred on the portrait")
        // The logo is a filled disc, which covers most of its bounds; the `circle.circle` symbol is two
        // thin rings, which cover about a third.
        let coverage = Double(logo.count) / Double(bounds.width * bounds.height * image.scale * image.scale)
        XCTAssertGreaterThan(coverage, 0.45, "a failed portrait shows the filled portal logo, not a ring symbol")
    }

    func test_TEST_UI_037_given_a_selected_segment_when_rendered_then_its_label_is_white_on_green_glass() throws {
        var options: [GlassSegmentedControl.Option] = []
        options.append(.init(id: "all", label: "All"))
        options.append(.init(id: "alive", label: "Alive"))
        options.append(.init(id: "dead", label: "Dead"))
        options.append(.init(id: "unknown", label: "Unknown"))
        let control = GlassSegmentedControl(selection: .constant("all"), options: options)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(MultiverseBrandColors.spaceBlack)
        let image = try HostedRendering.render(UIHostingController(rootView: control))

        // The selected segment is the leading quarter of the 370 pt control centred on the canvas.
        let segment = CGRect(x: 16, y: 437 - 22, width: 370 / 4, height: 44)
        let label = try HostedRendering.matchingPixels(in: image, region: segment) { red, green, blue in
            red > 200 && green > 200 && blue > 200
        }
        XCTAssertGreaterThan(label.count, 60, "the selected label is white (Figma 29:419), not Space Black")
    }

    func test_TEST_UI_037_given_an_empty_state_when_rendered_then_its_illustration_well_is_a_circle() throws {
        let empty = EmptyState(symbol: "heart", heading: "No favorites yet", body: "Tap the heart.", actionLabel: "Browse") {}
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
            .environment(\.multiverseGlassPath, .material)
        let image = try HostedRendering.render(UIHostingController(rootView: empty))

        // The well is the topmost painted shape; its rim and material are lighter than the black around
        // it. 10 pt in from its bounds' top-leading corner is inside a 28 pt rounded square but outside
        // a 120 pt circle (Figma `102:256`).
        let painted = try HostedRendering.matchingPixels(in: image) { red, green, blue in red + green + blue > 60 }
        let top = try XCTUnwrap(painted.bounds)
        let well = try XCTUnwrap(
            try HostedRendering.matchingPixels(
                in: image,
                region: CGRect(x: 0, y: top.minY, width: 402, height: MultiverseDimensions.emptyStateWell)
            ) { red, green, blue in red + green + blue > 60 }.bounds
        )
        XCTAssertEqual(well.width, MultiverseDimensions.emptyStateWell, accuracy: 4)
        let corner = try HostedRendering.color(in: image, at: CGPoint(x: well.minX + 10, y: well.minY + 10))
        XCTAssertLessThan(corner.red + corner.green + corner.blue, 30, "the well's corner is the black behind it")
    }

    /// The portal logo's greens at 40 % over the dark canvas: green leads red and blue clearly.
    private static func isPortalGreen(_ red: Int, _ green: Int, _ blue: Int) -> Bool {
        green > 50 && green > red + 15 && green > blue + 30
    }

    private func discovery(cards count: Int, load: any LoadState) -> DiscoveryScreen {
        let cards = (0..<count).map { index in
            CharacterCardUi(
                id: "\(index + 1)",
                name: "Rick Sanchez",
                species: DisplayTextData(value: "Human"),
                status: CharacterStatusAlive.shared,
                statusLabel: CopyKeys.shared.STATUS_ALIVE,
                imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(index + 1).jpeg"
            )
        }
        let state = CharacterListUiState(
            filter: CharacterFilter(query: count == 0 ? "zzz" : "", status: StatusFilter.all),
            items: cards,
            totalCount: KotlinInt(int: 826),
            loadState: load,
            isAppending: false,
            isStale: false,
            contentFailure: nil,
            isRefreshing: false
        )
        return DiscoveryScreen(state: state, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
    }
}
