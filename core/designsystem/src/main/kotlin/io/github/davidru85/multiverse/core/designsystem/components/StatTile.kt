package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

/** Where a tile sits in its connected group, which decides its corners (`UI_SPEC.md` §4.1). */
public enum class TilePosition { Start, Middle, End }

/**
 * One stat tile (`UI_SPEC.md` §4.1): a value in Headline Small Emphasized over its label in Label
 * Medium, on the caller's container colour.
 *
 * The three shapes are the connected group of the spec — start 28/8/8/28, middle 8, end 8/28/28/8 —
 * so a row of three tiles reads as one shape.
 */
@Composable
public fun StatTile(
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    position: TilePosition,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(120.dp)
                .height(76.dp)
                .clip(position.shape())
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The row of three connected tiles (`UI_SPEC.md` §4.1): 4 dp gaps. */
@Composable
public fun StatTileRow(
    tiles: List<Triple<String, String, Pair<Color, Color>>>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.forEachIndexed { index, (value, label, colors) ->
            val position =
                when (index) {
                    0 -> TilePosition.Start
                    tiles.lastIndex -> TilePosition.End
                    else -> TilePosition.Middle
                }
            StatTile(
                value = value,
                label = label,
                containerColor = colors.first,
                contentColor = colors.second,
                position = position,
            )
        }
    }
}

private fun TilePosition.shape(): RoundedCornerShape {
    val large = MultiverseDimensions.cornerExtraLarge
    val small = MultiverseDimensions.cornerSmall
    return when (this) {
        TilePosition.Start -> RoundedCornerShape(topStart = large, topEnd = small, bottomEnd = small, bottomStart = large)
        TilePosition.Middle -> RoundedCornerShape(small)
        TilePosition.End -> RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = large, bottomStart = small)
    }
}

/**
 * One info-list item (`UI_SPEC.md` §4.1): a 48 dp leading glyph well, a Label Medium label and a
 * Title Medium value, 72 dp minimum height.
 *
 * The glyph is a painter the caller supplies, so the design system names no icon library. A `null`
 * [value] hides the row entirely, which is how a missing enrichment keeps its rows absent rather
 * than showing a placeholder (`UI_SPEC.md` §6.3).
 */
@Composable
public fun InfoListItem(
    label: String,
    value: String?,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    if (value == null) return
    Row(
        modifier = modifier.height(72.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionGlyph(icon = icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MultiverseColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MultiverseColors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Surface Container group that holds the info items (`UI_SPEC.md` §4.1): corner 28. */
@Composable
public fun InfoListGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(MultiverseDimensions.cornerExtraLarge))
                .background(MultiverseColors.surfaceContainer),
    ) {
        Column { content() }
    }
}

@Preview
@Composable
private fun StatTileRowPreview() {
    MultiverseTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            StatTileRow(
                tiles =
                    listOf(
                        Triple("826", "Characters", MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer),
                        Triple("51", "Episodes", MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer),
                        Triple("C-137", "Dimension", MultiverseColors.secondaryFixedDim to MultiverseColors.onSecondaryFixed),
                    ),
            )
        }
    }
}

@Preview
@Composable
private fun InfoListItemPreview() {
    MultiverseTheme {
        Box(modifier = Modifier.padding(12.dp).size(320.dp, 90.dp)) {
            InfoListGroup {
                InfoListItem(label = "Species", value = "Human", icon = PortraitPreviewIcons.Dot)
            }
        }
    }
}

/** A dot painter for previews, so no icon dependency is needed. */
private object PortraitPreviewIcons {
    val Dot: Painter = ColorPainter(Color.White)
}
