package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.components.CardHeight
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCard
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCardSkeleton
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.layout.MultiverseGrid
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.formatArguments
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState

/**
 * The Discovery screen (`UI_SPEC.md` §6.2, `TASK-001`, `TASK-003`, `TASK-004`, `TASK-010`).
 *
 * The content order is the specification's: the app bar's search field, the headline with its count,
 * the four filter chips, then the staggered grid — or, in its place, the designed empty or error
 * surface. Every string is resolved from the one Android copy set through `CopyResolver`, and every
 * display value comes from `IC-017` or from the state object; the screen computes no label of its own
 * and never reaches a repository, a use case or the pager (`IC-018`, `CONTRACTS.md` R2).
 *
 * Paging is a view effect: one `LoadNextPage` is sent when the last visible index comes within
 * [PREFETCH_DISTANCE] of the end, and the reducer's own guards — in flight, end reached, failure —
 * decide what the pager does with it (`API_SPECS.md` §8).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun DiscoveryScreen(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** The image seam the cards draw with; the composition root supplies the one allow-listed loader. */
    seam: ImageSeam = PreviewSeam,
    /** The painter the empty and error surfaces draw; the shell passes the app's own illustration. */
    illustration: Painter = ColorPainter(Color.Transparent),
    /**
     * The card the user tapped, so the caller publishes it to the `IC-025` hand-off and navigates
     * (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`). The screen does not navigate itself, and it does not know
     * the detail destination exists (`ADR-0001`).
     */
    onOpenDetail: (CharacterCardUi) -> Unit = {},
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(state = state, onIntent = onIntent)
            Headline(state = state)
            FilterRow(state = state, onIntent = onIntent)
            when (val loadState = state.loadState) {
                is LoadState.Empty -> DiscoveryEmptyState(state = state, onIntent = onIntent, illustration = illustration)

                is LoadState.Error ->
                    DiscoveryErrorState(
                        failure = loadState.failure,
                        onRetry = { onIntent(CharacterListIntent.Retry) },
                        illustration = illustration,
                    )

                LoadState.Loading, LoadState.Content ->
                    CharacterGrid(
                        state = state,
                        onIntent = onIntent,
                        seam = seam,
                        gridState = gridState,
                        onOpenDetail = onOpenDetail,
                    )
            }
        }
        DiscoveryNotice(state = state, onIntent = onIntent, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The search field of `UI_SPEC.md` §4.1: the spec's placeholder, and no mic (`DEC-002`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = remember { TextFieldState(state.filter.query) }
    Surface(color = MultiverseColors.surface, modifier = Modifier.fillMaxWidth()) {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { onIntent(CharacterListIntent.QueryChanged(it)) },
            placeholder = { Text(CopyResolver.copy(CopyKeys.SEARCH_CHARACTERS.value)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    LaunchedEffect(textFieldState) {
        // The field holds the raw text and reports every edit; the reducer owns the 300 ms debounce, so
        // nothing here decides when a request happens (`REQ-FUNC-003`, `DEC-002`: a dictated query takes
        // the same path).
        snapshotFlow { textFieldState.text.toString() }.collect { query ->
            onIntent(CharacterListIntent.QueryChanged(query))
        }
    }
}

/** The headline and its count line, whose number is the server's `info.count` (`AC-REQ-FUNC-001-3`). */
@Composable
private fun Headline(state: CharacterListUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = CopyResolver.copy(CopyKeys.NAV_CHARACTERS.value),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MultiverseColors.onSurface,
        )
        state.totalCount?.let { count ->
            Text(
                text =
                    CopyResolver
                        .copy(CopyKeys.CHARACTERS_COUNT.value)
                        .format(DefaultPresentationFormatters.charactersCount(count)),
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
            )
        }
    }
}

/**
 * The four single-select filter chips (`UI_SPEC.md` §4.1, `AC-REQ-FUNC-004-2`). The option list is built
 * from `CopyKeys`, so the row cannot drift from the shared copy vocabulary, and `All` is selected by
 * default because [CharacterListUiState.filter]'s default status is `StatusFilter.All`.
 */
@Composable
private fun FilterRow(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
) {
    val options =
        listOf(
            StatusFilter.All to CopyKeys.FILTER_ALL,
            StatusFilter.Alive to CopyKeys.STATUS_ALIVE,
            StatusFilter.Dead to CopyKeys.STATUS_DEAD,
            StatusFilter.Unknown to CopyKeys.VALUE_UNKNOWN,
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, key) ->
            ElevatedFilterChip(
                selected = state.filter.status == status,
                onClick = { onIntent(CharacterListIntent.StatusSelected(status)) },
                label = { Text(CopyResolver.copy(key.value)) },
            )
        }
    }
}

