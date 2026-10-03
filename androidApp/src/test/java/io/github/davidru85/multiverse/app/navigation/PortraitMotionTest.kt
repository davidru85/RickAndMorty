package io.github.davidru85.multiverse.app.navigation

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UI-008` — the card-to-detail transition's motion contract (`REQ-FUNC-009`,
 * `REQ-UX-007`, `UI_SPEC.md` §7).
 *
 * Frame-level motion is not asserted reliably by Robolectric (`TESTING.md` §14.2), so the cases pin the
 * parts a test *can* decide and a reviewer can check against the specification: the duration, the
 * curve, the shared key's derivation from the canonical id, and the Reduced Motion substitution. The
 * rendered frame is `TEST-A11Y-006`'s manual checklist.
 */
class PortraitMotionTest {
    @Test
    fun `TEST-UI-008 given_a_transition_when_it_runs_then_it_is_450_milliseconds_on_the_emphasized_decelerate_curve`() {
        assertEquals("the specification's duration (UI_SPEC.md 7)", 450L, PortraitMotion.TRANSITION_MILLIS.toLong())
        assertTrue(
            "the Emphasized Decelerate curve the design system's tokens name",
            PortraitMotion.Easing == CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
        )
    }

    @Test
    fun `TEST-UI-008 given_a_character_when_two_surfaces_share_its_portrait_then_the_key_follows_the_canonical_id`() {
        assertEquals("the key is the id, never a list position", "portrait-1", PortraitMotion.sharedKey("1"))
        assertEquals(
            "the same character carries one key in the grid and in the hero",
            PortraitMotion.sharedKey("1"),
            PortraitMotion.sharedKey("1"),
        )
        assertNotEquals(
            PortraitMotion.sharedKey("1"),
            PortraitMotion.sharedKey("2"),
            "two characters never share a transition key",
        )
    }

    @Test
    fun `TEST-UI-008 given_reduce_motion_when_the_transition_is_built_then_the_transform_is_replaced_by_a_cross_fade`() {
        val (enter, exit) = PortraitMotion.transition(reduceMotion = true)
        assertTrue(
            "Reduce Motion replaces the travel with a cross-fade (AC-REQ-FUNC-009-2)",
            enter ==
                androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(PortraitMotion.REDUCED_MOTION_CROSSFADE_MILLIS),
                ),
        )
        assertTrue(
            "and the exit half is the matching cross-fade",
            exit ==
                androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(PortraitMotion.REDUCED_MOTION_CROSSFADE_MILLIS),
                ),
        )
    }

    @Test
    fun `TEST-A11Y-006 given_reduce_motion_disabled_when_the_transition_is_built_then_the_full_transform_is_used`() {
        val (enter, _) = PortraitMotion.transition(reduceMotion = false)
        assertNotEquals(
            "without Reduce Motion the container transform runs at its own duration",
            enter,
            androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(PortraitMotion.REDUCED_MOTION_CROSSFADE_MILLIS),
            ),
        )
    }
}
