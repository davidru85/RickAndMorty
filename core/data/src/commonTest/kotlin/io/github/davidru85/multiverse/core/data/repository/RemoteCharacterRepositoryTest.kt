package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.data.cache.bypassedCache
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.FixtureLoader
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The REST repository composition of `IC-007` (`TASK-038`), on the real policy path: the real
 * adapter behind it, Ktor `MockEngine` with committed fixtures underneath, virtual time for every
 * backoff and deterministic randomness for the jitter.
 *
 * - `TEST-UNIT-022` — the attempt budget of `DEC-084`: at most three attempts for an eligible
 *   transient sequence, one for every non-retryable outcome, at most one `429` retry and only after
 *   readable advice, a fresh budget per call, and cancellation that stops the sequence.
 * - `TEST-UNIT-055` — the composition: an empty filtered page is a success, enrichment is one bounded
 *   episode call, a failed enrichment keeps the detail, and an unknown id is `NotFound`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCharacterRepositoryTest {
    /** One scripted engine answer: a status served with a committed fixture body, or a thrown failure. */
    private sealed interface Step {
        data class Status(
            val code: Int,
            val headers: Map<String, String> = emptyMap(),
        ) : Step

        data class Throw(
            val failure: Throwable,
        ) : Step
    }

    /**
     * A scripted engine on the test's dispatcher, so every attempt runs in virtual time: by default
     * `MockEngine` answers on a dispatcher of its own, and whether an attempt has happened by a given
     * virtual instant would then depend on thread timing.
     */
    private class Engine(
        steps: List<Step>,
        dispatcher: CoroutineDispatcher,
    ) {
        private val script = ArrayDeque(steps)
        var calls = 0
            private set

        private val config =
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler {
                    calls++
                    when (val step = script.removeFirstOrNull() ?: error("the scripted engine is exhausted")) {
                        is Step.Throw -> throw step.failure
                        is Step.Status ->
                            respond(
                                content = FixtureLoader.text("character-page-01.json"),
                                status = HttpStatusCode.fromValue(step.code),
                                headers =
                                    headersOf(
                                        *(step.headers + ("content-type" to "application/json"))
                                            .map {
                                                it.key to
                                                    listOf(it.value)
                                            }.toTypedArray(),
                                    ),
                            )
                    }
                }
            }

        val client: HttpClient = HttpClient(MockEngine(config)) { rickAndMortyDefaults() }
    }

    private fun TestScope.engine(vararg steps: Step) = Engine(steps.toList(), StandardTestDispatcher(testScheduler))

    private fun TestScope.repository(
        client: HttpClient,
        random: Random = FixedRandom(0.5),
    ): RemoteCharacterRepository {
        val logger = ValidatingAppLogger.forDebug(RecordingLogSink())
        return RemoteCharacterRepository(
            remote = RestCharacterRemoteDataSource(client, StandardTestDispatcher(testScheduler), MutableFakeClock(), logger),
            scope = backgroundScope,
            random = random,
            logger = logger,
            cache = bypassedCache(MutableFakeClock()),
        )
    }

    // ------------------------------------------------------------------ TEST-UNIT-022

    @Test
    fun `TEST-UNIT-022 given_a_failing_server_when_a_page_is_loaded_then_three_attempts_are_made_with_the_documented_backoff`() =
        TestTime.run {
            val engine = engine(*Array(3) { Step.Status(503) })

            val started = testScheduler.currentTime
            val result = repository(engine.client).page(CharacterFilter(), 1)

            assertEquals(ApiFailure.Server(503), (result as DataResult.Failure).failure)
            assertEquals(3, engine.calls, "TEST-UNIT-022: the original plus two retries (DEC-084)")
            assertEquals(2_000, testScheduler.currentTime - started, "TEST-UNIT-022: 500 ms then 1,500 ms at the midpoint factor")
        }

    @Test
    fun `TEST-UNIT-022 given_the_jitter_bounds_when_backing_off_then_the_delays_stay_within_the_documented_window`() =
        TestTime.run {
            val low = engine(*Array(3) { Step.Throw(IOException("reset")) })
            val high = engine(*Array(3) { Step.Throw(IOException("reset")) })

            var started = testScheduler.currentTime
            repository(low.client, FixedRandom(0.0)).page(CharacterFilter(), 1)
            assertEquals(1_600, testScheduler.currentTime - started, "TEST-UNIT-022: 400 + 1,200 ms at the lowest factor 0.8")
            started = testScheduler.currentTime
            repository(high.client, FixedRandom(0.999_999)).page(CharacterFilter(), 1)
            assertEquals(2_400, testScheduler.currentTime - started, "TEST-UNIT-022: 600 + 1,800 ms at the highest factor 1.2")
        }

    @Test
    fun `TEST-UNIT-022 given_an_earlier_success_when_retrying_then_the_sequence_stops`() =
        TestTime.run {
            val engine = engine(Step.Throw(HttpRequestTimeoutException("request", 20_000)), Step.Status(200))

            val result = repository(engine.client).page(CharacterFilter(), 1)

            assertIs<DataResult.Success<*>>(result, "TEST-UNIT-022: a timeout is retried and the success ends the sequence")
            assertEquals(2, engine.calls)
        }

    @Test
    fun `TEST-UNIT-022 given_a_non_retryable_outcome_when_loaded_then_exactly_one_attempt_is_made`() =
        TestTime.run {
            listOf(
                "400" to Step.Status(400),
                "redirect" to Step.Status(302),
                "engine defect" to Step.Throw(IllegalStateException("defect")),
            ).forEach { (name, step) ->
                val engine = engine(step)
                assertIs<DataResult.Failure>(repository(engine.client).page(CharacterFilter(), 1))
                assertEquals(1, engine.calls, "TEST-UNIT-022: `$name` gets one attempt (AC-REQ-REL-003-1)")
            }
            val (malformed, served) = MockHttp.client(MockHttp.route("malformed-body.txt"))
            val result = repository(malformed.config { rickAndMortyDefaults() }).page(CharacterFilter(), 1)
            assertEquals(ApiFailure.MalformedResponse, (result as DataResult.Failure).failure)
            assertEquals(1, served.size, "TEST-UNIT-022: a decode failure gets one attempt (AC-REQ-REL-003-1)")
        }

    @Test
    fun `TEST-UNIT-022 given_rate_limiting_when_loaded_then_one_retry_follows_readable_advice_only`() =
        TestTime.run {
            val advised = engine(Step.Status(429, mapOf("Retry-After" to "2")), Step.Status(200))
            val started = testScheduler.currentTime
            assertIs<DataResult.Success<*>>(repository(advised.client).page(CharacterFilter(), 1))
            assertEquals(2, advised.calls)
            assertEquals(2_000, testScheduler.currentTime - started, "TEST-UNIT-022: the advised wait, with no jitter")

            val twice = engine(*Array(2) { Step.Status(429, mapOf("Retry-After" to "1")) })
            assertIs<ApiFailure.RateLimited>((repository(twice.client).page(CharacterFilter(), 1) as DataResult.Failure).failure)
            assertEquals(2, twice.calls, "TEST-UNIT-022: at most one 429 retry")

            listOf(emptyMap(), mapOf("Retry-After" to "soon"), mapOf("Retry-After" to "120")).forEach { headers ->
                val engine = engine(Step.Status(429, headers))
                repository(engine.client).page(CharacterFilter(), 1)
                assertEquals(1, engine.calls, "TEST-UNIT-022: no automatic retry for $headers (60-second ceiling)")
            }
        }

    @Test
    fun `TEST-UNIT-022 given_an_exhausted_budget_when_the_user_retries_then_a_fresh_budget_starts`() =
        TestTime.run {
            val engine = engine(*Array(6) { Step.Throw(IOException("offline")) })
            val subject = repository(engine.client)

            assertEquals(ApiFailure.Offline, (subject.page(CharacterFilter(), 1) as DataResult.Failure).failure)
            assertEquals(ApiFailure.Offline, (subject.page(CharacterFilter(), 1) as DataResult.Failure).failure)
            assertEquals(6, engine.calls, "TEST-UNIT-022: each call is a fresh three-attempt budget; nothing polls")
        }

    @Test
    fun `TEST-UNIT-022 given_a_cancelled_caller_when_backing_off_then_no_further_attempt_is_made`() =
        TestTime.run {
            val engine = engine(*Array(3) { Step.Status(500) })
            val load = async(start = CoroutineStart.UNDISPATCHED) { repository(engine.client).page(CharacterFilter(), 1) }

            advanceTimeBy(200)
            load.cancel()
            advanceTimeBy(5_000)

            assertFailsWith<CancellationException>("TEST-UNIT-022: cancellation is never a failure") { load.await() }
            assertEquals(1, engine.calls, "TEST-UNIT-022: the wait is cancellable and the sequence stops")
        }

    // ------------------------------------------------------------------ TEST-UNIT-055

    @Test
    fun `TEST-UNIT-055 given_a_filter_with_no_match_when_a_page_is_loaded_then_it_is_an_empty_success`() =
        TestTime.run {
            val (raw, served) = MockHttp.client(MockHttp.route("character-filter-empty-404.json"))

            val result = repository(raw.config { rickAndMortyDefaults() }).page(CharacterFilter(query = "zzzznotreal"), 1)

            assertTrue(
                (result as DataResult.Success<CharacterPage>).value.characters.isEmpty(),
                "TEST-UNIT-055: IC-007 empty filtered result",
            )
            assertEquals(1, served.size)
        }

    @Test
    fun `TEST-UNIT-055 given_enrichment_when_details_are_loaded_then_one_bounded_episode_call_is_made`() =
        TestTime.run {
            val (raw, served) =
                MockHttp.client(
                    MockHttp.route("character-detail.json", urlContains = "/character/1"),
                    MockHttp.route("episode-batch.json", urlContains = "/episode/"),
                )
            val subject = repository(raw.config { rickAndMortyDefaults() })

            val plain = (subject.details(CharacterId("1"), enrich = false) as DataResult.Success<CharacterDetails>).value
            assertNull(plain.episodeSummaries, "TEST-UNIT-055: no enrichment unless requested")
            assertEquals(1, served.size, "TEST-UNIT-055: enrich = false issues no episode request")

            val enriched = subject.details(CharacterId("1"), enrich = true) as DataResult.Success<CharacterDetails>
            val episodeRequests = served.drop(2).map { Url(it.url).encodedPath }
            assertEquals(3, episodeRequests.size, "TEST-UNIT-055: 51 episodes in chunks of 20, never one request each")
            assertEquals(listOf(EpisodeId("1"), EpisodeId("2"), EpisodeId("3")), enriched.value.episodeSummaries?.map { it.id })
            assertTrue(enriched.warnings.all { it.code == RemoteWarnings.MISSING_RESOURCE }, "TEST-UNIT-055: omissions are warnings")
        }

    @Test
    fun `TEST-UNIT-055 given_a_failing_episode_batch_when_details_are_enriched_then_the_detail_is_kept_without_summaries`() =
        TestTime.run {
            val (raw, _) =
                MockHttp.client(
                    MockHttp.route("character-detail.json", urlContains = "/character/1"),
                    MockHttp.errorRoute("character-page-beyond-last-404.json", 500, urlContains = "/episode/"),
                )

            val result = repository(raw.config { rickAndMortyDefaults() }).details(CharacterId("1"), enrich = true)

            val success = assertIs<DataResult.Success<*>>(result, "TEST-UNIT-055: the detail renders (ERROR_FLOW.md 7)")
            val details = success.value as CharacterDetails
            assertNull(details.episodeSummaries, "TEST-UNIT-055: the dependent rows stay hidden")
            assertTrue(
                success.warnings.any { it.code == RemoteWarnings.ENRICHMENT_FAILED },
                "TEST-UNIT-055: the partial detail is marked, so a cache never stores it",
            )
        }

    @Test
    fun `TEST-UNIT-055 given_an_unknown_id_when_details_are_loaded_then_it_is_not_found_and_nothing_is_enriched`() =
        TestTime.run {
            val (raw, served) = MockHttp.client(MockHttp.route("character-detail-not-found-404.json"))

            val result = repository(raw.config { rickAndMortyDefaults() }).details(CharacterId("99999"), enrich = true)

            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER, "99999"), (result as DataResult.Failure).failure)
            assertEquals(1, served.size, "TEST-UNIT-055: no episode request for a missing character")
        }
}
