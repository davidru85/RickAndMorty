package io.github.davidru85.multiverse.app.splash

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UI-006`'s rotation half (`TASK-007`, `UI_SPEC.md` §7): the portal's angle is a pure
 * function of the cycle fraction, so the prototype keyframes are asserted exactly, with no
 * clock in the case.
 *
 * The specification states 0° → −360° over 1.2 s on the ease-in cubic, then a constant ≈900°/s
 * sweep to −1080° at 2 s. The first draft of this surface restarted a 1.2 s ease-in cycle from 0°,
 * which made the portal visibly stop at the instant the acceleration ended — the defect this set
 * of cases pins and the rewrite removes.
 */
class PortalRotationTest {
    @Test
    fun `TEST-UI-006 given_the_acceleration_phase_when_the_angle_is_read_then_it_follows_the_documented_easing`() {
        // Halfway through the acceleration the ease-in cubic has advanced little (0.32,0,0.67,0);
        // the value is pinned so a silent easing swap fails here.
        val half = portalAngleAt(0.25f)
        assertTrue(
            "early in the cycle the eased sweep is small (UI_SPEC.md §7)",
            -360f * 0.2f < half && half <= 0f,
        )
        assertEquals(
            "the acceleration ends at −360° at 1.2 s",
            -360f,
            portalAngleAt(ACCELERATION_MILLIS.toFloat() / CYCLE_MILLIS),
        )
    }

    @Test
    fun `TEST-UI-006 given_the_constant_phase_when_the_angle_is_read_then_the_sweep_rate_is_the_documented_one`() {
        // 0.8 s covering 720° is exactly the ≈900°/s the specification names.
        val perSecond = (portalAngleAt(1f) - portalAngleAt(ACCELERATION_MILLIS.toFloat() / CYCLE_MILLIS + 0.01f)) / (0.8f - 0.01f) * -1f
        assertEquals(
            "the constant phase sweeps the documentation's ≈900°/s (UI_SPEC.md §7)",
            900f,
            perSecond,
            15f,
        )
    }

    @Test
    fun `TEST-UI-006 given_the_cycle_wrap_when_the_angle_loops_then_the_image_is_continuous`() {
        // −1080° ≡ 0° (mod 360°): the restart draws the same image, so the portal never stops.
        assertTrue(
            "the cycle ends a whole number of turns below zero",
            abs(portalAngleAt(1f) % 360f) < 1e-3f,
        )
        assertEquals(0f, portalAngleAt(0f), 1e-6f)
    }

    @Test
    fun `TEST-UI-006 given_the_documented_bounds_when_they_are_read_then_the_cycle_is_two_seconds`() {
        assertEquals(2_000, CYCLE_MILLIS)
        assertEquals(1_200, ACCELERATION_MILLIS)
    }
}