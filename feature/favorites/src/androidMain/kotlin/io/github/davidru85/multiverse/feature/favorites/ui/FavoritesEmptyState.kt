package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/**
 * The Favorites empty state (`UI_SPEC.md` §6.4, `TASK-008`): the section's designed empty state, with
 * the copy the specification gives it and one action that returns to Characters.
 *
 * It is the state the section renders while the stored set is empty; the toggle, the list and the
 * persisted state arrive with `TASK-006`, which replaces this screen with the real one. Until then the
 * source is a **stateless UI placeholder**, the shape `DEC-104` exempts from the `S3` layer
 * requirement.
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
        heading = CopyResolver.copy("favorites_heading"),
        body = CopyResolver.copy("favorites_body"),
        illustration = illustration,
        actionLabel = CopyResolver.copy("browse_characters"),
        containerColor = MultiverseColors.primaryContainer,
        onAction = onBrowseCharacters,
        modifier = modifier,
    )
}
