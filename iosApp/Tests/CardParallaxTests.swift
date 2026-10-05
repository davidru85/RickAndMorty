@testable import MultiverseApp
import XCTest

/// `TEST-UNIT-091` — the glass card portrait's scroll parallax (`UI_SPEC.md` §4.2, §7, `TASK-114`).
///
/// The portrait is overscanned by 14 pt and moves at 0.85× the scroll, so inside its card it drifts by
/// 0.15× the card's distance from the viewport's centre, never past the overscan; Reduce Motion
/// disables it. The card had neither.
final class CardParallaxTests: XCTestCase {
    func test_TEST_UNIT_091_given_a_card_at_the_centre_when_offset_then_the_portrait_is_centred() {
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: 0, reduceMotion: false), 0)
    }

    func test_TEST_UNIT_091_given_a_card_off_centre_when_offset_then_the_portrait_lags_by_15_percent() {
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: 40, reduceMotion: false), -6, accuracy: 0.001)
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: -40, reduceMotion: false), 6, accuracy: 0.001)
    }

    func test_TEST_UNIT_091_given_a_far_card_when_offset_then_the_portrait_stays_within_the_overscan() {
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: 400, reduceMotion: false), -14)
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: -400, reduceMotion: false), 14)
        XCTAssertEqual(CardParallax.overscan, 14, "the overscan is the spec's 14 pt")
    }

    func test_TEST_UNIT_091_given_reduce_motion_when_offset_then_the_portrait_does_not_move() {
        XCTAssertEqual(CardParallax.offset(distanceFromCenter: 120, reduceMotion: true), 0)
    }
}
