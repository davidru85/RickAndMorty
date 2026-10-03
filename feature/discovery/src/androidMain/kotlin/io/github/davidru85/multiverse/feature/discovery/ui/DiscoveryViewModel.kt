package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import io.github.davidru85.multiverse.feature.discovery.presentation.DiscoveryReducer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow

/**
 * The Android Discovery state holder (`DESIGN.md` §4.1): a thin adapter over the shared
 * [DiscoveryReducer], which owns every rule — the debounce, the cancellation, the page-1 reset and the
 * `IC-018` precedence.
 *
 * It computes no display string and no status label: those come from `IC-017` and from the state
 * object, so the Android and iOS surfaces cannot diverge (`CONTRACTS.md` R2). The caller supplies the
 * scope's dispatcher and the pager resolved for that scope, so the graph decides where work runs and
 * closing the scope cancels every load (`GUIDELINES.md` §2.7).
 */
public class DiscoveryViewModel(
    /**
     * The pager for **this** screen, built here rather than injected: `IC-014` gives one pager to one
     * state-holder scope, and the scope is `viewModelScope`, which no graph can supply.
     */
    pagerFor: (kotlinx.coroutines.CoroutineScope) -> CharacterPager,
    dispatcher: CoroutineDispatcher,
    formatters: PresentationFormatters = DefaultPresentationFormatters,
) : ViewModel() {
    private val reducer =
        DiscoveryReducer(
            pager = pagerFor(viewModelScope),
            scope = viewModelScope,
            dispatcher = dispatcher,
            formatters = formatters,
        )

    /** What the screen renders; the screen holds no state of its own. */
    public val state: StateFlow<CharacterListUiState> = reducer.state

    init {
        reducer.start(CharacterFilter())
    }

    /** The only write path: every view interaction arrives here as an [CharacterListIntent]. */
    public fun onIntent(intent: CharacterListIntent) {
        reducer.onIntent(intent)
    }
}
