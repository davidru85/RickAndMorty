package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.components.CharacterPortrait
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.InfoListGroup
import io.github.davidru85.multiverse.core.designsystem.components.InfoListItem
import io.github.davidru85.multiverse.core.designsystem.components.StatTileRow
import io.github.davidru85.multiverse.core.designsystem.components.StatusBadge
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitTransition
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKey
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.Recovery
import io.github.davidru85.multiverse.core.presentation.formatArguments
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
 * already carries it — the screen never waits for the load — and a failure shows the inline error in
 * the info list's place while the hero, the name and the badge stay (`AC-REQ-FUNC-002-1`, `-3`). A
 * failure with no header has nothing known to keep, so it renders the full-surface error state
 * instead of an empty hero; both offer the `IC-017` recovery — Back for a not-found detail, Retry for
 * everything else (`ERROR_FLOW.md` §4, §10, `DEC-131`).
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
    /** Shares the character on screen; the caller builds the payload from it (`DEC-125`). */
    onShare: (CharacterCardUi) -> Unit,
    modifier: Modifier = Modifier,
    portalMark: Painter? = null,
) {
    val failure = (state.loadState as? LoadState.Error)?.failure
    if (failure != null && state.header == null) {
        DetailErrorState(
            failure = failure,
            onIntent = onIntent,
            onBack = onBack,
            illustration = portalMark ?: TransparentPainter,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MultiverseColors.surface)
                .verticalScroll(rememberScrollState())
                // The screen draws edge to edge (Figma `21:1217`): the hero sits under the status bar,
                // and the content ends above the gesture area.
                .navigationBarsPadding(),
    ) {
        Hero(state = state, seam = seam, portalMark = portalMark, onBack = onBack, onShare = onShare)
        TitleBlock(state = state)
        Stats(state = state)
        Info(state = state, onIntent = onIntent, onBack = onBack)
        FavoriteAction(isFavorite = state.isFavorite, onIntent = onIntent)
    }
}

/**
 * The full-surface error state of `ERROR_FLOW.md` §4 for a failure with no header (`AC-REQ-UX-009-1`,
 * `DEC-131`): the shared title, the failure's own message and its one recovery, under the back control
 * the hero would otherwise carry.
 */
