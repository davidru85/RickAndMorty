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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.davidru85.multiverse.core.designsystem.components.Cookie9
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import kotlin.random.Random
import kotlinx.coroutines.isActive

/** The acceleration of `UI_SPEC.md` §7: 360° over 1.2 s on the stated curve. */
internal const val ACCELERATION_MILLIS = 1_200

/** The constant speed after the acceleration (`UI_SPEC.md` §7): ≈900° per second, clockwise. */
internal const val CONSTANT_DEGREES_PER_SECOND = 900f

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

    val loadingLabel = CopyResolver.copy(CopyKeys.SPLASH_LOADING.value)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MultiverseColors.surface)
                // Figma `20:1620`: a violet nebula at the top end, a green one at the bottom start, and
                // a fixed starfield, so the background is the same frame on every launch.
                .drawBehind { drawSpace() }
                .semantics {
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
                        // The Portal Glow around the cookie (`UI_SPEC.md` §6.1).
                        .dropShadow(Cookie9, Shadow(radius = 48.dp, color = MultiverseBrandColors.portalGlow.copy(alpha = 0.45f)))
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
                text = CopyResolver.copy(CopyKeys.SPLASH_WORDMARK.value),
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = MultiverseColors.onSurface,
            )
            Text(
                text = CopyResolver.copy(CopyKeys.SPLASH_WORDMARK_SUB.value),
                // "EXPLORER" carries +8 sp tracking (`UI_SPEC.md` §3.4).
                style = MaterialTheme.typography.labelLargeEmphasized.copy(letterSpacing = 8.sp),
                color = MultiverseColors.primary,
            )
            Text(
                text = CopyResolver.copy(CopyKeys.SPLASH_TAGLINE.value),
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
 * The portal's angle while it spins (`UI_SPEC.md` §7), read on the frame clock: the elapsed time since
 * the splash appeared, through [portalAngleAt]. The infinite-frame API lets a test clock freeze it, so
 * a snapshot is stable.
 */
@Composable
private fun portalRotation(): Float {
    val angle by produceState(0f) {
        val start = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            withInfiniteAnimationFrameNanos { now -> value = portalAngleAt((now - start) / 1_000_000L) % 360f }
        }
    }
    return angle
}

/**
 * The angle, in degrees **clockwise** (positive in Compose), [elapsedMillis] after the splash appeared:
 * 0° → 360° over the first `ACCELERATION_MILLIS` on the ease-in cubic, then a constant
 * [CONSTANT_DEGREES_PER_SECOND] for as long as the splash stays. The ease-in ends at the constant
 * speed, so the hand-over shows no jump, and nothing restarts, so the portal never stutters.
 */
internal fun portalAngleAt(elapsedMillis: Long): Float =
    if (elapsedMillis < ACCELERATION_MILLIS) {
        360f * PortalAcceleration.transform(elapsedMillis.toFloat() / ACCELERATION_MILLIS)
    } else {
        360f + CONSTANT_DEGREES_PER_SECOND * (elapsedMillis - ACCELERATION_MILLIS) / 1_000f
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

/** The portal itself: Figma's multi-tone spiral, drawn untinted so the rotation is visible. */
@Composable
private fun PortalMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter =
            androidx.compose.ui.res
                .painterResource(id = SPLASH_PORTAL_DRAWABLE),
        contentDescription = null,
        modifier = modifier,
    )
}

/**
 * The splash's space (Figma `20:1620`): a Tertiary nebula at the top end and a Primary one at the
 * bottom start over Surface, and a starfield placed by a fixed seed, so every launch draws the same
 * frame and a snapshot is stable.
 */
private fun DrawScope.drawSpace() {
    drawRect(
        Brush.radialGradient(
            colors = listOf(MultiverseColors.tertiary.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(size.width, 0f),
            radius = size.width * 0.9f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(MultiverseColors.primary.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(0f, size.height),
            radius = size.width * 0.9f,
        ),
    )
    val random = Random(STARFIELD_SEED)
    repeat(STAR_COUNT) {
        drawCircle(
            color = Color.White.copy(alpha = 0.35f + random.nextFloat() * 0.55f),
            radius = (0.6f + random.nextFloat() * 1.2f) * density,
            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
        )
    }
}

/** The starfield's fixed seed and size, so it is the same field on every launch. */
private const val STARFIELD_SEED = 20_1620
private const val STAR_COUNT = 70

/** The portal vector the shell bundles (`DEC-103`); the design system names no icon artifact. */
private val SPLASH_PORTAL_DRAWABLE = io.github.davidru85.multiverse.app.R.drawable.ic_portal_mark
