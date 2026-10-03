package io.github.davidru85.multiverse.feature.episodes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/**
 * The Episodes placeholder (`UI_SPEC.md` §6.4, `TASK-008`): the section's empty state with the copy the
 * specification gives it and one action that returns to Characters.
 *
 * Episodes has no data layer by decision (`DEC-005`), so this screen is a **stateless UI placeholder**:
 * it renders copy it is given and holds nothing. That is exactly the shape `DEC-104` exempts from the
 * `S3` domain/presentation requirement — creating empty layers to satisfy the rule would be fabricated
 * architecture, not delivery.
 *
 * "Browse characters" is a callback, never a route: the feature does not know where Characters lives,
 * so the shell maps the tap to its own destination and no feature-to-feature edge exists
 * (`REQ-FUNC-008`, ADR-0001).
 */
@Composable
public fun EpisodesPlaceholder(
    onBrowseCharacters: () -> Unit,
    illustration: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        heading = CopyResolver.copy("episodes_heading"),
        body = CopyResolver.copy("episodes_body"),
        illustration = illustration,
        actionLabel = CopyResolver.copy("browse_characters"),
        containerColor = MultiverseColors.secondaryContainer,
        onAction = onBrowseCharacters,
        modifier = modifier,
    )
}
