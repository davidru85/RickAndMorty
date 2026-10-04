@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// The detail screen lays out its hero, title and panel on the phone canvas (`UI_SPEC.md` §6.3,
/// `REQ-FUNC-002`, `AC-REQ-FUNC-002-1`, `TASK-055`).
///
/// The hero is a 402 × 520 portrait with the back, share and favourite controls over it, and the
/// character's name and the info panel follow it. A hero whose aspect-ratio frame fills an unbounded
/// height covers the whole screen with the portrait, so the name and every control disappear; the
/// case renders the real screen in a window and requires the name to be painted.
@MainActor
final class DetailLayoutTests: XCTestCase {
    func test_TEST_UI_002_given_detail_content_when_laid_out_then_the_title_is_painted_below_the_hero() throws {
        let header = CharacterCardUi(
            id: "1",
            name: "Rick Sanchez",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        let state = CharacterDetailUiState(
            header: header,
            episodeCount: KotlinInt(int: 51),
            dimension: "Dimension C-137",
            info: [],
            isFavorite: true,
            loadState: LoadStateContent.shared
        )
        let screen =
            CharacterDetailScreen(
                state: state,
                loader: PortraitImageStub(),
                onIntent: { _ in },
                onBack: {},
                onShare: {}
            )
            .background(MultiverseBrandColors.spaceBlack)

        let image = try HostedRendering.render(UIHostingController(rootView: screen))

        // The portrait stub is solid green, so near-white pixels can only come from the title, the
        // stats and the controls' symbols — none of which paint when the hero covers the screen.
        let bright = try HostedRendering.nearWhitePixels(in: image)
        XCTAssertGreaterThan(bright, 2_000, "the title and panel must be visible")
    }
}
