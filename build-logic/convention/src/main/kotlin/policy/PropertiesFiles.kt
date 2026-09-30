package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import java.io.StringReader
import java.util.Properties

/**
 * Reads a `*.properties` file with `java.util.Properties`, so that `\:` escapes and
 * `#` comments are handled the same way Gradle handles them (`TEST-UNIT-014` P5,
 * `TEST-UNIT-013` R7; DEC-061).
 */
internal object PropertiesFiles {

    /** The properties of [file], or an empty map when it does not exist. */
    fun read(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val properties = Properties()
        properties.load(StringReader(file.readText()))
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }
}
