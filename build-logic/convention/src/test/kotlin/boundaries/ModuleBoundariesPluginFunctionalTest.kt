package io.github.davidru85.multiverse.buildlogic.boundaries

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-043` R16 through the **real** `verifyModuleBoundaries` task (`TASK-091`).
 *
 * The unit tests evaluate a snapshot directly. These run Gradle against a fixture build that
 * applies the real plugin by id, so they also cover plugin registration, the `check` wiring, the
 * configuration snapshot and the configuration cache — the parts a pure evaluator cannot prove.
 *
 * The fixture includes the repository's own `build-logic`, so the plugin under test is the code in
 * this commit rather than a published artifact.
 */
class ModuleBoundariesPluginFunctionalTest {

    /** The repository root, found by walking up from the test's working directory. */
    private val repositoryRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "build-logic/settings.gradle.kts").isFile }

    /**
     * A minimal fixture build that applies the real plugin by id. The root project (`:`) is the
     * fixture itself, never an `include` entry, and every other path is a stub project with an
     * empty build script.
     */
    private fun fixture(vararg paths: String): File {
        val dir = kotlin.io.path.createTempDirectory("mc-fixture").toFile()
        val included = paths.filterNot { it == ":" }.distinct()
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            rootProject.name = "fixture"
            include(${included.joinToString(", ") { "\"$it\"" }})
            """.trimIndent() + "\n",
        )
        File(dir, "build.gradle.kts").writeText(
            """
            plugins { id("multiverse.module.boundaries") }
            """.trimIndent() + "\n",
        )
        included.forEach { path ->
            val project = File(dir, path.trimStart(':').replace(':', '/'))
            project.mkdirs()
            File(project, "build.gradle.kts").writeText("")
            // A feature fixture needs its own route declaration, because S1 is a real rule that
            // the accepted topology must satisfy; a stub without one would fail for the wrong
            // reason and mask what the test is asserting.
            ModuleSet.FEATURES.firstOrNull { it == path }?.let { feature ->
                val name = feature.removePrefix(":feature:").split('-').joinToString("") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
                val route = project.resolve(
                    "src/commonMain/kotlin/io/github/davidru85/multiverse/feature/" +
                        feature.removePrefix(":feature:").replace("-", "") + "/navigation/$name.kt",
                )
                route.parentFile.mkdirs()
                route.writeText(
                    "package io.github.davidru85.multiverse.feature." +
                        feature.removePrefix(":feature:").replace("-", "") + ".navigation\n\n" +
                        "import kotlinx.serialization.Serializable\n\n@Serializable\npublic data object $name\n",
                )
            }
        }
        return dir
    }

    private fun runner(dir: File, vararg args: String) = GradleRunner.create()
        .withProjectDir(dir)
        .withArguments(*args, "--configuration-cache", "--stacktrace")
        .forwardOutput()

    @Test
    fun `the plugin registers the task and a complete fixture passes`() {
        val dir = fixture(*ModuleSet.REQUIRED.toTypedArray(), *ModuleSet.CONTAINERS.toTypedArray())
        try {
            val result = runner(dir, "verifyModuleBoundaries").build()
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyModuleBoundaries")?.outcome)
            assertTrue(
                result.output.contains("R1\u2013R16"),
                "the success message names the enforced rule range",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a missing required module fails through the real task`() {
        val without = (ModuleSet.REQUIRED - ":feature:settings").toTypedArray()
        val dir = fixture(*without, *ModuleSet.CONTAINERS.toTypedArray())
        try {
            val result = runner(dir, "verifyModuleBoundaries").buildAndFail()
            assertEquals(TaskOutcome.FAILED, result.task(":verifyModuleBoundaries")?.outcome)
            assertTrue(
                result.output.contains("R16") && result.output.contains(":feature:settings"),
                "the task fails with R16 naming the absent module",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an unrecognised feature path fails through the real task`() {
        val dir = fixture(*ModuleSet.REQUIRED.toTypedArray(), ":feature:invented", *ModuleSet.CONTAINERS.toTypedArray())
        try {
            val result = runner(dir, "verifyModuleBoundaries").buildAndFail()
            assertTrue(
                result.output.contains("R13") && result.output.contains(":feature:invented"),
                "an invented feature is unknown and fails closed",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the check task depends on the verification task`() {
        val dir = fixture(*ModuleSet.REQUIRED.toTypedArray(), *ModuleSet.CONTAINERS.toTypedArray())
        try {
            val result = runner(dir, "check").build()
            assertEquals(
                TaskOutcome.SUCCESS,
                result.task(":verifyModuleBoundaries")?.outcome,
                "the root check pulls the boundary task",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a second run reuses the configuration cache`() {
        val dir = fixture(*ModuleSet.REQUIRED.toTypedArray(), *ModuleSet.CONTAINERS.toTypedArray())
        try {
            runner(dir, "verifyModuleBoundaries").build()
            val second = runner(dir, "verifyModuleBoundaries").build()
            assertTrue(
                second.output.contains("Reusing configuration cache"),
                "the task stays configuration-cache compatible",
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
