package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
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
 * `TEST-UNIT-018` (`REQ-PLAT-002`, `AC-REQ-PLAT-002-1`): the SDK levels the **artifact** declares —
 * `minSdk` 26 (`DEC-009`), `targetSdk` and `compileSdk` 37.
 *
 * The check reads the built APK with the platform's own `aapt2 dump badging`, so the numbers are the
 * ones a device reads when it decides whether to install the app, and they come from the artifact
 * rather than from the Gradle DSL. That is what catches a merged manifest or an overlay that
 * contradicts the build script, which a DSL-only assertion cannot.
 *
 * The tool is located from `ANDROID_HOME`/`ANDROID_SDK_ROOT` or the project's `local.properties`,
 * exactly as the Android plugin resolves it; a missing toolchain fails the check rather than skipping
 * it, so the verification cannot silently stop happening.
 */
public abstract class VerifySdkLevelsTask : DefaultTask(), VerificationTask {
    /** The APK whose manifest is inspected. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val apk: RegularFileProperty

    /** The SDK root holding `build-tools/`; the Android plugin's own value. */
    @get:org.gradle.api.tasks.Internal
    public abstract val sdkDirectory: org.gradle.api.file.DirectoryProperty

    /** Only the selected executable affects badging; the SDK's NDK/system images do not. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    public val aapt2Executable: org.gradle.api.provider.Provider<File>
        get() = sdkDirectory.map { locateAapt2(it.asFile) }

    /** Where the observed levels are written. */
    @get:OutputFile
    public abstract val report: RegularFileProperty

    init {
        group = "verification"
        description =
            "TEST-UNIT-018: the built APK declares minSdk 26 and targetSdk 37, read with aapt2 " +
                "(AC-REQ-PLAT-002-1)."
    }

    /** Gradle's process runner, injected so the task stays configuration-cache compatible. */
    @get:javax.inject.Inject
    public abstract val execOperations: org.gradle.process.ExecOperations

    @TaskAction
    public fun verify() {
        val file = apk.get().asFile
        val badging = runBadging(file)
        val minSdk = badging.value("minSdkVersion")
        val targetSdk = badging.value("targetSdkVersion")
        val compileSdk = badging.value("compileSdkVersion")

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText("apk=${file.name} minSdk=$minSdk targetSdk=$targetSdk compileSdk=$compileSdk\n")

        val findings = buildList {
            if (minSdk != EXPECTED_MIN_SDK) add("minSdk is $minSdk, but DEC-009 fixes $EXPECTED_MIN_SDK")
            if (targetSdk != EXPECTED_TARGET_SDK) add("targetSdk is $targetSdk, but the catalog pins $EXPECTED_TARGET_SDK")
            if (compileSdk != EXPECTED_COMPILE_SDK) add("compileSdk is $compileSdk, but the catalog pins $EXPECTED_COMPILE_SDK")
        }
        if (findings.isNotEmpty()) {
            throw GradleException(
                "verifySdkLevels (TEST-UNIT-018, AC-REQ-PLAT-002-1) failed:\n" + findings.joinToString("\n") { "  - $it" },
            )
        }
    }

    /** The `key:'value'` pairs of `aapt2 dump badging`, so the parser is a reader, not an inference. */
    private class Badging(
        private val pairs: Map<String, String>,
    ) {
        fun value(key: String): Int = pairs[key]?.toIntOrNull() ?: -1
    }

    private fun runBadging(apk: File): Badging {
        val aapt2 = aapt2Executable.get()
        val output = java.io.ByteArrayOutputStream()
        val result =
            execOperations.exec {
                commandLine(aapt2.absolutePath, "dump", "badging", apk.absolutePath)
                standardOutput = output
                isIgnoreExitValue = true
            }
        val text = output.toString(Charsets.UTF_8)
        val pairs =
            text
                .lines()
                .mapNotNull { line: String ->
                    val match = PAIR.find(line) ?: return@mapNotNull null
                    match.groupValues[1] to match.groupValues[2]
                }.toMap()
        if (pairs.isEmpty()) {
            throw GradleException(
                "verifySdkLevels could not read `$apk` with `$aapt2` (exit ${result.exitValue}): " +
                    "a missing or unreadable artifact fails the check rather than passing it",
            )
        }
        return Badging(pairs)
    }

    private companion object {
        /** The newest installed build-tools executable, without snapshotting the entire SDK. */
        fun locateAapt2(root: File): File {
            val buildTools = File(root, "build-tools")
            val candidate =
                buildTools
                    .listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.map { File(it, AAPT2) }
                    ?.firstOrNull { it.isFile }
            return candidate
                ?: throw GradleException(
                    "verifySdkLevels needs `$AAPT2` under `${buildTools.path}` (TEST-UNIT-018); " +
                        "a missing Android build-tools installation fails the check rather than skipping it",
                )
        }

        const val AAPT2 = "aapt2"
        const val EXPECTED_MIN_SDK = 26
        const val EXPECTED_TARGET_SDK = 37
        const val EXPECTED_COMPILE_SDK = 37
        /**
         * A key/value pair from `aapt2 dump badging`: `minSdkVersion:'26'` and `compileSdkVersion='37'`
         * both match, while `compileSdkVersionCodename` cannot shadow `compileSdkVersion` because the
         * key must be followed by the delimiter.
         */
        val PAIR = Regex("""(minSdkVersion|targetSdkVersion|compileSdkVersion)['"]?\s*[:=]\s*['"]?([0-9]+)""")
    }
}
