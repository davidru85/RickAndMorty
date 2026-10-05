package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-081` — how a failed Detail load is recovered (`IC-017`, `DEC-131`, `ERROR_FLOW.md` §4,
 * §10, `TASK-112`).
 *
 * Every Detail failure used to offer Retry under "Couldn't load these details". A `404` on a detail is
 * terminal for that identifier (`API-ERR-016`), so retrying it can never succeed: it is recovered by
 * Back, with the not-found message, and never by Retry. Every other failure keeps Retry.
 */
class DetailRecoveryTest {
    private val formatters: PresentationFormatters = DefaultPresentationFormatters
    private val notFound = ApiFailure.NotFound(resource = "character", id = "9999")
    private val others =
        listOf(
            ApiFailure.Offline,
            ApiFailure.Timeout,
            ApiFailure.InvalidRequest(detail = null),
            ApiFailure.RateLimited(retryAfterSeconds = 3),
            ApiFailure.Server(statusCode = 503),
            ApiFailure.GraphQl(codes = emptySet(), messages = emptyList()),
            ApiFailure.MalformedResponse,
            ApiFailure.EmptyBody,
            ApiFailure.Unknown(cause = null),
        )

    @Test
    fun `TEST-UNIT-081 given_a_detail_not_found_when_recovered_then_it_is_Back_and_never_Retry`() {
        assertEquals(Recovery.Back, formatters.recovery(notFound), "TEST-UNIT-081: API-ERR-016 is terminal for the identifier")
        assertEquals(CopyKeys.ACTION_BACK, Recovery.Back.actionKey, "TEST-UNIT-081: its affordance reads action_back")
    }

    @Test
    fun `TEST-UNIT-081 given_any_other_failure_when_recovered_then_it_is_Retry`() {
        others.forEach { failure ->
            assertEquals(Recovery.Retry, formatters.recovery(failure), "TEST-UNIT-081: $failure is recovered by Retry (ERROR_FLOW.md 10)")
        }
        assertEquals(CopyKeys.ACTION_RETRY, Recovery.Retry.actionKey, "TEST-UNIT-081: its affordance reads action_retry")
    }

    @Test
    fun `TEST-UNIT-081 given_a_failure_beside_a_header_when_its_inline_message_is_chosen_then_not_found_says_so`() {
        assertEquals(
            FailureMessage(CopyKeys.ERROR_MESSAGE_NOT_FOUND),
            formatters.inlineFailureMessage(notFound),
            "TEST-UNIT-081: a not-found detail says it is not in this dimension, not \"Retry\"",
        )
        others.forEach { failure ->
            assertEquals(
                FailureMessage(CopyKeys.DETAIL_ERROR_INLINE),
                formatters.inlineFailureMessage(failure),
                "TEST-UNIT-081: a retryable failure keeps the inline detail_error_inline ($failure)",
            )
        }
    }
}
