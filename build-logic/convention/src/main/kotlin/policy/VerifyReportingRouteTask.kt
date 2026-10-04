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
 * `TEST-UNIT-031` — the vulnerability-reporting route is stated identically in `SECURITY.md` §10 and
 * `CONTRIBUTING.md` §10 (`REQ-SEC-007`, `AC-REQ-SEC-007-1`).
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class VerifyReportingRouteTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val securityDocument: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contributingDocument: RegularFileProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val violations =
            ReportingRoutePolicy
                .scan(securityDocument.get().asFile, contributingDocument.get().asFile, root)
                .sortedBy { it.toString() }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The reporting-route parity check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle(
            "verifyReportingRoute passed: SECURITY.md 10 and CONTRIBUTING.md 10 state the same route " +
                "(${ReportingRoutePolicy.TEST_ID}).",
        )
    }
}
