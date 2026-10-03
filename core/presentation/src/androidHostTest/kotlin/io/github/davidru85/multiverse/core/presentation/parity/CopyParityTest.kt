package io.github.davidru85.multiverse.core.presentation.parity

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-036`, the resource half — copy parity (`REQ-UX-008`, `AC-REQ-UX-008-1`, `DEC-020`): for
 * each locale, every canonical key exists exactly once in the Android `strings.xml` and the Apple
 * `Localizable.strings`, nothing else does, and the two platforms carry the same value once each
 * format's escapes and placeholders are read. English is never compared with Spanish.
 *
 * These are controlled inputs under `copy-parity/` — a conforming control and mutations of it, each
 * run through the real parsers and comparison. They prove detection, not the apps' parity: the real
 * resource folders are passed to the same [CopyParity.verify] by `TASK-013` (Android) and `TASK-060`
 * (Apple), with `CopyKeys.all` as the canonical list (`CONF-70`).
 */
class CopyParityTest {
    private val canonical =
        setOf("error_title", "action_retry", "status_alive", "value_unknown", "empty_search_message", "probe_escapes", "probe_count")

    private val conforming = File(requireNotNull(javaClass.classLoader.getResource("copy-parity/conforming")).toURI())
    private val workspace: File = Files.createTempDirectory("copy-parity").toFile()

    @AfterTest
    fun release() {
        workspace.deleteRecursively()
    }

    /** A copy of the conforming control with [mutate] applied to one file, as a resource tree on disk. */
    private fun mutated(
        file: String,
        mutate: (String) -> String,
    ): File {
        conforming.copyRecursively(workspace, overwrite = true)
        val target = File(workspace, file)
        target.writeText(mutate(target.readText()))
        return workspace
    }

    private fun verify(root: File) =
        CopyParity.verify(canonical, androidResources = File(root, "android"), appleResources = File(root, "ios"))

    @Test
    fun `TEST-UNIT-036 given_the_conforming_control_when_verified_then_no_issue_is_reported`() {
        assertEquals(emptyList(), verify(conforming), "TEST-UNIT-036: escapes and placeholders read alike; en and es differ by design")
    }

    @Test
    fun `TEST-UNIT-036 given_a_key_missing_from_one_platform_locale_when_verified_then_it_is_reported_there`() {
        val root =
            mutated("android/values-es/strings.xml") {
                it
                    .lines()
                    .filterNot { line ->
                        "\"status_alive\"" in line
                    }.joinToString("\n")
            }

        assertEquals(listOf(ParityIssue.MissingKey(Platform.ANDROID, "es", "status_alive")), verify(root))
    }

    @Test
    fun `TEST-UNIT-036 given_a_key_outside_the_canonical_list_when_verified_then_it_is_an_extra_key`() {
        val root = mutated("ios/en.lproj/Localizable.strings") { "$it\n\"invented_key\" = \"Invented\";\n" }

        assertEquals(listOf(ParityIssue.ExtraKey(Platform.APPLE, "en", "invented_key")), verify(root))
    }

    @Test
    fun `TEST-UNIT-036 given_a_value_that_diverges_within_a_locale_when_verified_then_it_is_reported_with_both_values`() {
        val root = mutated("ios/es.lproj/Localizable.strings") { it.replace("\"Reintentar\"", "\"Volver a intentar\"") }

        assertEquals(
            listOf(ParityIssue.DivergentValue("es", "action_retry", android = "Reintentar", apple = "Volver a intentar")),
            verify(root),
        )
    }

    @Test
    fun `TEST-UNIT-036 given_a_placeholder_of_another_kind_when_verified_then_the_value_diverges`() {
        val root = mutated("ios/en.lproj/Localizable.strings") { it.replace("“%1\$@”", "“%1\$ld”") }

        val issue = verify(root).single()

        assertEquals(
            "empty_search_message",
            (issue as ParityIssue.DivergentValue).key,
            "TEST-UNIT-036: text and number placeholders differ",
        )
    }

    @Test
    fun `TEST-UNIT-036 given_a_duplicated_key_when_verified_then_it_is_reported`() {
        val root =
            mutated("android/values/strings.xml") {
                it.replace("</resources>", "    <string name=\"action_retry\">Retry</string>\n</resources>")
            }

        assertEquals(listOf(ParityIssue.DuplicateKey(Platform.ANDROID, "en", "action_retry")), verify(root))
    }

    @Test
    fun `TEST-UNIT-036 given_a_missing_locale_file_when_verified_then_the_resource_is_reported_missing`() {
        conforming.copyRecursively(workspace, overwrite = true)
        File(workspace, "ios/es.lproj/Localizable.strings").delete()

        assertEquals(listOf(ParityIssue.MissingResource(Platform.APPLE, "es")), verify(workspace))
    }
}
