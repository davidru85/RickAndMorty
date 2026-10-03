package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.LoadState

/**
 * What the Discovery screen renders (`IC-018`). Both platforms construct this one type — the Android
 * `DiscoveryViewModel` and the iOS `ObservableObject` — so the two screens cannot diverge.
 *
 * Every field but [loadState]'s session tracking is a projection of the observed `PagerState`
 * (`IC-014`): the holder adds no second source of truth. [filter] is the filter the current [items]
 * were loaded with, and a state whose filter has changed but whose items still belong to the previous
 * filter is never emitted. [loadState] obeys the `IC-018` precedence — `Loading` while no load has
 * ended for the current filter, `Error` when the newest attempt failed with nothing displayable,
 * `Empty` when a load ended with no failure and no items, `Content` otherwise. [isAppending] is true
 * only with `Content`, and [isStale] implies `Content`.
 */
public data class CharacterListUiState(
    public val filter: CharacterFilter = CharacterFilter(),
    public val items: List<CharacterCardUi> = emptyList(),
    public val totalCount: Int? = null,
    public val loadState: LoadState = LoadState.Loading,
    public val isAppending: Boolean = false,
    public val isStale: Boolean = false,
)

/**
 * The Discovery intents (`IC-018`), the only write path into the state holder: a view never calls a
 * repository or a use case directly (`ERROR_FLOW.md` §3 invariant 4).
 */
public sealed interface CharacterListIntent {
    /** The user typed (or dictated) [query]; the active status is preserved. */
    public data class QueryChanged(
        public val query: String,
    ) : CharacterListIntent

    /** The user selected [status]; the active query is preserved and paging resets to page 1. */
    public data class StatusSelected(
        public val status: StatusFilter,
    ) : CharacterListIntent

    /** The grid approached its end; the next page is requested unless the pager refuses. */
    public data object LoadNextPage : CharacterListIntent

    /** The user asked for a refresh; page 1 is revalidated over the network. */
    public data object Refresh : CharacterListIntent

    /** The user asked for a retry after a failure; a fresh attempt budget is granted. */
    public data object Retry : CharacterListIntent
}
