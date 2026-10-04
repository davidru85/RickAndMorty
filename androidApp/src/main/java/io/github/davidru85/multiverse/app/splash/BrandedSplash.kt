package io.github.davidru85.multiverse.app.splash

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.components.Cookie9
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import kotlinx.coroutines.isActive

/** The acceleration of `UI_SPEC.md` §7: 360° over 1.2 s on the stated curve. */
internal const val ACCELERATION_MILLIS = 1_200

/** The prototype cycle of `UI_SPEC.md` §7: 0° → −360° (1.2 s, ease-in) → −1080° (2 s, linear). */
internal const val CYCLE_MILLIS = 2_000

/** The Reduce Motion pulse of `UI_SPEC.md` §7: opacity 0.6 ↔ 1 over 1.2 s. */
private const val REDUCE_MOTION_MILLIS = 1_200

/** The crossfade to the Characters destination (`UI_SPEC.md` §6.1): 350–400 ms. */
public const val SplashExitCrossfadeMillis: Int = 380

/**
 * The in-app splash (`TASK-007`, `UI_SPEC.md` §6.1, §7): the Cookie-9 shape with the portal on it, the
 * wordmark and the tagline, over the Surface canvas.
 *
 * The rotation **is** the loading indicator, so the surface is exposed as an indeterminate progress
 * indicator labelled "Loading characters" (`TEST-A11Y-001`, `UI_SPEC.md` §9) — a screen reader
 * announces progress rather than describing the shape.
 *
 * With Reduce Motion the portal does not spin; because it is the loading indicator it pulses its
 * opacity instead, which keeps the same signal without motion (`AC-REQ-UX-007-1`).
 */
@Composable
public fun BrandedSplash(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val rotation = if (reduceMotion) 0f else portalRotation()
    val pulse =
        if (reduceMotion) {
            portalPulse()
        } else {
            1f
        }

    val loadingLabel = CopyResolver.copy("splash_loading")
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(MultiverseColors.surfaceContainerLow, MultiverseColors.surface),
                    ),
                ).semantics {
                    contentDescription = loadingLabel
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(240.dp)
                        .clip(Cookie9)
                        .background(MultiverseColors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                PortalMark(
                    modifier =
                        Modifier
                            .size(160.dp)
                            .rotate(rotation)
                            .alpha(pulse),
                )
            }
            Text(
                text = CopyResolver.copy("splash_wordmark"),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MultiverseColors.onSurface,
            )
            Text(
                text = CopyResolver.copy("splash_wordmark_sub"),
                style = MaterialTheme.typography.labelLarge,
                color = MultiverseColors.primary,
            )
            Text(
                text = CopyResolver.copy("splash_tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Whether the platform asks for reduced motion: Android keeps the setting in the animator duration
 * scale, which is `0` when animations are disabled. The value is read at composition, so a screen
 * honours a setting change the next time it is composed.
 */
@Composable
public fun rememberReduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * The portal's angle, as `UI_SPEC.md` §7's prototype keyframes state: 0° → −360° over 1.2 s on the
 * ease-in cubic, then −360° → −1080° over the next 0.8 s at the constant ≈900°/s the specification
 * names. One linear progress value drives both phases, so the angle is a pure function of the
 * elapsed fraction; the second phase ends at −1080°, which is the same image as 0°, so the cycle
 * restarts with no visible jump and the portal visibly never stops.
 */
@Composable
private fun portalRotation(): Float {
    val transition = rememberInfiniteTransition(label = "portal-rotation")
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = CYCLE_MILLIS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "portal-progress",
        )
    return portalAngleAt(progress)
}

/**
 * The angle at a cycle fraction [progress] in `0..1`: the ease-in acceleration over the first
 * `ACCELERATION_MILLIS`, then the constant-speed sweep to −1080° over the remainder.
 */
internal fun portalAngleAt(progress: Float): Float {
    val accelerationFraction = ACCELERATION_MILLIS.toFloat() / CYCLE_MILLIS
    return if (progress < accelerationFraction) {
        -360f * PortalAcceleration.transform(progress / accelerationFraction)
    } else {
        -360f - (1_080f - 360f) * ((progress - accelerationFraction) / (1f - accelerationFraction))
    }
}

/** The Reduce Motion signal: a gentle pulse instead of a spin (`UI_SPEC.md` §7). */
@Composable
private fun portalPulse(): Float {
    val pulse by produceState(PULSE_LOW) {
        // Scale 0 is this app's Reduce Motion signal. Frame time keeps the specified opacity-only
        // progress signal alive while the rotation and other system animations remain disabled.
        // The infinite-frame API also lets test clocks freeze this indicator for stable snapshots.
        val start = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            withInfiniteAnimationFrameNanos { now ->
                val cycle = ((now - start) / 1_000_000f / REDUCE_MOTION_MILLIS) % 2f
                val fraction = if (cycle <= 1f) cycle else 2f - cycle
                value = PULSE_LOW + (1f - PULSE_LOW) * fraction
            }
        }
    }
    return pulse
}

private const val PULSE_LOW = 0.6f

/** `CubicBezierEasing(0.32, 0, 0.67, 0)` of `UI_SPEC.md` §7. */
private val PortalAcceleration = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

/** The portal itself: the ring and the spiral core, drawn from tokens so it tints with the scheme. */
@Composable
private fun PortalMark(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(
            painter =
                androidx.compose.ui.res
                    .painterResource(id = SPLASH_PORTAL_DRAWABLE),
            contentDescription = null,
            tint = MultiverseBrandColors.portalGlow,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The portal vector the shell bundles (`DEC-103`); the design system names no icon artifact. */
private val SPLASH_PORTAL_DRAWABLE = io.github.davidru85.multiverse.app.R.drawable.ic_portal_mark
