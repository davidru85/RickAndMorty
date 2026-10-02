package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-044` — the workflow must carry the required set and block on it (`TESTING.md` §14.2).
 *
 * The guard is only worth having if it fails when the workflow narrows: these tests pin both
 * directions, so deleting a job, dropping a runner, making a check advisory, unpinning an action or
 * satisfying a rule without executing it is a build failure rather than a quietly smaller gate.
 *
 * The fixtures are synthesised from the real workflow's shape, and the action SHAs are the
 * repository's own pinned values, so a test never needs the network. The `GAP-016` reproductions
 * that mutate the repository's own workflows live in `WorkflowGateReproductionTest`.
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
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
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
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
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
                    appendLine("          ./gradlew :core:data:contractTestReplayAndroidHost")
                    appendLine("          ./gradlew :core:data:contractTestReplayIosSimulator")
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
        val root = kotlin.io.path.createTempDirectory("workflow-guard-no-ios").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        File(dir, "pull-request.yml").writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
                appendLine("jobs:")
                appendLine("  android:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: ./gradlew check :androidApp:assembleDebug buildHealth verifyModuleBoundaries")
                appendLine("      - run: ./gradlew verifyDependencyPolicy verifyRepositoryHygiene verifyNoLiveHosts")
                appendLine("      - run: ./gradlew verifyDocumentedGate")
            },
        )
        // DEC-083: the `ios` job is required again once an iosApp application target exists.
        withIosApp(root)
        assertTrue(
            findings(root).any { it.reason.contains("`ios` job is missing") },
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
        file.writeText(
            file.readText().replaceFirst(
                "      - uses: $pinned",
                "      - uses: $pinned\n        continue-on-error: true",
            ),
        )
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

    @Test
    fun `the real workflows pass every rule`() {
        val root =
            File(System.getProperty("user.dir"))
                .let { start -> generateSequence(start) { it.parentFile }.first { File(it, ".github/workflows").isDirectory } }
        assertTrue(
            allFindings(root).isEmpty(),
            "TEST-UNIT-044: the repository's own workflows must satisfy every rule; got " +
                allFindings(root).map { "${it.path}:${it.line}: ${it.reason}" },
        )
    }

    // --- TEST-UNIT-045 (TASK-093, DEC-078): no workflow step may merge, tag, release or push ---

    /** A workflow that passes every existing rule plus one automated integration step. */
    private fun withIntegrationStep(step: String): File {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
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
                appendLine("      - run: ./gradlew buildHealth verifyDocumentedGate")
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
        assertTrue(
            integrationFindings("gh release create v1.0.0").any { it.contains("release") },
            "TEST-UNIT-045: a step that releases must fail the guard (AC-REQ-FUNC-014-2)",
        )
    }

    @Test
    fun `a workflow that asks for write permission is rejected`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText("permissions:\n  contents: write\n" + file.readText())
        assertTrue(
            findings(root).any { it.reason.contains("contents: write") },
            "TEST-UNIT-045: a write token is what an automated integration would need",
        )
    }

    @Test
    fun `the repository's own integration prohibition holds`() {
        // Only the integration rule is asserted here: the baseline workflow is `complete()`, which
        // already carries the full required set, and this asserts no integration finding appears.
        val reasons = findings(complete()).map { it.reason }
        assertTrue(
            reasons.none { it.contains("integrate automatically") },
            "TEST-UNIT-045: a workflow without an integration step must not raise the integration rule",
        )
    }

    // --- TEST-UNIT-044 (TASK-026, DEC-073): live mode is unreachable from the merge gate ---

    @Test
    fun `every registered live task name is recognised as a live-mode marker`() {
        listOf("contract-live", "contractLiveProbe", "contractLiveTest").forEach { task ->
            val root = complete()
            val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
            file.writeText(
                file.readText().replace(
                    "./gradlew verifyDocumentedGate",
                    "./gradlew verifyDocumentedGate\n          ./gradlew :core:data:$task",
                ),
            )
            assertTrue(
                allFindings(root).any { it.reason.contains("fixture/replay mode only") },
                "`$task` must be recognised as live mode; a merge-gate workflow must not reach it",
            )
        }
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

    // --- TASK-098 (B2-R01): structure, not concatenated text --------------------------------

    /**
     * A gate workflow in which the step that would carry `verifyNoLiveHosts` is replaced by the
     * given YAML. Every other required check stays present, so a finding can only be about the
     * substituted step: the fixture isolates one bypass at a time.
     */
    private fun withStep(stepYaml: String): File {
        val root = kotlin.io.path.createTempDirectory("workflow-guard-withstep").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        File(dir, "pull-request.yml").writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
                appendLine("jobs:")
                appendLine("  android:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: ./gradlew check :androidApp:assembleDebug buildHealth")
                appendLine("      - run: ./gradlew verifyModuleBoundaries verifyDependencyPolicy")
                appendLine("      - run: ./gradlew verifyRepositoryHygiene")
                appendLine("      - run: ./gradlew verifyDocumentedGate")
                stepYaml.trimEnd().lines().forEach { appendLine("      $it") }
                appendLine("  ios:")
                appendLine("    runs-on: macos-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: ./gradlew check iosSimulatorArm64Test buildHealth")
                appendLine("      - run: ./gradlew verifyModuleBoundaries verifyDependencyPolicy")
                appendLine("      - run: ./gradlew verifyRepositoryHygiene")
                appendLine("      - run: ./gradlew verifyDocumentedGate")
            },
        )
        return root
    }

    @Test
    fun `a required check that exists only in a comment does not satisfy the gate`() {
        val root = withStep("# ./gradlew verifyNoLiveHosts")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: a comment is not a step (B2-R01 item 4)",
        )
    }

    @Test
    fun `a required check named in a step name does not satisfy the gate`() {
        val root = withStep("- name: ./gradlew verifyNoLiveHosts\n  run: echo done")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: a step name is not an execution (B2-R01 item 4)",
        )
    }

    @Test
    fun `a required check only in an env value does not satisfy the gate`() {
        val root = withStep("- env:\n    CHECK: ./gradlew verifyNoLiveHosts\n  run: echo ready")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: an env value is data, not an execution (TASK-098 criteria)",
        )
    }

    @Test
    fun `a required check echoed rather than executed does not satisfy the gate`() {
        val root = withStep("- run: echo ./gradlew verifyNoLiveHosts")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: `echo` prints the command, it does not run it (B2-R01 item 9)",
        )
    }

    @Test
    fun `a required check invoked as a dry run does not satisfy the gate`() {
        val root = withStep("- run: ./gradlew verifyNoLiveHosts --dry-run")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: a dry run plans the task graph without running it (TASK-098 criteria)",
        )
    }

    @Test
    fun `a required check excluded with -x does not satisfy the gate`() {
        val root = withStep("- run: ./gradlew check -x verifyNoLiveHosts")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: `-x` excludes the task from the invocation (TASK-098 criteria)",
        )
    }

    @Test
    fun `a required check whose failure is suppressed does not satisfy the gate`() {
        val root = withStep("- run: ./gradlew verifyNoLiveHosts || true")
        assertTrue(
            findings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: `|| true` swallows the failure, so the check is not required " +
                "(TASK-098 criteria)",
        )
    }

    @Test
    fun `a required check behind a condition that can skip it does not satisfy the gate`() {
        val root = withStep("- if: false\n  run: ./gradlew verifyNoLiveHosts")
        assertTrue(
            findings(root).any { it.reason.contains("can skip") || it.reason.contains("if: false") },
            "TEST-UNIT-044: `if: false` is an always-skipped step, not a required check (B2-R01 item 4)",
        )
    }

    @Test
    fun `a required check only in a job the gate does not name does not satisfy the gate`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/other.yml")
        file.writeText(
            buildString {
                appendLine("name: other")
                appendLine("on:")
                appendLine("  schedule:")
                appendLine("    - cron: '0 3 * * 1'")
                appendLine("jobs:")
                appendLine("  diagnostics:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                appendLine("      - run: ./gradlew verifyNoLiveHosts")
            },
        )
        // The real gate must still carry the check; removing it from the gate and leaving it in an
        // unrelated diagnostic workflow is the narrowing this rule exists to catch.
        val gate = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        gate.writeText(gate.readText().replace(" verifyNoLiveHosts", ""))
        assertTrue(
            allFindings(root).any { it.reason.contains("verifyNoLiveHosts") },
            "TEST-UNIT-044: an unrelated workflow carrying the command is not the gate (B2-R01 item 3)",
        )
    }

    @Test
    fun `a merge-gate workflow with no pull-request trigger is reported`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(file.readText().replace("  pull_request:\n    branches: [main]\n", ""))
        assertTrue(
            findings(root).any { it.reason.contains("not triggered by `pull_request`") },
            "TEST-UNIT-044: a gate that never runs on a pull request is not a gate (B2-R01 item 6)",
        )
    }

    @Test
    fun `a narrowed pull-request trigger is reported`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(
            file.readText().replace(
                "  pull_request:\n    branches: [main]",
                "  pull_request:\n    paths-ignore: ['docs/**']",
            ),
        )
        assertTrue(
            findings(root).any { it.reason.contains("filter") },
            "TEST-UNIT-044: `paths-ignore` would skip the gate for documentation-only changes that still " +
                "must be gated (B2-R01 item 6)",
        )
    }

    @Test
    fun `a scalar on declaration fails the complete-set requirement`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        // `on: pull_request` is a valid scalar, and it omits the push trigger the gate needs.
        file.writeText(
            file.readText().replace(
                "on:\n  pull_request:\n    branches: [main]\n  push:\n    branches: [main]",
                "on: pull_request",
            ),
        )
        assertTrue(
            findings(root).any { it.reason.contains("not triggered by `push`") },
            "TEST-UNIT-044: a scalar `on:` is recognised, so its missing push trigger is reported " +
                "(TASK-098 criteria)",
        )
    }

    @Test
    fun `every on spelling is recognised for the live-mode rule`() {
        val spellings =
            listOf(
                "on:\n  pull_request:\n    branches: [main]" to "block mapping",
                "on: {pull_request: {branches: [main]}}" to "flow mapping",
                "'on':\n  pull_request:\n    branches: [main]" to "quoted key",
            )
        spellings.forEach { (declaration, description) ->
            val root = complete()
            val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
            file.writeText(
                file.readText().replace(
                    "on:\n  pull_request:\n    branches: [main]\n  push:\n    branches: [main]",
                    declaration,
                ).replace(
                    "./gradlew verifyDocumentedGate",
                    "./gradlew verifyDocumentedGate\n          ./gradlew :core:data:contractLiveProbe",
                ),
            )
            assertTrue(
                findings(root).any { it.reason.contains("fixture/replay mode only") },
                "TEST-UNIT-044: the live-mode rule must recognise a $description `on:` declaration",
            )
        }
    }

    @Test
    fun `malformed YAML is reported rather than falling back to text search`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText("on: [pull_request\njobs: {")
        assertTrue(
            findings(root).any { it.reason.contains("not valid YAML") },
            "TEST-UNIT-044: a configuration the guard cannot read is one it cannot vouch for (B2-R01 item 8)",
        )
    }

    @Test
    fun `the iOS job must use the macOS runner by job identity`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        // Both runner names are still present in the file; only the jobs carrying them are swapped.
        file.writeText(
            file.readText()
                .replace("runs-on: ubuntu-latest", "runs-on: SWAP")
                .replace("runs-on: macos-latest", "runs-on: ubuntu-latest")
                .replace("runs-on: SWAP", "runs-on: macos-latest"),
        )
        assertTrue(
            findings(root).any { it.reason.contains("must run on") },
            "TEST-UNIT-044: the runner is bound to the job, not to the directory (B2-R01 item 3)",
        )
    }

    @Test
    fun `an artifact upload may be conditional on always`() {
        val root = withStep("- name: Upload reports\n  if: always()\n  uses: actions/upload-artifact@3d3c42e5aac5ba805825da76410c181273ba90b1")
        assertTrue(
            findings(root).none { it.reason.contains("if: always()") },
            "TEST-UNIT-044: `if: always()` on an artifact upload is the documented exception",
        )
    }

    @Test
    fun `a condition other than always on an upload is still reported`() {
        val root = withStep("- name: Upload reports\n  if: success()\n  uses: actions/upload-artifact@3d3c42e5aac5ba805825da76410c181273ba90b1")
        assertTrue(
            findings(root).any { it.reason.contains("can skip") },
            "TEST-UNIT-044: only `if: always()` is exempt; another condition can skip the step",
        )
    }

    @Test
    fun `a commented continue-on-error does not fail an otherwise valid gate`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(
            file.readText().replaceFirst(
                "      - uses: $pinned",
                "      # never add continue-on-error: true here\n      - uses: $pinned",
            ),
        )
        assertEquals(
            emptyList(),
            findings(root).map { it.reason },
            "TEST-UNIT-044: a comment explaining the rule is not a violation of it",
        )
    }

    // --- DEC-083: the temporary iOS suspension and its restoration tripwire ------------------

    /** The commands the `android` job carries on its own while the `ios` job is suspended. */
    private val androidGateCommands =
        listOf(
            "./gradlew check",
            "./gradlew :androidApp:assembleDebug",
            "./gradlew buildHealth",
            "./gradlew verifyModuleBoundaries verifyDependencyPolicy verifyRepositoryHygiene",
            "./gradlew verifyNoLiveHosts verifyDocumentedGate verifyWorkflowGate",
            "./gradlew :core:data:contractTestReplayAndroidHost",
        )

    /** A gate with the `android` job only, running [commands]. */
    private fun androidOnly(commands: List<String> = androidGateCommands): File {
        val root = kotlin.io.path.createTempDirectory("workflow-guard-android-only").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        File(dir, "pull-request.yml").writeText(
            buildString {
                appendLine("name: pull-request")
                appendLine("on:")
                appendLine("  pull_request:")
                appendLine("    branches: [main]")
                appendLine("  push:")
                appendLine("    branches: [main]")
                appendLine("jobs:")
                appendLine("  android:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: $pinned")
                commands.forEach { appendLine("      - run: $it") }
            },
        )
        return root
    }

    /** Adds an Xcode project under `iosApp/` whose one native target has [productType]. */
    private fun withIosApp(
        root: File,
        productType: String = "com.apple.product-type.application",
    ): File {
        val project = File(root, "iosApp/MultiverseExplorer.xcodeproj")
        project.mkdirs()
        File(project, "project.pbxproj").writeText(
            """
            // !${'$'}*UTF8*${'$'}!
            {
                objects = {
                    0A1B2C3D /* MultiverseExplorer */ = {
                        isa = PBXNativeTarget;
                        name = MultiverseExplorer;
                        productType = "$productType";
                    };
                };
            }
            """.trimIndent() + "\n",
        )
        return root
    }

    @Test
    fun `an android-only gate passes while no iOS application target exists`() {
        assertEquals(
            emptyList(),
            findings(androidOnly()).map { it.reason },
            "TEST-UNIT-044: DEC-083 suspends the ios job until an iosApp application target exists",
        )
    }

    @Test
    fun `the android job must run the host contract replay`() {
        val root = androidOnly(androidGateCommands - "./gradlew :core:data:contractTestReplayAndroidHost")
        assertTrue(
            findings(root).any { it.reason.contains("contractTestReplayAndroidHost") },
            "TEST-UNIT-044: the contract-fixture row is active on the android job (TASK-037, DEC-073)",
        )
    }

    @Test
    fun `the suspension relaxes no android rule`() {
        val swallowed = androidOnly(androidGateCommands.map { if (it == "./gradlew check") "./gradlew check || true" else it })
        val skipped = androidOnly(androidGateCommands - "./gradlew :androidApp:assembleDebug")
        assertTrue(
            findings(swallowed).any { it.reason.contains("./gradlew check") },
            "TEST-UNIT-044: a swallowed failure is still a missing check under DEC-083",
        )
        assertTrue(
            findings(skipped).any { it.reason.contains(":androidApp:assembleDebug") },
            "TEST-UNIT-044: an Android check cannot be dropped under DEC-083",
        )
    }

    @Test
    fun `an iOS application target restores the ios job, its native suites and the native replay`() {
        val reasons = findings(withIosApp(androidOnly())).map { it.reason }
        assertTrue(reasons.any { it.contains("`ios` job is missing") }, "TEST-UNIT-044: the tripwire restores the job; got $reasons")
        assertTrue(reasons.any { it.contains("iosSimulatorArm64Test") }, "TEST-UNIT-044: and the native suites; got $reasons")
        assertTrue(
            reasons.any { it.contains("contractTestReplayIosSimulator") },
            "TEST-UNIT-044: and the native contract replay (TASK-051); got $reasons",
        )
    }

    @Test
    fun `a project with no application target does not trip the restoration`() {
        val root = withIosApp(androidOnly(), productType = "com.apple.product-type.framework")
        assertEquals(
            emptyList(),
            findings(root).map { it.reason },
            "TEST-UNIT-044: only an application target ends the suspension; a framework is not the app (TASK-051)",
        )
    }

    @Test
    fun `an ios job present during the suspension is still held to its runner and its conditions`() {
        val root = complete()
        val file = File(root, "${WorkflowGateGuard.WORKFLOW_DIRECTORY}/pull-request.yml")
        file.writeText(file.readText().replace("    runs-on: macos-latest", "    if: false\n    runs-on: ubuntu-latest"))
        val reasons = findings(root).map { it.reason }
        assertTrue(reasons.any { it.contains("must run on `macos-latest`") }, "TEST-UNIT-044: got $reasons")
        assertTrue(reasons.any { it.contains("`ios` job carries `if: false`") }, "TEST-UNIT-044: got $reasons")
    }
}
