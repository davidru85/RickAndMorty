package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * `TEST-UNIT-044` — the required-check set is present and blocking in the workflow configuration
 * (`AC-REQ-NFR-011-1`; `TESTING.md` §14.2, §14.3).
 *
 * A workflow that quietly omits a suite, or runs it with `continue-on-error`, produces a green
 * rollup while proving less than the gate claims. The guard reads the workflow the same way a
 * reviewer does and fails closed on both omissions:
 *
 * - every check the repository can currently execute must be reachable from a blocking job;
 * - no check may be neutralised by `continue-on-error: true`, and no job may be marked
 *   `if: false` or similar;
 * - every action reference must be pinned to a 40-character commit SHA, never a tag or a branch
 *   (`SECURITY.md` §9, `DEC-037`).
 *
 * It deliberately does **not** require the rows whose harness does not exist yet: `DEC-071`
 * activates those with their own tasks, and an always-green placeholder job would be worse than an
 * absent one because it would be cited as evidence.
 */
internal object WorkflowGateGuard {

    /** One problem with the workflow configuration. */
    data class Finding(val path: String, val line: Int, val reason: String)

    /** The workflow files the guard reads, relative to the repository root. */
    const val WORKFLOW_DIRECTORY = ".github/workflows"

    /**
     * The checks that exist today and must therefore be reachable from a blocking job.
     *
     * Each entry is a literal substring the workflow must contain. They are the same commands
     * `TESTING.md` §14.2 lists for this stage of activation, and they are asserted here so that
     * deleting one from the workflow is a build failure rather than a silent narrowing of the gate.
     */
    private val REQUIRED_COMMANDS = listOf(
        "./gradlew check",
        ":androidApp:assembleDebug",
        "iosSimulatorArm64Test",
        "verifyModuleBoundaries",
        "verifyDependencyPolicy",
        "verifyRepositoryHygiene",
        "verifyNoLiveHosts",
        "buildHealth",
        "verifyDocumentedGate",
    )

    /** The jobs that must exist, because each carries part of the required set. */
    private val REQUIRED_JOBS = listOf("android", "ios")

    /** The runners the gate must cover: one Linux/Android, one macOS (`DEC-054`). */
    private val REQUIRED_RUNNERS = listOf("ubuntu-latest", "macos-latest")

    /**
     * `TEST-UNIT-045` (`TASK-093`, `DEC-078`): the automated-integration patterns a workflow step
     * may never contain. Integration reaches `main` through a human merge only (`DEC-049`).
     */
    private val INTEGRATION_PATTERNS = listOf(
        "gh\\s+pr\\s+merge",
        "gh\\s+release\\s+create",
        "git\\s+merge",
        "git\\s+tag",
        "git\\s+push",
    )

    fun scan(workflows: Collection<File>, root: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val files = workflows.filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
            .sortedBy { it.path }
        if (files.isEmpty()) {
            return listOf(
                Finding(
                    path = WORKFLOW_DIRECTORY,
                    line = 1,
                    reason = "no workflow file exists; the required-check set is not configured anywhere " +
                        "(AC-REQ-NFR-011-1)",
                ),
            )
        }

        // TEST-UNIT-044: a required command must be reachable from a step, not merely mentioned.
        // Comments explain the rules and quote these commands, so reachability is decided on the
        // workflow with comment lines removed; the SHA-pin and integration checks below still read
        // the raw lines, because they need the line number a reviewer sees.
        val text = files.joinToString("\n") { file ->
            file.readLines().joinToString("\n") { line -> line.substringBefore('#') }
        }
        val relative = files.joinToString(", ") { it.relativeTo(root).invariantSeparatorsPath }

        REQUIRED_JOBS.forEach { job ->
            if (!Regex("(?m)^ {2}$job:").containsMatchIn(text)) {
                findings += Finding(relative, 1, "the `$job` job is missing; its part of the required set is not run")
            }
        }
        REQUIRED_RUNNERS.forEach { runner ->
            if (!text.contains(runner)) {
                findings += Finding(relative, 1, "no job runs on `$runner`; the gate must cover both platforms")
            }
        }
        REQUIRED_COMMANDS.forEach { command ->
            if (!text.contains(command)) {
                findings += Finding(
                    relative,
                    1,
                    "`$command` is not reachable from any job; an omitted check is a missing check, not a pass",
                )
            }
        }

        files.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val lineNumber = index + 1
                if (Regex("(?m)^\\s*continue-on-error\\s*:\\s*true").containsMatchIn(line)) {
                    findings += Finding(
                        file.relativeTo(root).invariantSeparatorsPath,
                        lineNumber,
                        "`continue-on-error: true` neutralises a check; a required check cannot be made advisory",
                    )
                }
                // TEST-UNIT-045 (`TASK-093`, AC-REQ-FUNC-014-1/-2): a workflow may never merge,
                // tag, release or push. Integration is a human action (DEC-049), so a step that
                // does any of them is an automated integration path. Comment lines are skipped —
                // they explain the rules and legitimately quote the words — and the `on: push:`
                // trigger is not a step.
                val code = line.substringBefore('#')
                val integration = INTEGRATION_PATTERNS.firstOrNull { Regex(it).containsMatchIn(code) }
                if (integration != null) {
                    findings += Finding(
                        file.relativeTo(root).invariantSeparatorsPath,
                        lineNumber,
                        "`$integration` makes a workflow integrate automatically; merging, tagging, " +
                            "releasing and pushing are human-only actions (AC-REQ-FUNC-014-2, DEC-049)",
                    )
                }
                if (Regex("contents\\s*:\\s*write").containsMatchIn(code)) {
                    findings += Finding(
                        file.relativeTo(root).invariantSeparatorsPath,
                        lineNumber,
                        "`contents: write` gives a workflow the token it would need to push; the gate " +
                            "runs read-only (AC-REQ-FUNC-014-2, SECURITY.md 9)",
                    )
                }

                val releaseAction = Regex("uses:\\s*(\\S*(?:action-gh-release|create-release|gh-release|gh-actions-release)[^\\s#]*)")
                    .find(code)?.groupValues?.get(1)
                if (releaseAction != null) {
                    findings += Finding(
                        file.relativeTo(root).invariantSeparatorsPath,
                        lineNumber,
                        "`$releaseAction` publishes a release automatically; releasing is a human-only " +
                            "action (AC-REQ-FUNC-014-2, DEC-049)",
                    )
                }

                val action = Regex("uses:\\s*([^\\s#]+)").find(line)?.groupValues?.get(1) ?: return@forEachIndexed
                // A local action (`./…`) or a Docker reference is not a supply-chain risk of the
                // same kind; every remote action must be pinned by full commit SHA.
                if (action.startsWith("./") || action.startsWith("docker://")) return@forEachIndexed
                val sha = action.substringAfter('@', missingDelimiterValue = "")
                if (!Regex("^[0-9a-f]{40}$").matches(sha)) {
                    findings += Finding(
                        file.relativeTo(root).invariantSeparatorsPath,
                        lineNumber,
                        "`$action` is not pinned by a full commit SHA; a moving tag could change the code " +
                            "that executes with this repository's token (SECURITY.md 9, DEC-037)",
                    )
                }
            }
        }
        return findings
    }
}
