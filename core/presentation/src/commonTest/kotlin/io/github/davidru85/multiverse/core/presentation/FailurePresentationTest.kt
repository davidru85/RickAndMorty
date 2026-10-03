package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `TEST-UNIT-010`'s formatter half and `GAP-027` — one countdown, one failure message, both platforms.
 *
 * `ERROR_FLOW.md` §4.1 says the number in `error_message_rate_limited` is substituted by a
 * `:core:presentation` formatter, so two platforms cannot format the same `Retry-After` differently.
 * These cases drive that formatter and the failure→copy mapping it belongs to, over the `ApiFailure`
 * values the data layer produces (`IC-017`, `DEC-015`, `REQ-FUNC-022`).
 */
class FailurePresentationTest {
    @Test
    fun `TEST-UNIT-010 given_a_rate_limit_with_advice_when_the_countdown_is_formatted_then_it_is_the_identical_number_everywhere`() {
        assertEquals("30", DefaultPresentationFormatters.rateLimitCountdown(30L), "delta-seconds reaches the message unchanged")
        assertEquals("60", DefaultPresentationFormatters.rateLimitCountdown(60L), "the largest advised value is rendered")
        assertEquals("0", DefaultPresentationFormatters.rateLimitCountdown(0L), "a date in the past reads as now (API_SPECS.md 6.3)")
    }

    @Test
    fun `TEST-UNIT-010 given_advice_that_names_no_number_when_the_countdown_is_formatted_then_there_is_none_to_render`() {
        assertNull(
            DefaultPresentationFormatters.rateLimitCountdown(null),
            "a missing Retry-After means no countdown, never an invented zero",
        )
        assertNull(DefaultPresentationFormatters.rateLimitCountdown(-1), "a negative advice is not a countdown")
    }

    @Test
    fun `TEST-UNIT-010 given_each_failure_the_matrix_names_when_it_is_mapped_then_it_reaches_its_own_copy_key`() {
        val expected =
            mapOf(
                (ApiFailure.Offline as ApiFailure) to CopyKeys.ERROR_MESSAGE_OFFLINE,
                (ApiFailure.Timeout as ApiFailure) to CopyKeys.ERROR_MESSAGE_TIMEOUT,
                (ApiFailure.NotFound("character", "1") as ApiFailure) to CopyKeys.ERROR_MESSAGE_NOT_FOUND,
                (ApiFailure.InvalidRequest(null) as ApiFailure) to CopyKeys.ERROR_MESSAGE_INVALID_REQUEST,
                (ApiFailure.RateLimited(30) as ApiFailure) to CopyKeys.ERROR_MESSAGE_RATE_LIMITED,
                (ApiFailure.Server(500) as ApiFailure) to CopyKeys.ERROR_MESSAGE_SERVER,
                (ApiFailure.MalformedResponse as ApiFailure) to CopyKeys.ERROR_MESSAGE_MALFORMED,
                (ApiFailure.EmptyBody as ApiFailure) to CopyKeys.ERROR_MESSAGE_EMPTY_BODY,
                (ApiFailure.GraphQl(setOf("GRAPHQL_VALIDATION_FAILED"), listOf("x")) as ApiFailure) to CopyKeys.ERROR_MESSAGE_GRAPHQL,
                (ApiFailure.Unknown(null) as ApiFailure) to CopyKeys.ERROR_MESSAGE_UNKNOWN,
            )

        expected.forEach { (failure, key) ->
            val message = DefaultPresentationFormatters.failureMessage(failure)
            assertEquals(key, message.key, "every failure class of ERROR_FLOW.md 4 has its own message: $failure")
        }

        assertEquals(CopyKeys.ERROR_TITLE, DefaultPresentationFormatters.failureTitle(), "the title is shared by the full-surface error")
        assertEquals(CopyKeys.ACTION_RETRY, DefaultPresentationFormatters.retryAction(), "and the retry affordance is one key")
    }

    @Test
    fun `TEST-UNIT-010 given_a_rate_limited_failure_when_it_is_mapped_then_the_countdown_is_bound_into_the_message`() {
        assertEquals(
            listOf("30"),
            DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(30)).arguments,
            "the rate-limit message carries its number as an argument, never interpolated in shared code",
        )
        assertEquals(
            listOf("1"),
            DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(1)).arguments,
            "and a one-second advice is one argument, not a formatted sentence",
        )
        assertEquals(
            listOf("0"),
            DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(0)).arguments,
        )
    }

    @Test
    fun `TEST-UNIT-010 given_a_non_retryable_failure_when_it_is_mapped_then_it_offers_no_automatic_attempt`() {
        assertEquals(
            false,
            DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.MalformedResponse),
            "a decode failure is never retried automatically (REQ-REL-003, AC-REQ-REL-003-1)",
        )
        assertEquals(true, DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.Offline))
        assertEquals(true, DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.Timeout))
        assertEquals(true, DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.Server(500)))
        assertEquals(false, DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.NotFound("character", "1")))
        assertEquals(false, DefaultPresentationFormatters.isAutomaticallyRetryable(ApiFailure.InvalidRequest(null)))
    }

    @Test
    fun `TEST-UNIT-010 given_an_unknown_value_when_a_status_is_labelled_then_it_keeps_the_shared_unknown_treatment`() {
        assertEquals(CopyKeys.STATUS_ALIVE, DefaultPresentationFormatters.statusKey(CharacterStatus.Alive))
        assertEquals(CopyKeys.STATUS_DEAD, DefaultPresentationFormatters.statusKey(CharacterStatus.Dead))
        assertEquals(CopyKeys.VALUE_UNKNOWN, DefaultPresentationFormatters.statusKey(CharacterStatus.Unsupported("weird")))
    }
}
