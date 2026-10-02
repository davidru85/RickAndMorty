package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.FilterName
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.LogScreen
import io.github.davidru85.multiverse.core.domain.logging.PathTemplate
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.result.DataSource

/**
 * An event's catalogue fields, rendered in their `OBSERVABILITY.md` §2.2 form. A field the row lists
 * but the event leaves `null` — an unknown status family, a request outside a correlation scope — is
 * absent rather than rendered empty; a constant the row fixes (`outcome=FAILURE` on `LOG-003`) is
 * rendered from the row.
 */
internal fun LogEvent.fields(): Map<LogField, String> =
    buildMap {
        when (val event = this@fields) {
            is LogEvent.RequestStarted -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.PATH_TEMPLATE, event.pathTemplate.template)
                putPage(event.page)
                putFilterNames(event.filterNames)
                put(LogField.PROTOCOL, event.protocol.wireName())
                putCorrelation(event.correlationId)
            }
            is LogEvent.RequestCompleted -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.PATH_TEMPLATE, event.pathTemplate.template)
                putPage(event.page)
                put(LogField.STATUS_FAMILY, event.statusFamily.wireName)
                put(LogField.DURATION_MS, event.durationMs.toString())
                putCorrelation(event.correlationId)
                put(LogField.OUTCOME, event.outcome.name)
            }
            is LogEvent.RequestFailed -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.PATH_TEMPLATE, event.pathTemplate.template)
                putPage(event.page)
                event.statusFamily?.let { put(LogField.STATUS_FAMILY, it.wireName) }
                put(LogField.ERROR_CLASS, event.errorClass.name)
                put(LogField.DURATION_MS, event.durationMs.toString())
                putCorrelation(event.correlationId)
                put(LogField.OUTCOME, LogOutcome.FAILURE.name)
            }
            is LogEvent.ForeignHostRejected -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.ERROR_CLASS, ErrorClass.INVALID_REQUEST.name)
                event.screen?.let { put(LogField.SCREEN, it.name) }
                putCorrelation(event.correlationId)
            }
            is LogEvent.PageLoaded -> {
                put(LogField.OPERATION, LogOperation.CHARACTER_LIST.name)
                putPage(event.page)
                put(LogField.OUTCOME, event.outcome.name)
                put(LogField.DURATION_MS, event.durationMs.toString())
                put(LogField.CACHE_SOURCE, event.cacheSource.name)
                putCorrelation(event.correlationId)
            }
            is LogEvent.PaginationExhausted -> {
                put(LogField.OPERATION, LogOperation.CHARACTER_LIST.name)
                putPage(event.page)
                put(LogField.OUTCOME, LogOutcome.SUCCESS.name)
            }
            is LogEvent.RequestDeduplicated -> {
                put(LogField.OPERATION, event.operation.name)
                putPage(event.page)
                putFilterNames(event.filterNames)
                putCorrelation(event.correlationId)
            }
            is LogEvent.RetryScheduled -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.ERROR_CLASS, event.errorClass.name)
                event.statusFamily?.let { put(LogField.STATUS_FAMILY, it.wireName) }
                event.retryAfterSeconds?.let { put(LogField.RETRY_AFTER_SECONDS, it.toString()) }
                putCorrelation(event.correlationId)
            }
            is LogEvent.RequestCancelled -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.OUTCOME, LogOutcome.CANCELLED.name)
                putCorrelation(event.correlationId)
            }
            is LogEvent.UnknownValuePreserved -> {
                put(LogField.OPERATION, event.operation.name)
                put(LogField.PATH_TEMPLATE, event.pathTemplate.template)
                put(LogField.OUTCOME, LogOutcome.SUCCESS.name)
            }
        }
    }

private fun MutableMap<LogField, String>.putPage(page: Int?) {
    if (page != null) put(LogField.PAGE, page.toString())
}

private fun MutableMap<LogField, String>.putFilterNames(names: Set<FilterName>) {
    if (names.isNotEmpty()) put(LogField.FILTER_NAMES, names.map { it.wireName }.sorted().joinToString(","))
}

private fun MutableMap<LogField, String>.putCorrelation(id: String?) {
    if (id != null) put(LogField.CORRELATION_ID, id)
}

private fun RemoteProtocol.wireName(): String =
    when (this) {
        RemoteProtocol.Rest -> "REST"
        RemoteProtocol.GraphQl -> "GRAPHQL"
    }

/**
 * The `OBSERVABILITY.md` §2.2 form of each field. A field no event of this build carries has no form
 * yet, so any value for it is rejected until its emitter and its rule arrive together.
 */
internal object LogFieldRules {
    private val CORRELATION_ID = Regex("^[0-9a-f]{16}$")

    fun isValid(
        field: LogField,
        value: String,
    ): Boolean =
        when (field) {
            LogField.PAGE -> (value.toIntOrNull() ?: 0) >= 1
            LogField.DURATION_MS, LogField.RETRY_AFTER_SECONDS -> (value.toLongOrNull() ?: -1) >= 0
            LogField.CORRELATION_ID -> CORRELATION_ID.matches(value)
            LogField.FILTER_NAMES -> value.split(',').all { name -> FilterName.entries.any { it.wireName == name } }
            LogField.PATH_TEMPLATE -> PathTemplate.entries.any { it.template == value }
            LogField.OPERATION -> LogOperation.entries.any { it.name == value }
            LogField.STATUS_FAMILY -> StatusFamily.entries.any { it.wireName == value }
            LogField.CACHE_SOURCE -> value == NO_CACHE_SOURCE || DataSource.entries.any { it.name == value }
            LogField.IS_STALE -> value == "true" || value == "false"
            LogField.OUTCOME -> LogOutcome.entries.any { it.name == value }
            LogField.ERROR_CLASS -> ErrorClass.entries.any { it.name == value }
            LogField.SCREEN -> LogScreen.entries.any { it.name == value }
            LogField.PROTOCOL -> value == "REST" || value == "GRAPHQL"
            LogField.COMPONENT, LogField.APP_VERSION, LogField.PLATFORM, LogField.BUILD_TYPE, LogField.CAUSE -> false
        }

    private const val NO_CACHE_SOURCE = "NONE"
}
