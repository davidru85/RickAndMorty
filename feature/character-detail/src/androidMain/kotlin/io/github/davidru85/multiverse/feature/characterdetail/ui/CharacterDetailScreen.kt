package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.components.CharacterPortrait
import io.github.davidru85.multiverse.core.designsystem.components.InfoListGroup
import io.github.davidru85.multiverse.core.designsystem.components.InfoListItem
import io.github.davidru85.multiverse.core.designsystem.components.StatTileRow
import io.github.davidru85.multiverse.core.designsystem.components.StatusBadge
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.presentation.CopyKey
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.R
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailIntent
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.InfoRowKind

/** The hero's size (`UI_SPEC.md` §6.3): 412 × 468 dp, a 412:468 aspect on the full width. */
private val HeroAspect = 412f / 468f

/** The tonal container of the controls over the hero (`UI_SPEC.md` §4.1): 40 dp at 72 % over the image. */
private val ControlSize = 40.dp

/**
 * The Character detail screen (`UI_SPEC.md` §6.3, `TASK-002`, `TASK-023`).
 *
 * It renders [state] and nothing it derives itself: the hero, the title block, the three connected
 * stat tiles, the info rows and the extended FAB all read fields the shared `IC-019` state and
 * `IC-017` produced, so a platform value cannot drift from the other platform's
 * (`CONTRACTS.md` §7 R2).
 *
 * The one thing the screen owns is the platform concern: the glyphs, the tonal controls, and the
 * [seam] the hero draws through. The header is present in the first composed frame because `state`
 * already carries it — the screen never waits for the load — and a failure shows the inline retry in
 * the info list's place while the hero, the name and the badge stay (`AC-REQ-FUNC-002-1`, `-3`).
 *
 * [portalMark] is the design system's error-state painter (`UI_SPEC.md` §5.3) and is passed through
 * to the hero; the module names no icon library and no image API of its own.
 */
@Composable
public fun CharacterDetailScreen(
    state: CharacterDetailUiState,
    seam: ImageSeam,
    onIntent: (CharacterDetailIntent) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    portalMark: Painter? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MultiverseColors.surface)
                .verticalScroll(rememberScrollState()),
    ) {
        Hero(state = state, seam = seam, portalMark = portalMark, onBack = onBack, onShare = onShare)
        TitleBlock(state = state)
        Stats(state = state)
        Info(state = state, onIntent = onIntent)
        FavoriteAction(isFavorite = state.isFavorite, onIntent = onIntent)
    }
}

/** The collapsing hero: a full-bleed portrait with the tonal back and share controls over it. */
@Composable
private fun Hero(
    state: CharacterDetailUiState,
    seam: ImageSeam,
    portalMark: Painter?,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(HeroAspect)) {
        CharacterPortrait(
            imageUrl = state.header?.imageUrl.orEmpty(),
            seam = seam,
            modifier = Modifier.fillMaxSize(),
            portalMark = portalMark,
            // The portrait stands alone here rather than inside a card, so it carries the name.
            contentDescription = state.header?.name,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(MultiverseDimensions.spaceL),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroControl(glyph = R.drawable.ic_arrow_back, label = CopyKeys.ACTION_BACK, onClick = onBack)
            HeroControl(glyph = R.drawable.ic_share, label = CopyKeys.ACTION_SHARE, onClick = onShare)
        }
    }
}

/** One tonal icon button over the image (`UI_SPEC.md` §4.1): a 48 dp target holding a 40 dp container. */
@Composable
private fun HeroControl(
    glyph: Int,
    label: CopyKey,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(MultiverseDimensions.space3Xl),
        colors =
            androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MultiverseColors.surfaceContainerHighest.copy(alpha = 0.72f),
            ),
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = CopyResolver.copy(label.value),
            modifier = Modifier.size(ControlSize),
        )
    }
}

