package io.github.davidru85.multiverse.core.domain.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * The failure taxonomy of `API_SPECS.md` §6 (`IC-004`). Classification is total: every failure that
 * reaches a consumer is one of these variants, mapped inside `:core:data`. The copy a user reads is
 * owned by `ERROR_FLOW.md`, never derived from these fields.
 */
public sealed interface ApiFailure {
    public data object Offline : ApiFailure

    public data object Timeout : ApiFailure

    public data class NotFound(
        public val resource: String,
        public val id: String,
    ) : ApiFailure

    public data class InvalidRequest(
        public val detail: String?,
    ) : ApiFailure

    public data class RateLimited(
        public val retryAfterSeconds: Long?,
    ) : ApiFailure

    public data class Server(
        public val statusCode: Int,
    ) : ApiFailure

    /** Never produced by the REST adapter (`DEC-011`, `ERROR_FLOW.md` §2). */
    public data class GraphQl(
        public val codes: Set<String>,
        public val messages: List<String>,
    ) : ApiFailure

    public data object MalformedResponse : ApiFailure

    public data object EmptyBody : ApiFailure

    /**
     * A failure whose cause cannot be classified more precisely. A cancellation is control flow,
     * not a failure, and is never wrapped here (`ERROR_FLOW.md` §6).
     */
    public data class Unknown(
        public val cause: Throwable?,
    ) : ApiFailure {
        init {
            require(cause !is CancellationException) {
                "A cancellation is rethrown, never mapped to ApiFailure (ERROR_FLOW.md 6)"
            }
        }
    }
}
