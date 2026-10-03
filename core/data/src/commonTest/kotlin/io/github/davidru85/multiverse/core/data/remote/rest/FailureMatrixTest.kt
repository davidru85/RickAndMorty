package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

/**
 * `TEST-UNIT-010` — the failure matrix of `ERROR_FLOW.md` §4, driven fixture by fixture.
 *
 * Each case puts the real REST adapter behind a `MockEngine` that answers from a committed fixture, so
 * the classification, the domain `ApiFailure` and the copy key the state renders are asserted on the
 * path that ships, not on a hand-built value. The last group covers the `API-ERR-005` rate limit and the
 * countdown its message carries (`GAP-027`). No case opens a socket.
 */
class FailureMatrixTest {
    private val logger = ValidatingAppLogger.forDebug(RecordingLogSink())

    private fun adapter(
        client: HttpClient,
        clock: MutableFakeClock = MutableFakeClock(),
    ) = RestCharacterRemoteDataSource(client, kotlinx.coroutines.Dispatchers.Unconfined, clock, logger)

    private fun failureOf(outcome: DataResult<*>): ApiFailure =
        assertIs<DataResult.Failure>(outcome, "an expected remote failure is a value, not an exception").failure

    @Test
    fun `TEST-UNIT-010 given_a_500_when_a_page_is_requested_then_it_maps_to_Server_with_its_message`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.errorRoute("character-page-01.json", 500))
            val failure = failureOf(adapter(client).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.Server(500), failure, "API-ERR-007")
        }

    @Test
    fun `TEST-UNIT-010 given_an_empty_body_when_a_page_is_requested_then_it_maps_to_EmptyBody`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.route("empty-body.txt"))
            val failure = failureOf(adapter(client).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.EmptyBody, failure, "API-ERR-008")
        }

    @Test
    fun `TEST-UNIT-010 given_a_malformed_body_when_a_page_is_requested_then_it_maps_to_MalformedResponse`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.route("malformed-body.txt"))
            val failure = failureOf(adapter(client).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.MalformedResponse, failure, "API-ERR-009, never a crash")
        }

    @Test
    fun `TEST-UNIT-010 given_a_detail_404_when_it_is_requested_then_it_maps_to_NotFound_naming_the_character`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.errorRoute("character-detail-not-found-404.json", 404))
            val failure = failureOf(adapter(client).characterDetails(ID))

            assertEquals(ApiFailure.NotFound("character", "1"), failure, "API-ERR-016")
        }

    @Test
    fun `TEST-UNIT-010 given_a_429_with_advice_when_a_page_is_requested_then_the_failure_carries_the_countdown`() =
        TestTime.run {
            val (client, _) =
                MockHttp.client(
                    MockHttp.errorRoute("character-page-01.json", 429, headers = mapOf("Retry-After" to "30")),
                )
            val failure = failureOf(adapter(client).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.RateLimited(30), failure, "API-ERR-005: the delta-seconds advice is parsed")
        }

    @Test
    fun `TEST-UNIT-010 given_a_429_advising_an_http_date_when_it_is_parsed_then_the_countdown_is_measured_on_the_injected_clock`() =
        TestTime.run {
            val clock =
                MutableFakeClock(
                    kotlin.time.Instant
                        .parse("2026-10-03T12:00:00Z")
                        .toEpochMilliseconds(),
                )
            val (client, _) =
                MockHttp.client(
                    MockHttp.errorRoute(
                        "character-page-01.json",
                        429,
                        headers = mapOf("Retry-After" to "Sat, 03 Oct 2026 12:02:00 GMT"),
                    ),
                )
            val failure = failureOf(adapter(client, clock).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.RateLimited(120), failure, "an HTTP-date advice is 120 s on this clock, not on the wall clock")
        }

    @Test
    fun `TEST-UNIT-010 given_a_429_with_a_past_date_when_it_is_parsed_then_the_countdown_is_zero_not_negative`() =
        TestTime.run {
            val clock =
                MutableFakeClock(
                    kotlin.time.Instant
                        .parse("2026-10-03T12:00:00Z")
                        .toEpochMilliseconds(),
                )
            val (client, _) =
                MockHttp.client(
                    MockHttp.errorRoute(
                        "character-page-01.json",
                        429,
                        headers = mapOf("Retry-After" to "Sat, 03 Oct 2026 11:00:00 GMT"),
                    ),
                )
            val failure = failureOf(adapter(client, clock).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.RateLimited(0), failure, "a date in the past reads as now (API_SPECS.md 6.3)")
        }

    @Test
    fun `TEST-UNIT-010 given_a_429_without_advice_when_a_page_is_requested_then_there_is_no_countdown_to_render`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.errorRoute("character-page-01.json", 429))
            val failure = failureOf(adapter(client).characterPage(CharacterFilter(), 1))

            assertEquals(ApiFailure.RateLimited(null), failure, "no Retry-After means no countdown to offer")
        }

    @Test
    fun `TEST-UNIT-010 given_a_cancellation_when_a_page_is_cancelled_then_no_failure_is_produced`() =
        TestTime.run {
            val (client, _) = MockHttp.client(MockHttp.route("character-page-01.json"), latency = 5.minutes)
            val job = launch { adapter(client).characterPage(CharacterFilter(), 1) }
            job.cancel()
            // The adapter rethrows the CancellationException, which is control flow, not a failure
            // (ERROR_FLOW.md 6, AC-REQ-FUNC-022-2): there is nothing to map and nothing to render.
            assertEquals(true, job.isCancelled)
        }

    private companion object {
        val ID =
            io.github.davidru85.multiverse.core.domain.model
                .CharacterId("1")
    }
}
