package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.data.remote.rest.retryAfterSeconds
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlinx.io.IOException
import kotlin.time.Instant

// The transport and status mapping both `IC-011` implementations share (`API_SPECS.md` §6.1, §6.2
// step 1): GraphQL evaluates the REST families first, so the two protocols must not drift apart on
// them. The GraphQL-only failures — `ApiFailure.GraphQl`, and the envelope's own decode — stay in the
// GraphQL adapter.

/** The detail an `InvalidRequest` carries for a status the adapter refuses to follow. */
internal const val REDIRECT_DETAIL: String = "redirect"

/**
 * `API_SPECS.md` §6.1: a TLS failure is `Unknown` and is never retried (`API-ERR-003`); any other I/O
 * failure is a connectivity failure, `Offline`, retried within the budget (`API-ERR-001`); anything
 * else is not a transport failure at all and stays `Unknown` with its cause.
 */
internal fun classifyTransportFailure(failure: Exception): ApiFailure =
    when {
        failure.isTlsFailure() -> ApiFailure.Unknown(failure)
        failure is IOException -> ApiFailure.Offline
        else -> ApiFailure.Unknown(failure)
    }

/**
 * The REST failure families of one HTTP status (`API_SPECS.md` §6.1), shared by both protocols:
 * `null` for a success, `429` carrying the advice [retryAfter] gives from [now], and a redirect
 * refused rather than followed (`REQ-SEC-001`).
 */
internal fun statusFailure(
    status: Int,
    retryAfter: String?,
    now: Instant,
): ApiFailure? =
    when (status) {
        in 200..299 -> null
        REQUEST_TIMEOUT -> ApiFailure.Timeout
        TOO_MANY_REQUESTS -> ApiFailure.RateLimited(retryAfterSeconds(retryAfter, now))
        in 400..499 -> ApiFailure.InvalidRequest("http-$status")
        in 500..599 -> ApiFailure.Server(status)
        // A redirect is never followed, so its `Location` is never requested (`REQ-SEC-001`).
        in 300..399 -> ApiFailure.InvalidRequest(REDIRECT_DETAIL)
        else -> ApiFailure.Unknown(null)
    }

private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429
