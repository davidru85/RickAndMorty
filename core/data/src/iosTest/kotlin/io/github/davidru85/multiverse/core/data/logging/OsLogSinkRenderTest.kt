package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-075`'s iOS half — the unified-log line names its fields as the catalogue does, in the
 * catalogue's one order, after the level the unified log has no column for (`OBSERVABILITY.md` §2.2,
 * `TASK-116`). The Android half asserts the same line after Logcat's own level column.
 */
class OsLogSinkRenderTest {
    @Test
    fun `TEST-UNIT-075 given_a_request_start_when_rendered_for_the_unified_log_then_it_matches_the_android_line`() {
        val record =
            LogRecord(
                level = LogLevel.DEBUG,
                catalogueId = "LOG-001",
                fields =
                    linkedMapOf(
                        LogField.CORRELATION_ID to "0123456789abcdef",
                        LogField.PAGE to "1",
                        LogField.PATH_TEMPLATE to "/character",
                        LogField.OPERATION to "CHARACTER_LIST",
                        LogField.PROTOCOL to "GRAPHQL",
                    ),
            )

        assertEquals(
            "DEBUG LOG-001 protocol=GRAPHQL operation=CHARACTER_LIST pathTemplate=/character page=1 correlationId=0123456789abcdef",
            OsLogSink.render(record),
        )
    }
}
