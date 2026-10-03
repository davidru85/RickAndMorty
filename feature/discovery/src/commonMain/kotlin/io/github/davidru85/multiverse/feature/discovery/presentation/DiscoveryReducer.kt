package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Discovery state holder's shared half (`IC-018`, `DESIGN.md` §4.1).
 *
 * The declarations are the contract the cases drive; the debounce, the superseded-request
 * cancellation, the page-1 reset and the `loadState` precedence are implemented in the commit that
 * follows this one, which is where these cases turn green.
 */
public class DiscoveryReducer(
    private val pager: CharacterPager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val formatters: PresentationFormatters,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
) {
    public val state: StateFlow<CharacterListUiState> = MutableStateFlow(CharacterListUiState())

    public fun onIntent(intent: CharacterListIntent) = Unit

    public fun start(initialFilter: CharacterFilter = CharacterFilter()): Job = scope.launch(dispatcher) { }

    public fun render(
        state: PagerState,
        isLoading: Boolean,
    ): CharacterListUiState =
        CharacterListUiState(
            filter = state.filter,
            items = state.items.map { CharacterCardUi.from(it, formatters) },
            totalCount = state.totalCount,
        )

    public companion object {
        /** The settled-query window of `API_SPECS.md` §8 and `REQ-FUNC-003`. */
        public val DEFAULT_DEBOUNCE: Duration = 300.milliseconds
    }
}
