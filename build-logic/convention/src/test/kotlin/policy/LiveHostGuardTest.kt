package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-024` — the live-host guard (`TESTING.md` §4.1).
 *
 * The guard is a prohibition, so it is only worth having if it fails when the prohibition is
 * broken: these tests pin both directions, the offender being caught and the two permitted shapes
 * — a fixture-backed test and the scheduled `contract-live` source set — passing. The host literal
 * is assembled from parts so this file, which the guard itself scans, does not trip it.
 */
class LiveHostGuardTest {

    private fun tree(vararg files: Pair<String, String>): File {
        val root = kotlin.io.path.createTempDirectory("live-host-guard").toFile()
        files.forEach { (path, content) ->
            val file = File(root, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        return root
    }

    private val host = listOf("rickandmortyapi", "com").joinToString(".")

    @Test
    fun `a test source set naming the live host is reported with its line`() {
        val root = tree(
            "core/data/src/commonTest/kotlin/Leak.kt" to
                "package leak\n\nval url = \"https://$host/api/character\"\n",
        )
        val findings = LiveHostGuard.scan(listOf(root))
        assertEquals(1, findings.size, "TEST-UNIT-024: the offender must be reported exactly once: $findings")
        assertEquals("Leak.kt", findings.single().path.substringAfterLast('/'))
        assertEquals(3, findings.single().line, "TEST-UNIT-024: the finding must name the offending line")
    }

    @Test
    fun `a fixture-backed test passes`() {
        val root = tree(
            "core/data/src/commonTest/kotlin/Clean.kt" to
                "package clean\n\nimport io.github.davidru85.multiverse.testing.FixtureLoader\n" +
                "val body = FixtureLoader.text(\"character-page-01.json\")\n",
        )
        assertEquals(emptyList(), LiveHostGuard.scan(listOf(root)).map { it.render() })
    }

    @Test
    fun `the scheduled contract-live source set is the one exemption`() {
        val root = tree(
            "core/testing/src/contract-live/kotlin/Live.kt" to "package live\nval url = \"https://$host/api\"\n",
        )
        // The exemption is by directory identity, so a path that merely *contains* the word in a
        // file name is not exempt.
        val decoy = tree(
            "core/testing/src/commonTest/kotlin/contract-live-not-a-source-set.kt" to
                "package decoy\nval url = \"https://$host/api\"\n",
        )
        assertEquals(emptyList(), LiveHostGuard.scan(listOf(root)).map { it.render() }, "contract-live is exempt")
        assertEquals(1, LiveHostGuard.scan(listOf(decoy)).size, "TEST-UNIT-024: the exemption is a source set, not a name")
    }

    @Test
    fun `a non-Kotlin file is not scanned`() {
        val root = tree("docs/notes.md" to "see https://$host/api/character\n")
        assertTrue(LiveHostGuard.scan(listOf(root)).isEmpty(), "TEST-UNIT-024: only Kotlin test sources are scanned")
    }

    private fun LiveHostGuard.Finding.render() = "$path:$line:$host"
}
