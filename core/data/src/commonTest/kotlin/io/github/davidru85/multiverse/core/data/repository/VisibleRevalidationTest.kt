package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `TEST-UNIT-078` — background revalidation reaches the screen, costs no duplicate request and keeps
 * an enriched detail whole (`IC-007`, `IC-014`, `DEC-130`, `ERROR_FLOW.md` §9, `REQ-REL-002`,
 * `TASK-112`).
 *
 * A stale entry is served at once and revalidated behind the caller (`DEC-012`). The revalidation used
 * to write the cache only, so "Showing saved results" stayed up for the whole session; a forced load
 * issued meanwhile sent a second request for the same key; and an enriched detail's revalidation sent
 * a request whose result it then discarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisibleRevalidationTest {
    private val clock = MutableFakeClock()
    private val logger = ValidatingAppLogger.forDebug(RecordingLogSink())
    private val all = CharacterFilter()
    private val catalogue =
        FakeCatalogue(
            characters = (1..30).map { FakeCatalogue.character("$it", episodeIds = listOf("1")) },
            episodes = listOf(EpisodeSummary(EpisodeId("1"), "Pilot", "S01E01", "December 2, 2013")),
        )

    private fun cache() = ResponseCache(FakeCacheStorage(), clock, CachePolicy(), logger)

    /**
     * Runs the background work the case started. `advanceUntilIdle` stops once only `backgroundScope`
     * tasks remain, and a revalidation is exactly such a task, so the case advances virtual time instead.
     */
    private fun TestScope.settle() {
        advanceTimeBy(1.seconds)
        runCurrent()
    }

    /** Past the 24 h fresh window, inside the 7 d stale-while-revalidate one. */
    private fun ageIntoTheStaleWindow() {
        clock.advanceBy(48.hours.inWholeMilliseconds)
    }

    @Test
    fun `TEST-UNIT-078 given_a_revalidation_in_flight_when_a_forced_load_of_the_same_page_runs_then_it_joins_it`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue, latency = 200.milliseconds)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(all, 1)
            ageIntoTheStaleWindow()

            val stale = assertIs<DataResult.Success<*>>(repository.page(all, 1))
            runCurrent()
            val forced = assertIs<DataResult.Success<*>>(repository.page(all, 1, PageLoadPolicy.ForceNetwork))
            settle()

            assertTrue(stale.isStale)
            assertEquals(2, remote.calls.size, "TEST-UNIT-078: the forced load joins the revalidation in flight (REQ-REL-002)")
            assertEquals(DataSource.NETWORK, forced.source, "TEST-UNIT-078: and answers with its network result")
            assertEquals(false, forced.isStale)
        }

    @Test
    fun `TEST-UNIT-078 given_a_stale_first_page_when_the_pager_shows_it_then_a_silent_revalidation_clears_the_stale_state`() =
        TestTime.run {
            // The network takes time, so the repository's revalidation is still in flight when the pager's
            // silent load arrives, and that load joins it.
            val remote = FakeRemoteSource(catalogue, latency = 200.milliseconds)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(all, 1)
            ageIntoTheStaleWindow()
            val pager = RepositoryCharacterPager(repository, backgroundScope, logger)

            pager.setFilter(all)
            val shown = pager.state.value
            settle()

            assertTrue(shown.isStale, "TEST-UNIT-078: the saved page renders at once, marked stale")
            assertEquals(false, pager.state.value.isStale, "TEST-UNIT-078: the revalidated page replaces it, so the banner goes away")
            assertNull(pager.state.value.failure)
            assertEquals(20, pager.state.value.items.size)
            assertEquals(2, remote.calls.size, "TEST-UNIT-078: the first load and one revalidation, nothing more")
        }

    @Test
    fun `TEST-UNIT-078 given_a_stale_first_page_when_its_silent_revalidation_fails_then_nothing_changes`() =
        TestTime.run {
            // The network takes time, so the repository's revalidation is still in flight when the pager's
            // silent load arrives, and that load joins it.
            val remote = FakeRemoteSource(catalogue, latency = 200.milliseconds)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            repository.page(all, 1)
            ageIntoTheStaleWindow()
            remote.failNext(ApiFailure.Unknown(null))
            val pager = RepositoryCharacterPager(repository, backgroundScope, logger)

            pager.setFilter(all)
            val shown = pager.state.value
            settle()

            assertEquals(shown, pager.state.value, "TEST-UNIT-078: a failed silent revalidation shows nothing and changes nothing")
        }

    @Test
    fun `TEST-UNIT-078 given_a_stale_enriched_detail_when_it_is_revalidated_then_the_complete_result_replaces_it`() =
        TestTime.run {
            val remote = FakeRemoteSource(catalogue)
            val repository = RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, cache())
            val id = CharacterId("1")
            repository.details(id, enrich = true)
            ageIntoTheStaleWindow()

            val stale = assertIs<DataResult.Success<*>>(repository.details(id, enrich = true))
            settle()
            val next = assertIs<DataResult.Success<*>>(repository.details(id, enrich = true))

            assertTrue(stale.isStale)
            assertEquals(
                listOf("Details", "Episodes", "Details", "Episodes"),
                remote.calls.map { it::class.simpleName },
                "TEST-UNIT-078: the revalidation fetches the detail and its episodes, as the first load did",
            )
            assertEquals(false, next.isStale, "TEST-UNIT-078: and stores the complete result, so the next read is fresh")
        }
}
