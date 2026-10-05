package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.motion.portraitSharedElement
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import androidx.compose.animation.core.tween as animationTween

/** The crossfade of `UI_SPEC.md` §5.3: the image appears over 200 ms once it is decoded. */
public const val PortraitCrossfadeMillis: Int = 200

/** The shimmer period of the placeholder (`UI_SPEC.md` §5.1). */
public const val PortraitShimmerMillis: Int = 1_000

/** The alpha of the portal mark in the error state (`UI_SPEC.md` §5.3, 40 %). */
public const val PortraitErrorMarkAlpha: Float = 0.4f

/**
 * The requested decode size of `UI_SPEC.md` §5.1: never above 300 px, whatever the source
 * resolution is (`AC-REQ-FUNC-005-3`). The caller passes the size its slot needs, capped here.
 */
public const val PortraitMaxDecodePx: Int = 300

/**
 * The portrait renderer (`UI_SPEC.md` §5.1, §5.3): a shimmering Surface Container High placeholder,
 * then the image with a 200 ms crossfade, or the portal mark at 40 % on failure.
 *
 * Everything visual comes from the tokens; the pixels come from the [seam], which the composition
 * root implements over the allow-listed image client (`DEC-097`). The portrait is decorative inside
 * a card — the card's merged node carries the meaning (`UI_SPEC.md` §9) — so [contentDescription] is
 * `null` by default and the caller passes one only when the portrait stands alone.
 *
 * [sharedKey] names the portrait for the card→Detail shared element (`PortraitTransition`,
 * `DEC-135`); it takes effect only inside the shell's transition, and is ignored elsewhere.
 *
 * The requested decode size is [decodePx] capped to [PortraitMaxDecodePx], so no surface can ask for
 * a larger bitmap than the specification allows.
 */
@Composable
public fun CharacterPortrait(
    imageUrl: String,
    seam: ImageSeam,
    modifier: Modifier = Modifier,
    decodePx: Int = PortraitMaxDecodePx,
    portalMark: Painter? = null,
    contentDescription: String? = null,
    sharedKey: String? = null,
) {
    val requested = decodePx.coerceAtMost(PortraitMaxDecodePx)
    val height = requested
    val result = seam.rememberPainter(url = imageUrl, widthPx = requested, heightPx = height)

    Box(
        modifier =
            modifier
                .portraitSharedElement(sharedKey)
                .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        Crossfade(
            targetState = result,
            animationSpec = animationTween(PortraitCrossfadeMillis),
            label = "portrait",
        ) { state ->
            when (state) {
                is ImageSeamResult.Loading -> PortraitPlaceholder(Modifier.fillMaxSize())
                is ImageSeamResult.Success ->
                    Image(
                        painter = state.painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )

                is ImageSeamResult.Failure ->
                    PortraitError(
                        portalMark = portalMark,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

/** The placeholder: Surface Container High with a 1 s shimmer (`UI_SPEC.md` §5.1). */
@Composable
private fun PortraitPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "portrait-shimmer")
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(PortraitShimmerMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "portrait-shimmer-progress",
        )
    val base = MultiverseColors.surfaceContainerHigh
    val highlight = MultiverseColors.surfaceContainerHighest
    val sweep = 0.35f
    val start = progress * 2f - 1f
    val brush =
        Brush.horizontalGradient(
            colors = listOf(base, highlight, base),
            startX = start,
            endX = start + sweep,
        )
    Box(modifier = modifier.background(brush))
}

/**
 * The error state: the same container with the portal mark at 40 % (`UI_SPEC.md` §5.3). It never
 * draws a broken-image glyph.
 */
@Composable
private fun PortraitError(
    portalMark: Painter?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MultiverseColors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (portalMark != null) {
            Image(
                painter = portalMark,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(48.dp)
                        .alpha(PortraitErrorMarkAlpha),
            )
        }
    }
}

/** The corner radius of a card's portrait (`UI_SPEC.md` §4.1): outer 28 minus the 6 dp inset. */
public val PortraitCorner: Dp = 20.dp

/** The modifier a portrait inside a card uses: its concentric corner. */
public fun Modifier.portraitCorner(): Modifier =
    clip(
        androidx.compose.foundation.shape
            .RoundedCornerShape(PortraitCorner),
    )
