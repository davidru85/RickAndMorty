package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.davidru85.multiverse.core.designsystem.components.CardHeight
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCard
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCardSkeleton
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.ScreenTitle
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.image.LocalPortalMark
import io.github.davidru85.multiverse.core.designsystem.layout.MultiverseGrid
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitTransition
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseComponentDimensions
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.formatArguments
import io.github.davidru85.multiverse.feature.discovery.R
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
            // The app bar takes Surface Container once the grid has scrolled under it (`UI_SPEC.md` §6.2).
            val scrolled by remember(gridState) {
                derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
            }
            SearchField(state = state, onIntent = onIntent, scrolled = scrolled)
            Headline(state = state)
            FilterRow(state = state, onIntent = onIntent)
            when (val loadState = state.loadState) {
                is LoadState.Empty -> DiscoveryEmptyState(state = state, onIntent = onIntent)

                is LoadState.Error ->
                    DiscoveryErrorState(
                        failure = loadState.failure,
                        onRetry = { onIntent(CharacterListIntent.Retry) },
                    )

                // The refresh gesture is bound to the shared state: a pull sends `Refresh`, and the
                // indicator holds while `isRefreshing` (`REQ-FUNC-012`, `DEC-134`).
                LoadState.Loading, LoadState.Content ->
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { onIntent(CharacterListIntent.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CharacterGrid(
                            state = state,
                            onIntent = onIntent,
                            seam = seam,
                            gridState = gridState,
                            onOpenDetail = onOpenDetail,
                        )
                    }
            }
        }
        DiscoveryNotice(state = state, onIntent = onIntent, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The 64 dp top app bar holding the M3 search bar (`UI_SPEC.md` §4.1, §6.2, Figma `20:1752`): a leading
 * search glyph and the spec's placeholder, and no mic and no avatar (`DEC-002`). Its container is
 * Surface, animating to Surface Container once the grid has scrolled under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    scrolled: Boolean,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = remember { TextFieldState(state.filter.query) }
    // The text the state last wrote into the field. Its echo through the edit stream is not a user
    // edit, so it is not sent back as a `QueryChanged` (`DEC-129`); every other edit is.
    val stateWritten = remember { mutableStateOf<String?>(state.filter.query) }
    val appBar by animateColorAsState(
        targetValue = if (scrolled) MultiverseColors.surfaceContainer else MultiverseColors.surface,
        label = "app-bar-container",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppBarHeight)
                .background(appBar)
                .padding(horizontal = MultiverseDimensions.spaceL),
        contentAlignment = Alignment.Center,
    ) {
        SearchBar(
            state = searchBarState,
            inputField = {
                SearchBarDefaults.InputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    onSearch = { onIntent(CharacterListIntent.QueryChanged(it)) },
                    placeholder = { Text(CopyResolver.copy(CopyKeys.SEARCH_CHARACTERS.value)) },
                    // Decorative: the field's own semantics name it (`UI_SPEC.md` §9).
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SearchBarDefaults.colors(containerColor = MultiverseColors.surfaceContainerHigh),
        )
    }
    LaunchedEffect(state.filter.query) {
        // A query the state changed — Clear filters — is shown in the field.
        val query = state.filter.query
        if (textFieldState.text.toString() != query) {
            stateWritten.value = query
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }
    LaunchedEffect(textFieldState) {
        // The field holds the raw text and reports every edit; the reducer owns the 300 ms debounce, so
        // nothing here decides when a request happens (`REQ-FUNC-003`, `DEC-002`: a dictated query takes
        // the same path).
        snapshotFlow { textFieldState.text.toString() }.collect { query ->
            if (query == stateWritten.value) {
                stateWritten.value = null
            } else {
                onIntent(CharacterListIntent.QueryChanged(query))
            }
        }
    }
}

/** The headline and its count line, whose number is the server's `info.count` (`AC-REQ-FUNC-001-3`). */
@Composable
private fun Headline(state: CharacterListUiState) {
    // 16 dp below the app bar (Figma `20:1842`).
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(
            text = CopyResolver.copy(CopyKeys.NAV_CHARACTERS.value),
            modifier =
                Modifier.padding(
                    start = MultiverseDimensions.spaceL,
                    end = MultiverseDimensions.spaceL,
                    top = MultiverseDimensions.spaceL,
                ),
        )
        CountLine(totalCount = state.totalCount)
    }
}

/**
 * The count line, which **keeps its line** while the count is unknown (`TASK-129`, `UI_SPEC.md` §6.2). A
 * filter change resets the total until the new page answers; removing the line for that time moved the
 * chips and the grid up and back. While the count is unknown the line keeps the last count's text —
 * `0` before any — at zero opacity and cleared from semantics, so it holds the same height and a screen
 * reader never hears a number that no longer applies.
 */
@Composable
private fun CountLine(totalCount: Int?) {
    val lastCount = remember { mutableIntStateOf(totalCount ?: 0) }
    SideEffect { if (totalCount != null) lastCount.intValue = totalCount }
    val known = totalCount != null
    val opacity by animateFloatAsState(
        targetValue = if (known) 1f else 0f,
        animationSpec = tween(COUNT_LINE_FADE_MILLIS),
        label = "count-line",
    )
    Text(
        text =
            CopyResolver
                .copy(CopyKeys.CHARACTERS_COUNT.value)
                .format(DefaultPresentationFormatters.charactersCount(totalCount ?: lastCount.intValue)),
        style = MaterialTheme.typography.bodyMedium,
        color = MultiverseColors.onSurfaceVariant,
        modifier =
            Modifier
                .padding(horizontal = MultiverseDimensions.spaceL)
                .graphicsLayer { alpha = opacity }
                .then(if (known) Modifier else Modifier.clearAndSetSemantics { }),
    )
}

