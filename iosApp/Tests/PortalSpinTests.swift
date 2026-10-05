@testable import MultiverseApp
import XCTest

/// `TEST-UNIT-092` — the iOS splash portal's rotation, the mirror of Android's `TEST-UNIT-086`
/// (`UI_SPEC.md` §6.1, §7, `REQ-FUNC-007`, `TASK-114`).
///
/// The iOS splash turned a ring linearly, one turn per 1.2 s, with no acceleration. The specification
/// turns the portal clockwise from 0° to 360° over 1.2 s on the ease-in cubic, then at a constant
/// ≈900°/s with no restart — the same curve on both platforms — and pulses its opacity instead under
/// Reduce Motion.
final class PortalSpinTests: XCTestCase {
    func test_TEST_UNIT_092_given_the_acceleration_when_the_angle_is_read_then_it_turns_clockwise_to_one_turn_at_1_2_s() {
        XCTAssertEqual(PortalSpin.angle(elapsed: 0), 0, accuracy: 1e-3)
        XCTAssertGreaterThan(PortalSpin.angle(elapsed: 0.6), 0, "the portal turns clockwise: positive degrees")
        XCTAssertLessThan(PortalSpin.angle(elapsed: 0.6), 360 * 0.35, "the ease-in has advanced little at mid-acceleration")
        XCTAssertEqual(PortalSpin.angle(elapsed: 1.2), 360, accuracy: 0.5, "one full turn when the acceleration ends")
    }

    func test_TEST_UNIT_092_given_the_end_of_the_acceleration_when_the_speed_is_read_then_it_continues_without_a_jump() {
        let before = (PortalSpin.angle(elapsed: 1.2) - PortalSpin.angle(elapsed: 1.19)) / 0.01
        let after = (PortalSpin.angle(elapsed: 1.21) - PortalSpin.angle(elapsed: 1.2)) / 0.01
        XCTAssertEqual(before, after, accuracy: 60, "the speed is continuous where the acceleration hands over (°/s)")
    }

    func test_TEST_UNIT_092_given_the_constant_phase_when_the_angle_is_read_then_it_keeps_the_documented_speed() {
        for start in [2.0, 5.0, 60.0] {
            let perSecond = PortalSpin.angle(elapsed: start + 1) - PortalSpin.angle(elapsed: start)
            XCTAssertEqual(perSecond, 900, accuracy: 15, "≈900°/s from \(start) s on (UI_SPEC.md §7)")
        }
    }

    func test_TEST_UNIT_092_given_reduce_motion_when_the_pulse_is_read_then_it_breathes_between_60_and_100_percent() {
        XCTAssertEqual(PortalSpin.pulse(elapsed: 0), 0.6, accuracy: 1e-3)
        XCTAssertEqual(PortalSpin.pulse(elapsed: 1.2), 1, accuracy: 1e-3)
        XCTAssertEqual(PortalSpin.pulse(elapsed: 2.4), 0.6, accuracy: 1e-3)
    }
}
