package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-044` — the workflow must carry the required set and block on it (`TESTING.md` §14.2).
 *
 * The guard is only worth having if it fails when the workflow narrows: these tests pin both
 * directions, so deleting a job, dropping a runner, making a check advisory or unpinning an action
 * is a build failure rather than a quietly smaller gate.
 *
 * The fixtures are synthesised from the real workflow's shape, and the action SHAs are the
 * repository's own pinned values, so a test never needs the network.
 */
class WorkflowGateGuardTest {

    private val pinned = "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"

    private fun workflow(vararg jobs: Pair<String, String>): File {
        val root = kotlin.io.path.createTempDirectory("workflow-guard").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        File(dir, "pull-request.yml").writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("jobs:")
                jobs.forEach { (name, body) ->
                    appendLine("  $name:")
                    appendLine("    runs-on: $body")
                    appendLine("    steps:")
                    appendLine("      - uses: $pinned")
                }
            },
        )
        return root
    }

    /** A workflow that satisfies every rule the guard enforces. */
    private fun complete(): File {
        val root = kotlin.io.path.createTempDirectory("workflow-guard-complete").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        File(dir, "pull-request.yml").writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("jobs:")
                listOf("android" to "ubuntu-latest", "ios" to "macos-latest").forEach { (name, runner) ->
                    appendLine("  $name:")
                    appendLine("    runs-on: $runner")
                    appendLine("    steps:")
                    appendLine("      - uses: $pinned")
                    appendLine("      - name: Run the gate")
                    appendLine("        run: |")
                    appendLine("          ./gradlew check")
                    appendLine("          ./gradlew :androidApp:assembleDebug")
                    appendLine("          ./gradlew iosSimulatorArm64Test")
                    appendLine("          ./gradlew verifyModuleBoundaries verifyDependencyPolicy")
                    appendLine("          ./gradlew verifyRepositoryHygiene verifyNoLiveHosts")
                    appendLine("          ./gradlew buildHealth")
                    appendLine("          ./gradlew verifyDocumentedGate")
                }
            },
        )
        return root
    }

    /** Every workflow of the fixture root, which is how the real build scans them. */
    private fun allFindings(root: File) =
        WorkflowGateGuard.scan(
            File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
                .listFiles { f -> f.extension == "yml" || f.extension == "yaml" }
                ?.toList() ?: emptyList(),
            root,
        )

    private fun findings(root: File) =
        WorkflowGateGuard.scan(listOf(File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")), root)

    @Test
    fun `a workflow carrying both jobs, both runners and every current check passes`() {
        assertEquals(
            emptyList(),
            findings(complete()).map { it.reason },
            "TEST-UNIT-044: the repository's own workflow shape must satisfy every rule",
        )
    }

    @Test
    fun `a missing job or runner is reported`() {
        val noIos = workflow("android" to "ubuntu-latest").let { root ->
            val f = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
            f.writeText(
                f.readText() + "./gradlew check\n:androidApp:assembleDebug\niosSimulatorArm64Test\n" +
                    "verifyModuleBoundaries\nverifyDependencyPolicy\nverifyRepositoryHygiene\nverifyNoLiveHosts\n",
            )
            root
        }
        assertTrue(
            findings(noIos).any { it.reason.contains("`ios` job is missing") },
            "TEST-UNIT-044: a missing platform job must be reported",
        )
    }

    @Test
    fun `an omitted check is reported rather than silently narrowing the gate`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(file.readText().replace("./gradlew verifyRepositoryHygiene verifyNoLiveHosts", "./gradlew verifyRepositoryHygiene"))
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: deleting a check from the workflow must fail the guard",
        )
    }

    @Test
    fun `a neutralised check is reported`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText("      - uses: $pinned\n        continue-on-error: true\n" + file.readText())
        assertTrue(
            findings(root).any { it.reason.contains("continue-on-error") },
            "TEST-UNIT-044: an advisory required check is not a required check",
        )
    }

    @Test
    fun `an unpinned action is reported`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(file.readText().replace(pinned, "actions/checkout@v7"))
        assertTrue(
            findings(root).any { it.reason.contains("full commit SHA") },
            "TEST-UNIT-044: a moving tag must be reported (SECURITY.md 9, DEC-037)",
        )
    }

    @Test
    fun `an absent workflow directory is reported`() {
        val empty = kotlin.io.path.createTempDirectory("workflow-guard-empty").toFile()
        assertTrue(
            findings(empty).any { it.reason.contains("no workflow file exists") },
            "TEST-UNIT-044: the gate must not be satisfied by having no workflow at all",
        )
    }

    // --- TEST-UNIT-045 (TASK-093, DEC-078): no workflow step may merge, tag, release or push ---

    /** A workflow that passes every existing rule plus one automated integration step. */
    private fun withIntegrationStep(step: String): File {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        val text = file.readText().trimEnd().removeSuffix("jobs:").trimEnd()
        // `complete()` writes jobs as lines; rebuild a readable workflow with one extra step.
        file.writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("jobs:")
                appendLine("  android:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: |")
                step.trim().lines().forEach { appendLine("          $it") }
                appendLine("  ios:")
                appendLine("    runs-on: macos-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: ./gradlew check :androidApp:assembleDebug")
                appendLine("      - run: ./gradlew iosSimulatorArm64Test")
                appendLine("      - run: ./gradlew verifyModuleBoundaries verifyDependencyPolicy")
                appendLine("      - run: ./gradlew verifyRepositoryHygiene verifyNoLiveHosts")
            },
        )
        return root
    }

    private fun integrationFindings(step: String): List<String> {
        val root = withIntegrationStep(step)
        return findings(root).map { it.reason }
    }

    @Test
    fun `an automated merge step is rejected`() {
        assertTrue(
            integrationFindings("gh pr merge --squash --admin").any { it.contains("merge") },
            "TEST-UNIT-045: a step that merges must fail the guard (AC-REQ-FUNC-014-2)",
        )
    }

    @Test
    fun `an automated tag or release step is rejected`() {
        val tagged = integrationFindings("git tag v1.0.0 && git push origin v1.0.0")
        assertTrue(
            tagged.any { it.contains("tag") || it.contains("push") },
            "TEST-UNIT-045: a step that tags or pushes must fail the guard (AC-REQ-FUNC-014-2); observed: $tagged",
        )
    }

    @Test
    fun `a workflow that asks for write permission is rejected`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(file.readText() + "permissions:\n  contents: write\n")
        assertTrue(
            findings(root).any { it.reason.contains("contents: write") },
            "TEST-UNIT-045: a write token could push, so it must fail the guard (AC-REQ-FUNC-014-2)",
        )
    }

    @Test
    fun `the real workflows pass every rule`() {
        val root = File("").absoluteFile.let { dir ->
            generateSequence(dir) { it.parentFile }.first { File(it, "build-logic/settings.gradle.kts").isFile }
        }
        val workflows = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
            .listFiles { f -> f.extension == "yml" || f.extension == "yaml" }
            ?.toList()
            ?: emptyList()
        assertTrue(workflows.isNotEmpty(), "the repository has at least one workflow")
        assertEquals(
            emptyList(),
            findings(root).map { it.reason },
            "TEST-UNIT-045: the repository's own workflows must satisfy every rule",
        )
    }

    // --- TEST-UNIT-044 / DEC-073: no merge-gate workflow may reach live mode ---

    @Test
    fun `a pull-request workflow that references the live entry point is rejected`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(
            file.readText().replace(
                "./gradlew verifyDocumentedGate",
                "./gradlew verifyDocumentedGate\n          ./gradlew :core:data:contractTestLive",
            ),
        )
        assertTrue(
            allFindings(root).any { it.reason.contains("fixture/replay mode only") },
            "the merge gate must never reach live mode (AC-REQ-NFR-011-2)",
        )
    }

    @Test
    fun `a scheduled workflow that references live mode passes`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/contract-live.yml")
        file.writeText(
            """
            name: contract-live
            on:
              schedule:
                - cron: "0 6 * * 1"
              workflow_dispatch:
            permissions:
              contents: read
            jobs:
              live:
                runs-on: ubuntu-latest
                steps:
                  - uses: $pinned
                  - run: ./gradlew :core:data:contractLiveProbe
            """.trimIndent() + "\n",
        )
        assertEquals(
            emptyList(),
            allFindings(root).map { it.reason },
            "a schedule-only live workflow satisfies every rule",
        )
    }
}
