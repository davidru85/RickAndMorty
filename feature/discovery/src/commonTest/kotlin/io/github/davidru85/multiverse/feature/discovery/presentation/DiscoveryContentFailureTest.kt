package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeCharacterRepository.Call
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `TEST-UNIT-066` — a failure that happens while content is on screen is surfaced and recoverable
 * (`IC-018`, `DEC-124`, `ERROR_FLOW.md` §4 "Keep content, non-blocking error", §9, §10 rule 4,
 * `AC-REQ-FUNC-011-1`, `AC-REQ-FUNC-012-2`, `TASK-111`).
 *
 * A failed append leaves `PagerState.failure` set while the loaded pages stay; the pager then refuses
 * further pages until a retry. Without a field to carry that failure the screen showed plain content
 * and paging stopped with no visible reason. A stale result with no failure is the other recovery
 * case: the banner's Retry must reach the network, not do nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryContentFailureTest {
    /** Five characters, two per page: three pages, so an append can fail between two others. */
    private val catalogue = FakeCatalogue((1..5).map { FakeCatalogue.character("$it") }, pageSize = 2)

    private val FakeCharacterRepository.pageCalls: List<Call.Page>
        get() = calls.filterIsInstance<Call.Page>()

    private fun TestScope.reducer(
        repository: FakeCharacterRepository,
        dispatcher: TestDispatcher,
    ) = DiscoveryReducer(
        pager = FakeCharacterPager(repository, backgroundScope),
        scope = backgroundScope,
        dispatcher = dispatcher,
        formatters = DefaultPresentationFormatters,
    )

    @Test
    fun `TEST-UNIT-066 given_loaded_content_when_an_append_fails_then_the_content_stays_and_the_failure_is_exposed`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            val reducer = reducer(repository, dispatcher)
            reducer.start()
            runCurrent()

            repository.failNext(ApiFailure.Offline)
            reducer.onIntent(CharacterListIntent.LoadNextPage)
            runCurrent()

            val state = reducer.state.value
            assertEquals(LoadState.Content, state.loadState, "TEST-UNIT-066: a failed append never replaces content")
            assertEquals(2, state.items.size, "TEST-UNIT-066: the loaded page stays on screen")
            assertEquals(ApiFailure.Offline, state.contentFailure, "TEST-UNIT-066: the failure is carried beside the content")
        }

    @Test
    fun `TEST-UNIT-066 given_a_failed_append_when_retry_is_dispatched_then_paging_resumes_from_the_failed_page`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            val reducer = reducer(repository, dispatcher)
            reducer.start()
            runCurrent()
            repository.failNext(ApiFailure.Offline)
            reducer.onIntent(CharacterListIntent.LoadNextPage)
            runCurrent()

            reducer.onIntent(CharacterListIntent.Retry)
            runCurrent()

            assertEquals(2, repository.pageCalls.last().page, "TEST-UNIT-066: the retry re-requests the page that failed")
            assertEquals(4, reducer.state.value.items.size, "TEST-UNIT-066: loaded pages are kept, never discarded (§10 rule 4)")
            assertNull(reducer.state.value.contentFailure, "TEST-UNIT-066: a successful retry clears the failure")

            reducer.onIntent(CharacterListIntent.LoadNextPage)
            runCurrent()
            assertEquals(5, reducer.state.value.items.size, "TEST-UNIT-066: and paging continues after it")
        }

    @Test
    fun `TEST-UNIT-066 given_stale_content_and_no_failure_when_retry_is_dispatched_then_page_one_is_revalidated_over_the_network`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue, cached = catalogue, cachedIsStale = true)
            val reducer = reducer(repository, dispatcher)
            reducer.start()
            runCurrent()
            assertTrue(reducer.state.value.isStale, "TEST-UNIT-066: the case starts on stale content")

            reducer.onIntent(CharacterListIntent.Retry)
            runCurrent()

            assertEquals(
                Call.Page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork),
                repository.pageCalls.last(),
                "TEST-UNIT-066: the stale banner's Retry must reach the network (ERROR_FLOW.md §9)",
            )
            assertFalse(reducer.state.value.isStale, "TEST-UNIT-066: a fresh network page clears the stale state")
        }

    @Test
    fun `TEST-UNIT-066 given_a_refresh_when_it_is_in_flight_then_the_state_says_so_until_it_ends`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue, latency = 1.seconds)
            val reducer = reducer(repository, dispatcher)
            reducer.start()
            advanceTimeBy(2.seconds)
            runCurrent()

            reducer.onIntent(CharacterListIntent.Refresh)
            runCurrent()
            assertTrue(reducer.state.value.isRefreshing, "TEST-UNIT-066: a user refresh in flight is visible")

            advanceTimeBy(2.seconds)
            runCurrent()
            assertFalse(reducer.state.value.isRefreshing, "TEST-UNIT-066: and it ends with the refresh")
        }

    @Test
    fun `TEST-UNIT-066 given_a_failure_with_nothing_displayable_when_rendered_then_it_is_the_error_state_and_not_a_content_failure`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Offline)
            val reducer = reducer(repository, dispatcher)
            reducer.start()
            runCurrent()

            assertEquals(LoadState.Error(ApiFailure.Offline), reducer.state.value.loadState)
            assertNull(reducer.state.value.contentFailure, "TEST-UNIT-066: contentFailure is non-null only with Content")
        }
}
