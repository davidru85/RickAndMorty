package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.FilterName
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.LogScreen
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import io.github.davidru85.multiverse.core.domain.logging.PathTemplate
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.RecordingLogSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-032` — the one logging contract with permitted fields only (`REQ-OBS-001`,
 * `AC-REQ-OBS-001-1`, `CONTRACTS.md` `IC-024`, `OBSERVABILITY.md` §2, §7) — and the release half of
 * `TEST-UNIT-033`: a release logger writes `ERROR` events only, with no override (`DEC-039`).
 *
 * The validating implementation writes each event's catalogue fields and nothing else, drops a field
 * whose value fails its §2.2 form and counts it, never throws into its caller and never builds a
 * disabled event. The catalogue below is one instance per row that has an emitter in this build, with
 * every field set.
 */
class ValidatingAppLoggerTest {
    private val correlation = "0123456789abcdef"

    private data class Row(
        val event: LogEvent,
        val catalogueId: String,
        val level: LogLevel,
        val fields: Set<LogField>,
    )

    private val catalogue =
        listOf(
            Row(
                LogEvent.RequestStarted(
                    LogOperation.CHARACTER_LIST,
                    PathTemplate.CHARACTER,
                    page = 2,
                    filterNames = setOf(FilterName.STATUS, FilterName.NAME),
                    protocol = RemoteProtocol.Rest,
                    correlationId = correlation,
                ),
                "LOG-001",
                LogLevel.DEBUG,
                setOf(
                    LogField.OPERATION,
                    LogField.PATH_TEMPLATE,
                    LogField.PAGE,
                    LogField.FILTER_NAMES,
                    LogField.PROTOCOL,
                    LogField.CORRELATION_ID,
                ),
            ),
            Row(
                LogEvent.RequestCompleted(
                    LogOperation.CHARACTER_LIST,
                    PathTemplate.CHARACTER,
                    page = 2,
                    statusFamily = StatusFamily.SUCCESSFUL,
                    durationMs = 12,
                    correlationId = correlation,
                    outcome = LogOutcome.SUCCESS,
                ),
                "LOG-002",
                LogLevel.INFO,
                setOf(
                    LogField.OPERATION,
                    LogField.PATH_TEMPLATE,
                    LogField.PAGE,
                    LogField.STATUS_FAMILY,
                    LogField.DURATION_MS,
                    LogField.CORRELATION_ID,
                    LogField.OUTCOME,
                ),
            ),
            Row(
                LogEvent.RequestFailed(
                    LogOperation.CHARACTER_DETAIL,
                    PathTemplate.CHARACTER_BY_ID,
                    page = null,
                    statusFamily = StatusFamily.SERVER_ERROR,
                    errorClass = ErrorClass.SERVER,
                    durationMs = 40,
                    correlationId = correlation,
                ),
                "LOG-003",
                LogLevel.ERROR,
                setOf(
                    LogField.OPERATION,
                    LogField.PATH_TEMPLATE,
                    LogField.STATUS_FAMILY,
                    LogField.ERROR_CLASS,
                    LogField.DURATION_MS,
                    LogField.CORRELATION_ID,
                    LogField.OUTCOME,
                ),
            ),
            Row(
                LogEvent.ForeignHostRejected(LogOperation.CHARACTER_LIST, screen = LogScreen.DISCOVERY, correlationId = correlation),
                "LOG-004",
                LogLevel.ERROR,
                setOf(LogField.OPERATION, LogField.ERROR_CLASS, LogField.SCREEN, LogField.CORRELATION_ID),
            ),
            Row(
                LogEvent.PageLoaded(
                    page = 3,
                    outcome = LogOutcome.SUCCESS,
                    durationMs = 5,
                    cacheSource = DataSource.NETWORK,
                    correlationId = correlation,
                ),
                "LOG-010",
                LogLevel.DEBUG,
                setOf(
                    LogField.OPERATION,
                    LogField.PAGE,
                    LogField.OUTCOME,
                    LogField.DURATION_MS,
                    LogField.CACHE_SOURCE,
                    LogField.CORRELATION_ID,
                ),
            ),
            Row(
                LogEvent.PaginationExhausted(page = 3),
                "LOG-011",
                LogLevel.DEBUG,
                setOf(LogField.OPERATION, LogField.PAGE, LogField.OUTCOME),
            ),
            Row(
                LogEvent.RequestDeduplicated(
                    LogOperation.CHARACTER_LIST,
                    page = 1,
                    filterNames = setOf(FilterName.NAME),
                    correlationId = correlation,
                ),
                "LOG-012",
                LogLevel.DEBUG,
                setOf(LogField.OPERATION, LogField.PAGE, LogField.FILTER_NAMES, LogField.CORRELATION_ID),
            ),
            Row(
                LogEvent.RetryScheduled(
                    LogOperation.CHARACTER_LIST,
                    ErrorClass.RATE_LIMITED,
                    statusFamily = StatusFamily.CLIENT_ERROR,
                    retryAfterSeconds = 3,
                    correlationId = correlation,
                ),
                "LOG-013",
                LogLevel.WARN,
                setOf(
                    LogField.OPERATION,
                    LogField.ERROR_CLASS,
                    LogField.STATUS_FAMILY,
                    LogField.RETRY_AFTER_SECONDS,
                    LogField.CORRELATION_ID,
                ),
            ),
            Row(
                LogEvent.RequestCancelled(LogOperation.EPISODE_BATCH, correlationId = correlation),
                "LOG-014",
                LogLevel.DEBUG,
                setOf(LogField.OPERATION, LogField.OUTCOME, LogField.CORRELATION_ID),
            ),
            Row(
                LogEvent.UnknownValuePreserved(LogOperation.CHARACTER_DETAIL, PathTemplate.CHARACTER_BY_ID),
                "LOG-022",
                LogLevel.DEBUG,
                setOf(LogField.OPERATION, LogField.PATH_TEMPLATE, LogField.OUTCOME),
            ),
        )

    @Test
    fun `TEST-UNIT-032 given_each_catalogue_event_when_logged_then_its_record_carries_exactly_its_catalogue_fields`() {
        val sink = RecordingLogSink()
        val logger = ValidatingAppLogger.forDebug(sink)

        catalogue.forEach { logger.log(it.event) }

        assertEquals(catalogue.map { it.catalogueId }, sink.records.map { it.catalogueId }, "TEST-UNIT-032: one record per event")
        catalogue.zip(sink.records).forEach { (row, record) ->
            assertEquals(row.level, record.level, "TEST-UNIT-032: ${row.catalogueId} is written at its catalogue level")
            assertEquals(row.fields, record.fields.keys, "TEST-UNIT-032: ${row.catalogueId} carries exactly its row's fields")
        }
        val started = sink.records.first().fields
        assertEquals("/character", started[LogField.PATH_TEMPLATE], "TEST-UNIT-032: a path is a template")
        assertEquals("name,status", started[LogField.FILTER_NAMES], "TEST-UNIT-032: filter names, sorted, never values")
        assertEquals("REST", started[LogField.PROTOCOL])
        assertEquals("2XX", sink.records[1].fields[LogField.STATUS_FAMILY])
        assertEquals("FAILURE", sink.records[2].fields[LogField.OUTCOME], "TEST-UNIT-032: LOG-003 is outcome=FAILURE")
        assertEquals(
            "INVALID_REQUEST",
            sink.records[3].fields[LogField.ERROR_CLASS],
            "TEST-UNIT-032: LOG-004 is errorClass=INVALID_REQUEST",
        )
        assertEquals("CANCELLED", sink.records[8].fields[LogField.OUTCOME], "TEST-UNIT-032: LOG-014 is outcome=CANCELLED")
    }

    @Test
    fun `TEST-UNIT-032 given_field_values_that_fail_validation_when_logged_then_only_those_fields_are_dropped_and_counted`() {
        val sink = RecordingLogSink()
        val logger = ValidatingAppLogger.forDebug(sink)

        logger.log(
            LogEvent.RequestStarted(
                LogOperation.CHARACTER_LIST,
                PathTemplate.CHARACTER,
                page = 0,
                filterNames = emptySet(),
                protocol = RemoteProtocol.Rest,
                correlationId = "Rick Sanchez",
            ),
        )
        logger.log(
            LogEvent.RetryScheduled(
                LogOperation.CHARACTER_LIST,
                ErrorClass.RATE_LIMITED,
                null,
                retryAfterSeconds = -1,
                correlationId = correlation,
            ),
        )
        logger.log(
            LogEvent.RequestCompleted(
                LogOperation.CHARACTER_LIST,
                PathTemplate.CHARACTER,
                page = 1,
                statusFamily = StatusFamily.SUCCESSFUL,
                durationMs = -5,
                correlationId = correlation,
                outcome = LogOutcome.SUCCESS,
            ),
        )

        val (started, retry, completed) = sink.records
        assertEquals(
            setOf(LogField.OPERATION, LogField.PATH_TEMPLATE, LogField.PROTOCOL),
            started.fields.keys,
            "TEST-UNIT-032: a non-id and page 0 are dropped",
        )
        assertFalse(LogField.RETRY_AFTER_SECONDS in retry.fields, "TEST-UNIT-032: a negative delay is dropped")
        assertFalse(LogField.DURATION_MS in completed.fields, "TEST-UNIT-032: a negative duration is dropped")
        assertEquals(4, logger.droppedFields, "TEST-UNIT-032: each dropped field is counted, not printed (OBSERVABILITY.md 4.1 rule 2)")
    }

    @Test
    fun `TEST-UNIT-032 given_a_failing_sink_when_events_are_logged_then_the_caller_never_sees_an_exception`() {
        var attempts = 0
        val logger =
            ValidatingAppLogger.forDebug(
                LogSink {
                    attempts++
                    throw IllegalStateException("sink unavailable")
                },
            )

        catalogue.take(2).forEach { logger.log(it.event) }

        assertEquals(2, attempts, "TEST-UNIT-032: each record is offered once and never retried or queued")
        assertEquals(2, logger.lostRecords, "TEST-UNIT-032: a failing sink loses the record (OBSERVABILITY.md 7)")
    }

    @Test
    fun `TEST-UNIT-032 given_a_disabled_level_when_logged_through_the_inline_form_then_no_event_is_built`() {
        val sink = RecordingLogSink()
        val logger = ValidatingAppLogger.forRelease(sink)
        var built = false

        logger.log(LogLevel.DEBUG) {
            built = true
            catalogue.first().event
        }

        assertFalse(built, "TEST-UNIT-032: a disabled payload is never constructed (OBSERVABILITY.md 7 rule 3)")
        assertEquals(emptyList(), sink.records)
    }

    @Test
    fun `TEST-UNIT-033 given_the_release_logger_when_every_catalogue_event_is_logged_then_only_errors_are_written`() {
        val sink = RecordingLogSink()
        val logger = ValidatingAppLogger.forRelease(sink)

        catalogue.forEach { logger.log(it.event) }

        assertEquals(listOf(false, false, false, true), LogLevel.entries.map(logger::isEnabled), "TEST-UNIT-033: ERROR only (DEC-039)")
        assertEquals(listOf("LOG-003", "LOG-004"), sink.records.map { it.catalogueId }, "TEST-UNIT-033: the release-visible rows")
    }

    @Test
    fun `TEST-UNIT-032 given_the_debug_logger_then_every_level_is_enabled`() {
        val logger = ValidatingAppLogger.forDebug(RecordingLogSink())

        assertTrue(LogLevel.entries.all(logger::isEnabled))
    }
}
