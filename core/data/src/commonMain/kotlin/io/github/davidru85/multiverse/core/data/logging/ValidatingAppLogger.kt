package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink

/**
 * The validating, redacting implementation of `IC-024` (`DEC-087`, ADR-0013): it turns an event into
 * its catalogue fields, drops a field whose value fails its `OBSERVABILITY.md` §2.2 form, and writes the
 * record to the injected [sink].
 *
 * The threshold is fixed when the logger is built and has no setter: [forRelease] is `ERROR` only and
 * [forDebug] is every level (`DEC-039`, §4.1 rule 7). A composition root picks one from a build-variant
 * source set, never from a runtime flag. Logging never throws into its caller and never queues: a
 * dropped field and a lost record are counted, not printed (§4.1 rule 2, §7).
 */
public class ValidatingAppLogger private constructor(
    private val sink: LogSink,
    private val threshold: LogLevel,
) : AppLogger {
    /** Fields dropped because their value failed validation. */
    internal var droppedFields: Int = 0
        private set

    /** Records lost because they could not be built or the sink failed. */
    internal var lostRecords: Int = 0
        private set

    override fun isEnabled(level: LogLevel): Boolean = level >= threshold

    override fun log(event: LogEvent) {
        if (!isEnabled(event.level)) return
        val record =
            try {
                LogRecord(event.level, event.catalogueId, validated(event.fields()))
            } catch (_: Exception) {
                lostRecords++
                return
            }
        try {
            sink.write(record)
        } catch (_: Exception) {
            lostRecords++
        }
    }

    private fun validated(fields: Map<LogField, String>) =
        fields.filter { (field, value) ->
            LogFieldRules.isValid(field, value).also { valid -> if (!valid) droppedFields++ }
        }

    public companion object {
        /** `ERROR` events only, fixed for the life of the logger (`DEC-039`). */
        public fun forRelease(sink: LogSink): ValidatingAppLogger = ValidatingAppLogger(sink, LogLevel.ERROR)

        /** Every level, for debug builds and tests. */
        public fun forDebug(sink: LogSink): ValidatingAppLogger = ValidatingAppLogger(sink, LogLevel.DEBUG)
    }
}
