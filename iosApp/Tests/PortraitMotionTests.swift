@testable import MultiverseApp
import XCTest

/// `TEST-UI-008` and `TEST-A11Y-006` on iOS (`REQ-FUNC-009`, `REQ-UX-007`,
/// `AC-REQ-FUNC-009-2`, `AC-REQ-UX-007-1`, `TASK-057`).
///
/// The cases assert the **decision** the motion contract makes, which is the part a snapshot cannot
/// see: with Reduce Motion on, the zoom and the parallax are off and the cross-fade is what runs, with
/// its own shorter duration; with it off, the zoom runs at the documented duration. The rendered
/// motion itself is carried by the snapshot variants `TASK-059` commits and by the recorded
/// checklist (`TESTING.md` §9.2).
final class PortraitMotionTests: XCTestCase {
    func test_TEST_UI_008_given_reduce_motion_off_when_the_transition_is_chosen_then_the_zoom_runs() {
        XCTAssertTrue(PortraitMotion.usesZoomTransition(reduceMotion: false))
        XCTAssertTrue(PortraitMotion.usesParallax(reduceMotion: false))
        XCTAssertEqual(PortraitMotion.transitionSeconds, 0.450, accuracy: 0.001)
    }

    func test_TEST_UI_008_given_reduce_motion_when_the_transition_is_chosen_then_the_zoom_is_replaced() {
        XCTAssertFalse(
            PortraitMotion.usesZoomTransition(reduceMotion: true),
            "the zoom must be replaced, not merely shortened (AC-REQ-UX-007-1)"
        )
        XCTAssertFalse(PortraitMotion.usesParallax(reduceMotion: true))
        XCTAssertEqual(PortraitMotion.reducedMotionCrossfadeSeconds, 0.300, accuracy: 0.001)
        XCTAssertLessThan(
            PortraitMotion.reducedMotionCrossfadeSeconds,
            PortraitMotion.transitionSeconds,
            "the cross-fade covers no distance and is shorter for it"
        )
    }

    func test_TEST_A11Y_006_given_reduce_motion_when_the_splash_is_considered_then_it_pulses_instead_of_spinning() {
        XCTAssertTrue(PortraitMotion.splashPulsesInsteadOfSpinning(reduceMotion: true))
        XCTAssertFalse(PortraitMotion.splashPulsesInsteadOfSpinning(reduceMotion: false))
    }

    func test_TEST_UI_008_given_a_character_when_the_shared_key_is_built_then_it_comes_from_the_canonical_id() {
        XCTAssertEqual(
            PortraitMotion.sharedKey(characterID: "1"),
            "portrait-1",
            "the key must derive from IC-001's id, never from a list position"
        )
        XCTAssertNotEqual(
            PortraitMotion.sharedKey(characterID: "1"),
            PortraitMotion.sharedKey(characterID: "2"),
            "two characters must never share a key, or the transition would fly to the wrong portrait"
        )
    }

    func test_TEST_A11Y_006_given_the_preference_seam_when_read_then_the_branch_needs_no_system() {
        struct StubPreference: MotionPreference {
            let reduceMotionEnabled: Bool
        }
        XCTAssertTrue(StubPreference(reduceMotionEnabled: true).reduceMotionEnabled)
        XCTAssertFalse(StubPreference(reduceMotionEnabled: false).reduceMotionEnabled)
        _ = SystemMotionPreference()
    }
}
