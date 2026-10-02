package io.github.davidru85.multiverse.buildlogic.boundaries

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-015` through the **real** `verifyDocumentedGate` task (`TASK-103`, `B2-R06`).
 *
 * `GAP-020` recorded that the documented local gate was not substantiated by its verifier: it
 * resolved a documented token by name *or* by path suffix, so the bare `test` of README §9's "All
 * shared and unit tests" row resolved to whichever Android unit-test task happened to match while
 * the shared KMP suites ran not at all, and it compared only each aggregate's direct dependencies.
 *
 * These functional tests run Gradle against a fixture build that applies the real plugin, so they
 * cover the plugin wiring, the graph capture and the parity rule — the parts a pure evaluator
 * cannot prove. Each case is a bypass that used to pass and must now fail.
 */
class DocumentedGatePluginFunctionalTest {

    /** The repository root, found by walking up from the test's working directory. */
    private val repositoryRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "build-logic/settings.gradle.kts").isFile }

    /**
     * A fixture build with the two READMEs and a root `check` whose dependency on the build-logic
     * suite can be seeded on and off.
     */
    private fun fixture(
        englishGateBlock: String,
        spanishGateBlock: String,
        withIncludedBuildDependency: Boolean = true,
    ): File {
        val dir = kotlin.io.path.createTempDirectory("documented-gate").toFile()
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories { gradlePluginPortal(); mavenCentral() }
            }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "fixture"
            """.trimIndent() + "\n",
        )
        File(dir, "gradle").mkdirs()
        File(dir, "gradle/libs.versions.toml").writeText(
            """
            [versions]
            kotlin = "2.4.20"

            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            """.trimIndent() + "\n",
        )
        File(dir, "build.gradle.kts").writeText(
            buildString {
                appendLine("plugins { id(\"multiverse.dependency.policy\") }")
                appendLine("val gateSuite by tasks.registering { }")
                appendLine("tasks.named(\"check\") {")
                if (withIncludedBuildDependency) {
                    appendLine("    dependsOn(gateSuite)")
                }
                appendLine("}")
                appendLine("tasks.register(\"allTests\") { dependsOn(gateSuite) }")
            },
        )
        File(dir, "README.md").writeText(
            """
            # Fixture

            | Task | Command | State |
            | --- | --- | --- |
            <!-- local-gate:begin -->
            $englishGateBlock
            <!-- local-gate:end -->
            """.trimIndent() + "\n",
        )
        File(dir, "README.es.md").writeText(
            """
            # Fixture

            | Tarea | Comando | Estado |
            | --- | --- | --- |
            <!-- local-gate:begin -->
            $spanishGateBlock
            <!-- local-gate:end -->
            """.trimIndent() + "\n",
        )
        // The task reads `docs/CONTRIBUTING.md` too; an empty file keeps the fixture focused.
        File(dir, "docs").mkdirs()
        File(dir, "docs/CONTRIBUTING.md").writeText("# Fixture\n")
        return dir
    }

    /** Runs the guard and returns its output, whether it passed or failed. */
    private fun run(dir: File): String {
        val result =
            GradleRunner.create()
                .withProjectDir(dir)
                .withArguments("verifyDocumentedGate", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .buildAndFail()
        return result.output
    }

    /** A row whose command genuinely exists and selects what it claims. */
    private val validRow = "| All tests | `./gradlew allTests` | Executed 2026-10-02 |"

    /** The suite a "all shared and unit tests" row is proved to reach. */
    private val gateSuitePath = ":gateSuite"

    @Test
    fun `a nonexistent documented task is reported`() {
        val dir = fixture("| All tests | `./gradlew allTestsMissing` | Executed |", "| Todos | `./gradlew allTestsMissing` | Ejecutado |")
        val output = run(dir)
        assertTrue(
            output.contains("registers no task the invocation would select"),
            "GAP-020: a documented command that names no task must fail; got $output",
        )
    }

    @Test
    fun `a removed dependency that carried a required suite is reported`() {
        // The row names a task the aggregate used to reach; the aggregate no longer depends on it.
        // This is the `GAP-020` narrowing decided on the EFFECTIVE graph, not on the name.
        val row = "| All tests | `./gradlew allTests` | Executed |"
        val dir = fixture(row, row, withIncludedBuildDependency = false)
        val output = run(dir)
        assertTrue(
            output.contains("does not run") || output.contains("selects no task"),
            "GAP-020: removing the dependency that carried a required suite must fail; got $output",
        )
    }

    @Test
    fun `a row that prints its command rather than running it is reported`() {
        val dir = fixture("| All tests | `echo ./gradlew allTests` | Executed |", "| Todos | `echo ./gradlew allTests` | Ejecutado |")
        val output = run(dir)
        assertTrue(
            output.contains("printing the command rather than executing it"),
            "a row that echoes its command is not evidence that the command ran; got $output",
        )
    }

    @Test
    fun `the two languages must document the same commands`() {
        val dir = fixture(validRow, "| Todos | `./gradlew allTestsSomethingElse` | Ejecutado |")
        val output = run(dir)
        assertTrue(
            output.contains("the two tables must stay aligned"),
            "DEC-047: a command corrected in one language only leaves half the documentation lying; got $output",
        )
    }

    @Test
    fun `aligned tables naming an existing command pass the existence and parity rules`() {
        val dir = fixture(validRow, "| Todos | `./gradlew allTests` | Ejecutado |")
        val output = run(dir)
        // The fixture cannot satisfy the repository's claim table (it names the repository's own
        // suites), so this asserts the fixture-specific rules only.
        assertTrue(
            !output.contains("registers no task the invocation would select"),
            "an existing, aligned documented command must not be reported as missing; got $output",
        )
        assertTrue(
            !output.contains("the two tables must stay aligned"),
            "aligned tables must not raise the parity rule; got $output",
        )
    }
}
