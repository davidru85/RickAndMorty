package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TASK-089`/`GAP-013` — the artifact→validator **wiring**, through the real plugin.
 *
 * The grammar is pinned by `PolicyParsersTest` and the end-to-end failure by the disposable-clone
 * matrix recorded in `PROJECT_LOG.md` (`LOG-0051`). What neither proves automatically is the
 * wiring itself: that every Android artifact family the plugin claims to cover really resolves to a
 * task that depends on `verifyDependencyPins`. A future edit could rename the family regex and the
 * clean repository would still pass.
 *
 * These tests therefore inspect the **task graph** with `--dry-run`: it executes nothing, so the
 * fixture needs no catalog, README or design document, and the assertion is exactly the wiring
 * rather than a side effect. A stub `com.android.application` plugin stands in for the real one,
 * because the policy reacts to the plugin id and the real Android plugin would add an SDK
 * dependency this wiring does not have.
 */
class ArtifactVersionWiringFunctionalTest {

    private val repositoryRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "build-logic/settings.gradle.kts").isFile }

    /** A fixture whose stub Android application plugin is applied and whose artifact tasks exist. */
    private fun fixture(): File {
        val dir = kotlin.io.path.createTempDirectory("artifact-wiring").toFile()
        val stub = File(dir, "android-stub")
        File(stub, "src/main/groovy").mkdirs()
        File(stub, "settings.gradle.kts").writeText("rootProject.name = \"android-stub\"\n")
        File(stub, "build.gradle.kts").writeText(
            """
            plugins { `java-gradle-plugin`; groovy }
            gradlePlugin {
                plugins {
                    register("androidApplication") {
                        id = "com.android.application"
                        implementationClass = "AndroidApplicationStub"
                    }
                }
            }
            """.trimIndent() + "\n",
        )
        File(stub, "src/main/groovy/AndroidApplicationStub.groovy").writeText(
            """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class AndroidApplicationStub implements Plugin<Project> {
                void apply(Project project) { }
            }
            """.trimIndent() + "\n",
        )
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                includeBuild("android-stub")
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "artifact-wiring"
            """.trimIndent() + "\n",
        )
        File(dir, "gradle").mkdirs()
        // Gradle registers `libs` only when the file declares something.
        File(dir, "gradle/libs.versions.toml").writeText("[versions]\nfixture = \"1.0.0\"\n")
        File(dir, "build.gradle.kts").writeText(
            """
            plugins {
                id("multiverse.dependency.policy")
                id("com.android.application")
            }

            // One stub per artifact family the plugin claims to cover, plus a neighbour the family
            // must not capture.
            val families = listOf(
                "assembleDebug", "assembleRelease", "bundleRelease", "packageReleaseBundle",
                "installDebug", "uninstallDebug", "extractRelease", "connectedDebugAndroidTest",
            )
            families.forEach { name -> tasks.register(name) { } }
            tasks.register("reportVersion") { }
            """.trimIndent() + "\n",
        )
        return dir
    }

    private fun dryRun(dir: File, vararg args: String): String = GradleRunner.create()
        .withProjectDir(dir)
        .withArguments(*args, "--dry-run", "--stacktrace")
        .forwardOutput()
        .build()
        .output

    @Test
    fun `every Android artifact family resolves through the canonical validator`() {
        val dir = fixture()
        try {
            listOf(
                "assembleDebug", "assembleRelease", "bundleRelease", "packageReleaseBundle",
                "installDebug", "uninstallDebug", "extractRelease", "connectedDebugAndroidTest",
            ).forEach { name ->
                val graph = dryRun(dir, name)
                assertTrue(
                    graph.contains(":verifyDependencyPins"),
                    "`$name` must depend on the canonical validation; observed graph:\n$graph",
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an unrelated task is not captured by the artifact family`() {
        val dir = fixture()
        try {
            val graph = dryRun(dir, "reportVersion")
            assertTrue(
                !graph.contains(":verifyDependencyPins"),
                "`reportVersion` is not an artifact path and must stay independent:\n$graph",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the aggregation task owns the validator so root check covers it`() {
        val dir = fixture()
        try {
            val graph = dryRun(dir, "verifyDependencyPolicy")
            assertTrue(graph.contains(":verifyDependencyPins"), "the aggregate depends on the validator:\n$graph")
        } finally {
            dir.deleteRecursively()
        }
    }
}
