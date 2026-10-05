package io.github.davidru85.multiverse.app.di

import android.util.Log
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink

/**
 * The Android sink (`TASK-044`, `OBSERVABILITY.md` §4): validated records to Logcat under one tag.
 *
 * It writes only what the logger already validated, so no search text, response body, image byte,
 * personal datum or stack trace can reach Logcat through it (`REQ-SEC-005`, `DEC-039`). The record's
 * fields are rendered as `key=value` pairs sorted by key, so two runs of the same event read alike.
 */
public val LogcatSink: LogSink =
    LogSink { record ->
        val line = record.render()
        when (record.level) {
            LogLevel.DEBUG -> Log.d(TAG, line)
            LogLevel.INFO -> Log.i(TAG, line)
            LogLevel.WARN -> Log.w(TAG, line)
            LogLevel.ERROR -> Log.e(TAG, line)
        }
    }

/** The one Logcat tag. */
public const val TAG: String = "Multiverse"

/**
 * One record as a single Logcat line: catalogue id, then the permitted fields by their wire names in
 * the catalogue's order (`OBSERVABILITY.md` §2.2). The iOS sink prints the same line after a level
 * marker, because the unified log has no level column of its own (`TEST-UNIT-075`).
 */
private fun LogRecord.render(): String =
    buildString {
        append(catalogueId)
        fields.keys.sorted().forEach { key -> append(' ').append(key.wireName).append('=').append(fields.getValue(key)) }
    }