@Composable
private fun DetailErrorState(
    failure: ApiFailure,
    onIntent: (CharacterDetailIntent) -> Unit,
    onBack: () -> Unit,
    illustration: Painter,
    modifier: Modifier = Modifier,
) {
    val formatters = DefaultPresentationFormatters
    val message = formatters.failureMessage(failure)
    val recovery = formatters.recovery(failure)
    Column(modifier = modifier.fillMaxSize().background(MultiverseColors.surface).systemBarsPadding()) {
        Box(modifier = Modifier.padding(MultiverseDimensions.spaceL)) {
            HeroControl(glyph = R.drawable.ic_arrow_back, label = CopyKeys.ACTION_BACK, onClick = onBack)
        }
        EmptyState(
            heading = CopyResolver.copy(formatters.failureTitle().value),
            // The typed arguments go to the resource formatter unchanged (`DEC-123`).
            body = CopyResolver.copy(message.key.value, *message.formatArguments()),
            illustration = illustration,
            actionLabel = CopyResolver.copy(recovery.actionKey.value),
            onAction = { recovery.perform(onIntent, onBack) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Runs this recovery: Retry is an intent to the state holder, Back is the caller's navigation. */
private fun Recovery.perform(
    onIntent: (CharacterDetailIntent) -> Unit,
    onBack: () -> Unit,
) {
    when (this) {
        Recovery.Retry -> onIntent(CharacterDetailIntent.Retry)
        Recovery.Back -> onBack()
    }
}

/** The collapsing hero: a full-bleed portrait with the tonal back and share controls over it. */
@Composable
private fun Hero(
    state: CharacterDetailUiState,
    seam: ImageSeam,
    portalMark: Painter?,
    onBack: () -> Unit,
    onShare: (CharacterCardUi) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(HeroAspect)) {
        CharacterPortrait(
            imageUrl = state.header?.imageUrl.orEmpty(),
            seam = seam,
            modifier = Modifier.fillMaxSize(),
            portalMark = portalMark,
            // The portrait stands alone here rather than inside a card, so it carries the name.
            contentDescription = state.header?.name,
            // The target of the card→Detail shared element, under the card's key (`DEC-135`).
            sharedKey = state.header?.id?.let { PortraitTransition.key(it.value) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(MultiverseDimensions.spaceL),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroControl(glyph = R.drawable.ic_arrow_back, label = CopyKeys.ACTION_BACK, onClick = onBack)
            // Share names the character, so it waits for a header: a deep link has none until the
            // detail arrives, and then the reducer builds one from the response.
            val header = state.header
            HeroControl(
                glyph = R.drawable.ic_share,
                label = CopyKeys.ACTION_SHARE,
                enabled = header != null,
                onClick = { header?.let(onShare) },
            )
        }
    }
}

/** One tonal icon button over the image (`UI_SPEC.md` §4.1): a 48 dp target holding a 40 dp container. */
@Composable
private fun HeroControl(
    glyph: Int,
    label: CopyKey,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
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
        val status = CopyResolver.copy(header.statusLabel.value)
        StatusBadge(
            tone = header.status.tone(),
            label = status,
            // Standalone, so it announces the status sentence (`UI_SPEC.md` §9).
            announcement = CopyResolver.copy(CopyKeys.STATUS_ANNOUNCEMENT.value, status),
        )
        Text(
            text = header.name,
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MultiverseColors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Subtitle(state = state, species = header.species)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleMedium,
                color = MultiverseColors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `Species · Gender · Origin` (`UI_SPEC.md` §6.3, `DEC-131`). The screen joins the parts it is given
 * rather than deriving any of them: [CharacterDetailUiState.gender] and the origin row's value already
 * carry the shared derivations, and a part that is absent is left out — the gender until the detail
 * answers. An unknown origin is left out too: the Origin row below already reads "Unknown", and the
 * subtitle names a place or nothing.
 */
@Composable
private fun Subtitle(
    state: CharacterDetailUiState,
    species: DisplayText,
): String? {
    val gender = state.gender?.let { CopyResolver.copy(it.value) }
    val origin = (state.info.firstOrNull { it.kind == InfoRowKind.Origin }?.value as? DisplayText.Data)?.value
    val parts = listOfNotNull(species.text(), gender, origin)
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
            add(
                Triple(
                    header.species.text(),
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
 * inline error — never by a full-surface error, because the header above it is still the list's data
 * (`AC-REQ-FUNC-002-3`) — with the `IC-017` message and recovery: `detail_error_inline` and Retry, or
 * the not-found message and Back (`DEC-131`).
 */
@Composable
private fun Info(
    state: CharacterDetailUiState,
    onIntent: (CharacterDetailIntent) -> Unit,
    onBack: () -> Unit,
) {
    val failure = state.loadState as? LoadState.Error
    if (failure != null) {
        InlineError(failure = failure.failure, onIntent = onIntent, onBack = onBack)
        return
    }
    if (state.info.isEmpty()) return
    Box(modifier = Modifier.fillMaxWidth().padding(MultiverseDimensions.spaceL)) {
        InfoListGroup {
            state.info.forEach { row ->
                InfoListItem(
                    label = CopyResolver.copy(row.copyKey.value),
                    value = row.value.text(),
                    icon = painterResource(row.kind.glyph()),
                )
            }
        }
    }
}

/** The inline error of `ERROR_FLOW.md` §4: the message plus the failure's one recovery. */
@Composable
private fun InlineError(
    failure: ApiFailure,
    onIntent: (CharacterDetailIntent) -> Unit,
    onBack: () -> Unit,
) {
    val message = DefaultPresentationFormatters.inlineFailureMessage(failure)
    val recovery = DefaultPresentationFormatters.recovery(failure)
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
            text = CopyResolver.copy(message.key.value, *message.formatArguments()),
            style = MaterialTheme.typography.bodyMedium,
            color = MultiverseColors.onErrorContainer,
        )
        TextButton(onClick = { recovery.perform(onIntent, onBack) }) {
            Text(text = CopyResolver.copy(recovery.actionKey.value), color = MultiverseColors.onErrorContainer)
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

/** The illustration a preview or a case gets when the caller passes no portal mark. */
private val TransparentPainter: Painter = ColorPainter(Color.Transparent)

/** A [DisplayText] resolved for the design system, which takes primitives only (`DESIGN.md` §3.4). */
@Composable
private fun DisplayText.text(): String =
    when (this) {
        is DisplayText.Data -> value
        is DisplayText.Copy -> CopyResolver.copy(key.value)
    }

/** The bundled glyph for an info row (`UI_SPEC.md` §6.3): Origin, LastKnownLocation, FirstSeenIn. */
private fun InfoRowKind.glyph(): Int =
    when (this) {
        InfoRowKind.Origin -> R.drawable.ic_origin
        InfoRowKind.LastKnownLocation -> R.drawable.ic_location
        InfoRowKind.FirstSeenIn -> R.drawable.ic_first_seen
    }
