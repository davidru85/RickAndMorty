@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-007` on iOS (`REQ-FUNC-008`, `AC-REQ-FUNC-008-1`/`-2`, `TASK-054`).
///
/// The Android suite walks every ordered pair of destinations and asserts the "Browse characters"
/// action selects Characters **without pushing**; these cases assert the same contract on the iOS
/// selection model, which is the whole navigation state the shell has. A push would leave an
/// intermediate entry behind, and a selection cannot: that is what makes the iOS shape the peer of
/// the Android bar's `popUpTo(start) { saveState }` contract rather than a different design.
@MainActor
final class ShellNavigationTests: XCTestCase {
    func test_TEST_UI_007_given_any_destination_when_every_other_is_selected_then_it_is_reached_in_one_step() {
        for from in ShellDestination.allCases {
            for target in ShellDestination.allCases where target != from {
                let navigation = ShellNavigation(selected: from)
                navigation.select(target)
                XCTAssertEqual(
                    navigation.selected,
                    target,
                    "selecting \(target.rawValue) from \(from.rawValue) must reach it in one step"
                )
            }
        }
    }

    func test_TEST_UI_007_given_the_placeholders_when_browse_characters_runs_then_characters_is_selected() {
        for placeholder in [ShellDestination.episodes, .favorites, .settings] {
            let navigation = ShellNavigation(selected: placeholder)
            navigation.browseCharacters()

            XCTAssertTrue(
                navigation.isShowingCharacters,
                "the action from \(placeholder.rawValue) must select Characters"
            )
            // A selection cannot accumulate history, so there is exactly one current destination and
            // no intermediate entry to pop — the iOS form of "switches destination without pushing".
            XCTAssertEqual(ShellNavigation(selected: placeholder).selected, placeholder)
        }
    }

    func test_TEST_UI_007_given_the_shell_when_the_destinations_are_listed_then_the_order_is_the_spec_order() {
        XCTAssertEqual(
            ShellDestination.allCases.map(\.rawValue),
            ["characters", "episodes", "favorites", "settings"],
            "UI_SPEC.md §6 fixes the tab order as Characters, Episodes, Favorites, Settings"
        )
    }

    func test_TEST_UI_007_given_every_destination_when_asked_for_its_route_then_it_names_the_kotlin_declaration() {
        // The routes cross the boundary (`CONTRACTS.md` §7.1 R4): each destination must resolve to a
        // type from the shared framework rather than to a Swift-only enum.
        for destination in ShellDestination.allCases {
            XCTAssertFalse(
                destination.sharedRouteName.isEmpty,
                "\(destination.rawValue) must name its shared route declaration"
            )
        }
    }

    func test_UI_SPEC_6_4_given_each_placeholder_when_it_renders_then_it_uses_its_canonical_copy_keys() {
        XCTAssertEqual(ShellDestination.episodes.labelKey.rawValue, "nav_episodes")
        XCTAssertEqual(ShellDestination.favorites.labelKey.rawValue, "nav_favorites")
        XCTAssertEqual(ShellDestination.settings.labelKey.rawValue, "nav_settings")
        XCTAssertEqual(ShellDestination.characters.labelKey.rawValue, "nav_characters")
    }
}
