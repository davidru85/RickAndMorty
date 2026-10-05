package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseComponentDimensions
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

/**
 * The status mirror a badge renders (`UI_SPEC.md` §4.1): the dot's colour is data, the label is copy
 * the caller already resolved. `:core:designsystem` names no domain type, so the caller maps its
 * `CharacterStatus` to this mirror at the feature or shell boundary (`DESIGN.md` §3.4).
 */
public enum class StatusTone { Alive, Dead, Unknown }

/**
 * The status badge (`UI_SPEC.md` §4.1): a pill of an 8 dp dot plus a Label Medium label on Surface
 * Container Highest at 90 %, overlaid at (10, 10) on a card.
 *
 * Status is never colour-only (`REQ-UX-005`): the dot always carries its text, and the whole pill is
 * one semantics node that reads [announcement] — the caller's resolved "Status: <label>" sentence
 * (`UI_SPEC.md` §9) — rather than the dot. Inside a card the card's own sentence names the status, so
 * the card clears the badge's node. The Alive dot glows (`UI_SPEC.md` §4.1).
 */
@Composable
public fun StatusBadge(
    tone: StatusTone,
    label: String,
    modifier: Modifier = Modifier,
    announcement: String = label,
) {
    val dot = tone.dotColor()
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MultiverseColors.surfaceContainerHighest.copy(alpha = 0.9f))
                .padding(
                    horizontal = MultiverseComponentDimensions.badgePaddingHorizontal,
                    vertical = MultiverseComponentDimensions.badgePaddingVertical,
                ).clearAndSetSemantics { contentDescription = announcement },
        horizontalArrangement = Arrangement.spacedBy(MultiverseComponentDimensions.badgeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(MultiverseComponentDimensions.badgeDot)
                    .then(if (tone == StatusTone.Alive) Modifier.drawBehind { drawGlow(dot) } else Modifier)
                    .clip(CircleShape)
                    .background(dot),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MultiverseColors.onSurface,
        )
    }
}

/** A soft halo around the Alive dot: the dot's colour fading out to twice its radius. */
private fun DrawScope.drawGlow(color: Color) {
    val radius = size.minDimension
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = center, radius = radius),
        radius = radius,
    )
}

/** The dot colour of `UI_SPEC.md` §3.2's status family. */
private fun StatusTone.dotColor(): Color =
    when (this) {
        StatusTone.Alive -> MultiverseBrandColors.statusAlive
        StatusTone.Dead -> MultiverseBrandColors.statusDead
        StatusTone.Unknown -> MultiverseBrandColors.statusUnknown
    }

@Preview
@Composable
private fun StatusBadgePreview() {
    MultiverseTheme {
        Row(
            modifier = Modifier.padding(MultiverseDimensions.spaceS),
            horizontalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceS),
        ) {
            StatusBadge(StatusTone.Alive, "Alive")
            StatusBadge(StatusTone.Dead, "Dead")
            StatusBadge(StatusTone.Unknown, "Unknown")
        }
    }
}
