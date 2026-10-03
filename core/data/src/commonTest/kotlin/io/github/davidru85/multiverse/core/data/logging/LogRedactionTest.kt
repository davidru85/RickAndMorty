package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.data.cache.bypassedCache
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-029` — a query string never reaches a log sink (`REQ-SEC-005`, `AC-REQ-SEC-005-1`,
 * `OBSERVABILITY.md` §2.3, §4.2).
 *
 * The evidence is the real path at the most permissive level: a search carrying a distinctive query
 * and a status filter runs through the production stack — adapter, retry policy, single flight,
 * pager and the validating logger over a recording sink at `DEBUG` — across every event the build can
 * emit for it: started, completed, empty, failed, retry scheduled with and without advice, page
 * loaded, pagination exhausted, deduplicated and cancelled. The requests are shown to carry the
 * value, and no record carries it, at any level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogRedactionTest {
    private val query = "Zq9Xenomorph"

    @Test
    fun `TEST-UNIT-029 given_a_search_with_a_distinctive_query_when_it_runs_through_every_logged_path_then_no_record_carries_it`() =
        TestTime.run {
            val sink = RecordingLogSink()
            val logger = ValidatingAppLogger.forDebug(sink)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val searched = "name=$query"
            val (raw, served) =
                MockHttp.sequence(
                    MockHttp.errorRoute("character-page-01.json", 500, urlContains = searched),
                    MockHttp.route("character-page-01.json", urlContains = searched),
                    MockHttp.errorRoute("character-page-01.json", 429, urlContains = searched, headers = mapOf("Retry-After" to "1")),
                    MockHttp.route("character-page-42.json", urlContains = searched),
                    MockHttp.route("character-filter-empty-404.json", urlContains = searched),
                    dispatcher = dispatcher,
                    latency = 100.milliseconds,
                )
            val remote =
                RestCharacterRemoteDataSource(
                    raw.config { rickAndMortyDefaults() },
                    dispatcher,
                    MutableFakeClock(),
                    logger,
                    testScheduler.timeSource,
                )
            val repository =
                RemoteCharacterRepository(
                    remote,
                    backgroundScope,
                    FixedRandom(0.5),
                    logger,
                    bypassedCache(MutableFakeClock(), logger),
                )
            val pager = RepositoryCharacterPager(repository, this, logger, timeSource = testScheduler.timeSource)
            val filter = CharacterFilter(query = "  $query ", status = StatusFilter.Alive)

            pager.setFilter(filter)
            pager.next()
            pager.refresh()
            val duplicates =
                List(2) { async(start = CoroutineStart.UNDISPATCHED) { repository.page(filter, 3) } }
            advanceTimeBy(50)
            duplicates.forEach { it.cancel() }
            runCurrent()

            assertTrue(
                served.all { searched in it.url && "status=alive" in it.url },
                "TEST-UNIT-029: the value really travelled the request path",
            )
            val ids = sink.records.map { it.catalogueId }.toSet()
            assertEquals(
                setOf("LOG-001", "LOG-002", "LOG-003", "LOG-010", "LOG-011", "LOG-012", "LOG-013", "LOG-014"),
                ids,
                "TEST-UNIT-029: every event the search path can emit was exercised",
            )
            assertEquals(LogLevel.entries.toSet(), sink.records.map { it.level }.toSet(), "TEST-UNIT-029: at every level, DEBUG included")
            val forbidden = listOf(query, "xenomorph", "alive", "name=", "status=", "?", RickAndMortyApi.HOST)
            sink.records.forEach { record ->
                val text = record.toString()
                forbidden.forEach { value ->
                    assertTrue(!text.contains(value, ignoreCase = true), "TEST-UNIT-029: `$value` reached ${record.catalogueId}: $text")
                }
            }
        }
}
