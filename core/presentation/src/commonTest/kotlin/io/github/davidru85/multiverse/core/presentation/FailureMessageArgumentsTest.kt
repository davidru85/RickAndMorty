package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-067` — a failure message never asks a platform to substitute what it does not have
 * (`IC-017`, `DEC-123`, `ERROR_FLOW.md` §4.1, `TASK-111`).
 *
 * `error_message_rate_limited` carries a numeric placeholder. When the server advised no usable
 * `Retry-After`, there is no number to put in it, so the formatter must choose the message that has
 * no placeholder rather than hand the platform a template and an empty argument list.
 */
class FailureMessageArgumentsTest {
    private val noCountdown = CopyKey("error_message_rate_limited_no_countdown")

    @Test
    fun `TEST-UNIT-067 given_a_rate_limit_without_advice_when_it_is_mapped_then_the_message_has_no_placeholder_to_fill`() {
        val message = DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(null))

        assertEquals(noCountdown, message.key, "TEST-UNIT-067: no advice selects the message without a countdown")
        assertTrue(message.arguments.isEmpty(), "TEST-UNIT-067: and that message substitutes nothing")
    }

    @Test
    fun `TEST-UNIT-067 given_a_negative_advice_when_it_is_mapped_then_it_is_treated_as_no_advice`() {
        val message = DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(-1))

        assertEquals(noCountdown, message.key, "TEST-UNIT-067: a negative advice is not a countdown")
    }

    @Test
    fun `TEST-UNIT-067 given_a_rate_limit_with_advice_when_it_is_mapped_then_the_countdown_message_carries_one_argument`() {
        val message = DefaultPresentationFormatters.failureMessage(ApiFailure.RateLimited(30))

        assertEquals(CopyKeys.ERROR_MESSAGE_RATE_LIMITED, message.key, "TEST-UNIT-067: advice keeps the countdown message")
        assertEquals(1, message.arguments.size, "TEST-UNIT-067: with exactly the one value its placeholder needs")
    }

    @Test
    fun `TEST-UNIT-067 given_the_no_countdown_key_when_the_key_list_is_read_then_it_is_registered`() {
        assertTrue(noCountdown in CopyKeys.all, "TEST-UNIT-067: the key both platforms resolve is in the canonical list")
    }
}