/** The count line's fade when the count leaves or returns (`UI_SPEC.md` §6.2). */
private const val COUNT_LINE_FADE_MILLIS = 150

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
    // The chips wait for the first page: a tap during the initial load would reset a load that has not
    // answered yet (`UI_SPEC.md` §8, "filters disabled").
    val enabled = state.loadState != LoadState.Loading
    Row(
        // 18 dp below the headline, 8 dp apart (Figma `20:1845`). On a narrow screen or at a large font
        // scale the row scrolls rather than wrapping a label onto several lines.
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    start = MultiverseDimensions.spaceL,
                    end = MultiverseDimensions.spaceL,
                    top = MultiverseComponentDimensions.chipsTopGap,
                ),
        horizontalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceS),
    ) {
        options.forEach { (status, key) ->
            val selected = state.filter.status == status
            ElevatedFilterChip(
                selected = selected,
                enabled = enabled,
                onClick = { onIntent(CharacterListIntent.StatusSelected(status)) },
                label = { Text(CopyResolver.copy(key.value), maxLines = 1) },
                leadingIcon =
                    if (selected) {
                        { Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
            )
        }
    }
}

/** The stated empty-results surface (`UI_SPEC.md` §8, `TASK-010`): the message and "Clear filters". */
@Composable
private fun DiscoveryEmptyState(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
) {
    EmptyState(
        heading =
            CopyResolver
                .copy(CopyKeys.EMPTY_SEARCH_MESSAGE.value)
                .format(state.filter.query),
        body = "",
        illustration = portalMark(),
        illustrationTint = Color.Unspecified,
        illustrationAlpha = PORTAL_MARK_ALPHA,
        actionLabel = CopyResolver.copy(CopyKeys.ACTION_CLEAR_FILTERS.value),
        // Both dimensions in one intent; the field and the chips follow the state (`DEC-129`).
        onAction = { onIntent(CharacterListIntent.ClearFilters) },
        modifier = Modifier.padding(top = MultiverseDimensions.spaceL),
    )
}

/** The full-surface error state: the shared title, the failure's own message and one retry action. */
@Composable
private fun DiscoveryErrorState(
    failure: ApiFailure,
    onRetry: () -> Unit,
) {
    val message = DefaultPresentationFormatters.failureMessage(failure)
    EmptyState(
        heading = CopyResolver.copy(DefaultPresentationFormatters.failureTitle().value),
        // The typed arguments go to the resource formatter unchanged (`DEC-123`).
        body = CopyResolver.copy(message.key.value, *message.formatArguments()),
        illustration = portalMark(),
        illustrationTint = Color.Unspecified,
        illustrationAlpha = PORTAL_MARK_ALPHA,
        actionLabel = CopyResolver.copy(DefaultPresentationFormatters.retryAction().value),
        onAction = onRetry,
        modifier = Modifier.fillMaxSize().padding(MultiverseDimensions.spaceL),
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
            // 16 dp screen margins and 12 dp gutters, 20 dp below the chips (Figma `20:1868`, `UI_SPEC.md` §3.3).
            contentPadding =
                PaddingValues(
                    start = MultiverseDimensions.spaceL,
                    end = MultiverseDimensions.spaceL,
                    top = MultiverseComponentDimensions.gridTopGap,
                    bottom = MultiverseDimensions.spaceL,
                ),
            horizontalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceM),
            verticalItemSpacing = MultiverseDimensions.spaceM,
        ) {
            if (state.loadState == LoadState.Loading) {
                // Six skeletons in the Tall/Regular pattern while no load has completed (§8).
                items(SKELETON_COUNT) { index -> CharacterCardSkeleton(height = heightOf(index)) }
            } else {
                // Keyed by the canonical id, so a filter change animates the cards that stay rather than
                // re-binding them by position (`UI_SPEC.md` §7, "Chips / filters").
                itemsIndexed(items, key = { _, card -> card.id.value }, contentType = { _, _ -> CARD_CONTENT_TYPE }) { index, card ->
                    CharacterCard(
                        modifier = Modifier.animateItem(),
                        name = card.name,
                        species = card.species.text(),
                        statusTone = card.status.tone(),
                        statusLabel = CopyResolver.copy(card.statusLabel.value),
                        imageUrl = card.imageUrl,
                        seam = seam,
                        height = heightOf(index),
                        onClick = { onOpenDetail(card) },
                        // The source of the card→Detail shared element (`DEC-135`).
                        sharedKey = PortraitTransition.key(card.id.value),
                    )
                }
            }
            if (state.isAppending) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MultiverseDimensions.spaceM),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The 48 dp contained indicator is the paging indicator, and only that (§8).
                        ContainedLoadingIndicator(
                            containerColor = MultiverseColors.secondaryContainer,
                            modifier = Modifier.size(MultiverseComponentDimensions.pagingIndicator),
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
    SnackbarHost(hostState = host, modifier = modifier.padding(MultiverseDimensions.spaceL))
}

/** The brand mark the shell provides for the empty and error states, or nothing in a bare preview. */
@Composable
private fun portalMark(): Painter = LocalPortalMark.current ?: ColorPainter(Color.Transparent)

/** The portal mark's opacity on the empty and error states (`UI_SPEC.md` §8: "Portal logo (40%)"). */
private const val PORTAL_MARK_ALPHA = 0.4f

/** The single content type of the grid's cards, so the lazy layout reuses their compositions. */
private const val CARD_CONTENT_TYPE = "character-card"

/** The top app bar's height (`UI_SPEC.md` §4.1): 64 dp. */
private val AppBarHeight = MultiverseComponentDimensions.appBarHeight

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
