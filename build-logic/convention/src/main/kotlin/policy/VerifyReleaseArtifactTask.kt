package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

/**
 * `TEST-UNIT-033`, the artifact half (`AC-REQ-OBS-002-1`, `DEC-088`, `TASK-044`).
 *
 * The boundary rules already decide the graph: `:core:diagnostics` may be linked from a `debug*`
 * configuration only, and the release closure of the shell never reaches it. This task answers the
 * different question the criterion actually asks — is the diagnostic API **absent from the artifact
 * we ship** — by reading the real APKs' dex payloads.
 *
 * It fails when the release output carries the diagnostic package or the panel host, and it fails
 * when the debug output does **not** carry them, so a configuration that silently drops the feature
 * from both variants cannot pass by removing the thing under test. Both APKs are inputs, so a change
 * to either re-runs the check, and the observed evidence is written to a report a reviewer can read.
 */
public abstract class VerifyReleaseArtifactTask : DefaultTask(), VerificationTask {
    /** The release APK the build produces. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val releaseApk: RegularFileProperty

    /** The debug APK, which must carry the panel (otherwise the check proves nothing). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val debugApk: RegularFileProperty

    /** Where the observed evidence is written. */
    @get:OutputFile
    public abstract val report: RegularFileProperty

    init {
        group = "verification"
        description =
            "TEST-UNIT-033 (artifact): the diagnostic API and the panel host are absent from the " +
                "real release APK and present in the debug one (AC-REQ-OBS-002-1, DEC-088)."
    }

    @TaskAction
    public fun verify() {
        val release = releaseApk.get().asFile
        val debug = debugApk.get().asFile
        val releaseEntries = dexEntries(release)
        val debugEntries = dexEntries(debug)

        val findings = mutableListOf<String>()
        DIAGNOSTIC_TYPES.forEach { descriptor ->
            val needle = descriptor.toByteArray(Charsets.UTF_8)
            val inRelease = releaseEntries.any { it.containsBytes(needle) }
            val inDebug = debugEntries.any { it.containsBytes(needle) }
            if (inRelease) {
                findings += "the release artifact carries `$descriptor`, which only a debug build may link (DEC-088)"
            }
            if (!inDebug) {
                findings += "the debug artifact does not carry `$descriptor`, so the check would prove nothing"
            }
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildList {
                add("release=${release.name} dexBytes=${releaseEntries.sumOf { it.size }}")
                add("debug=${debug.name} dexBytes=${debugEntries.sumOf { it.size }}")
                DIAGNOSTIC_TYPES.forEach { descriptor ->
                    val needle = descriptor.toByteArray(Charsets.UTF_8)
                    add(
                        "$descriptor release=${releaseEntries.any { it.containsBytes(needle) }} " +
                            "debug=${debugEntries.any { it.containsBytes(needle) }}",
                    )
                }
            }.joinToString("\n") + "\n",
        )

        if (findings.isNotEmpty()) {
            throw GradleException(
                "verifyReleaseArtifact (TEST-UNIT-033, AC-REQ-OBS-002-1) failed:\n" +
                    findings.joinToString("\n") { "  - $it" },
            )
        }
    }

    /**
     * The dex payloads of the APK.
     *
     * The check reads the artifact itself rather than the module graph, because the criterion is about
     * what ships. These types' descriptors are plain ASCII, and a dex file lists every class it
     * defines in its string table, so a match on the payload is exactly the question "does this build
     * carry the type". The task proves itself from both sides: present in debug, absent in release.
     */
    private fun dexEntries(apk: File): List<ByteArray> =
        ZipFile(apk).use { zip ->
            zip.entries()
                .asSequence()
                .filter { DEX.matches(it.name) }
                .map { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }

    /** Whether [haystack] contains every byte of [needle], in order: Kotlin's `contains` takes one byte. */
    private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private companion object {
        val DEX = Regex("""classes\d*\.dex""")

        /** The two things a release artifact must never carry. */
        val DIAGNOSTIC_TYPES =
            listOf(
                "Lio/github/davidru85/multiverse/core/diagnostics/DiagnosticsRecorder;",
                "Lio/github/davidru85/multiverse/app/debug/DiagnosticsActivity;",
            )
    }
}
