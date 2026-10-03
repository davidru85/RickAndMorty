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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

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
 * one semantics node that announces "Status: <label>" rather than reading the dot.
 */
@Composable
public fun StatusBadge(
    tone: StatusTone,
    label: String,
    modifier: Modifier = Modifier,
) {
    val dot = tone.dotColor()
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MultiverseColors.surfaceContainerHighest.copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(8.dp)
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
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(StatusTone.Alive, "Alive")
            StatusBadge(StatusTone.Dead, "Dead")
            StatusBadge(StatusTone.Unknown, "Unknown")
        }
    }
}
