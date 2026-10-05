package io.github.davidru85.multiverse.app.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UNIT-086` — the portal's rotation (`UI_SPEC.md` §7, `REQ-FUNC-007`, `AC-REQ-FUNC-007-3`,
 * `TASK-113`): the angle is a pure function of the elapsed time, so the motion is asserted exactly
 * with no clock in the case.
 *
 * The specification turns the portal **clockwise** — positive degrees in Compose — from 0° to 360°
 * over 1.2 s on the ease-in cubic, then at a constant ≈900°/s. The earlier curve copied Figma's
 * negative keyframes, so the portal turned anticlockwise, and it restarted the ease-in every 2 s, so
 * the speed dropped from ≈900°/s to 0 at each restart: a visible stutter where the spec asks for
 * constant speed.
 */
class PortalRotationTest {
    @Test
    fun `TEST-UNIT-086 given_the_acceleration_when_the_angle_is_read_then_it_turns_clockwise_to_one_turn_at_1_2_s`() {
        assertEquals(0f, portalAngleAt(0), 1e-3f)
        assertTrue("the portal turns clockwise: positive degrees in Compose", portalAngleAt(600) > 0f)
        assertTrue("the ease-in has advanced little at mid-acceleration", portalAngleAt(600) < 360f * 0.35f)
        assertEquals("one full turn when the acceleration ends", 360f, portalAngleAt(ACCELERATION_MILLIS.toLong()), 0.5f)
    }

    @Test
    fun `TEST-UNIT-086 given_the_end_of_the_acceleration_when_the_speed_is_read_then_it_continues_without_a_jump`() {
        val end = ACCELERATION_MILLIS.toLong()
        val before = (portalAngleAt(end) - portalAngleAt(end - 10)) / 0.010f
        val after = (portalAngleAt(end + 10) - portalAngleAt(end)) / 0.010f
        assertEquals("the speed is continuous where the acceleration hands over (°/s)", before, after, 60f)
    }

    @Test
    fun `TEST-UNIT-086 given_the_constant_phase_when_the_angle_is_read_then_it_keeps_the_documented_speed_and_never_restarts`() {
        listOf(2_000L, 5_000L, 60_000L).forEach { at ->
            val perSecond = portalAngleAt(at + 1_000) - portalAngleAt(at)
            assertEquals("≈900°/s from $at ms on (UI_SPEC.md §7)", 900f, perSecond, 15f)
        }
        assertTrue("no restart: the angle only grows", portalAngleAt(2_010) > portalAngleAt(1_990))
    }
}
