package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import org.gradle.api.GradleException

/**
 * The SDK's `aapt2`, shared by the checks that read a built APK (`TEST-UNIT-018`, `TEST-UNIT-028`).
 *
 * Only the selected executable is a task input, so inspecting an APK never snapshots the entire SDK
 * installation; a missing build-tools installation fails the requesting check rather than skipping it.
 */
internal object Aapt2 {
    const val EXECUTABLE = "aapt2"

    /**
     * The newest installed build-tools executable.
     *
     * @param sdkRoot the SDK root holding `build-tools/`.
     * @param requester the task and test id named in the failure, e.g. `verifySdkLevels` and `TEST-UNIT-018`.
     */
    fun locate(sdkRoot: File, requester: String, testId: String): File {
        val buildTools = File(sdkRoot, "build-tools")
        val candidate =
            buildTools
                .listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.map { File(it, EXECUTABLE) }
                ?.firstOrNull { it.isFile }
        return candidate
            ?: throw GradleException(
                "$requester needs `$EXECUTABLE` under `${buildTools.path}` ($testId); " +
                    "a missing Android build-tools installation fails the check rather than skipping it",
            )
    }
}
