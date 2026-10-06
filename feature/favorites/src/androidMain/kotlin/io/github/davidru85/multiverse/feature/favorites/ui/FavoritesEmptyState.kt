package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.preview.MultiverseComponentPreviews
import io.github.davidru85.multiverse.core.designsystem.preview.MultiversePreviewSurface
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.presentation.CopyKeys

/**
 * The Favorites empty state (`UI_SPEC.md` §6.4): the section's designed empty state, with the copy the
 * specification gives it and one action that returns to Characters.
 *
 * It is the state `:feature:favorites` renders while the stored set is empty — `IC-020`'s `Empty`, and
 * only then — and the populated grid is its sibling in the same screen (`TASK-006`). It stays a
 * stateless composable over the copy and a callback, so no state and no data access lives here.
 *
 * "Browse characters" is a callback the shell maps to its own destination, so the feature never names
 * another feature (`REQ-FUNC-008`, ADR-0001).
 */
@Composable
public fun FavoritesEmptyState(
    onBrowseCharacters: () -> Unit,
    illustration: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        heading = CopyResolver.copy(CopyKeys.FAVORITES_HEADING.value),
        body = CopyResolver.copy(CopyKeys.FAVORITES_BODY.value),
        illustration = illustration,
        actionLabel = CopyResolver.copy(CopyKeys.BROWSE_CHARACTERS.value),
        containerColor = MultiverseColors.primaryContainer,
        onAction = onBrowseCharacters,
        modifier = modifier,
    )
}

@MultiverseComponentPreviews
@Composable
private fun FavoritesEmptyStatePreview() {
    MultiversePreviewSurface {
        FavoritesEmptyState(
            onBrowseCharacters = {},
            // The shell passes its heart; a token stand-in shows where the illustration draws.
            illustration = ColorPainter(MultiverseColors.onPrimaryContainer),
        )
    }
}
