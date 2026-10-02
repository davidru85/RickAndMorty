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

    // --- TASK-100 (B2-R03, GAP-017): the outcome, per target --------------------------------

    /**
     * A fixture whose single case carries `body` inside the `<testcase>` element, written as a
     * committed JUnit report. The verifier reads reports, so a report is what a bypass has to fake.
     */
    private fun reportFixture(target: String, caseName: String, body: String): File {
        val dir = kotlin.io.path.createTempDirectory("contract-report").toFile()
        val reports = File(dir, "build/test-results/$target")
        reports.mkdirs()
        File(reports, "TEST-cases.Case.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="cases.Case" tests="1" skipped="0" failures="0" errors="0">
              <testcase name="$caseName" classname="cases.Case">$body</testcase>
            </testsuite>
            """.trimIndent() + "\n",
        )
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories { mavenCentral() }
            }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "contract-report"
            """.trimIndent() + "\n",
        )
        // Only the verifier runs: the report is committed, so no test task needs to execute.
        File(dir, "build.gradle.kts").writeText(
            """
            plugins { id("multiverse.contract.tests") }

            tasks.named<io.github.davidru85.multiverse.buildlogic.testing.VerifyContractCasesTask>(
                "verifyContractCases",
            ) {
                reports.from(layout.buildDirectory.dir("test-results/$target").map { fileTree(it) { include("*.xml") } })
                targetReportDirectories.set(mapOf("$target" to layout.buildDirectory.dir("test-results/$target").get().asFile.absolutePath))
            }
            """.trimIndent() + "\n",
        )
        return dir
    }

    @Test
    fun `a skipped contract case is not counted as executed`() {
        val dir = reportFixture("test", "TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes", "<skipped/>")
        try {
            val result = gradle(dir, "verifyContractCases", "--no-configuration-cache").buildAndFail()
            assertTrue(
                result.output.contains("skipped") || result.output.contains("executed no TEST-CONTRACT-"),
                "GAP-017: a skipped case must not stand in for an executed one:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a failing contract case is reported as a failure`() {
        val dir =
            reportFixture(
                "test",
                "TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes",
                "<failure message=\"boom\">stack</failure>",
            )
        try {
            val result = gradle(dir, "verifyContractCases", "--no-configuration-cache").buildAndFail()
            assertTrue(
                result.output.contains("failed") || result.output.contains("did not pass"),
                "GAP-017: a failing contract case must be a failure:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a target that produced no report fails even when another target passed`() {
        val dir = reportFixture("testAndroidHostTest", "TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes", "")
        try {
            // The fixture declares only the JVM host target; the entry point claims both, so the
            // native target's absence must be the finding. A total over both would have passed.
            File(dir, "build.gradle.kts").appendText(
                """
                tasks.named<io.github.davidru85.multiverse.buildlogic.testing.VerifyContractCasesTask>(
                    "verifyContractCases",
                ) {
                    targetReportDirectories.set(
                        mapOf(
                            "testAndroidHostTest" to layout.buildDirectory.dir("test-results/testAndroidHostTest").get().asFile.absolutePath,
                            "iosSimulatorArm64Test" to layout.buildDirectory.dir("test-results/iosSimulatorArm64Test").get().asFile.absolutePath,
                        ),
                    )
                }
                """.trimIndent() + "\n",
            )
            val result = gradle(dir, "verifyContractCases", "--no-configuration-cache").buildAndFail()
            assertTrue(
                result.output.contains("iosSimulatorArm64Test"),
                "GAP-017: a missing native report must fail even when the JVM target passed:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- TASK-037 (DEC-090): one entry point per target, registered by the plugin -------------

    /**
     * A JVM fixture whose replay entry point is registered through the plugin's `contractTests`
     * extension, the way `:core:data` registers its Android-host and Apple-simulator entry points.
     * [cases] are test functions written verbatim into one class.
     */
    private fun replayFixture(vararg cases: String): File {
        val dir = kotlin.io.path.createTempDirectory("contract-replay").toFile()
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories { mavenCentral() }
            }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "contract-replay"
            """.trimIndent() + "\n",
        )
        File(dir, "gradle").mkdirs()
        File(dir, "gradle/libs.versions.toml").writeText("[versions]\nfixture = \"1.0.0\"\n")
        File(dir, "src/test/kotlin/cases").mkdirs()
        File(dir, "src/test/kotlin/cases/Case.kt").writeText(
            "package cases\n\nclass Case {\n" + cases.joinToString("\n") + "\n}\n",
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

            contractTests {
                replay("Jvm", "test")
            }
            """.trimIndent() + "\n",
        )
        return dir
    }

    private fun passing(name: String) = "    @org.junit.Test\n    fun `$name`() { }"

    private fun failing(name: String) = "    @org.junit.Test\n    fun `$name`() { throw AssertionError(\"ran\") }"

    @Test
    fun `a per-target entry point runs exactly the contract cases of its target`() {
        val dir =
            replayFixture(
                passing("TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes"),
                failing("TEST-UNIT-001 given_a_unit_case_when_replayed_then_it_must_not_run"),
            )
        try {
            val result = gradle(dir, "contractTestReplayJvm", "--no-configuration-cache").build()
            assertTrue(
                result.output.contains("executed 1 contract case(s) across 1 target(s): test=1"),
                "TEST-CONTRACT-007: the entry point selects the contract case and nothing else:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the same suite outside an entry point is not narrowed`() {
        val dir =
            replayFixture(
                passing("TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes"),
                failing("TEST-UNIT-001 given_a_unit_case_when_run_then_it_runs"),
            )
        try {
            val result = gradle(dir, "test", "--no-configuration-cache").buildAndFail()
            assertTrue(
                result.output.contains("TEST-UNIT-001"),
                "TEST-CONTRACT-007: an ordinary test run keeps every case; the filter belongs to the entry point:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a per-target entry point whose target ran no contract case fails with the verifier's diagnostic`() {
        val dir = replayFixture(passing("TEST-UNIT-001 given_a_unit_case_when_replayed_then_it_is_not_contract"))
        try {
            val result = gradle(dir, "contractTestReplayJvm", "--no-configuration-cache").buildAndFail()
            assertTrue(
                result.output.contains("TEST-CONTRACT:") && result.output.contains("`test`"),
                "TEST-CONTRACT-007: zero contract cases on the target is the verifier's finding, not Gradle's:\n${result.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a native entry point never accepts a host report`() {
        val dir = reportFixture("testAndroidHostTest", "TEST-CONTRACT-001 given_a_fixture_when_replayed_then_it_decodes", "")
        try {
            // Replace the hand-wired verifier with the two entry points `:core:data` registers.
            File(dir, "build.gradle.kts").writeText(
                """
                plugins { id("multiverse.contract.tests") }

                contractTests {
                    replay("AndroidHost", "testAndroidHostTest")
                    replay("IosSimulator", "iosSimulatorArm64Test")
                }
                """.trimIndent() + "\n",
            )
            val host = gradle(dir, "contractTestReplayAndroidHost", "--no-configuration-cache").build()
            assertTrue(
                host.output.contains("testAndroidHostTest=1"),
                "TEST-CONTRACT-007: the host entry point reads the host report:\n${host.output}",
            )
            val native = gradle(dir, "contractTestReplayIosSimulator", "--no-configuration-cache").buildAndFail()
            assertTrue(
                native.output.contains("`iosSimulatorArm64Test` produced no JUnit report"),
                "TEST-CONTRACT-007: a host report cannot satisfy the native entry point:\n${native.output}",
            )
            val aggregate = gradle(dir, "contractTestReplay", "--no-configuration-cache").buildAndFail()
            assertTrue(
                aggregate.output.contains("iosSimulatorArm64Test"),
                "TEST-CONTRACT-007: the two-target aggregate still requires both targets:\n${aggregate.output}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
