package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-028` — no microphone or speech permission in either shipped app (`REQ-SEC-004`,
 * `AC-REQ-SEC-004-1`).
 *
 * The audit scans every manifest in the repository for `RECORD_AUDIO` and every `Info.plist` for a
 * speech or microphone usage description. The shipped manifest must be present for a clean run to
 * mean anything, so its absence fails closed; `iosApp/` does not exist yet, and the empty plist set
 * is a pass rather than an error.
 */
class PermissionAbsenceTest {

    private fun doc(vararg lines: String): String = lines.joinToString("\n") + "\n"

    private val shippedManifest =
        doc(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">",
            "    <uses-permission android:name=\"android.permission.INTERNET\" />",
            "</manifest>",
        )

    private fun scan(manifest: String = shippedManifest, plists: Map<String, String> = emptyMap()): List<Violation> {
        val root = kotlin.io.path.createTempDirectory("permissions").toFile()
        val manifestFile =
            File(root, PermissionAbsencePolicy.SHIPPED_MANIFEST).apply {
                parentFile.mkdirs()
                writeText(manifest)
            }
        val plistFiles = plists.map { (path, content) -> File(root, path).apply { parentFile.mkdirs(); writeText(content) } }
        return PermissionAbsencePolicy.scan(listOf(manifestFile), plistFiles, root)
    }

    @Test
    fun `TEST-UNIT-028 the shipped manifest with only INTERNET passes`() {
        assertEquals(emptyList(), scan().map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-028 a RECORD_AUDIO entry is reported`() {
        val findings = scan(shippedManifest.replace("android.permission.INTERNET", PermissionAbsencePolicy.RECORD_AUDIO))

        assertEquals(1, findings.size, "one forbidden permission is one finding: $findings")
        assertTrue(findings.single().location.endsWith("AndroidManifest.xml:3"), findings.single().location)
    }

    @Test
    fun `TEST-UNIT-028 a speech usage description in an Info plist is reported`() {
        val findings =
            scan(
                plists =
                    mapOf(
                        "iosApp/Info.plist" to
                            doc("<key>NSSpeechRecognitionUsageDescription</key>", "<string>Voice search</string>"),
                        "iosApp/Microphone.plist" to doc("<key>NSMicrophoneUsageDescription</key>", "<string>Voice search</string>"),
                    ),
            )

        assertEquals(2, findings.size, "both usage descriptions are findings: $findings")
        assertTrue(findings.all { it.reason.contains("REQ-SEC-004") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-028 a missing shipped manifest fails closed`() {
        val root = kotlin.io.path.createTempDirectory("permissions").toFile()

        val findings = PermissionAbsencePolicy.scan(emptyList(), emptyList(), root)

        assertTrue(findings.any { it.reason.contains("missing") }, findings.map { it.toString() }.toString())
    }
}
