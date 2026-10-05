@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-023` — the Detail's informative "Appears in N episodes" line (`UI_SPEC.md` §6.3,
/// `DEC-132`, `TASK-112`).
///
/// The line is plural copy: the count picks the form in each locale, from the app's own
/// `Localizable.stringsdict`, which `TEST-UNIT-082` holds identical to the Android `<plurals>`. It
/// renders only when the Detail knows the count, so it never shows a placeholder number.
@MainActor
final class EpisodeCountLineTests: XCTestCase {
    func test_TEST_UI_023_given_english_when_the_line_resolves_then_one_and_other_read_their_own_form() {
        XCTAssertEqual(CharacterPresentation.episodeCountLine(1), "Appears in 1 episode")
        XCTAssertEqual(CharacterPresentation.episodeCountLine(51), "Appears in 51 episodes")
    }

    func test_TEST_UI_023_given_the_spanish_resources_when_the_line_resolves_then_it_is_translated() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "es", ofType: "lproj"), "the app bundle ships es")
        let spanish = LocalizedCopy(bundle: try XCTUnwrap(Bundle(path: path)))

        XCTAssertEqual(CharacterPresentation.episodeCountLine(1, copy: spanish), "Aparece en 1 episodio")
        XCTAssertEqual(CharacterPresentation.episodeCountLine(51, copy: spanish), "Aparece en 51 episodios")
    }

    func test_TEST_UI_023_given_the_registered_plural_key_when_it_crosses_the_boundary_then_it_is_the_resource_name() {
        XCTAssertEqual(
            CharacterPresentation.key(CopyKeys.shared.DETAIL_APPEARS_IN_EPISODES),
            "detail_appears_in_episodes"
        )
    }
}
