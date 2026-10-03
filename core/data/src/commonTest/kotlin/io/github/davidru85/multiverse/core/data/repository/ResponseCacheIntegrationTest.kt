package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeRemoteSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * `TEST-UNIT-009`, `TEST-UNIT-020` and `TEST-UNIT-023` — the cache as the repository uses it.
 *
 * The subject is the real [RemoteCharacterRepository] over the real [ResponseCache], the behavioural
 * `IC-011` double and a store a test can inspect, so the read policy, the write guard, the identity and
 * the clock are asserted end to end rather than on the policy class alone (`REQ-FUNC-020`,
 * `AC-REQ-FUNC-020-1`…`3`, `REQ-REL-001`, `REQ-REL-004`).
 */
class ResponseCacheIntegrationTest {
    private val clock = MutableFakeClock()
    private val storage = FakeCacheStorage()
    private val sink = RecordingLogSink()
    private val logger = ValidatingAppLogger.forDebug(sink)
    private val catalogue = FakeCatalogue(characters = (1..30).map { FakeCatalogue.character("$it", name = "Rick $it") })

    private fun cache() = ResponseCache(storage, clock, CachePolicy(), logger)

    @Test
    fun `TEST-UNIT-009 given_a_repeat_visit_inside_the_fresh_window_when_it_loads_then_no_network_request_is_made`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())

            val first = repository.page(CharacterFilter(), 1)
            clock.advanceBy(1.hours.inWholeMilliseconds)
            val second = repository.page(CharacterFilter(), 1)

            assertIs<DataResult.Success<*>>(first)
            assertEquals(1, remote.calls.size, "TEST-UNIT-009: the second visit inside the window is served from the cache")
            assertEquals(DataSource.NETWORK, first.source)
            assertEquals(DataSource.DISK_CACHE, second.source, "TEST-UNIT-009: and it says where it came from")
            assertEquals(false, (second as DataResult.Success).isStale, "TEST-UNIT-009: a fresh entry is not stale")
        }

    @Test
    fun `TEST-UNIT-009 given_an_offline_repository_with_a_cached_entry_when_it_loads_then_content_is_served_stale`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(CharacterFilter(), 1)
            // Past the revalidate window: the entry may answer only when the network fails.
            clock.advanceBy(20.days.inWholeMilliseconds)
            repeat(3) { remote.failNext(ApiFailure.Offline) }

            val outcome = repository.page(CharacterFilter(), 1)

            val success = assertIs<DataResult.Success<*>>(outcome, "TEST-UNIT-009: offline with a cached entry shows content")
            assertEquals(DataSource.DISK_CACHE, success.source)
            assertEquals(true, success.isStale, "TEST-UNIT-009: and marks it stale")
            assertTrue(sink.records.any { it.catalogueId == "LOG-007" }, "TEST-UNIT-009: the stale fallback is logged")
        }

    @Test
    fun `TEST-UNIT-009 given_an_offline_repository_with_no_entry_when_it_loads_then_the_offline_error_is_returned`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            remote.failNext(ApiFailure.Offline)
            remote.failNext(ApiFailure.Offline)
            remote.failNext(ApiFailure.Offline)

            val outcome = repository.page(CharacterFilter(), 1)

            val failure = assertIs<DataResult.Failure>(outcome, "TEST-UNIT-009: no entry is the offline error, not an empty list")
            assertEquals(ApiFailure.Offline, failure.failure)
        }

    @Test
    fun `TEST-UNIT-009 given_a_failure_when_a_page_load_settles_then_nothing_is_stored`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            remote.failNext(ApiFailure.Server(500))
            remote.failNext(ApiFailure.Server(500))
            remote.failNext(ApiFailure.Server(500))

            repository.page(CharacterFilter(), 1)

            assertEquals(0, storage.writes, "TEST-UNIT-009: an error is never admitted (AC-REQ-FUNC-020-3)")
            assertEquals(emptyList(), storage.keys, "TEST-UNIT-009: and nothing is retained under any key")
        }

    @Test
    fun `TEST-UNIT-009 given_a_partial_enriched_detail_when_it_settles_then_it_is_never_stored`() =
        TestTime.run {
            val withEpisodes =
                FakeCatalogue(
                    characters = listOf(FakeCatalogue.character("1", episodeIds = listOf("1"))),
                    episodes = listOf(FakeCatalogue.episode("1")),
                )
            val remote = FakeRemoteSource(withEpisodes)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())

            // The detail succeeds first, then the episode batch fails three times, so the outcome is
            // partial (ERROR_FLOW.md §7): it renders and must never be stored.
            repeat(3) { remote.failNext(ApiFailure.Server(500), FakeRemoteSource.Kind.Episodes) }
            val outcome = repository.details(SAMPLE_ID, enrich = true)

            val success = assertIs<DataResult.Success<*>>(outcome, "TEST-UNIT-009: the detail still renders")
            assertTrue(success.warnings.isNotEmpty(), "TEST-UNIT-009: with the enrichment warning")
            assertTrue(storage.keys.none { it.value.contains("details") }, "TEST-UNIT-009: and no partial detail is stored")
        }

    @Test
    fun `TEST-UNIT-020 given_two_filter_combinations_when_both_are_cached_then_their_entries_do_not_collide`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())

            repository.page(CharacterFilter(query = "Rick", status = StatusFilter.Alive), 1)
            repository.page(CharacterFilter(query = "Rick", status = StatusFilter.Dead), 1)
            repository.page(CharacterFilter(query = "Rick", status = StatusFilter.Alive), 2)

            assertEquals(3, storage.keys.size, "TEST-UNIT-020: status and page are part of the identity")
            assertEquals(3, storage.keys.distinct().size, "TEST-UNIT-020: two filters never share an entry")
        }

    @Test
    fun `TEST-UNIT-020 given_the_two_protocols_when_the_same_page_is_cached_then_the_keys_differ`() =
        TestTime.run {
            val rest = pageCacheKey(RemoteProtocol.Rest, CharacterFilter(), 1)
            val graphQl = pageCacheKey(RemoteProtocol.GraphQl, CharacterFilter(), 1)

            assertTrue(rest != graphQl, "TEST-UNIT-020: the protocol is part of the identity (AC-REQ-FUNC-034-4)")
        }

    @Test
    fun `TEST-UNIT-020 given_a_blank_query_when_the_key_is_built_then_no_name_parameter_is_sent`() =
        TestTime.run {
            val blank = pageCacheKey(RemoteProtocol.Rest, CharacterFilter(query = "   "), 1)
            val absent = pageCacheKey(RemoteProtocol.Rest, CharacterFilter(), 1)

            assertEquals(absent, blank, "TEST-UNIT-020: a blank query sends nothing, so the identity is the same")
            assertTrue(!blank.value.contains("name="), "TEST-UNIT-020: and the key carries no name parameter")
        }

    @Test
    fun `TEST-UNIT-008_related given_a_stale_entry_when_a_page_loads_then_it_is_served_and_revalidated_behind_the_caller`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(CharacterFilter(), 1)
            clock.advanceBy(48.hours.inWholeMilliseconds)

            val served = repository.page(CharacterFilter(), 1)
            val success = assertIs<DataResult.Success<*>>(served, "the first render never blocks")

            assertEquals(true, success.isStale, "a revalidated entry is marked stale while it is shown")
            assertEquals(DataSource.DISK_CACHE, success.source)
            advanceUntilIdle()
            assertEquals(2, remote.calls.size, "and the background revalidation did reach the network")

            val refreshed = assertIs<DataResult.Success<*>>(repository.page(CharacterFilter(), 1))
            assertEquals(false, refreshed.isStale, "so the next read is fresh")
        }

    @Test
    fun `TEST-UNIT-023 given_a_fresh_entry_when_a_manual_refresh_runs_then_the_network_is_still_reached`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(CharacterFilter(), 1)

            val refreshed = repository.page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork)
            advanceUntilIdle()

            assertEquals(2, remote.calls.size, "TEST-UNIT-023: ForceNetwork bypasses even a fresh entry (DEC-086)")
            assertEquals(2, remote.calls.size, "and the manual refresh reached the network despite the fresh entry")
            assertEquals(DataSource.NETWORK, (refreshed as DataResult.Success).source)
        }

    @Test
    fun `TEST-UNIT-007 given_a_refreshed_pager_over_the_production_cache_then_the_live_repository_bypasses_the_fresh_entry`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val pager =
                io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager(
                    repository,
                    this,
                    logger,
                    timeSource = testScheduler.timeSource,
                )

            // Page 1 first, so the production cache holds a fresh entry for the identity a refresh uses.
            pager.setFilter(CharacterFilter())
            assertEquals(1, remote.calls.size)
            pager.refresh()

            assertEquals(
                2,
                remote.calls.size,
                "TEST-UNIT-007: the refresh bypassed the fresh production entry (AC-REQ-FUNC-012-1, DEC-086)",
            )
        }

    @Test
    fun `TEST-UNIT-007 given_a_failed_refresh_over_the_production_cache_then_the_entries_remain`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val pager =
                io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager(
                    repository,
                    this,
                    logger,
                    timeSource = testScheduler.timeSource,
                )
            pager.setFilter(CharacterFilter())

            repeat(3) { remote.failNext(ApiFailure.Offline) }
            pager.refresh()

            val state = pager.state.value
            assertEquals(20, state.items.size, "TEST-UNIT-007: a failed refresh keeps the items (AC-REQ-FUNC-012-2)")
            assertEquals(ApiFailure.Offline, state.failure, "and surfaces the failure alongside them")
        }

    @Test
    fun `TEST-UNIT-006 given_a_failed_load_over_the_production_cache_when_retry_succeeds_then_the_error_clears`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val pager =
                io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager(
                    repository,
                    this,
                    logger,
                    timeSource = testScheduler.timeSource,
                )

            // The first load fails outright, so the pager holds a failure and no content.
            repeat(3) { remote.failNext(ApiFailure.Offline) }
            pager.setFilter(CharacterFilter())
            val failed = pager.state.value
            assertEquals(ApiFailure.Offline, failed.failure, "the failure is reported rather than thrown")

            // The retry is a genuinely fresh attempt: its own three-attempt budget against the network.
            pager.retry()

            val retried = pager.state.value
            assertNull(retried.failure, "TEST-UNIT-006: retry clears the error on success (AC-REQ-FUNC-011-1)")
            assertEquals(20, retried.items.size, "TEST-UNIT-006: and the content arrives")
            assertEquals(4, remote.calls.size, "TEST-UNIT-006: one failed sequence plus one fresh attempt")
        }

    @Test
    fun `TEST-UNIT-006 given_a_retry_over_the_production_cache_when_it_runs_then_the_second_attempt_is_not_served_from_the_cache`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val pager =
                io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager(
                    repository,
                    this,
                    logger,
                    timeSource = testScheduler.timeSource,
                )

            pager.setFilter(CharacterFilter())
            // A later append fails; the retry must re-attempt that append, not answer it from the
            // entry the first page's success stored under its own key.
            repeat(3) { remote.failNext(ApiFailure.Server(500), FakeRemoteSource.Kind.Page) }
            pager.next()
            assertEquals(ApiFailure.Server(500), pager.state.value.failure)

            pager.retry()

            assertNull(pager.state.value.failure, "TEST-UNIT-006: the failed append succeeded on its own retry")
            assertEquals(
                30,
                pager.state.value.items.size,
                "TEST-UNIT-006: the retry re-attempted the append, so the 30 items of pages 1 and 2 are on screen (20 + 10)",
            )
        }

    @Test
    fun `TEST-UNIT-006 given_a_cancelled_load_when_the_caller_is_cancelled_then_no_failure_reaches_the_state`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue, latency = kotlin.time.Duration.parse("5m"))
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val pager =
                io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager(
                    repository,
                    this,
                    logger,
                    timeSource = testScheduler.timeSource,
                )

            val load = launch { pager.setFilter(CharacterFilter()) }
            runCurrent()
            load.cancel()
            advanceUntilIdle()

            assertNull(
                pager.state.value.failure,
                "TEST-UNIT-006: a cancellation is control flow, so no failure reaches the surface (AC-REQ-FUNC-022-2)",
            )
        }

    private companion object {
        val SAMPLE_ID = CharacterId("1")
    }
}
