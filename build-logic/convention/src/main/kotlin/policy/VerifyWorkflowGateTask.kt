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
 * `TEST-UNIT-044` — the workflow configuration carries the blocking required set
 * (`AC-REQ-NFR-011-1`; `TESTING.md` §14.2).
 *
 * The guard is fail-closed: a missing job, a missing runner, an omitted check, a neutralised check
 * or an unpinned action each fail the task with the file and line that carries the problem.
 */
abstract class VerifyWorkflowGateTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workflows: ConfigurableFileCollection

    /**
     * The Xcode projects under `iosApp/` the restoration tripwire reads (`DEC-083`): declared so a new
     * application target is part of what the task inspects, never an untracked file it happens to see.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val iosProjectFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val findings = WorkflowGateGuard.scan(workflows.files, root)
            .sortedWith(compareBy({ it.path }, { it.line }, { it.reason }))
        if (findings.isEmpty()) {
            logger.lifecycle("verifyWorkflowGate passed: the required-check set is present and blocking (TEST-UNIT-044).")
            return
        }
        val rendered = findings.joinToString("\n") { finding ->
            "  TEST-UNIT-044: ${finding.path}:${finding.line}: ${finding.reason}"
        }
        throw IllegalStateException(
            "verifyWorkflowGate found ${findings.size} workflow configuration problem(s):\n$rendered\n" +
                "The rules are owned by docs/TESTING.md 14.2 and docs/CONTRIBUTING.md 5.3; the repository root is `${root.name}`.",
        )
    }
}
