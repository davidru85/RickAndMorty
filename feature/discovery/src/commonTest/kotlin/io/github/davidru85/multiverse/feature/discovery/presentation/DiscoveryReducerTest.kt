package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeCharacterRepository.Call
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Discovery state holder's shared cases — `TEST-UNIT-001`, `003`, `004`, `005` and `016` —
 * driven over the behavioural `IC-007` double, the `IC-014` double and the real 300 ms debounce on
 * virtual time (`TESTING.md` §5). No case sleeps, waits on a wall clock or touches a network.
 *
 * `TEST-UNIT-003` (`TASK-003`, `REQ-FUNC-003`) is the search contract: one request per settled query,
 * a superseded query producing no later state, and a blank query reaching the filter unchanged.
 * `TEST-UNIT-004` (`TASK-004`, `REQ-FUNC-004`) is the status filter: a change resets to page 1 and
 * preserves the active query, and no other filter dimension exists. `TEST-UNIT-005` (`TASK-010`,
 * `REQ-FUNC-010`) separates "nothing matched" from "the load failed". `TEST-UNIT-001` and
 * `TEST-UNIT-016` cover the paging contract the precedence is observed through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryReducerTest {
    private fun catalogue(size: Int) = FakeCatalogue((1..size).map { FakeCatalogue.character("$it") })

    /** Every page request the double received, in order: the `Call.Page` arm of its history. */
    private val FakeCharacterRepository.pageCalls: List<Call.Page>
        get() = calls.filterIsInstance<Call.Page>()

    /** The real debounce window, plus the one virtual millisecond that completes it. */
    private val settled = DiscoveryReducer.DEFAULT_DEBOUNCE + 1.milliseconds

    /**
     * The reducer under test. Its scope is the test's [TestScope.backgroundScope], not the test body's
     * own scope: the reducer holds a never-completing collector (the rendered state), which the test
     * body would otherwise wait for. `backgroundScope` is cancelled when the body ends.
     */
    private fun TestScope.reducer(
        repository: FakeCharacterRepository,
        dispatcher: TestDispatcher,
        debounce: Duration = DiscoveryReducer.DEFAULT_DEBOUNCE,
    ): DiscoveryReducer =
        DiscoveryReducer(
            pager = FakeCharacterPager(repository, backgroundScope),
            scope = backgroundScope,
            dispatcher = dispatcher,
            formatters = DefaultPresentationFormatters,
            debounce = debounce,
        )

    // TEST-UNIT-003 ---------------------------------------------------------------------------------

    @Test
    fun `TEST-UNIT-003 given_a_user_typing_when_the_query_settles_then_the_burst_is_one_request`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(3))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()
            val firstPage = repository.pageCalls.size

            reducer.onIntent(CharacterListIntent.QueryChanged("r"))
            reducer.onIntent(CharacterListIntent.QueryChanged("ri"))
            reducer.onIntent(CharacterListIntent.QueryChanged("ric"))
            runCurrent()
            assertEquals(firstPage, repository.pageCalls.size, "no keystroke is a request of its own")

            advanceTimeBy(settled)
            runCurrent()
            assertEquals(firstPage + 1, repository.pageCalls.size, "the settled query is exactly one request")
            assertEquals(
                "ric",
                repository.pageCalls
                    .last()
                    .filter.query,
            )
            job.cancel()
        }

    @Test
    fun `TEST-UNIT-003 given_a_query_changing_mid_load_when_the_previous_work_is_superseded_then_it_emits_no_later_state`() =
        TestTime.run { dispatcher ->
            // The unfiltered first page is still in flight when the debounce elapses, so the case proves
            // a cancellation rather than merely an ordering.
            val repository = FakeCharacterRepository(catalogue(4), latency = 4.seconds)
            val reducer = reducer(repository, dispatcher)
            val states = mutableListOf<CharacterListUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reducer.state.toList(states) }
            val job = reducer.start()
            // `runCurrent` starts the load but does not let its 100 ms of virtual latency elapse, so the
            // unfiltered request is genuinely in flight when the query changes.
            runCurrent()
            assertTrue(
                repository.pageCalls
                    .single()
                    .filter.query
                    .isEmpty(),
                "the first page of the unfiltered list is in flight when the query changes",
            )

            reducer.onIntent(CharacterListIntent.QueryChanged("zzzznotreal"))
            // The settled query starts its replacement while the first load is still in flight.
            advanceTimeBy(settled)
            runCurrent()
            advanceUntilIdle()

            // The superseded load is cancelled when the settled query replaces the filter, so it never
            // publishes: no state carries the new filter with the previous filter's items, and none
            // arrives after the newest one.
            assertTrue(
                states.none { it.filter.query == "zzzznotreal" && it.items.isNotEmpty() },
                "the superseded unfiltered load never publishes its four items under the newest query",
            )
            val newest = states.indexOfLast { it.filter.query == "zzzznotreal" }
            assertTrue(newest >= 0, "the newest query is the one the state reports")
            assertTrue(
                states.drop(newest).all { it.filter.query == "zzzznotreal" },
                "no state after the newest one belongs to the superseded filter",
            )
            assertTrue(
                states.none { it.filter.query.isEmpty() && it.items.isEmpty() && it.loadState != LoadState.Loading },
                "the superseded unfiltered load reported no completion, because it was cancelled",
            )
            job.cancel()
        }

    @Test
    fun `TEST-UNIT-003 given_a_blank_query_when_the_page_is_requested_then_the_filter_carries_it_unchanged_for_the_adapter_to_trim`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(3))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()
            val blank = "   "

            reducer.onIntent(CharacterListIntent.QueryChanged(blank))
            advanceTimeBy(settled)
            runCurrent()

            assertEquals(
                CharacterFilter(query = blank),
                repository.pageCalls.last().filter,
                "a blank query reaches the seam verbatim; the adapter drops the name parameter (IC-010)",
            )
            assertEquals(
                LoadState.Content,
                reducer.state.value.loadState,
                "a blank query is not a filter, so the unfiltered page still matches",
            )
            job.cancel()
        }

    // TEST-UNIT-004 ---------------------------------------------------------------------------------

    @Test
    fun `TEST-UNIT-004 given_an_active_query_when_a_status_is_selected_then_page_one_reloads_with_both_dimensions`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(45))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()
            // A query every catalogued name contains, so the filtered page is a real, non-empty page.
            reducer.onIntent(CharacterListIntent.QueryChanged("Character"))
            advanceTimeBy(settled)
            runCurrent()
            // Two pages are loaded, so the reset to page 1 is observable rather than incidental.
            reducer.onIntent(CharacterListIntent.LoadNextPage)
            advanceUntilIdle()
            runCurrent()
            assertEquals(2, repository.pageCalls.last().page, "the append is on page 2 before the filter changes")

            reducer.onIntent(CharacterListIntent.StatusSelected(StatusFilter.Alive))
            runCurrent()

            assertEquals(1, repository.pageCalls.last().page, "a status change resets paging to page 1")
            assertEquals(
                CharacterFilter(query = "Character", status = StatusFilter.Alive),
                repository.pageCalls.last().filter,
                "the status change preserves the active query (AC-REQ-FUNC-004-1)",
            )
            job.cancel()
        }

    @Test
    fun `TEST-UNIT-004 given_the_domain_filter_when_its_dimensions_are_listed_then_the_four_status_options_are_the_only_ones`() {
        assertEquals(
            listOf(StatusFilter.All, StatusFilter.Alive, StatusFilter.Dead, StatusFilter.Unknown),
            StatusFilter.entries.toList(),
            "exactly four status options exist, All first (AC-REQ-FUNC-004-2)",
        )
        assertEquals(
            CharacterFilter(),
            CharacterFilter(query = "", status = StatusFilter.All),
            "the filter has the query and the status dimension only; species, type and gender are not offered",
        )
    }

    // TEST-UNIT-005 ---------------------------------------------------------------------------------

    @Test
    fun `TEST-UNIT-005 given_a_filtered_request_matching_nobody_when_the_load_completes_then_it_is_Empty_and_never_Error`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(3))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()

            reducer.onIntent(CharacterListIntent.QueryChanged("zzzznotreal"))
            advanceTimeBy(settled)
            runCurrent()

            assertEquals(LoadState.Empty, reducer.state.value.loadState, "the filtered 404 chain is the empty state")
            assertTrue(
                reducer.state.value.loadState !is LoadState.Error,
                "a filtered 404 is never classified as a failure (AC-REQ-FUNC-010-1)",
            )
            job.cancel()
        }

    @Test
    fun `TEST-UNIT-005 given_a_genuine_failure_when_the_load_completes_then_it_is_Error_and_not_Empty`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(3))
            repository.failNext(ApiFailure.Offline)
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()

            assertEquals(
                LoadState.Error(ApiFailure.Offline),
                reducer.state.value.loadState,
                "a transport failure is the error state; the two are distinguished, not merged",
            )
            job.cancel()
        }

    @Test
    fun `TEST-UNIT-005 given_the_empty_state_when_the_filter_is_cleared_then_the_unfiltered_first_page_is_requested`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(3))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()
            reducer.onIntent(CharacterListIntent.QueryChanged("zzzznotreal"))
            advanceTimeBy(settled)
            runCurrent()
            assertEquals(LoadState.Empty, reducer.state.value.loadState)

            reducer.onIntent(CharacterListIntent.QueryChanged(""))
            advanceTimeBy(settled)
            runCurrent()

            assertEquals(CharacterFilter(), repository.pageCalls.last().filter, "clearing restores the unfiltered filter")
            assertEquals(1, repository.pageCalls.last().page, "clearing resets to the first page (AC-REQ-FUNC-010-2)")
            assertEquals(LoadState.Content, reducer.state.value.loadState)
            assertEquals(3, reducer.state.value.items.size, "the unfiltered catalogue is back on screen")
            job.cancel()
        }

    // TEST-UNIT-001 ---------------------------------------------------------------------------------

    @Test
    fun `TEST-UNIT-001 given_the_first_page_when_it_arrives_then_the_cards_and_the_server_count_render_without_a_later_page`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(45))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()

            assertEquals(1, repository.pageCalls.size, "page 1 alone is fetched; no later page is requested")
            assertEquals(1, repository.pageCalls.single().page)
            val state = reducer.state.value
            assertEquals(LoadState.Content, state.loadState)
            assertEquals(20, state.items.size, "the server's page size, not the catalogue's size")
            assertEquals(45, state.totalCount, "the count is the server's info.count (AC-REQ-FUNC-001-3)")
            assertEquals("Character 1", state.items.first().name, "each card is the one mapping from a summary")
            job.cancel()
        }

    // TEST-UNIT-016 ---------------------------------------------------------------------------------

    @Test
    fun `TEST-UNIT-016 given_the_last_page_when_the_next_page_is_asked_for_then_no_further_request_is_made`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue(25))
            val reducer = reducer(repository, dispatcher)
            val job = reducer.start()
            runCurrent()

            reducer.onIntent(CharacterListIntent.LoadNextPage)
            advanceUntilIdle()
            runCurrent()
            assertEquals(2, repository.pageCalls.size, "the second page is appended while the server states one")

            reducer.onIntent(CharacterListIntent.LoadNextPage)
            advanceUntilIdle()
            reducer.onIntent(CharacterListIntent.LoadNextPage)
            advanceUntilIdle()

            assertEquals(2, repository.pageCalls.size, "loading stops once the server's nextPage is null")
            assertEquals(25, reducer.state.value.items.size, "every loaded page is kept")
            assertEquals(25, reducer.state.value.totalCount, "the server's count survives every later state")
            job.cancel()
        }
}
