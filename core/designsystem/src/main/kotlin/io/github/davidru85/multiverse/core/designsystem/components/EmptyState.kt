package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

/**
 * The designed empty state (`UI_SPEC.md` §6.4): the Cookie-9 illustration, a heading, a body and one
 * action button.
 *
 * Reading order is illustration → heading → body → button, and the illustration is decorative, so a
 * screen reader announces the heading first (`UI_SPEC.md` §9). It is used by the Episodes and
 * Favorites placeholders of `TASK-008` and by the designed empty-results state of `TASK-010`; its
 * container colour is a parameter because the spec gives each section its own (Secondary Container
 * for Episodes, Primary Container for Favorites).
 */
@Composable
public fun EmptyState(
    heading: String,
    body: String,
    illustration: Painter,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MultiverseColors.primaryContainer,
    illustrationSize: androidx.compose.ui.unit.Dp = 160.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cookie9Illustration(
            icon = illustration,
            contentDescription = null,
            containerColor = containerColor,
            size = illustrationSize,
        )
        Text(
            text = heading,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MultiverseColors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MultiverseColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAction) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MultiverseColors.primary,
            )
        }
    }
}

/** The corner a section group uses for its own panel (`UI_SPEC.md` §4.1): 28. */
internal val SectionCorner = MultiverseDimensions.cornerExtraLarge

@Preview
@Composable
private fun EmptyStatePreview() {
    MultiverseTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyState(
                heading = "No favorites yet",
                body = "Tap the heart on a character's page to keep them here.",
                illustration = PreviewPainter,
                actionLabel = "Browse characters",
                onAction = {},
            )
        }
    }
}

private val PreviewPainter: Painter =
    androidx.compose.ui.graphics.painter
        .ColorPainter(Color.White)
