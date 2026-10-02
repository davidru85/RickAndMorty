package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.FilterName
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * The request scope's correlation id (`OBSERVABILITY.md` §4.1 rule 5): client-generated from a random
 * source, process-local, never persisted and never derived from user input. It travels in the coroutine
 * context, so the pager, the single flight, the retry policy and the adapter of one request share it.
 */
internal class CorrelationId(
    val value: String,
) : AbstractCoroutineContextElement(CorrelationId) {
    companion object Key : CoroutineContext.Key<CorrelationId> {
        fun next(): CorrelationId =
            CorrelationId(
                Random
                    .nextLong()
                    .toULong()
                    .toString(HEX)
                    .padStart(ID_LENGTH, '0'),
            )

        private const val HEX = 16
        private const val ID_LENGTH = 16
    }
}

/** The correlation id of the request this coroutine serves, if it serves one. */
internal suspend fun currentCorrelationId(): String? = currentCoroutineContext()[CorrelationId]?.value

/** The failure *type* of an `ApiFailure` (`OBSERVABILITY.md` §2.2), never its message. */
internal fun ApiFailure.errorClass(): ErrorClass =
    when (this) {
        ApiFailure.Offline -> ErrorClass.OFFLINE
        ApiFailure.Timeout -> ErrorClass.TIMEOUT
        is ApiFailure.NotFound -> ErrorClass.NOT_FOUND
        is ApiFailure.InvalidRequest -> ErrorClass.INVALID_REQUEST
        is ApiFailure.RateLimited -> ErrorClass.RATE_LIMITED
        is ApiFailure.Server -> ErrorClass.SERVER
        ApiFailure.MalformedResponse -> ErrorClass.MALFORMED_RESPONSE
        ApiFailure.EmptyBody -> ErrorClass.EMPTY_BODY
        // GraphQL is not a REST family (`DEC-011`); it gets its own class with its adapter.
        is ApiFailure.GraphQl, is ApiFailure.Unknown -> ErrorClass.UNKNOWN
    }

/**
 * The status family a failure implies where it implies one: a timeout may be a `408` or no response
 * at all, so it implies none.
 */
internal fun ApiFailure.statusFamily(): StatusFamily? =
    when (this) {
        ApiFailure.Offline -> StatusFamily.NO_RESPONSE
        is ApiFailure.RateLimited -> StatusFamily.CLIENT_ERROR
        is ApiFailure.Server -> StatusFamily.SERVER_ERROR
        else -> null
    }

/** The family of an HTTP status, or `null` for one outside the §2.2 families (a redirect). */
internal fun statusFamilyOf(status: Int): StatusFamily? =
    when (status) {
        in 200..299 -> StatusFamily.SUCCESSFUL
        in 400..499 -> StatusFamily.CLIENT_ERROR
        in 500..599 -> StatusFamily.SERVER_ERROR
        else -> null
    }

/** The names of the filters a list request sends — never their values. */
internal fun filterNames(
    query: String,
    status: StatusFilter,
): Set<FilterName> =
    buildSet {
        if (query.isNotBlank()) add(FilterName.NAME)
        if (status != StatusFilter.All) add(FilterName.STATUS)
    }
