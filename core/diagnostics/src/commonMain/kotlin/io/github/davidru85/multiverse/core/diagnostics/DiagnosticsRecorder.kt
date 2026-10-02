package io.github.davidru85.multiverse.core.diagnostics

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogScreen
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The debug-only diagnostic API (`REQ-OBS-002`, `OBSERVABILITY.md` §5, `DEC-085`, `DEC-088`): a
 * read-only fold over the validated records the logger writes, exposed as one [snapshot].
 *
 * It is a [LogSink], so it sees only what passed the `:core:data` validator and can show no field the
 * logger dropped. It offers no request trigger, no export and no share; reading it changes nothing. A
 * value whose emitter does not exist in this build is [Diagnostic.Unavailable], naming who delivers it,
 * and a value not seen yet is [Diagnostic.NotYetObserved] — never an invented zero or a fabricated
 * screen. Release artifacts never link this module (`R11`, `TEST-UNIT-033`).
 */
public class DiagnosticsRecorder : LogSink {
    private val state = MutableStateFlow(DiagnosticsSnapshot())

    /** The current view; a collector receives it first, then each change. */
    public val snapshot: StateFlow<DiagnosticsSnapshot> = state.asStateFlow()

    override fun write(record: LogRecord) {
        state.update { it.fold(record) }
    }

    private fun DiagnosticsSnapshot.fold(record: LogRecord): DiagnosticsSnapshot {
        val fields = record.fields
        return when (record.catalogueId) {
            REQUEST_COMPLETED -> copy(lastRequest = Diagnostic.Observed(fields.request()))
            REQUEST_FAILED ->
                copy(
                    lastRequest = Diagnostic.Observed(fields.request()),
                    lastFailure = fields.failure()?.let { Diagnostic.Observed(it) } ?: lastFailure,
                )
            FOREIGN_HOST_REJECTED -> copy(lastFailure = fields.failure()?.let { Diagnostic.Observed(it) } ?: lastFailure)
            PAGE_LOADED -> pageLoaded(fields)
            PAGINATION_EXHAUSTED ->
                fields[LogField.PAGE]?.toIntOrNull()?.let { copy(pager = Diagnostic.Observed(PagerDiagnostic(it, hasNextPage = false))) }
                    ?: this
            else -> this
        }
    }

    /** A published page sets the current source and the pager position; a failed one changes neither. */
    private fun DiagnosticsSnapshot.pageLoaded(fields: Map<LogField, String>): DiagnosticsSnapshot {
        if (fields[LogField.OUTCOME] == LogOutcome.FAILURE.name) return this
        val source = DataSource.entries.firstOrNull { it.name == fields[LogField.CACHE_SOURCE] }
        val page = fields[LogField.PAGE]?.toIntOrNull()
        return copy(
            currentSource = source?.let { Diagnostic.Observed(it) } ?: currentSource,
            pager = page?.let { Diagnostic.Observed(PagerDiagnostic(it, hasNextPage = true)) } ?: pager,
        )
    }

    private fun Map<LogField, String>.request() =
        RequestDiagnostic(
            durationMs = this[LogField.DURATION_MS]?.toLongOrNull(),
            statusFamily = StatusFamily.entries.firstOrNull { it.wireName == this[LogField.STATUS_FAMILY] },
        )

    private fun Map<LogField, String>.failure(): FailureDiagnostic? {
        val errorClass = ErrorClass.entries.firstOrNull { it.name == this[LogField.ERROR_CLASS] } ?: return null
        val screen = LogScreen.entries.firstOrNull { it.name == this[LogField.SCREEN] }
        return FailureDiagnostic(errorClass, screen?.let { Diagnostic.Observed(it) } ?: SCREEN_UNAVAILABLE)
    }

    private companion object {
        const val REQUEST_COMPLETED = "LOG-002"
        const val REQUEST_FAILED = "LOG-003"
        const val FOREIGN_HOST_REJECTED = "LOG-004"
        const val PAGE_LOADED = "LOG-010"
        const val PAGINATION_EXHAUSTED = "LOG-011"

        /** The data layer does not know the screen; the feature state holders will (`LOG-021`, B4). */
        val SCREEN_UNAVAILABLE = Diagnostic.Unavailable("the screen state holders (LOG-021, TASK-043)")
    }
}
