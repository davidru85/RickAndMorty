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
}
