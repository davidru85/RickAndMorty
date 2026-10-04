package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * `TEST-UNIT-028`, the artifact half (`REQ-SEC-004`, `AC-REQ-SEC-004-1`, `TASK-048`).
 *
 * The criterion is about the **shipped app**, and a library manifest merged at build time can add a
 * permission no source manifest declares. This task therefore reads the release APK's own permission
 * table with the platform's `aapt2 dump permissions` and fails when `RECORD_AUDIO` is in it; the source
 * half (`verifyNoMicSpeechPermission`) keeps covering every manifest and plist in the repository.
 * The observed table is written to a report a reviewer can read.
 */
public abstract class VerifyShippedPermissionsTask : DefaultTask(), VerificationTask {
    /** The release APK, the artifact that ships. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val apk: RegularFileProperty

    /** The SDK root holding `build-tools/`; the Android plugin's own value. */
    @get:Internal
    public abstract val sdkDirectory: DirectoryProperty

    /** Only the selected executable affects the dump; the rest of the SDK does not. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    public val aapt2Executable: Provider<File>
        get() = sdkDirectory.map { Aapt2.locate(it.asFile, "verifyShippedPermissions", PermissionAbsencePolicy.TEST_ID) }

    /** Where the observed permission table is written. */
    @get:OutputFile
    public abstract val report: RegularFileProperty

    /** The repository root, used only to render the APK's location. */
    @get:Internal
    public abstract val rootDirectory: DirectoryProperty

    /** Gradle's process runner, injected so the task stays configuration-cache compatible. */
    @get:Inject
    public abstract val execOperations: ExecOperations

    init {
        group = "verification"
        description =
            "TEST-UNIT-028 (artifact): the release APK's merged permission table carries no RECORD_AUDIO, " +
                "read with aapt2 (AC-REQ-SEC-004-1)."
    }

    @TaskAction
    public fun verify() {
        val file = apk.get().asFile
        val aapt2 = aapt2Executable.get()
        val output = java.io.ByteArrayOutputStream()
        execOperations.exec {
            commandLine(aapt2.absolutePath, "dump", "permissions", file.absolutePath)
            standardOutput = output
            isIgnoreExitValue = true
        }
        val dump = output.toString(Charsets.UTF_8)
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(dump)
        }
        val violations = PermissionAbsencePolicy.scanArtifact(dump, file.location(rootDirectory.get().asFile))
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The shipped-APK permission audit failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle(
            "verifyShippedPermissions passed: ${file.name} requests no microphone permission (${PermissionAbsencePolicy.TEST_ID}).",
        )
    }
}
