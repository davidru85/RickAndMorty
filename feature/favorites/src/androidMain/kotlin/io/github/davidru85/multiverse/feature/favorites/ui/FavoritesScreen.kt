package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import io.github.davidru85.multiverse.core.designsystem.components.CardHeight
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCard
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.ScreenTitle
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.LocalPortalMark
import io.github.davidru85.multiverse.core.designsystem.layout.MultiverseGrid
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitTransition
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.formatArguments
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesIntent
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesUiState

/**
 * The Favorites section (`UI_SPEC.md` §6.4, §6.2; `TASK-006`).
 *
 * It renders [state] and nothing it derives itself, so a favourite is displayed by the same component
 * contract Discovery's grid uses (`IC-016`, `IC-020`):
 *
 * - [LoadState.Empty] is the section's designed empty state — the one `FavoritesEmptyState` renders,
 *   with its copy and a "Browse characters" action that returns to Characters (`AC-REQ-FUNC-006-3`);
 * - [LoadState.Content] is the **same cards as Discovery**: a two-column staggered grid with the same
 *   `Tall`-when-`index % 4` is 0 or 3 pattern (`UI_SPEC.md` §4.1);
 * - [LoadState.Error] is the full-surface error — `error_title`, the `ApiFailure`-specific message and
 *   `action_retry` — with a working Retry (`ERROR_FLOW.md` §4);
 * - [LoadState.Loading] renders nothing of its own: the section is still finding out whether it has
 *   favourites, so it holds the surface rather than flashing a state it cannot justify.
 *
 * Tapping a card calls [onCharacterSelected] with the card it tapped. That callback is a parameter, not
 * a route: the feature does not know where the detail destination lives, so the shell publishes the card
 * to the navigation hand-off and navigates (`IC-025`, `ADR-0001`, rule 6). The feature names no other
 * feature and no `NavHost`.
 *
 * [seam] is the shell's portrait transport, and [portalMark] the design system's error-state painter;
 * both are passed in because `:core:designsystem` may declare Compose only and owns no image API
 * (`DEC-097`).
 */
@Composable
public fun FavoritesScreen(
    state: FavoritesUiState,
    seam: ImageSeam,
    onCharacterSelected: (CharacterCardUi) -> Unit,
    onIntent: (FavoritesIntent) -> Unit,
    onBrowseCharacters: () -> Unit,
    illustration: Painter,
    modifier: Modifier = Modifier,
    portalMark: Painter? = null,
) {
    Column(modifier = modifier.fillMaxSize().background(MultiverseColors.surface)) {
        // The section's title, in the place every top-level screen puts it (Figma `101:637`).
        ScreenTitle(
            text = CopyResolver.copy(CopyKeys.NAV_FAVORITES.value),
            modifier =
                Modifier.padding(
                    start = MultiverseDimensions.spaceL,
                    end = MultiverseDimensions.spaceL,
                    top = MultiverseDimensions.spaceL,
                ),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            FavoritesBody(
                state = state,
                seam = seam,
                onCharacterSelected = onCharacterSelected,
                onIntent = onIntent,
                onBrowseCharacters = onBrowseCharacters,
                illustration = illustration,
                portalMark = portalMark,
            )
        }
    }
}

/** What the stored set renders under the title: nothing yet, the empty state, the error or the grid. */
@Composable
private fun FavoritesBody(
    state: FavoritesUiState,
    seam: ImageSeam,
    onCharacterSelected: (CharacterCardUi) -> Unit,
    onIntent: (FavoritesIntent) -> Unit,
    onBrowseCharacters: () -> Unit,
    illustration: Painter,
    portalMark: Painter?,
) {
    when (val load = state.loadState) {
        LoadState.Loading -> Unit

        LoadState.Empty ->
            FavoritesEmptyState(
                onBrowseCharacters = onBrowseCharacters,
                illustration = illustration,
            )

        is LoadState.Error ->
            FullSurfaceError(
                failure = load.failure,
                onRetry = { onIntent(FavoritesIntent.Retry) },
            )

        LoadState.Content ->
            Grid(
                items = state.items,
                seam = seam,
                portalMark = portalMark,
                onCharacterSelected = onCharacterSelected,
            )
    }
}