/** The badge, the name and the `Species · Gender · Origin` line (`UI_SPEC.md` §6.3). */
@Composable
private fun TitleBlock(state: CharacterDetailUiState) {
    val header = state.header ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(MultiverseDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceS),
    ) {
        StatusBadge(
            tone = header.status.tone(),
            label = CopyResolver.copy(header.statusLabel.value),
        )
        Text(
            text = header.name,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MultiverseColors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Subtitle(state = state, species = header.species)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `Species · Gender · Origin` joined from data-derived parts only. The screen joins the parts it is
 * given rather than deriving any of them: [CharacterDetailUiState.dimension] and the origin row's
 * value already carry the shared derivations, and a part that is absent is left out.
 */
@Composable
private fun Subtitle(
    state: CharacterDetailUiState,
    species: DisplayText,
): String? {
    val origin = state.info.firstOrNull { it.kind == InfoRowKind.Origin }?.value
    val parts =
        listOfNotNull(
            when (species) {
                is DisplayText.Data -> species.value
                is DisplayText.Copy -> CopyResolver.copy(species.key.value)
            },
            origin,
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The three connected tiles (`UI_SPEC.md` §6.3): Episodes count · Dimension · Species. */
@Composable
private fun Stats(state: CharacterDetailUiState) {
    val header = state.header ?: return
    val tiles =
        buildList {
            state.episodeCount?.let {
                add(
                    Triple(
                        it.toString(),
                        CopyResolver.copy(CopyKeys.DETAIL_STAT_EPISODES.value),
                        MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer,
                    ),
                )
            }
            // `null` dimension means hide the tile, never a placeholder (`IC-019`, UI_SPEC.md 6.3).
            state.dimension?.let {
                add(
                    Triple(
                        it,
                        CopyResolver.copy(CopyKeys.DETAIL_STAT_DIMENSION.value),
                        MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer,
                    ),
                )
            }
            val species =
                when (val text = header.species) {
                    is DisplayText.Data -> text.value
                    is DisplayText.Copy -> CopyResolver.copy(text.key.value)
                }
            add(
                Triple(
                    species,
                    CopyResolver.copy(CopyKeys.DETAIL_STAT_SPECIES.value),
                    MultiverseColors.secondaryFixedDim to MultiverseColors.onSecondaryFixed,
                ),
            )
        }
    if (tiles.isEmpty()) return
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = MultiverseDimensions.spaceL)) {
        StatTileRow(tiles = tiles)
    }
}

/**
 * The info list and the inline error (`UI_SPEC.md` §6.3, §8). On a failure the list is replaced by the
 * inline `detail_error_inline` message and its Retry — never by a full-surface error, because the
 * header above it is still the list's data (`AC-REQ-FUNC-002-3`).
 */
@Composable
private fun Info(
    state: CharacterDetailUiState,
    onIntent: (CharacterDetailIntent) -> Unit,
) {
    val failure = state.loadState as? LoadState.Error
    if (failure != null) {
        InlineError(onRetry = { onIntent(CharacterDetailIntent.Retry) })
        return
    }
    if (state.info.isEmpty()) return
    Box(modifier = Modifier.fillMaxWidth().padding(MultiverseDimensions.spaceL)) {
        InfoListGroup {
            state.info.forEach { row ->
                InfoListItem(
                    label = CopyResolver.copy(row.copyKey.value),
                    value = row.value,
                    icon = painterResource(row.kind.glyph()),
                )
            }
        }
    }
}

/** The inline error of `ERROR_FLOW.md` §4: the message plus the shared retry affordance. */
@Composable
private fun InlineError(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(MultiverseDimensions.spaceL)
                .clip(RoundedCornerShape(MultiverseDimensions.cornerExtraLarge))
                .background(MultiverseColors.errorContainer)
                .padding(MultiverseDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceS),
    ) {
        Text(
            text = CopyResolver.copy(CopyKeys.DETAIL_ERROR_INLINE.value),
            style = MaterialTheme.typography.bodyMedium,
            color = MultiverseColors.onErrorContainer,
        )
        TextButton(onClick = onRetry) {
            Text(text = CopyResolver.copy(CopyKeys.ACTION_RETRY.value), color = MultiverseColors.onErrorContainer)
        }
    }
}

/** The medium extended FAB (`UI_SPEC.md` §4.1, §6.3): unmarked outline, marked filled heart. */
@Composable
private fun FavoriteAction(
    isFavorite: Boolean,
    onIntent: (CharacterDetailIntent) -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = { onIntent(CharacterDetailIntent.ToggleFavorite) },
        modifier = Modifier.padding(MultiverseDimensions.spaceL),
        containerColor = MultiverseColors.primary,
        contentColor = MultiverseColors.onPrimary,
        icon = {
            Icon(
                painter = painterResource(if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                // The control announces its action, and the label below it carries the state
                // (`UI_SPEC.md` §6.3): unmarked is the outline, marked is the filled heart.
                contentDescription = CopyResolver.copy(CopyKeys.DETAIL_ACTION_FAVORITE.value),
            )
        },
        text = { Text(text = CopyResolver.copy(CopyKeys.DETAIL_ACTION_FAVORITE.value)) },
    )
}

/** The status mirror the design system's badge renders (`StatusTone`). */
private fun CharacterStatus.tone(): StatusTone =
    when (this) {
        CharacterStatus.Alive -> StatusTone.Alive
        CharacterStatus.Dead -> StatusTone.Dead
        CharacterStatus.Unknown, is CharacterStatus.Unsupported -> StatusTone.Unknown
    }

/** The bundled glyph for an info row (`UI_SPEC.md` §6.3): Origin, LastKnownLocation, FirstSeenIn. */
private fun InfoRowKind.glyph(): Int =
    when (this) {
        InfoRowKind.Origin -> R.drawable.ic_origin
        InfoRowKind.LastKnownLocation -> R.drawable.ic_location
        InfoRowKind.FirstSeenIn -> R.drawable.ic_first_seen
    }
