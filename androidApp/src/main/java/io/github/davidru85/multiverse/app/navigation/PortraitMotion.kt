package io.github.davidru85.multiverse.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitTransition

/**
 * The card-to-detail transition's motion contract (`UI_SPEC.md` §7, `REQ-FUNC-009`, `REQ-UX-007`).
 *
 * The container transform is 450 ms on an Emphasized Decelerate curve, with the portrait's corner
 * radius going 20 → 0 while the rest of the card fades through. Android draws it with the shell's
 * `SharedTransitionLayout` and the design system's `Modifier.sharedElement` under
 * `PortraitTransition.key(id)` (`DEC-135`); the key is a function of the character id and nothing else,
 * so the card and the hero agree on it without either feature naming the other (`ADR-0001`).
 *
 * With **Reduce Motion** enabled the transform is replaced by a cross-fade
 * (`UI_SPEC.md` §7, "Reduce Motion (both platforms)"), because the motion *is* the decoration in that
 * case: the destination still changes, and no travel across the screen happens.
 */
public object PortraitMotion {
    /** The container transform's duration (`UI_SPEC.md` §7), the design system's shared-element one. */
    public const val TRANSITION_MILLIS: Int = PortraitTransition.DURATION_MILLIS

    /** The Emphasized Decelerate curve the transform runs on (`UI_SPEC.md` §7). */
    public val Easing: CubicBezierEasing = PortraitTransition.Easing

    /** The cross-fade Reduce Motion substitutes for the transform (`UI_SPEC.md` §7). */
    public const val REDUCED_MOTION_CROSSFADE_MILLIS: Int = 300

    /**
     * The shared-element key of one character's portrait.
     *
     * It is derived from the canonical id (`IC-001`), never from a position in a list, so the same
     * character carries the same key in the grid and in the hero.
     */
    public fun sharedKey(characterId: String): String = PortraitTransition.key(characterId)

    /**
     * The transition pair the destination change runs with: the container transform, or the
     * cross-fade Reduce Motion substitutes for it.
     */
    public fun transition(reduceMotion: Boolean): Pair<EnterTransition, ExitTransition> =
        if (reduceMotion) {
            fadeIn(tween(REDUCED_MOTION_CROSSFADE_MILLIS)) to fadeOut(tween(REDUCED_MOTION_CROSSFADE_MILLIS))
        } else {
            fadeIn(tween(TRANSITION_MILLIS, easing = Easing)) to fadeOut(tween(TRANSITION_MILLIS, easing = Easing))
        }
}

/**
 * Holds the pair of destinations a card-to-detail transition moves between, with the motion the
 * platform setting selects.
 *
 * The shell calls this around its `NavHost`; the shared element itself is applied by the design
 * system's portrait, which is the only place that knows both the card's and the hero's bounds
 * (`DEC-097`). This host therefore owns the **timing and the Reduced Motion fallback**, which is the
 * part `TASK-009` fixes, and the key both sides use comes from [PortraitMotion.sharedKey].
 *
 * [reduceMotion] is read from the platform at render time by the caller, so a settings change is
 * visible without a restart, exactly as the splash's pulse does (`TASK-007`).
 */
@Composable
public fun MotionAwareDestination(
    destination: Any,
    reduceMotion: Boolean,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable (Any) -> Unit,
) {
    val (enter, exit) = PortraitMotion.transition(reduceMotion)
    AnimatedContent(
        targetState = destination,
        transitionSpec = { enter togetherWith exit },
        label = "destination",
        modifier = modifier,
    ) { target ->
        content(target)
    }
}
