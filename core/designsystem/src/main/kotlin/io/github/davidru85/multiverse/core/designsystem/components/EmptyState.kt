package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/**
 * The designed empty state (`UI_SPEC.md` §6.4): the Cookie-9 illustration, a heading, a body and one
 * action button.
 *
 * Reading order is illustration → heading → body → button, and the illustration is decorative, so a
 * screen reader announces the heading first (`UI_SPEC.md` §9). It is used by the Episodes and
 * Favorites placeholders of `TASK-008`, by the designed empty-results state of `TASK-010` and by the
 * full-surface error states; its container colour and the glyph's tint are parameters because the
 * spec gives each section its own (Secondary Container / On Secondary Container for Episodes, Primary
 * Container / On Primary Container for Favorites, the untinted portal mark at 40 % for the list's
 * empty and error states, §8). The heading is Headline Small Emphasized, the body Body Medium at most
 * 320 dp wide, and the action an M3 filled Medium button (§6.4).
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
    illustrationTint: Color = MultiverseColors.onPrimaryContainer,
    illustrationAlpha: Float = 1f,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cookie9Illustration(
            icon = illustration,
            contentDescription = null,
            containerColor = containerColor,
            size = illustrationSize,
            iconTint = illustrationTint,
            iconAlpha = illustrationAlpha,
        )
        Text(
            text = heading,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MultiverseColors.onSurface,
            textAlign = TextAlign.Center,
        )
        // An empty body adds no blank line and no spacing of its own.
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = BodyWidth),
            )
        }
        Button(
            onClick = onAction,
            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(text = actionLabel, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

/** The body's measure (`UI_SPEC.md` §6.4): 320 dp, centred. */
private val BodyWidth = 320.dp

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
