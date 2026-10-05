package io.github.davidru85.multiverse.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * The card→Detail shared element (`UI_SPEC.md` §7, `REQ-FUNC-009`, `DEC-135`).
 *
 * The portrait travels from the grid card to the Detail hero in 450 ms on the Emphasized Decelerate
 * curve. Both ends name it by [key], derived from the canonical id (`IC-001`) and never from a list
 * position, so the features and the shell agree on it without naming each other (`ADR-0001`).
 */
public object PortraitTransition {
    /** The transform's duration (`UI_SPEC.md` §7). */
    public const val DURATION_MILLIS: Int = 450

    /** The Emphasized Decelerate curve the transform runs on (`UI_SPEC.md` §7). */
    public val Easing: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** The shared-element key of one character's portrait. */
    public fun key(characterId: String): String = "portrait-$characterId"
}

/**
 * The shell's `SharedTransitionLayout` scope, or `null` where no transition runs — outside the shell,
 * and with Reduce Motion, when the destination change is the cross-fade instead (`AC-REQ-FUNC-009-2`).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
public val LocalPortraitTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> = staticCompositionLocalOf { null }

/** The animated-visibility scope of the destination a portrait is drawn in, provided by the shell. */
public val LocalPortraitVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> = compositionLocalOf { null }

/**
 * The shared-element key a portrait carries while it takes part in the transition, in semantics, so a
 * test can find both ends; it is set exactly when the shared element is applied.
 */
public val PortraitSharedKey: SemanticsPropertyKey<String> = SemanticsPropertyKey("PortraitSharedKey")

private var SemanticsPropertyReceiver.portraitSharedKey by PortraitSharedKey

/**
 * Makes this portrait the shared element under [key] when the shell provides both scopes; otherwise —
 * no key, outside the shell, or Reduce Motion — it is unchanged.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.portraitSharedElement(key: String?): Modifier {
    val transitionScope = LocalPortraitTransitionScope.current
    val visibilityScope = LocalPortraitVisibilityScope.current
    if (key == null || transitionScope == null || visibilityScope == null) return this
    return with(transitionScope) {
        this@portraitSharedElement
            .semantics { portraitSharedKey = key }
            .sharedElement(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = PortraitBounds,
            )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val PortraitBounds: BoundsTransform =
    BoundsTransform { _, _ -> tween(PortraitTransition.DURATION_MILLIS, easing = PortraitTransition.Easing) }
