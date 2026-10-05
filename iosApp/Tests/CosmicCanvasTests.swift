@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-036` — the cosmic canvas behind every top-level screen (`UI_SPEC.md` §3.2, Figma `29:383`,
/// `29:385`, `102:322`, `TASK-114`).
///
/// Discovery drew on the system background and Favorites, Episodes and Settings on the M3 Surface
/// `#0F0E12`, where Figma draws Space Black `#07060B` under a Cosmic Violet glow centred near the
/// top-leading corner and a Portal Green one off the trailing edge. Each screen is rendered alone, in
/// a window, over plain black, so the colours sampled can only come from the screen's own canvas.
@MainActor
final class CosmicCanvasTests: XCTestCase {
    func test_TEST_UI_036_given_each_top_level_screen_when_rendered_then_it_draws_on_the_cosmic_canvas() throws {
        for (name, screen) in screens() {
            let image = try HostedRendering.render(UIHostingController(rootView: screen.background(Color.black)))

            // 8 pt in from the leading edge, clear of every screen's content, 73 pt from the violet
            // glow's centre at (50, 90): the glow is still at about 43 % there.
            let glow = try HostedRendering.color(in: image, at: CGPoint(x: 8, y: 150))
            XCTAssertGreaterThan(glow.blue, 80, "\(name): the top-leading corner carries the Cosmic Violet glow")
            XCTAssertGreaterThan(glow.blue - glow.red, 30, "\(name): the glow there is violet, not grey")

            // The bottom-leading corner is beyond both glows, so it is the canvas itself: Space Black.
            let canvas = try HostedRendering.color(in: image, at: CGPoint(x: 8, y: 860))
            XCTAssertLessThanOrEqual(canvas.red, 10, "\(name): the canvas is Space Black #07060B, not Surface #0F0E12")
        }
    }

    private func screens() -> [(String, AnyView)] {
        var screens: [(String, AnyView)] = []
        screens.append(("Discovery", AnyView(discovery())))
        screens.append(
            (
                "Favorites",
                AnyView(
                    FavoritesScreen(
                        state: FavoritesUiState(items: [], loadState: LoadStateEmpty.shared),
                        loader: PortraitImageStub(),
                        onIntent: { _ in },
                        onCharacterSelected: { _ in },
                        onBrowseCharacters: {}
                    )
                )
            )
        )
        screens.append(("Episodes", AnyView(EpisodesPlaceholderScreen(onBrowseCharacters: {}))))
        screens.append(
            (
                "Settings",
                AnyView(
                    SettingsScreen(
                        state: SettingsUiState(
                            soundsEnabled: false,
                            remoteProtocol: RemoteProtocol.rest,
                            canDeleteFavorites: false,
                            isConfirmingDelete: false
                        ),
                        onIntent: { _ in }
                    )
                )
            )
        )
        return screens
    }

    private func discovery() -> DiscoveryScreen {
        let card = CharacterCardUi(
            id: "1",
            name: "Rick Sanchez",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        let state = CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: [card],
            totalCount: KotlinInt(int: 826),
            loadState: LoadStateContent.shared,
            isAppending: false,
            isStale: false,
            contentFailure: nil,
            isRefreshing: false
        )
        return DiscoveryScreen(state: state, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
    }
}
