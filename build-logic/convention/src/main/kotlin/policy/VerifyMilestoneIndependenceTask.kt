package io.github.davidru85.multiverse.buildlogic.policy

import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

/**
 * `TEST-UNIT-019` — the Android milestone assembles with the iOS app absent
 * (`REQ-PLAT-004`, `AC-REQ-PLAT-004-1`, `DEC-040`, `TASK-050`).
 *
 * The criterion is not "the build succeeded while an iOS target happened to be missing"; it is that
 * the Android deliverable is independent of the iOS one. So this task asserts the property from the
 * **artifact** in both directions:
 *
 * 1. The release APK carries no Kotlin/Native or iOS-interop payload — no `iosApp` asset, no
 *    `:core:ios` framework, no `kotlin-native` binary — because an Android artifact that shipped one
 *    would mean the iOS work had become a runtime dependency of the Android app.
 * 2. The Android build graph reaches no module that exists only for iOS. The task is given the set of
 *    modules whose sole purpose is the Apple target, and fails if any of them is a dependency of
 *    `:androidApp` in a configuration that reaches the release artifact.
 *
 * The second half is what makes the check more than a filename scan: a future `:core:ios` edge added
 * to the app in any configuration would be invisible in the first half until it produced output, but
 * it is exactly the regression `AC-REQ-PLAT-004-1` exists to prevent.
 */
public abstract class VerifyMilestoneIndependenceTask : DefaultTask(), VerificationTask {
    /** The release APK the build produces. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val releaseApk: RegularFileProperty

    /**
     * The dependency names `:androidApp` declares in its release configurations. A module that exists
     * only for iOS appears here if the app depends on it.
     */
    @get:Input
    public abstract val androidReleaseEdges: ListProperty<String>

    /** The modules whose only purpose is the Apple target. */
    @get:Input
    public abstract val iosOnlyModules: ListProperty<String>

    /** Where the observed evidence is written. */
    @get:OutputFile
    public abstract val report: RegularFileProperty

    init {
        group = "verification"
        description =
            "TEST-UNIT-019: the Android milestone assembles with the iOS app absent — the release " +
                "artifact carries no iOS payload and no Android configuration reaches an iOS-only " +
                "module (AC-REQ-PLAT-004-1, DEC-040)."
    }

    @TaskAction
    fun verify() {
        val apk = releaseApk.get().asFile
        val violations = mutableListOf<String>()

        // 1. The artifact carries no iOS payload.
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                if (IOS_PAYLOAD_MARKERS.any { name.contains(it, ignoreCase = true) }) {
                    violations +=
                        "the release APK carries `$name`, which is iOS/Kotlin-Native payload; the " +
                            "Android deliverable must be independent of the iOS one (AC-REQ-PLAT-004-1)"
                }
            }
        }

        // 2. No Android configuration reaches a module that exists only for iOS.
        val edges = androidReleaseEdges.get().map { it.trim() }.filter { it.isNotEmpty() }
        val iosOnly = iosOnlyModules.get().map { it.trim() }.filter { it.isNotEmpty() }
        val reached = edges.filter { edge -> iosOnly.any { edge == it || edge.endsWith(":$it") } }
        reached.forEach { edge ->
            violations +=
                "`:androidApp`'s release configuration reaches `$edge`, a module that exists only for " +
                    "the Apple target; `REQ-PLAT-004` requires the Android milestone to build with no " +
                    "iOS artifact at all"
        }

        val evidence =
            buildString {
                appendLine("TEST-UNIT-019 — milestone independence (AC-REQ-PLAT-004-1, DEC-040)")
                appendLine("release artifact: ${apk.name} (${apk.length()} bytes)")
                appendLine("entries scanned: ${ZipFile(apk).use { it.size() }}")
                appendLine("iOS-only modules declared: ${iosOnly.joinToString(", ").ifEmpty { "(none)" }}")
                appendLine("Android release edges inspected: ${edges.size}")
                appendLine("violations: ${violations.size}")
                violations.forEach { appendLine("  - $it") }
            }
        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(evidence)

        check(violations.isEmpty()) {
            "TEST-UNIT-019 failed:\n" + violations.joinToString("\n") { "  $it" } +
                "\nThe evidence report is at ${reportFile.absolutePath}."
        }
    }

    /** Path fragments only an iOS or Kotlin/Native payload can produce. */
    private companion object {
        val IOS_PAYLOAD_MARKERS =
            listOf(
                "iosapp/",
                "core/ios",
                "core-ios",
                "kotlin-native",
                "kotlinnative",
                ".framework/",
                "libbackend.a",
                "libclang_rt",
            )
    }
}
