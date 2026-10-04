package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `TEST-UNIT-028` — no microphone or speech permission in either shipped app (`REQ-SEC-004`,
 * `AC-REQ-SEC-004-1`).
 *
 * Every manifest and every plist under the repository is an input, so a new source set or a future
 * `iosApp/` target is scanned the moment it lands; the shipped manifest's own presence is checked in
 * the task action, not assumed from the input set.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class VerifyNoMicSpeechPermissionTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifests: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val plists: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val violations =
            PermissionAbsencePolicy
                .scan(manifests.files, plists.files, root)
                .sortedWith(compareBy({ it.location }, { it.reason }))
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The microphone and speech permission audit failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle(
            "verifyNoMicSpeechPermission passed: ${manifests.files.size} manifest(s) and ${plists.files.size} plist(s) " +
                "carry no microphone or speech entry (${PermissionAbsencePolicy.TEST_ID}).",
        )
    }
}
