package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink

/**
 * A `LogSink` (`IC-024`) that keeps every record it receives, in order, so a test asserts on what
 * actually reached a sink rather than on what a call site intended (`TASK-047`, `OBSERVABILITY.md` §4.2).
 */
public class RecordingLogSink : LogSink {
    private val recorded = mutableListOf<LogRecord>()

    /** Every record written so far, in order. */
    public val records: List<LogRecord> get() = recorded.toList()

    override fun write(record: LogRecord) {
        recorded += record
    }
}
