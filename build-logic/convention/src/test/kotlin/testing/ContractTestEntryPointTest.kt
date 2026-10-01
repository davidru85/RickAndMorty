package testing

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TEST-CONTRACT-*` non-emptiness (`TASK-026`, `DEC-073`; `AC-REQ-NFR-011-2`).
 *
 * The entry point's whole value is that it refuses a green run with nothing behind it: a filtered
 * test task that never starts is `NO-SOURCE`/`SKIPPED`, which is green, so the aggregate carries its
 * own verification over the reports. These fixtures pin that behaviour in both directions, because
 * the clean repository cannot show it — it has no contract case yet by design.
 */
class ContractTestEntryPointTest {

    private val repositoryRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "build-logic/settings.gradle.kts").isFile }

    /** A fixture module with the contract entry point applied and one test case. */
    private fun fixture(caseId: String, suffix: String): File {
        val caseName = "$caseId $suffix" // the id stays the first token (TESTING.md 13.2)
        val dir = kotlin.io.path.createTempDirectory("contract-entry").toFile()
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories { mavenCentral() }
            }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "contract-entry"
            """.trimIndent() + "\n",
        )
        File(dir, "gradle").mkdirs()
        File(dir, "gradle/libs.versions.toml").writeText("[versions]\nfixture = \"1.0.0\"\n")
        // Kotlin, because `TESTING.md` §13.2 puts the exact id (with hyphens) first in the test
        // name, and only a Kotlin backticked name can carry it verbatim.
        File(dir, "src/test/kotlin/cases").mkdirs()
        File(dir, "src/test/kotlin/cases/Case.kt").writeText(
            """
            package cases

            class Case {
                @org.junit.Test
                fun `$caseName`() { }
            }
            """.trimIndent() + "\n",
        )
        File(dir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.4.20"
                id("multiverse.contract.tests")
            }
            kotlin { jvmToolchain(17) }

            repositories { mavenCentral() }
            dependencies { testImplementation("junit:junit:4.13.2") }

            // The real module wires the filtered test task into the verification; the fixture does
            // the same with the plain `test` task so the report the verifier reads is produced.
            // Unfiltered on purpose: the verifier is the component under test, and a filtered
            // task that discovers nothing would fail first and mask the diagnostic.
            tasks.named("verifyContractCases") {
                mustRunAfter("test")
                dependsOn("test")
                outputs.upToDateWhen { false }
            }
            tasks.named("verifyContractCases") {
                mustRunAfter("test")
                dependsOn("test")
                outputs.upToDateWhen { false }
            }
            """.trimIndent() + "\n",
        )
        // The reports location the verifier reads.
        File(dir, "build.gradle.kts").appendText(
            """
            tasks.named<io.github.davidru85.multiverse.buildlogic.testing.VerifyContractCasesTask>(
                "verifyContractCases",
            ) {
                reports.from(layout.buildDirectory.dir("test-results/test").map { fileTree(it) { include("*.xml") } })
            }
            """.trimIndent() + "\n",
        )
        return dir
    }

    private fun gradle(dir: File, vararg args: String): org.gradle.testkit.runner.GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withArguments(*args, "--stacktrace")
        .forwardOutput()

    @Test
    fun `the entry point fails with an explicit diagnostic when no contract case exists`() {
        val dir = fixture("TEST-UNIT-001", "given_a_unit_case_when_run_then_it_is_not_contract")
        try {
            val result = gradle(dir, "verifyContractCases").buildAndFail()
            assertTrue(
                result.output.contains("executed no TEST-CONTRACT- case"),
                "the diagnostic must name the missing case family:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the entry point passes and counts the case when a contract case executed`() {
        val dir = fixture("TEST-CONTRACT-001", "given_a_fixture_when_replayed_then_it_decodes")
        try {
            val result = gradle(dir, "verifyContractCases").build()
            assertTrue(
                result.output.contains("executed 1 contract case(s)"),
                "a case whose name carries the id must be counted:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
