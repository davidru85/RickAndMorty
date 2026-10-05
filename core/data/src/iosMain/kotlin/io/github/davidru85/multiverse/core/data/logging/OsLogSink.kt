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
 * The render is the level, the catalogue id and the field pairs by their wire names in the catalogue's
 * order: the Android `LogcatSink` line after a level marker, so one log line reads the same on both
 * platforms (`TEST-UNIT-075`).
 */
public object OsLogSink : LogSink {
    override fun write(record: LogRecord) {
        // One `%s` argument, never `%@`: in a C variadic call a Kotlin `String` arrives as a C string,
        // not as an Objective-C object, so `%@` would read its characters as a pointer and crash. The
        // line is ASCII by construction — catalogue ids, enum names, numbers and hex ids — so `%s`
        // renders it exactly, and no field can ever be read as part of the format (`TEST-UNIT-074`).
        NSLog("%s", render(record))
    }

    /**
     * The level, the catalogue id, then the field pairs by their wire names in the catalogue's order:
     * the Android Logcat line after a level marker, which the unified log has no column for
     * (`OBSERVABILITY.md` §2.2, `TEST-UNIT-075`).
     */
    internal fun render(record: LogRecord): String {
        val fields =
            record.fields.entries
                .sortedBy { it.key }
                .joinToString(separator = " ") { (field, value) -> "${field.wireName}=$value" }
        return "${record.level.name} ${record.catalogueId}${if (fields.isEmpty()) "" else " $fields"}"
    }
}
