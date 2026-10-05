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