/** The stated empty-results surface (`UI_SPEC.md` §8, `TASK-010`): the message and "Clear filters". */
@Composable
private fun DiscoveryEmptyState(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    illustration: Painter,
) {
    EmptyState(
        heading =
            CopyResolver
                .copy(CopyKeys.EMPTY_SEARCH_MESSAGE.value)
                .format(state.filter.query),
        body = "",
        illustration = illustration,
        actionLabel = CopyResolver.copy(CopyKeys.ACTION_CLEAR_FILTERS.value),
        // Clearing both dimensions is what "clear filters" means, and the query is the one dimension a
        // chip cannot clear; the chip row follows the state on the next frame (`AC-REQ-FUNC-010-2`).
        onAction = { onIntent(CharacterListIntent.QueryChanged("")) },
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** The full-surface error state: the shared title, the failure's own message and one retry action. */
@Composable
private fun DiscoveryErrorState(
    failure: ApiFailure,
    onRetry: () -> Unit,
    illustration: Painter,
) {
    val message = DefaultPresentationFormatters.failureMessage(failure)
    EmptyState(
        heading = CopyResolver.copy(DefaultPresentationFormatters.failureTitle().value),
        // The typed arguments go to the resource formatter unchanged (`DEC-123`).
        body = CopyResolver.copy(message.key.value, *message.formatArguments()),
        illustration = illustration,
        actionLabel = CopyResolver.copy(DefaultPresentationFormatters.retryAction().value),
        onAction = onRetry,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    )
}

/** The staggered grid: two fixed columns, 12 dp gutters, the Tall/Regular alternation of §4.1. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CharacterGrid(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    seam: ImageSeam,
    gridState: LazyStaggeredGridState,
    onOpenDetail: (CharacterCardUi) -> Unit,
) {
    val items = state.items
    if (state.loadState == LoadState.Content) {
        PagingEffect(state = state, gridState = gridState, itemCount = items.size, onIntent = onIntent)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = MultiverseGrid.columns(),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            if (state.loadState == LoadState.Loading) {
                // Six skeletons in the Tall/Regular pattern while no load has completed (§8).
                items(SKELETON_COUNT) { index -> CharacterCardSkeleton(height = heightOf(index)) }
            } else {
                itemsIndexed(items) { index, card ->
                    CharacterCard(
                        name = card.name,
                        species = card.species.text(),
                        statusTone = card.status.tone(),
                        statusLabel = CopyResolver.copy(card.statusLabel.value),
                        imageUrl = card.imageUrl,
                        seam = seam,
                        height = heightOf(index),
                        onClick = { onOpenDetail(card) },
                    )
                }
            }
            if (state.isAppending) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The 48 dp contained indicator is the paging indicator, and only that (§8).
                        ContainedLoadingIndicator(
                            containerColor = MultiverseColors.secondaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One `LoadNextPage` when the last visible index is within [PREFETCH_DISTANCE] of the end
 * (`API_SPECS.md` §8: prefetch at most the next page as the user approaches the end).
 */
@Composable
private fun PagingEffect(
    state: CharacterListUiState,
    gridState: LazyStaggeredGridState,
    itemCount: Int,
    onIntent: (CharacterListIntent) -> Unit,
) {
    val shouldLoadNext by
        remember(gridState, itemCount) {
            derivedStateOf {
                val last =
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: return@derivedStateOf false
                last >= itemCount - 1 - PREFETCH_DISTANCE && itemCount > 0
            }
        }
    LaunchedEffect(shouldLoadNext, itemCount, state.loadState) {
        if (shouldLoadNext && state.loadState == LoadState.Content) onIntent(CharacterListIntent.LoadNextPage)
    }
}

/**
 * The non-blocking notice over content (`UI_SPEC.md` §8: a snackbar with Retry; `ERROR_FLOW.md` §4,
 * §9; `DEC-124`). A load that failed beside displayable content shows its own class-specific message,
 * stale content shows "Showing saved results", and either offers Retry, which the reducer turns into
 * a re-attempt of the failed load or a network revalidation. Nothing shows while a refresh is in
 * flight, and a failure with nothing displayable is the full-surface error instead.
 */
@Composable
private fun DiscoveryNotice(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val failure = state.contentFailure
    val message =
        when {
            state.loadState != LoadState.Content || state.isRefreshing -> null
            failure != null -> {
                val failureMessage = DefaultPresentationFormatters.failureMessage(failure)
                CopyResolver.copy(failureMessage.key.value, *failureMessage.formatArguments())
            }
            state.isStale -> CopyResolver.copy(CopyKeys.STATE_STALE_BANNER.value)
            else -> null
        }
    val retry = CopyResolver.copy(DefaultPresentationFormatters.retryAction().value)
    val host = remember { SnackbarHostState() }
    // Keyed by the message: a new notice replaces the shown one, and a null one dismisses it, because
    // restarting the effect cancels the suspended `showSnackbar`.
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val result = host.showSnackbar(message = message, actionLabel = retry, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onIntent(CharacterListIntent.Retry)
    }
    SnackbarHost(hostState = host, modifier = modifier.padding(16.dp))
}

/** Tall when `index % 4` is 0 or 3, Regular otherwise (`UI_SPEC.md` §4.1). */
private fun heightOf(index: Int): CardHeight = if (index % 4 == 0 || index % 4 == 3) CardHeight.Tall else CardHeight.Regular

/** A [DisplayText] resolved for the design system, which takes primitives only (`DESIGN.md` §3.4). */
@Composable
private fun DisplayText.text(): String =
    when (this) {
        is DisplayText.Data -> value
        is DisplayText.Copy -> CopyResolver.copy(key.value)
    }

/** The design system's status mirror (`StatusTone`), mapped at the feature boundary. */
private fun CharacterStatus.tone(): StatusTone =
    when (this) {
        CharacterStatus.Alive -> StatusTone.Alive
        CharacterStatus.Dead -> StatusTone.Dead
        CharacterStatus.Unknown, is CharacterStatus.Unsupported -> StatusTone.Unknown
    }

/** The skeleton count of `UI_SPEC.md` §8, "Initial loading". */
private const val SKELETON_COUNT = 6

/** The rows a page request starts before the end of the list (`API_SPECS.md` §8). */
private const val PREFETCH_DISTANCE = 2

/** The seam a call that supplies none draws with: no I/O, the placeholder state. */
private object PreviewSeam : ImageSeam {
    @Composable
    override fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult = ImageSeamResult.Loading
}
