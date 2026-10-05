package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-077`'s presentation half — while the pager loads the first page of a newly selected
 * protocol, the screen is `Loading`, never `Empty` (`IC-014`, `IC-018`, `DEC-130`, `TASK-112`).
 *
 * A protocol switch happens inside the pager, so the reducer's own per-filter flag never learns about
 * it: only the pager's `isLoading` can tell an empty reset from an empty result.
 */
class DiscoveryProtocolResetTest {
    private val scope = CoroutineScope(Job())

    private val reducer =
        DiscoveryReducer(
            pager = FakeCharacterPager(FakeCharacterRepository(FakeCatalogue(emptyList())), scope),
            scope = scope,
            dispatcher = Dispatchers.Unconfined,
            formatters = DefaultPresentationFormatters,
        )

    @Test
    fun `TEST-UNIT-077 given_a_reset_pager_loading_its_first_page_when_rendered_then_the_screen_is_loading_and_not_empty`() {
        val reset =
            PagerState(
                filter = CharacterFilter(),
                items = emptyList(),
                totalCount = null,
                isAppending = false,
                isEndReached = false,
                isStale = false,
                failure = null,
                isLoading = true,
            )

        assertEquals(LoadState.Loading, reducer.render(reset, isLoading = false).loadState)
    }
}
