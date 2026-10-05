@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-020`'s iOS half — the line the Detail's Share sends (`UI_SPEC.md` §6.3, `DEC-125`,
/// `TASK-111`).
///
/// The control used to call an empty closure. It now presents the system share sheet with one line:
/// the character's name and its API resource URL, which the shared `:core:data` builds from the
/// configured host so no Swift source names one.
@MainActor
final class CharacterShareTests: XCTestCase {
    func test_TEST_UI_020_given_a_character_when_its_share_line_is_built_then_it_names_it_and_its_resource() {
        XCTAssertEqual(
            CharacterShare.text(for: card()),
            "Rick Sanchez on Multiverse Explorer: https://rickandmortyapi.com/api/character/1"
        )
    }

    func test_TEST_UI_020_given_the_spanish_resources_when_the_share_line_is_built_then_it_is_translated() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "es", ofType: "lproj"), "the app bundle ships es")
        let spanish = LocalizedCopy(bundle: try XCTUnwrap(Bundle(path: path)))

        XCTAssertEqual(
            CharacterShare.text(for: card(), copy: spanish),
            "Rick Sanchez en Multiverse Explorer: https://rickandmortyapi.com/api/character/1"
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
