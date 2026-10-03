package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The M3 Expressive **Cookie-9** silhouette (`UI_SPEC.md` §6.1, §6.4): a disc with nine soft
 * scalloped lobes, which is the shape the splash and both empty states draw.
 *
 * It is a real scalloped path rather than a rounded square, so the illustration matches the Figma
 * component. One definition lives here, so the splash (`TASK-007`) and the placeholders cannot
 * drift apart.
 */
public class Cookie9Shape(
    /** How deeply the lobes bite into the disc, as a fraction of the radius. */
    private val scallopDepth: Float = 0.06f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        val lobes = LOBES
        val outer = radius
        val inner = radius * (1f - scallopDepth)

        val path =
            Path().apply {
                val steps = lobes * STEPS_PER_LOBE
                for (step in 0..steps) {
                    val angle = 2.0 * PI * step / steps
                    // A cosine wave rides the disc's circumference, so the edge swells and pinches.
                    val wave = (1.0 - cos(lobes * angle)) / 2.0
                    val r = inner + (outer - inner) * wave
                    val point = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
                    if (step == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
        return Outline.Generic(path)
    }

    /** The rectangular bounds the shape draws within; a Cookie-9 is a disc, so it fills its box. */
    public fun bounds(size: Size): Rect = Rect(Offset.Zero, size)

    private companion object {
        const val LOBES = 9
        const val STEPS_PER_LOBE = 12
    }
}

/** The shared Cookie-9 instance, so every surface draws the identical silhouette. */
public val Cookie9: Cookie9Shape = Cookie9Shape()

/**
 * A section illustration on the Cookie-9 shape (`UI_SPEC.md` §6.4): the glyph on the shape.
 *
 * Decorative by default: the caller marks it `contentDescription = null` when the following heading
 * and body carry the meaning, and supplies a label when the illustration itself is the content.
 */
@Composable
public fun Cookie9Illustration(
    icon: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MultiverseColors.primaryContainer,
    size: Dp = 160.dp,
    iconSize: Dp = 64.dp,
) {
    Box(
        modifier = modifier.size(size).clip(Cookie9).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MultiverseColors.onPrimaryContainer,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A 48 dp circular glyph well, the smaller illustration of a list item or a row. */
@Composable
public fun SectionGlyph(
    icon: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MultiverseColors.secondaryContainer,
    iconTint: Color = MultiverseColors.onSecondaryContainer,
) {
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(24.dp))
    }
}
