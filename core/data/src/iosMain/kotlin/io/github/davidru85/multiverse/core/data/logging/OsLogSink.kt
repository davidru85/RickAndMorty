package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import platform.Foundation.NSLog

/**
 * The iOS platform sink (`IC-024`, `OBSERVABILITY.md` §2, `TASK-051`).
 *
 * It renders the **validated** record the contract hands it and nothing else: the record already
 * carries only the permitted fields, so the redaction boundary stays a property of the contract
 * rather than of each call site (`SECURITY.md` §7).
 *
 * `NSLog` is the platform's own sink, and it is deliberate that no logging library is added: a
 * third-party logger would be a dependency the privacy rules would have to re-review (`REQ-OBS-003`).
 * The render is the catalogue id followed by the field pairs in their wire names, which is the same
 * shape the Android `LogcatSink` writes, so one log line reads the same on both platforms.
 */
public object OsLogSink : LogSink {
    override fun write(record: LogRecord) {
        val fields =
            record.fields.entries
                .joinToString(separator = " ") { (field, value) -> "${field.wireName}=$value" }
        NSLog("%@ %@%@", record.level.name, record.catalogueId, if (fields.isEmpty()) "" else " $fields")
    }
}
