package io.github.davidru85.multiverse.core.data.paging

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeCharacterRepository.Call
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-016` and the pager half of `TEST-UNIT-007` — the shared pager (`CONTRACTS.md` `IC-014`,
 * ADR-0009, `DEC-016`, `DEC-092`) through its `:core:data` implementation, over the behavioural
 * double of `IC-007` with latency spent in virtual time. The double is freshness-aware where a case
 * needs a cached entry, so the refresh bypass is proven against an entry the cache could serve
 * (`DEC-086`); the production cache is `TASK-020`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterPagerTest {
    private val all = CharacterFilter()

    private fun catalogue(
        size: Int,
        name: (Int) -> String = { "Rick $it" },
    ) = FakeCatalogue((1..size).map { FakeCatalogue.character("$it", name = name(it)) })

    private fun ids(range: IntRange) = range.map { "$it" }

    /** The production logger over a recording sink, so every pager path also runs its instrumentation. */
    private fun logger() = ValidatingAppLogger.forDebug(RecordingLogSink())

    private val PagerState.ids get() = items.map { it.id.value }

    /** Every state the pager publishes, in order: an unconfined collector sees each value it is set to. */
    private fun TestScope.record(pager: CharacterPager): List<PagerState> {
        val states = mutableListOf<PagerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pager.state.toList(states) }
        return states
    }

    /**
     * An `IC-007` call that ignores cancellation and hands its result back anyway, as a call stuck in a
     * blocking engine would: the result is produced in [worker] and resumes the caller through a
     * non-cancellable continuation, so only the pager's own guard can stop it from publishing.
     */
    private class LateRepository(
        private val delegate: CharacterRepository,
        private val worker: CoroutineScope,
    ) : CharacterRepository by delegate {
        override suspend fun page(
            filter: CharacterFilter,
            page: Int,
            policy: PageLoadPolicy,
        ): DataResult<CharacterPage> =
            suspendCoroutine { caller ->
                worker.launch { caller.resume(delegate.page(filter, page, policy)) }
            }
    }

    @Test
    fun `TEST-UNIT-016 given_a_new_pager_when_its_state_is_collected_then_the_current_state_replays_and_nothing_loads`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())

            val first = pager.state.first()
            advanceUntilIdle()

            assertEquals(
                PagerState(all, emptyList(), totalCount = null, isAppending = false, isEndReached = false, isStale = false, failure = null),
                first,
                "TEST-UNIT-016: the current state replays, with no total before the server states one (AC-REQ-FUNC-001-3)",
            )
            assertEquals(emptyList(), repository.calls, "TEST-UNIT-016: collecting never triggers a load")
        }

    @Test
    fun `TEST-UNIT-016 given_a_filter_when_set_then_page_one_alone_loads_with_the_server_metadata`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            val rick = CharacterFilter(query = "Rick")

            pager.setFilter(rick)

            val state = pager.state.value
            assertEquals(ids(1..20), state.ids, "TEST-UNIT-016: server order")
            assertEquals(45, state.totalCount, "TEST-UNIT-016: the total is read from the response, never a constant")
            assertFalse(state.isEndReached, "TEST-UNIT-016: info.next is non-null")
            assertEquals(
                listOf(Call.Page(rick, 1, PageLoadPolicy.Default)),
                repository.calls,
                "TEST-UNIT-016: the first page alone; the result set is never fetched up front (AC-REQ-FUNC-001-1)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_pages_remaining_when_next_runs_then_each_appends_in_order_until_info_next_is_null`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            val rick = CharacterFilter(query = "Rick")

            pager.setFilter(rick)
            repeat(3) { pager.next() }

            val state = pager.state.value
            assertEquals(ids(1..45), state.ids, "TEST-UNIT-016: page n follows pages 1..n-1, in server order")
            assertTrue(state.isEndReached, "TEST-UNIT-016: info.next == null ends pagination")
            assertEquals(
                (1..3).map { Call.Page(rick, it, PageLoadPolicy.Default) },
                repository.calls,
                "TEST-UNIT-016: the active filter on every page, and no request after the end (AC-REQ-FUNC-001-2)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_a_load_in_flight_when_next_is_called_again_then_no_second_request_starts`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45), latency = 300.milliseconds)
            val pager = RepositoryCharacterPager(repository, this, logger())

            listOf(async { pager.setFilter(all) }, async { pager.next() }).awaitAll()
            listOf(async { pager.next() }, async { pager.next() }).awaitAll()

            assertEquals(
                listOf(Call.Page(all, 1, PageLoadPolicy.Default), Call.Page(all, 2, PageLoadPolicy.Default)),
                repository.calls,
                "TEST-UNIT-016: a next() racing a load joins it; at most the next page is requested (ADR-0009 rules 2-3)",
            )
            assertEquals(ids(1..40), pager.state.value.ids)
        }

    @Test
    fun `TEST-UNIT-016 given_loads_when_observed_then_isAppending_marks_appends_only_and_content_never_turns_empty`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45), latency = 300.milliseconds)
            val pager = RepositoryCharacterPager(repository, this, logger())
            val states = record(pager)

            pager.setFilter(all)
            pager.next()
            pager.refresh()

            // A reset also flips `isLoading` (DEC-130), which this projection does not show; consecutive
            // equal projections are therefore one step of the sequence.
            assertEquals(
                listOf(0 to false, 20 to false, 20 to true, 40 to false, 20 to false),
                states.map { it.items.size to it.isAppending }.fold(emptyList<Pair<Int, Boolean>>()) { steps, step ->
                    if (steps.lastOrNull() == step) steps else steps + step
                },
                "TEST-UNIT-016: only an append sets isAppending, and a refresh keeps the items until it succeeds (ADR-0009 rule 5)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_a_load_in_flight_when_the_filter_changes_then_it_is_cancelled_and_the_reset_is_one_emission`() =
        TestTime.run {
            val repository =
                FakeCharacterRepository(catalogue(45) { if (it % 2 == 0) "Rick $it" else "Morty $it" }, latency = 300.milliseconds)
            val pager = RepositoryCharacterPager(repository, this, logger())
            val morty = CharacterFilter(query = "Morty")
            pager.setFilter(CharacterFilter(query = "Rick"))
            pager.next()
            val states = record(pager)

            val superseded = async { pager.setFilter(CharacterFilter(query = "Rick", status = StatusFilter.Alive)) }
            advanceTimeBy(100)
            pager.setFilter(morty)
            superseded.await()

            assertEquals(1, repository.cancellations, "TEST-UNIT-016: the superseded load is cancelled, not left running")
            val reset = states.indexOfFirst { it.filter == morty }
            assertEquals(
                PagerState(
                    morty,
                    emptyList(),
                    totalCount = null,
                    isAppending = false,
                    isEndReached = false,
                    isStale = false,
                    failure = null,
                    // The reset says its first page is loading, so it is never read as an empty result (DEC-130).
                    isLoading = true,
                ),
                states[reset],
                "TEST-UNIT-016: the new filter and the cleared accumulation arrive in one emission (AC-REQ-FUNC-003-2)",
            )
            assertTrue(
                states.drop(reset).all { state -> state.items.all { it.name.startsWith("Morty") } },
                "TEST-UNIT-016: no item of a previous filter is published once the new filter is active",
            )
            assertTrue(states.none { it.failure != null }, "TEST-UNIT-016: a cancellation is never a failure (API-ERR-017)")
            assertEquals((1..39 step 2).map { "$it" }, states.last().ids)
        }

    @Test
    fun `TEST-UNIT-016 given_a_superseded_load_that_completes_anyway_when_its_result_arrives_then_it_is_not_published`() =
        TestTime.run {
            val repository =
                FakeCharacterRepository(
                    catalogue(45) {
                        if (it % 2 ==
                            0
                        ) {
                            "Rick $it"
                        } else {
                            "Morty $it"
                        }
                    },
                    latency = 300.milliseconds,
                )
            val pager = RepositoryCharacterPager(LateRepository(repository, worker = this), this, logger())
            val morty = CharacterFilter(query = "Morty")
            val states = record(pager)

            val superseded = async { pager.setFilter(CharacterFilter(query = "Rick")) }
            advanceTimeBy(100)
            val current = async { pager.setFilter(morty) }
            listOf(superseded, current).awaitAll()

            assertTrue(
                states.none { state -> state.items.any { it.name.startsWith("Rick") } },
                "TEST-UNIT-016: a late result of an older generation never publishes",
            )
            assertEquals(morty, pager.state.value.filter)
            assertEquals((1..39 step 2).map { "$it" }, pager.state.value.ids)
        }

    @Test
    fun `TEST-UNIT-016 given_a_page_after_the_first_not_found_when_reached_by_next_then_pagination_ends_without_failure`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            repository.serve(catalogue(15))

            pager.next()
            pager.next()

            val state = pager.state.value
            assertEquals(ids(1..20), state.ids, "TEST-UNIT-016: the loaded items stay")
            assertTrue(state.isEndReached, "TEST-UNIT-016: a paging 404 is the end (ERROR_FLOW.md §5.2)")
            assertNull(state.failure, "TEST-UNIT-016: and no error")
            assertFalse(state.isAppending)
            assertEquals(2, repository.calls.size, "TEST-UNIT-016: nothing is requested after the end")
        }

    @Test
    fun `TEST-UNIT-016 given_page_one_not_found_when_loaded_then_it_is_a_failure_not_the_end`() =
        TestTime.run {
            val pager = RepositoryCharacterPager(FakeCharacterRepository(FakeCatalogue(emptyList())), this, logger())

            pager.setFilter(all)

            val state = pager.state.value
            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, "1"), state.failure, "TEST-UNIT-016: an unfiltered page-1 404")
            assertFalse(state.isEndReached)
        }

    @Test
    fun `TEST-UNIT-016 given_ids_repeated_across_pages_when_appended_then_the_first_occurrence_is_kept`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            // A character inserted ahead of the loaded page shifts page 2 back by one: it now starts at 20.
            repository.serve(FakeCatalogue((0..45).map { FakeCatalogue.character("$it", name = "Rick $it") }))

            pager.next()

            assertEquals(
                ids(1..39),
                pager.state.value.ids,
                "TEST-UNIT-016: the repeated 20 is dropped before publication (ADR-0009 rule 9)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_an_established_total_when_a_later_result_states_none_then_the_known_total_is_kept`() =
        TestTime.run {
            val rick = CharacterFilter(query = "Rick")
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(rick)
            repository.serve(catalogue(45) { "Morty $it" })

            pager.refresh()

            assertEquals(emptyList(), pager.state.value.items)
            assertEquals(
                45,
                pager.state.value.totalCount,
                "TEST-UNIT-016: a known total is never overwritten with null or 0 (ERROR_FLOW.md §5.3)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_an_append_failure_then_items_stay_next_is_suppressed_and_retry_loads_the_failed_page`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            repository.failNext(ApiFailure.Offline)

            pager.next()
            val failed = pager.state.value
            pager.next()
            pager.next()
            val callsWhileFailed = repository.calls.size
            pager.retry()

            assertEquals(ids(1..20), failed.ids, "TEST-UNIT-016: a failed append keeps the displayed items (AC-REQ-FUNC-012-2)")
            assertEquals(ApiFailure.Offline, failed.failure)
            assertFalse(failed.isAppending)
            assertEquals(2, callsWhileFailed, "TEST-UNIT-016: a failure suppresses further speculative loads (DEC-092)")
            assertEquals(
                Call.Page(all, 2, PageLoadPolicy.Default),
                repository.calls.last(),
                "TEST-UNIT-016: retry re-attempts the failed append",
            )
            assertEquals(ids(1..40), pager.state.value.ids)
            assertNull(pager.state.value.failure, "TEST-UNIT-016: a successful load clears the failure")
        }

    @Test
    fun `TEST-UNIT-016 given_a_failed_first_page_when_retried_then_page_one_loads_and_without_a_failure_retry_does_nothing`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            repository.failNext(ApiFailure.Offline)
            pager.setFilter(all)
            val failed = pager.state.value

            pager.retry()
            pager.retry()

            assertEquals(ApiFailure.Offline, failed.failure)
            assertEquals(emptyList(), failed.items)
            assertEquals(ids(1..20), pager.state.value.ids)
            assertNull(pager.state.value.failure)
            assertEquals(
                listOf(Call.Page(all, 1, PageLoadPolicy.Default), Call.Page(all, 1, PageLoadPolicy.Default)),
                repository.calls,
                "TEST-UNIT-016: retry re-attempts page 1 once, and is a no-op without a failure (DEC-092)",
            )
        }

    @Test
    fun `TEST-UNIT-016 given_the_owner_scope_closed_when_an_append_is_in_flight_then_it_is_cancelled_without_a_failure`() =
        TestTime.run {
            val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val repository = FakeCharacterRepository(catalogue(45), latency = 300.milliseconds)
            val pager = RepositoryCharacterPager(repository, owner, logger())
            pager.setFilter(all)

            val append = async { pager.next() }
            advanceTimeBy(100)
            owner.cancel()
            append.await()

            val state = pager.state.value
            assertEquals(1, repository.cancellations, "TEST-UNIT-016: the owner's end cancels the load it owns")
            assertFalse(state.isAppending, "TEST-UNIT-016: the append indicator ends with the cancelled append")
            assertNull(state.failure, "TEST-UNIT-016: a cancellation is never a failure")
            assertEquals(ids(1..20), state.ids)
        }

    @Test
    fun `TEST-UNIT-007 given_a_fresh_cached_first_page_when_refreshed_then_page_one_is_requested_with_force_network`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45), cached = catalogue(45) { "Cached $it" })
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            val cached = pager.state.value

            pager.refresh()

            assertEquals("Cached 1", cached.items.first().name, "TEST-UNIT-007: a fresh cached entry is available")
            assertFalse(cached.isStale)
            assertEquals(
                Call.Page(all, 1, PageLoadPolicy.ForceNetwork),
                repository.calls.last(),
                "TEST-UNIT-007: a manual refresh selects the bypass over the fresh entry (AC-REQ-FUNC-012-1, DEC-086)",
            )
            assertEquals(
                (1..20).map { "Rick $it" },
                pager.state.value.items
                    .map { it.name },
            )
        }

    @Test
    fun `TEST-UNIT-007 given_a_failed_refresh_then_items_and_the_established_pagination_state_are_kept`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            repeat(2) { pager.next() }
            val before = pager.state.value
            repository.failNext(ApiFailure.Offline)

            pager.refresh()

            assertEquals(
                before.copy(failure = ApiFailure.Offline),
                pager.state.value,
                "TEST-UNIT-007: items, end of pagination and network provenance stay; only the failure is reported " +
                    "(AC-REQ-FUNC-012-2, CONF-71)",
            )
            assertTrue(before.isEndReached)
            assertEquals(Call.Page(all, 1, PageLoadPolicy.ForceNetwork), repository.calls.last())
        }

    @Test
    fun `TEST-UNIT-007 given_appended_pages_when_a_refresh_succeeds_then_page_one_replaces_them_and_paging_restarts`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            pager.next()
            repository.failNext(ApiFailure.Offline)
            pager.next()

            pager.refresh()
            val refreshed = pager.state.value
            pager.next()

            assertEquals(ids(1..20), refreshed.ids, "TEST-UNIT-007: a refresh replaces the collection; it is not a page-1 append")
            assertNull(refreshed.failure, "TEST-UNIT-007: success clears the transient failure")
            assertFalse(refreshed.isEndReached)
            assertEquals(
                Call.Page(all, 2, PageLoadPolicy.Default),
                repository.calls.last(),
                "TEST-UNIT-007: paging restarts from the new metadata",
            )
            assertEquals(ids(1..40), pager.state.value.ids)
        }

    @Test
    fun `TEST-UNIT-007 given_a_failed_refresh_when_retried_then_it_is_retried_as_a_refresh`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45))
            val pager = RepositoryCharacterPager(repository, this, logger())
            pager.setFilter(all)
            repository.failNext(ApiFailure.Offline)
            pager.refresh()

            pager.retry()

            assertEquals(
                listOf(PageLoadPolicy.Default, PageLoadPolicy.ForceNetwork, PageLoadPolicy.ForceNetwork),
                repository.calls.map { (it as Call.Page).policy },
                "TEST-UNIT-007: the failed load was a refresh, so its retry bypasses the cache too (DEC-092)",
            )
            assertNull(pager.state.value.failure)
        }

    @Test
    fun `TEST-UNIT-007 given_stale_cached_items_then_isStale_follows_result_provenance_and_a_failed_refresh_keeps_it`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(45), cached = catalogue(45), cachedIsStale = true)
            val pager = RepositoryCharacterPager(repository, this, logger())

            pager.setFilter(all)
            val fromStaleCache = pager.state.value.isStale
            repository.failNext(ApiFailure.Offline)
            pager.refresh()
            val afterFailedRefresh = pager.state.value.isStale
            pager.refresh()
            val afterRefresh = pager.state.value.isStale
            pager.next()

            assertTrue(fromStaleCache, "TEST-UNIT-007: items served past freshness are stale")
            assertTrue(afterFailedRefresh, "TEST-UNIT-007: a failed refresh neither sets nor clears it (CONF-71)")
            assertFalse(afterRefresh, "TEST-UNIT-007: network items are never stale")
            assertTrue(pager.state.value.isStale, "TEST-UNIT-007: a stale page among the items makes the collection stale")
        }
}
