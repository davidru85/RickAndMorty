package io.github.davidru85.multiverse.app.splash

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.davidru85.multiverse.core.designsystem.components.Cookie9
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import androidx.compose.ui.draw.clip

/** The acceleration of `UI_SPEC.md` §7: 360° over 1.2 s on the stated curve. */
private const val ACCELERATION_MILLIS = 1_200

/** The constant speed the portal keeps once accelerated, in degrees per second. */
private const val CONSTANT_DEGREES_PER_SECOND = 900f

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
 * The portal's angle: it accelerates on the documented curve for the first 1.2 s, then keeps spinning
 * at the speed it reached, which is what tells the user the app is still working.
 */
@Composable
private fun portalRotation(): Float {
    val transition = rememberInfiniteTransition(label = "portal-rotation")
    val degreesPerCycle = 360f
    val angle by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = degreesPerCycle,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = ACCELERATION_MILLIS, easing = PortalAcceleration),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "portal-angle",
        )
    return angle
}

/** The Reduce Motion signal: a gentle pulse instead of a spin (`UI_SPEC.md` §7). */
@Composable
private fun portalPulse(): Float {
    val transition = rememberInfiniteTransition(label = "portal-pulse")
    val pulse by
        transition.animateFloat(
            initialValue = PULSE_LOW,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = REDUCE_MOTION_MILLIS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "portal-pulse-alpha",
        )
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
            painter = androidx.compose.ui.res.painterResource(id = SPLASH_PORTAL_DRAWABLE),
            contentDescription = null,
            tint = MultiverseBrandColors.portalGlow,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The portal vector the shell bundles (`DEC-103`); the design system names no icon artifact. */
private val SPLASH_PORTAL_DRAWABLE = io.github.davidru85.multiverse.app.R.drawable.ic_portal_mark
