package io.github.davidru85.multiverse.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.tooling.preview.Preview
import io.github.davidru85.multiverse.core.designsystem.components.PortraitMaxDecodePx
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.image.LocalPortalMark
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/**
 * The preview set of a design-system component (`GUIDELINES.md` §5.4, `TASK-140`): the system in light
 * and in dark, which must render the same because the app has one appearance (`REQ-UX-001`), and the
 * largest font scale, where text grows rather than clips (`REQ-UX-006`).
 */
@Preview(name = "System light", uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "System dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Largest text", fontScale = LARGEST_FONT_SCALE)
public annotation class MultiverseComponentPreviews

/**
 * The preview set of a screen: [MultiverseComponentPreviews]' three configurations on the 412 × 892 dp
 * phone frame the Android designs are drawn on (`UI_SPEC.md` §1).
 */
@Preview(
    name = "System light",
    device = PHONE_FRAME,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    name = "System dark",
    device = PHONE_FRAME,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Largest text", device = PHONE_FRAME, fontScale = LARGEST_FONT_SCALE)
public annotation class MultiverseScreenPreviews

/** Android's largest accessibility font scale, past the grids' one-column threshold (`MultiverseGrid`). */
private const val LARGEST_FONT_SCALE = 2f

/** The Android designs' frame (`UI_SPEC.md` §1): 412 × 892 dp. */
private const val PHONE_FRAME = "spec:width=412dp,height=892dp,dpi=420"

/**
 * The frame a preview renders in: the theme, the Surface the shell draws every screen on, and a token
 * stand-in for the brand mark the shell provides through [LocalPortalMark] — the mark is the app's
 * asset, out of the design system's reach (`UI_SPEC.md` §5.3). A screen's `fillMaxSize` fills the
 * preview's frame; a component keeps its own size.
 */
@Composable
public fun MultiversePreviewSurface(content: @Composable () -> Unit) {
    MultiverseTheme {
        CompositionLocalProvider(LocalPortalMark provides ColorPainter(MultiverseColors.onSurfaceVariant)) {
            Box(modifier = Modifier.background(MultiverseColors.surface)) { content() }
        }
    }
}

/**
 * The image seam a preview draws portraits with: it answers every URL with one fixed result and never
 * reaches a loader or the network (`GUIDELINES.md` §5.4, `DESIGN.md` §8). It is the preview counterpart
 * of the composition root's seam, so a component or a screen previews each portrait state of
 * `UI_SPEC.md` §5.3 without an image asset.
 */
public class PreviewImageSeam private constructor(
    private val result: ImageSeamResult,
) : ImageSeam {
    @Composable
    override fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult = result

    public companion object {
        /** Every portrait stays in its shimmering placeholder. */
        public val Loading: ImageSeam = PreviewImageSeam(ImageSeamResult.Loading)

        /**
         * Every portrait has loaded; a token gradient stands in for the picture. Its brush spans the
         * decode size, so the painter has the finite intrinsic size a decoded image has: an unbounded
         * gradient measures 0 × NaN, which a cropped portrait cannot scale.
         */
        public val Loaded: ImageSeam =
            PreviewImageSeam(
                ImageSeamResult.Success(
                    BrushPainter(
                        Brush.linearGradient(
                            colors = listOf(MultiverseColors.tertiaryContainer, MultiverseColors.primaryContainer),
                            start = Offset.Zero,
                            end = Offset(PortraitMaxDecodePx.toFloat(), PortraitMaxDecodePx.toFloat()),
                        ),
                    ),
                ),
            )

        /** Every load has failed; the portrait shows its portal-mark error state. */
        public val Failed: ImageSeam = PreviewImageSeam(ImageSeamResult.Failure)
    }
}
