package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.data.cache.bypassedCache
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-032` on the real path — the request, retry, coalescing and pager components emit their
 * `OBSERVABILITY.md` §3 events through the one contract (`IC-024`), with the catalogue's fields and a
 * request-scoped correlation id — and the release half of `TEST-UNIT-033` on the same path.
 *
 * The stack is the production one: `MockEngine` answering committed fixtures on the test's
 * dispatcher, the REST adapter, the repository with its retry policy and single flight, the pager and
 * the validating logger over a recording sink. Durations are read from the test scheduler's time
 * source, so a backoff is measured in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestPathLoggingTest {
    private val hex = Regex("^[0-9a-f]{16}$")

    private class Stack(
        val sink: RecordingLogSink,
        val logger: ValidatingAppLogger,
        val remote: RestCharacterRemoteDataSource,
        val repository: RemoteCharacterRepository,
        val served: List<MockHttp.Served>,
    )

    private fun TestScope.stack(
        vararg routes: MockHttp.Route,
        scripted: Boolean = false,
        release: Boolean = false,
        latency: Duration = Duration.ZERO,
        rewriteHost: String? = null,
    ): Stack {
        val sink = RecordingLogSink()
        val logger = if (release) ValidatingAppLogger.forRelease(sink) else ValidatingAppLogger.forDebug(sink)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (raw, served) =
            if (scripted) {
                MockHttp.sequence(*routes, dispatcher = dispatcher, latency = latency)
            } else {
                MockHttp.client(*routes, dispatcher = dispatcher, latency = latency)
            }
        val client =
            raw.config {
                // A request rewritten ahead of the allow-list, as a defect elsewhere in the client would.
                if (rewriteHost !=
                    null
                ) {
                    install(createClientPlugin("Rewrite") { onRequest { request, _ -> request.url.host = rewriteHost } })
                }
                rickAndMortyDefaults()
            }
        val remote = RestCharacterRemoteDataSource(client, dispatcher, MutableFakeClock(), logger, testScheduler.timeSource)
        return Stack(
            sink,
            logger,
            remote,
            RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), logger, bypassedCache(MutableFakeClock(), logger)),
            served,
        )
    }

    private val Stack.ids get() = sink.records.map { it.catalogueId }

    private operator fun LogRecord.get(field: LogField) = fields[field]

    /** Every started request ends with exactly one terminal event of its own correlation id. */
    private fun assertEachRequestEndsOnce(records: List<LogRecord>) {
        val terminal = setOf("LOG-002", "LOG-003", "LOG-004", "LOG-014")
        records.groupBy { it[LogField.CORRELATION_ID] }.forEach { (id, group) ->
            assertEquals(
                group.count { it.catalogueId == "LOG-001" },
                group.count { it.catalogueId in terminal },
                "TEST-UNIT-032: each LOG-001 of $id ends once",
            )
        }
    }

    @Test
    fun `TEST-UNIT-032 given_a_page_loaded_through_the_pager_then_the_request_and_page_events_share_one_correlation_id`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-01.json", urlContains = "page=1"))
            val pager = RepositoryCharacterPager(s.repository, this, s.logger, timeSource = testScheduler.timeSource)

            pager.setFilter(CharacterFilter(query = "Rick"))

            assertEquals(listOf("LOG-001", "LOG-002", "LOG-010"), s.ids)
            val (started, completed, loaded) = s.sink.records
            assertEquals(LogLevel.DEBUG, started.level)
            assertEquals("CHARACTER_LIST", started[LogField.OPERATION])
            assertEquals("/character", started[LogField.PATH_TEMPLATE])
            assertEquals("1", started[LogField.PAGE])
            assertEquals("name", started[LogField.FILTER_NAMES], "TEST-UNIT-032: the filter's name, not its value")
            assertEquals("REST", started[LogField.PROTOCOL])
            assertTrue(hex.matches(started[LogField.CORRELATION_ID].orEmpty()), "TEST-UNIT-032: a client-generated id")
            assertEquals(LogLevel.INFO, completed.level)
            assertEquals("2XX", completed[LogField.STATUS_FAMILY])
            assertEquals("SUCCESS", completed[LogField.OUTCOME])
            assertEquals("NETWORK", loaded[LogField.CACHE_SOURCE])
            assertEquals("1", loaded[LogField.PAGE])
            assertEquals(
                1,
                s.sink.records
                    .map { it[LogField.CORRELATION_ID] }
                    .distinct()
                    .size,
                "TEST-UNIT-032: one request scope",
            )
        }

    @Test
    fun `TEST-UNIT-032 given_transient_failures_then_each_attempt_and_retry_is_logged_and_the_page_duration_is_virtual_time`() =
        TestTime.run {
            val s =
                stack(
                    MockHttp.errorRoute("character-page-01.json", 500),
                    MockHttp.errorRoute("character-page-01.json", 500),
                    MockHttp.route("character-page-01.json"),
                    scripted = true,
                )
            val pager = RepositoryCharacterPager(s.repository, this, s.logger, timeSource = testScheduler.timeSource)

            pager.setFilter(CharacterFilter())

            assertEquals(
                listOf("LOG-001", "LOG-003", "LOG-013", "LOG-001", "LOG-003", "LOG-013", "LOG-001", "LOG-002", "LOG-010"),
                s.ids,
            )
            val failed = s.sink.records[1]
            assertEquals(LogLevel.ERROR, failed.level)
            assertEquals("SERVER", failed[LogField.ERROR_CLASS])
            assertEquals("5XX", failed[LogField.STATUS_FAMILY])
            val retry = s.sink.records[2]
            assertEquals(LogLevel.WARN, retry.level)
            assertEquals("SERVER", retry[LogField.ERROR_CLASS])
            assertEquals(
                "2000",
                s.sink.records.last()[LogField.DURATION_MS],
                "TEST-UNIT-032: 500 ms + 1,500 ms of backoff, measured monotonically",
            )
            assertEquals(
                1,
                s.sink.records
                    .map { it[LogField.CORRELATION_ID] }
                    .distinct()
                    .size,
                "TEST-UNIT-032: retries stay in one scope",
            )
            assertEachRequestEndsOnce(s.sink.records)
        }

    @Test
    fun `TEST-UNIT-032 given_a_rate_limit_with_advice_then_the_retry_carries_the_advised_seconds`() =
        TestTime.run {
            val s =
                stack(
                    MockHttp.errorRoute("character-page-01.json", 429, headers = mapOf("Retry-After" to "3")),
                    MockHttp.route("character-page-01.json"),
                    scripted = true,
                )

            s.repository.page(CharacterFilter(), 1)

            val retry = s.sink.records.single { it.catalogueId == "LOG-013" }
            assertEquals("RATE_LIMITED", retry[LogField.ERROR_CLASS])
            assertEquals("4XX", retry[LogField.STATUS_FAMILY])
            assertEquals("3", retry[LogField.RETRY_AFTER_SECONDS])
        }

    @Test
    fun `TEST-UNIT-032 given_a_filter_that_matches_nothing_then_the_request_completes_empty_rather_than_failing`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-filter-empty-404.json"))

            s.repository.page(CharacterFilter(query = "Nobody"), 1)

            assertEquals(listOf("LOG-001", "LOG-002"), s.ids)
            assertEquals("4XX", s.sink.records[1][LogField.STATUS_FAMILY])
            assertEquals("EMPTY", s.sink.records[1][LogField.OUTCOME], "TEST-UNIT-032: a filtered 404 is EMPTY (OBSERVABILITY.md 6)")
        }

    @Test
    fun `TEST-UNIT-032 given_coalesced_identical_loads_then_the_duplicate_is_logged_with_the_shared_correlation_id`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-01.json"))

            listOf(
                async(start = CoroutineStart.UNDISPATCHED) { s.repository.page(CharacterFilter(), 1) },
                async(start = CoroutineStart.UNDISPATCHED) { s.repository.page(CharacterFilter(), 1) },
            ).forEach { it.await() }

            assertEquals(1, s.ids.count { it == "LOG-001" }, "TEST-UNIT-032: one request")
            val deduplicated = s.sink.records.single { it.catalogueId == "LOG-012" }
            assertEquals("CHARACTER_LIST", deduplicated[LogField.OPERATION])
            assertEquals("1", deduplicated[LogField.PAGE])
            assertEquals(
                s.sink.records.first { it.catalogueId == "LOG-001" }[LogField.CORRELATION_ID],
                deduplicated[LogField.CORRELATION_ID],
                "TEST-UNIT-032: the duplicate names the request it joined",
            )
        }

    @Test
    fun `TEST-UNIT-032 given_a_request_cancelled_in_flight_then_the_cancellation_is_logged_and_no_failure_is`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-01.json"), latency = 300.milliseconds)

            val load = async(start = CoroutineStart.UNDISPATCHED) { s.repository.page(CharacterFilter(), 1) }
            advanceTimeBy(100)
            load.cancel()
            runCurrent()

            assertEquals(listOf("LOG-001", "LOG-014"), s.ids)
            assertEquals("CANCELLED", s.sink.records[1][LogField.OUTCOME])
            assertEachRequestEndsOnce(s.sink.records)
        }

    @Test
    fun `TEST-UNIT-032 given_a_request_rewritten_to_a_foreign_host_then_its_rejection_is_logged_without_the_host`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-01.json"), rewriteHost = "evil.example")

            s.remote.characterPage(CharacterFilter(), 1)

            assertEquals(listOf("LOG-001", "LOG-004"), s.ids)
            val rejected = s.sink.records[1]
            assertEquals(LogLevel.ERROR, rejected.level)
            assertEquals("INVALID_REQUEST", rejected[LogField.ERROR_CLASS])
            assertTrue(s.sink.records.none { "evil" in it.toString() }, "TEST-UNIT-032: a rejected host is never reported")
            assertEquals(emptyList(), s.served, "TEST-UNIT-032: and never reached transport")
            assertEachRequestEndsOnce(s.sink.records)
        }

    @Test
    fun `TEST-UNIT-032 given_an_unknown_remote_value_then_its_preservation_is_logged_without_the_value`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-detail-unknown-enums.json"))

            s.remote.characterDetails(CharacterId("1"))

            assertEquals(listOf("LOG-001", "LOG-022", "LOG-002"), s.ids)
            val preserved = s.sink.records[1]
            assertEquals("CHARACTER_DETAIL", preserved[LogField.OPERATION])
            assertEquals("/character/{id}", preserved[LogField.PATH_TEMPLATE])
            assertEquals("SUCCESS", preserved[LogField.OUTCOME])
            assertTrue(s.sink.records.none { "Time-Traveller" in it.toString() }, "TEST-UNIT-032: a decoded value is never logged")
        }

    @Test
    fun `TEST-UNIT-033 given_the_release_logger_on_the_real_path_then_only_error_records_reach_the_sink`() =
        TestTime.run {
            val s =
                stack(
                    MockHttp.errorRoute("character-page-01.json", 500),
                    MockHttp.errorRoute("character-page-01.json", 500),
                    MockHttp.errorRoute("character-page-01.json", 500),
                    scripted = true,
                    release = true,
                )
            val pager = RepositoryCharacterPager(s.repository, this, s.logger, timeSource = testScheduler.timeSource)

            pager.setFilter(CharacterFilter())

            assertEquals(listOf("LOG-003", "LOG-003", "LOG-003"), s.ids, "TEST-UNIT-033: release builds log errors only (DEC-039)")
        }
}
