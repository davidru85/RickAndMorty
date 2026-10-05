package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import kotlin.test.Test

/**
 * `TEST-UNIT-074` — the iOS platform sink writes every validated record without taking the process
 * down (`IC-024`, `OBSERVABILITY.md` §2, `TASK-116`).
 *
 * The sink renders through `NSLog`, a C variadic function. A Kotlin `String` passed to its `%@` is not
 * an Objective-C object, so the formatter reads the characters as a pointer and the process dies. The
 * release threshold lets only `ERROR` records through, so that crash waited for the first failed
 * request on iOS; a debug build reaches it on the first request of all. Each case writes one record a
 * request path really emits; reaching the end of the case is the assertion.
 */
class OsLogSinkTest {
    @Test
    fun `TEST-UNIT-074 given_a_failure_record_when_the_ios_sink_writes_it_then_the_process_survives`() {
        OsLogSink.write(
            LogRecord(
                level = LogLevel.ERROR,
                catalogueId = "LOG-003",
                fields = mapOf(LogField.OPERATION to "CHARACTER_LIST", LogField.CORRELATION_ID to "0123456789abcdef"),
            ),
        )
    }

    @Test
    fun `TEST-UNIT-074 given_a_request_start_when_the_ios_sink_writes_it_then_the_process_survives`() {
        OsLogSink.write(
            LogRecord(
                level = LogLevel.DEBUG,
                catalogueId = "LOG-001",
                fields = mapOf(LogField.OPERATION to "CHARACTER_LIST", LogField.PROTOCOL to "GRAPHQL"),
            ),
        )
    }

    @Test
    fun `TEST-UNIT-074 given_a_record_without_fields_when_the_ios_sink_writes_it_then_the_process_survives`() {
        OsLogSink.write(LogRecord(level = LogLevel.INFO, catalogueId = "LOG-002", fields = emptyMap()))
    }
}
