package io.github.davidru85.multiverse.core.domain.logging

import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.result.DataSource

/**
 * The closed event catalogue of `OBSERVABILITY.md` §3 (`IC-024`): one implementation per row that has
 * an emitter in this build, whose properties are exactly the fields its row lists and whose id and
 * level are fixed by the row. A row whose emitter does not exist yet — the cache, image, app-start and
 * screen events — gains its implementation with that emitter.
 */
public sealed interface LogEvent {
    public val catalogueId: String
    public val level: LogLevel

    /** `LOG-001`: a request is about to be sent. */
    public data class RequestStarted(
        public val operation: LogOperation,
        public val pathTemplate: PathTemplate,
        public val page: Int?,
        public val filterNames: Set<FilterName>,
        public val protocol: RemoteProtocol,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-001"
        override val level: LogLevel get() = LogLevel.DEBUG
    }

    /** `LOG-002`: a request completed with a usable outcome; never a failure. */
    public data class RequestCompleted(
        public val operation: LogOperation,
        public val pathTemplate: PathTemplate,
        public val page: Int?,
        public val statusFamily: StatusFamily,
        public val durationMs: Long,
        public val correlationId: String?,
        public val outcome: LogOutcome,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-002"
        override val level: LogLevel get() = LogLevel.INFO
    }

    /** `LOG-003`: a request failed; its outcome is `FAILURE`. */
    public data class RequestFailed(
        public val operation: LogOperation,
        public val pathTemplate: PathTemplate,
        public val page: Int?,
        public val statusFamily: StatusFamily?,
        public val errorClass: ErrorClass,
        public val durationMs: Long,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-003"
        override val level: LogLevel get() = LogLevel.ERROR
    }

    /** `LOG-004`: the allow-list rejected a request before transport; its error class is `INVALID_REQUEST`. */
    public data class ForeignHostRejected(
        public val operation: LogOperation,
        public val screen: LogScreen?,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-004"
        override val level: LogLevel get() = LogLevel.ERROR
    }

    /** `LOG-010`: the pager published a list page; the operation is `CHARACTER_LIST`. */
    public data class PageLoaded(
        public val page: Int,
        public val outcome: LogOutcome,
        public val durationMs: Long,
        public val cacheSource: DataSource,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-010"
        override val level: LogLevel get() = LogLevel.DEBUG
    }

    /** `LOG-011`: the pager reached the end of the list; the operation is `CHARACTER_LIST`, the outcome `SUCCESS`. */
    public data class PaginationExhausted(
        public val page: Int,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-011"
        override val level: LogLevel get() = LogLevel.DEBUG
    }

    /** `LOG-012`: a request joined an identical one in flight (`REQ-REL-002`). */
    public data class RequestDeduplicated(
        public val operation: LogOperation,
        public val page: Int?,
        public val filterNames: Set<FilterName>,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-012"
        override val level: LogLevel get() = LogLevel.DEBUG
    }

    /** `LOG-013`: the retry policy scheduled another attempt (`REQ-REL-003`). */
    public data class RetryScheduled(
        public val operation: LogOperation,
        public val errorClass: ErrorClass,
        public val statusFamily: StatusFamily?,
        public val retryAfterSeconds: Long?,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-013"
        override val level: LogLevel get() = LogLevel.WARN
    }

    /** `LOG-014`: a request was cancelled in flight; its outcome is `CANCELLED`. */
    public data class RequestCancelled(
        public val operation: LogOperation,
        public val correlationId: String?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-014"
        override val level: LogLevel get() = LogLevel.DEBUG
    }

    /** `LOG-018`: a favourite toggle was written; the component is `FAVORITES_STORE`, and no id is carried. */
    public data class FavoritesToggled(
        public val outcome: LogOutcome,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-018"
        override val level: LogLevel get() = LogLevel.INFO
    }

    /** `LOG-019`: the favourites store could not be read or written; the error class is `UNKNOWN`. */
    public data class FavoritesStoreDegraded(
        public val screen: LogScreen?,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-019"
        override val level: LogLevel get() = LogLevel.ERROR
    }

    /** `LOG-022`: a response carried an unknown enum value, preserved rather than failed (`AC-REQ-NFR-004-2`). */
    public data class UnknownValuePreserved(
        public val operation: LogOperation,
        public val pathTemplate: PathTemplate,
    ) : LogEvent {
        override val catalogueId: String get() = "LOG-022"
        override val level: LogLevel get() = LogLevel.DEBUG
    }
}
