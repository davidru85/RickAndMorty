package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * `TEST-UNIT-022` — the `Retry-After` advice a `429` carries (`API_SPECS.md` §6.3, `DEC-084`):
 * delta-seconds or an IMF-fixdate HTTP-date read against the **injected** clock, a date in the past
 * read as "now", and anything else read as no advice — so the retry policy never waits on a value it
 * cannot trust, and no test depends on the wall clock (`REQ-REL-004`).
 */
class RetryAfterTest {
    private val now = Instant.parse("2026-10-02T10:00:00Z")

    private fun advised(header: String): ApiFailure {
        var failure: ApiFailure? = null
        TestTime.run { dispatcher ->
            val (raw, _) =
                MockHttp.client(
                    MockHttp.errorRoute("character-page-beyond-last-404.json", 429, headers = mapOf("Retry-After" to header)),
                )
            val clock = MutableFakeClock(now.toEpochMilliseconds())
            val result =
                RestCharacterRemoteDataSource(
                    raw.config { rickAndMortyDefaults() },
                    dispatcher,
                    clock,
                    ValidatingAppLogger.forDebug(RecordingLogSink()),
                ).characterPage(CharacterFilter(), 1)
            failure = (result as DataResult.Failure).failure
        }
        return failure!!
    }

    @Test
    fun `TEST-UNIT-022 given_delta_seconds_when_rate_limited_then_the_advice_is_kept`() {
        assertEquals(ApiFailure.RateLimited(7), advised("7"), "TEST-UNIT-022")
    }

    @Test
    fun `TEST-UNIT-022 given_an_http_date_when_rate_limited_then_the_advice_is_measured_on_the_injected_clock`() {
        assertEquals(ApiFailure.RateLimited(30), advised("Fri, 02 Oct 2026 10:00:30 GMT"), "TEST-UNIT-022: thirty seconds after now")
        assertEquals(ApiFailure.RateLimited(0), advised("Fri, 02 Oct 2026 09:59:00 GMT"), "TEST-UNIT-022: a past date means now")
    }

    @Test
    fun `TEST-UNIT-022 given_advice_that_cannot_be_read_when_rate_limited_then_there_is_no_advice`() {
        listOf("soon", "-5", "Fri, 31 Feb 2026 10:00:30 GMT", "Friday, 02-Oct-26 10:00:30 GMT", "").forEach { header ->
            assertEquals(ApiFailure.RateLimited(null), advised(header), "TEST-UNIT-022: `$header` is not advice")
        }
    }
}
