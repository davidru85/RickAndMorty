package io.github.davidru85.multiverse.app.di

import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * `TEST-UNIT-075`'s Android half — a Logcat line names its fields as the catalogue does
 * (`OBSERVABILITY.md` §2.2, `TASK-116`).
 *
 * The catalogue's field names are the wire names (`protocol`, `pathTemplate`), so a developer can
 * search a debug build's Logcat for `protocol=GRAPHQL` exactly as on iOS. The fields follow the
 * catalogue's one order, whatever order the record was built in, so the two platforms print the same
 * line after their own level marker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class LogcatSinkTest {
    @Test
    fun `TEST-UNIT-075 given_a_request_start_when_written_to_logcat_then_its_fields_use_the_catalogue_names_and_order`() {
        ShadowLog.clear()
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

        LogcatSink.write(record)

        assertEquals(
            listOf("LOG-001 protocol=GRAPHQL operation=CHARACTER_LIST pathTemplate=/character page=1 correlationId=0123456789abcdef"),
            ShadowLog.getLogsForTag(TAG).map { it.msg },
        )
    }
}
