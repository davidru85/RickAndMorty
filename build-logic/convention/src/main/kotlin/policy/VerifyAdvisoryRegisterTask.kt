package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `TEST-UNIT-030` — the advisory register is complete or legitimately empty (`REQ-SEC-006`,
 * `AC-REQ-SEC-006-1`).
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class VerifyAdvisoryRegisterTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val securityDocument: RegularFileProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val violations = AdvisoryRegisterPolicy.scan(securityDocument.get().asFile, root).sortedBy { it.toString() }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The advisory register check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle(
            "verifyAdvisoryRegister passed: every register row carries the 12 columns of SECURITY.md 11.2 " +
                "(${AdvisoryRegisterPolicy.TEST_ID}).",
        )
    }
}
