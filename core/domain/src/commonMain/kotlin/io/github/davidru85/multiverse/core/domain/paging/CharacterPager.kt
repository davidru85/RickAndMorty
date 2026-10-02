package io.github.davidru85.multiverse.core.domain.paging

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlinx.coroutines.flow.Flow

/**
 * The shared paging engine (`IC-014`, `DEC-016`): page accumulation, prefetch eligibility,
 * cancellation of a superseded load and the end-of-pagination flag. It is declared here, in the API
 * module, because a feature's state holder consumes it; the implementation lives in `:core:data` and
 * the composition root supplies it (`DEC-091`). The invariants are owned by `CONTRACTS.md` `IC-014`.
 */
public interface CharacterPager {
    /** Hot, replaying the current value; collecting never triggers a load. */
    public val state: Flow<PagerState>

    /** Resets to page 1 of [filter], cancelling any load in flight. */
    public suspend fun setFilter(filter: CharacterFilter)

    /** Loads the next page, unless a load is in flight, the end is reached or a failure is shown. */
    public suspend fun next()

    /** Revalidates page 1 through the network, keeping the items until it succeeds. */
    public suspend fun refresh()

    /** Re-attempts the failed load with a fresh attempt budget; a no-op without a failure (`DEC-092`). */
    public suspend fun retry()
}

/**
 * What the pager has loaded for [filter]. A failure is reported here rather than thrown, so a failed
 * load never destroys displayed content; there is no `LoadState`, which the presentation derives.
 */
public data class PagerState(
    public val filter: CharacterFilter,
    public val items: List<CharacterSummary>,
    public val totalCount: Int?,
    public val isAppending: Boolean,
    public val isEndReached: Boolean,
    public val isStale: Boolean,
    public val failure: ApiFailure?,
)
