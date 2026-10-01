package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `TEST-UNIT-024` — the live-host guard (`TESTING.md` §4.1, `REQ-NFR-005`).
 *
 * A test that names the real API host will eventually contact it; the repository's rule is that no
 * test source set outside the scheduled `contract-live` source set may do so, because the remote
 * boundary in every ordinary test is Ktor `MockEngine` or a fake seam. The task is fail-closed and
 * reports only a repository-relative path and a line, never the matched text.
 */
abstract class VerifyNoLiveHostsTask : DefaultTask() {

    /** Every test Kotlin source the guard scans. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    /** The repository root, used only to render repository-relative paths in diagnostics. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val roots = testSources.files.map { it.parentFile ?: it }.distinct()
        val findings = LiveHostGuard.scan(roots).sortedWith(compareBy({ it.path }, { it.line }))
        if (findings.isEmpty()) return

        val rendered = findings.joinToString("\n") { finding ->
            "  TEST-UNIT-024: ${finding.path}:${finding.line}: a test source set names the live host " +
                "`${finding.host}`; serve it from a committed fixture through Ktor MockEngine instead " +
                "(TESTING.md 4.1; only the scheduled `contract-live` source set may reach the live API)"
        }
        throw IllegalStateException(
            "verifyNoLiveHosts found ${findings.size} live-host literal(s) in test sources:\n$rendered\n" +
                "The repository root is `${root.name}`; no absolute path is printed.",
        )
    }
}
