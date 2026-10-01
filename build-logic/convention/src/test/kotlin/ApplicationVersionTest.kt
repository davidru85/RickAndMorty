package io.github.davidru85.multiverse.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `ApplicationVersion` — the reader behind the single `VERSION` source (`TASK-018`, `DEC-067`).
 *
 * The grammar itself is owned by `verifyDependencyPins` (`TEST-UNIT-014` P9); this reader supplies
 * the value to the Android `versionName`. These tests pin only its own contract: the file's single
 * value is returned without its final line terminator, and an absent file returns `null` so the
 * caller fails rather than inventing a version.
 */
class ApplicationVersionTest {

    private fun versionFile(text: String?): File {
        val dir = kotlin.io.path.createTempDirectory("version-reader").toFile()
        val file = dir.resolve("VERSION")
        text?.let { file.writeText(it) }
        return file
    }

    @Test
    fun `the value is returned without its final line terminator`() {
        assertEquals("0.1.0", ApplicationVersion.read(versionFile("0.1.0\n")))
        assertEquals("0.1.0", ApplicationVersion.read(versionFile("0.1.0\r\n")))
        assertEquals("0.1.0", ApplicationVersion.read(versionFile("0.1.0")))
    }

    @Test
    fun `the reader keeps inner content verbatim so P9 owns the grammar`() {
        // The reader is not a validator: a malformed value is returned as it stands and
        // `verifyDependencyPins` rejects it. Anything else would be a second grammar.
        assertEquals("invalid-version", ApplicationVersion.read(versionFile("invalid-version\n")))
        assertEquals("0.1.0", ApplicationVersion.read(versionFile("0.1.0\n\n")))
    }

    @Test
    fun `an absent file reads as null so the caller fails rather than invents`() {
        assertNull(ApplicationVersion.read(versionFile(null)))
    }
}
