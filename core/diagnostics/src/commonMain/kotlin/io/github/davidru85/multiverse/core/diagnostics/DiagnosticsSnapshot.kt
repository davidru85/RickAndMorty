package io.github.davidru85.multiverse.core.diagnostics

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.LogScreen
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.result.DataSource

/** One diagnostic value: seen, not seen yet, or not produced by anything in this build. */
public sealed interface Diagnostic<out T> {
    public data class Observed<T>(
        public val value: T,
    ) : Diagnostic<T>

    /** The emitter exists, and nothing has been recorded yet. */
    public data object NotYetObserved : Diagnostic<Nothing>

    /** Nothing in this build produces the value; [deliveredBy] names what will. */
    public data class Unavailable(
        public val deliveredBy: String,
    ) : Diagnostic<Nothing>
}

/**
 * The items of `OBSERVABILITY.md` §5 the build can show, each typed by the closed sets of §2.2.
 * Cache state, the favourites count, the build envelope and the stale indicator have no emitter yet,
 * so they are unavailable rather than zero.
 */
public data class DiagnosticsSnapshot(
    public val lastFailure: Diagnostic<FailureDiagnostic> = Diagnostic.NotYetObserved,
    public val currentSource: Diagnostic<DataSource> = Diagnostic.NotYetObserved,
    public val lastRequest: Diagnostic<RequestDiagnostic> = Diagnostic.NotYetObserved,
    public val pager: Diagnostic<PagerDiagnostic> = Diagnostic.NotYetObserved,
    public val stale: Diagnostic<Boolean> = Diagnostic.Unavailable("the screen state holders (LOG-021, TASK-043)"),
    public val cacheState: Diagnostic<Nothing> = Diagnostic.Unavailable("the response cache (LOG-005…LOG-009, TASK-020)"),
    public val favoritesCount: Diagnostic<Int> = Diagnostic.Unavailable("the favourites store (TASK-040) and the panel host (TASK-044)"),
    public val buildEnvelope: Diagnostic<Nothing> = Diagnostic.Unavailable("the app shells (LOG-020, TASK-044, TASK-051)"),
)

/** The last failure: its type, never its message, and the screen it happened on. */
public data class FailureDiagnostic(
    public val errorClass: ErrorClass,
    public val screen: Diagnostic<LogScreen>,
)

/** The most recent request's duration and status family. */
public data class RequestDiagnostic(
    public val durationMs: Long?,
    public val statusFamily: StatusFamily?,
)

/** The pager's position: the last published page and whether another follows. */
public data class PagerDiagnostic(
    public val page: Int,
    public val hasNextPage: Boolean,
)