/**
 * The favourite grid (`UI_SPEC.md` §4.1, §6.2): two fixed columns, 12 dp gutters, and the same
 * `Tall`-when-`index % 4` is 0 or 3 mix as Discovery, so the two lanes never line up.
 */
@Composable
private fun Grid(
    items: List<CharacterCardUi>,
    seam: ImageSeam,
    portalMark: Painter?,
    onCharacterSelected: (CharacterCardUi) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = MultiverseGrid.columns(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MultiverseDimensions.spaceL),
        horizontalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceM),
        verticalItemSpacing = MultiverseDimensions.spaceM,
    ) {
        itemsIndexed(items, key = { _, card -> card.id.value }) { index, card ->
            CharacterCard(
                name = card.name,
                species = card.species.resolve(),
                statusTone = card.status.tone(),
                statusLabel = CopyResolver.copy(card.statusLabel.value),
                imageUrl = card.imageUrl,
                seam = seam,
                height = if (index % TALL_EVERY == 0 || index % TALL_EVERY == TALL_EVERY - 1) CardHeight.Tall else CardHeight.Regular,
                portalMark = portalMark,
                onClick = { onCharacterSelected(card) },
                // A favourite opens the same Detail, so it is the same transition's source (`DEC-135`).
                sharedKey = PortraitTransition.key(card.id.value),
            )
        }
    }
}

/**
 * The full-surface error (`ERROR_FLOW.md` §4, `UI_SPEC.md` §8): the shared title, the
 * `ApiFailure`-specific message of `IC-017` and the shared retry affordance.
 *
 * Every string is a copy key resolved at this seam: the message comes from
 * `DefaultPresentationFormatters.failureMessage`, so the screen never builds a message from a failure
 * field (`REQ-FUNC-013`, `REQ-UX-008`).
 */
@Composable
private fun FullSurfaceError(
    failure: ApiFailure,
    onRetry: () -> Unit,
) {
    // The same full-surface error as Discovery's: the portal mark at 40 %, the shared title, the
    // failure's own message and a filled Retry (`UI_SPEC.md` §8).
    val message = DefaultPresentationFormatters.failureMessage(failure)
    EmptyState(
        heading = CopyResolver.copy(DefaultPresentationFormatters.failureTitle().value),
        // The typed arguments go to the resource formatter unchanged (`DEC-123`).
        body = CopyResolver.copy(message.key.value, *message.formatArguments()),
        illustration = LocalPortalMark.current ?: ColorPainter(Color.Transparent),
        illustrationTint = Color.Unspecified,
        illustrationAlpha = PORTAL_MARK_ALPHA,
        actionLabel = CopyResolver.copy(DefaultPresentationFormatters.retryAction().value),
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

/** The portal mark's opacity on the error state (`UI_SPEC.md` §8: "Portal logo (40%)"). */
private const val PORTAL_MARK_ALPHA = 0.4f

/** The displayed species of a card, resolved from the shared display text (`IC-016`). */
@Composable
private fun DisplayText.resolve(): String =
    when (this) {
        is DisplayText.Data -> value
        is DisplayText.Copy -> CopyResolver.copy(key.value)
    }

/** The status mirror the design system's badge renders (`StatusTone`). */
private fun CharacterStatus.tone(): StatusTone =
    when (this) {
        CharacterStatus.Alive -> StatusTone.Alive
        CharacterStatus.Dead -> StatusTone.Dead
        CharacterStatus.Unknown, is CharacterStatus.Unsupported -> StatusTone.Unknown
    }

/** The mix of card heights: a `Tall` card at 0 and 3 of every 4 (`UI_SPEC.md` §4.1). */
private const val TALL_EVERY: Int = 4
