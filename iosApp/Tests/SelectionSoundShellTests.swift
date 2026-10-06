@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-051` and `TEST-UI-052` on iOS — when the shell asks for the selection sound (`REQ-FUNC-036`,
/// `AC-REQ-FUNC-036-1`, `AC-REQ-FUNC-036-2`, `TASK-139`, `DEC-162`).
///
/// The system tab bar and the glass segmented control cannot be tapped from a unit test, so the cases assert
/// the two mappings the controls call: the shell's tab selection, which asks for the sound on a change of
/// destination only, and the selector's tap, which reports every tap, the option already selected included.
/// Whether the sound then plays is `TEST-UNIT-109`'s.
@MainActor
final class SelectionSoundShellTests: XCTestCase {
    func test_TEST_UI_051_given_the_tab_bar_when_the_destination_changes_then_the_sound_plays_and_otherwise_it_does_not() {
        let sound = RecordingSelectionSound()
        let navigation = ShellNavigation(selected: .characters, selectionSound: sound)

        navigation.selectFromTabBar(.episodes)
        XCTAssertEqual(sound.plays, 1, "TEST-UI-051: Characters to Episodes is a change of destination")
        navigation.selectFromTabBar(.episodes)
        XCTAssertEqual(sound.plays, 1, "TEST-UI-051: the current destination tapped again changes nothing")
        navigation.selectFromTabBar(.favorites)
        navigation.selectFromTabBar(.settings)
        navigation.selectFromTabBar(.characters)
        XCTAssertEqual(sound.plays, 4, "TEST-UI-051: each further change plays once")

        navigation.selectFromTabBar(.episodes)
        navigation.browseCharacters()
        XCTAssertEqual(navigation.selected, .characters)
        XCTAssertEqual(sound.plays, 5, "TEST-UI-051: Browse characters is the app's selection, not the bar's")

        navigation.playSelectionSound()
        XCTAssertEqual(sound.plays, 6, "TEST-UI-051: a status tap asks the shell's one sound")
    }

    func test_TEST_UI_052_given_the_status_selector_when_an_option_is_tapped_then_every_tap_is_reported_the_selected_one_included() {
        var sent: [any CharacterListIntent] = []
        var reported: [StatusFilter] = []
        let taps = [StatusFilter.all, .all, .alive, .alive, .dead, .unknown]

        for status in taps {
            DiscoveryScreen.selectStatus(
                CharacterPresentation.filterIdentifier(status),
                onIntent: { sent.append($0) },
                onStatusSelected: { reported.append($0) }
            )
        }
        DiscoveryScreen.selectStatus("not a status", onIntent: { sent.append($0) }, onStatusSelected: { reported.append($0) })

        XCTAssertEqual(reported, taps, "TEST-UI-052: one report per tap, in order, a repeated tap included")
        XCTAssertEqual(
            sent.compactMap { ($0 as? CharacterListIntentStatusSelected)?.status },
            taps,
            "TEST-UI-052: each tap still sends its intent"
        )
    }
}
