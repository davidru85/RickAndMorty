package io.github.davidru85.multiverse.buildlogic.policy

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.io.File

/**
 * `TEST-UNIT-044` — the required-check set is present and blocking in the workflow configuration
 * (`AC-REQ-NFR-011-1`; `TESTING.md` §14.2, §14.3).
 *
 * A workflow that quietly omits a suite, runs it with `continue-on-error`, gates it behind a
 * condition that never holds, or merely *names* the command in `echo` produces a green rollup
 * while proving less than the gate claims. The guard therefore **parses** each workflow file and
 * reasons about its structure — jobs, their runners, their triggers and their executable steps —
 * instead of searching the concatenated text of the whole directory (`TASK-098`, `B2-R01`).
 *
 * What it enforces, per file:
 *
 * - the gate is triggered by `pull_request` **and** by `push`, with no path or branch narrowing
 *   that would omit a required category of change;
 * - the `android` job runs on a Linux runner and the `ios` job on a macOS runner, by job identity
 *   rather than by the presence of the string anywhere in the directory;
 * - every check the repository can currently execute is an **executable step** of the job that is
 *   supposed to carry it, never a comment, a `name`, an `env` value, an `echo`, or a step of an
 *   unrelated scheduled workflow;
 * - a step or job condition that can skip an active required check fails, as does any setting that
 *   makes its failure advisory (`continue-on-error`), except `if: always()` on an artifact upload;
 * - a workflow reachable from a pull request or a push may not reference live mode, under any
 *   trigger spelling (`AC-REQ-NFR-011-2`);
 * - remote actions stay pinned to a 40-character commit SHA, and no step merges, tags, releases or
 *   pushes (`TEST-UNIT-045`, `DEC-049`).
 *
 * It deliberately does **not** require the rows whose harness does not exist yet: `DEC-071`
 * activates those with their own tasks, and an always-green placeholder job would be worse than an
 * absent one because it would be cited as evidence.
 *
 * **Temporary iOS suspension (`DEC-083`).** The `ios` job and its native checks are required only
 * once [IosAppTripwire] finds an `iosApp` application target; until then the gate is the `android`
 * context; under DEC-112, ModularWorkflowGate validates its independent workers and their result. Every rule above applies
 * to an `ios` job that is present anyway, and nothing about the `android` job is relaxed.
 *
 * Diagnostics carry the file and the line of the YAML node they concern, so a reviewer can act on
 * them without re-parsing by hand. Line numbers come from the file's own text; nothing here
 * re-implements YAML.
 */
internal object WorkflowGateGuard {

    /** One problem with the workflow configuration. */
    data class Finding(val path: String, val line: Int, val reason: String)

    /** The workflow files the guard reads, relative to the repository root. */
    const val WORKFLOW_DIRECTORY = ".github/workflows"

    /**
     * The checks that exist today and must therefore be reachable from a blocking job.
     *
     * `command` is the literal invocation a step's `run` must contain; `allowedJobs` names the jobs
     * that may carry it. The entries are the same commands `TESTING.md` §14.2 lists for this stage
     * of activation, asserted here so that deleting one from the workflow is a build failure rather
     * than a silent narrowing of the gate.
     */
    private val REQUIRED_COMMANDS =
        listOf(
            RequiredCommand("./gradlew check", setOf("android", "ios")),
            RequiredCommand(":androidApp:assembleDebug", setOf("android")),
            // `TASK-037` (`DEC-073`): the contract-fixture row is active on the Android host target.
            RequiredCommand(":core:data:contractTestReplayAndroidHost", setOf("android")),
            RequiredCommand("verifyModuleBoundaries", setOf("android", "ios")),
            RequiredCommand("verifyDependencyPolicy", setOf("android", "ios")),
            RequiredCommand("verifyRepositoryHygiene", setOf("android", "ios")),
            RequiredCommand("verifyNoLiveHosts", setOf("android", "ios")),
            RequiredCommand("buildHealth", setOf("android", "ios")),
            RequiredCommand("verifyDocumentedGate", setOf("android", "ios")),
        )

    /**
     * The native checks the `ios` job carries, required again once an `iosApp` application target
     * exists (`DEC-083`; restored by `TASK-051`).
     */
    private val IOS_RESTORED_COMMANDS =
        listOf(
            RequiredCommand("iosSimulatorArm64Test", setOf("ios")),
            RequiredCommand(":core:data:contractTestReplayIosSimulator", setOf("ios")),
        )

    /** A command that must run somewhere, and the job kinds allowed to carry it. */
    private data class RequiredCommand(val command: String, val allowedJobs: Set<String>)

    /** The jobs the gate knows: each carries part of the required set when it is required. */
    private val REQUIRED_JOBS = listOf("android", "ios")

    /** The jobs required at this stage: `ios` only once the tripwire has fired (`DEC-083`). */
    private fun requiredJobs(iosRestored: Boolean): List<String> = if (iosRestored) REQUIRED_JOBS else listOf("android")

    /** The runner each required job must use: one Linux, one macOS (`DEC-054`, `DEC-071`). */
    private val REQUIRED_RUNNERS = mapOf("android" to "ubuntu-latest", "ios" to "macos-latest")

    /**
     * `TEST-UNIT-044` (`GAP-028`): the task names a job may exclude with `-x`, because the runner's
     * platform *disables* them rather than failing them.
     *
     * The Kotlin Multiplatform plugin disables `iosSimulatorArm64Test` on a non-Apple host —
     * "cannot run on the current host (linux-x86_64)". Reaching it from `check` did not run it; it
     * pulled the whole Kotlin/Native chain in as a dependency (`downloadKotlinNativeDistribution`,
     * 13-15 minutes, and `compileKotlinIosSimulatorArm64` per module) for a task that could never
     * execute, which exhausted the `android` job's ceiling and cancelled four consecutive runs.
     *
     * The exclusion is therefore an allow-list keyed by job: `android` may exclude
     * `iosSimulatorArm64Test` and nothing else. The `ios` job may exclude nothing — on macOS the
     * suite is executable, so excluding it there would be a genuinely narrowed gate.
     */
    private val HOST_DISABLED_EXCLUSIONS = mapOf("android" to setOf("iosSimulatorArm64Test"))

    /**
     * The `-x`/`--exclude-task` names a `run` text excludes, so the rule can decide each one.
     */
    private fun String.excludedTasks(): List<String> =
        Regex("(?:^|\\s)-x\\s+(\\S+)|(?:^|\\s)--exclude-task[=\\s]+(\\S+)")
            .findAll(this)
            .mapNotNull { match -> match.groupValues[1].ifEmpty { match.groupValues[2] }.takeIf { it.isNotEmpty() } }
            .toList()

    /**
     * `TEST-UNIT-045` (`TASK-093`, `DEC-078`): the automated-integration patterns a workflow step
     * may never contain. Integration reaches `main` through a human merge only (`DEC-049`).
     */
    private val INTEGRATION_PATTERNS =
        listOf(
            "gh\\s+pr\\s+merge",
            "gh\\s+release\\s+create",
            "git\\s+merge",
            "git\\s+tag",
            "git\\s+push",
        )

    /**
     * `TEST-UNIT-044` (`TASK-026`, `DEC-073`; completed by `TASK-096`): markers that identify the
     * live contract mode. A workflow triggered by a pull request or a push may never reference one:
     * the fixture/replay suite is the gate's contract check, and live mode is a scheduled signal
     * (`AC-REQ-NFR-011-2`). Every registered live entry point belongs here — the probe (what the
     * scheduled run executes), the source set it compiles into, and the case that pins the record
     * it writes.
     */
    private val LIVE_MODE_MARKERS = listOf("contract-live", "contractLiveProbe", "contractLiveTest")

    /** The triggers that make a workflow part of the merge gate. */
    private val MERGE_GATE_TRIGGERS = listOf("pull_request", "push")

    /**
     * Shell forms that would make a `run` step stop being an execution of the command it names.
     * A step whose text contains a required command but also one of these is not evidence that the
     * command ran: `echo` prints it, `--dry-run` plans it, `-x` excludes it, and `|| true` swallows
     * its failure (`TASK-098`, `B2-R01` item 9).
     */
    private val NON_EXECUTING_FORMS =
        listOf(
            "\\becho\\b",
            "--dry-run|\\s-m\\b",
            "-x\\s",
            "\\|\\|\\s*true",
            "\\|\\|\\s*:",
        )

    fun scan(workflows: Collection<File>, root: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val files =
            workflows
                .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
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

        // TEST-UNIT-045 / SHA pinning / contents:write read raw lines, because they need the line
        // number a reviewer sees and a comment is a legitimate place to explain the rule.
        files.forEach { file -> scanRawLines(file, root, findings) }

        // Everything else reasons about parsed structure.
        val documents =
            files.mapNotNull { file ->
                parse(file, root, findings)?.let { WorkflowDocument(file, it) }
            }

        val iosApplications = IosAppTripwire.applicationProjects(root)
        scanTriggers(documents, root, findings, iosApplications)
        val modularDocuments = documents.filter { ModularWorkflowGate.matches(it) }
        modularDocuments.forEach { document ->
            findings += ModularWorkflowGate.scan(document, root, iosApplications.isNotEmpty())
        }
        scanJobs(documents - modularDocuments.toSet(), root, findings, iosApplications.isNotEmpty())

        // TEST-UNIT-044: live mode must be unreachable from the merge gate, under any trigger
        // spelling. The trigger set is decided from the parsed `on:` node, not from a regex.
        documents
            .filter { it.hasAnyTrigger(MERGE_GATE_TRIGGERS) }
            .forEach { document ->
                document.allScalarsWithLines().forEach { (value, line) ->
                    val reference = LIVE_MODE_MARKERS.firstOrNull { value.contains(it) }
                    if (reference != null) {
                        findings +=
                            Finding(
                                document.relative(root),
                                line,
                                "a pull-request- or push-triggered workflow references `$reference`; the merge " +
                                    "gate runs the contract suite in fixture/replay mode only " +
                                    "(AC-REQ-NFR-011-2, DEC-073)",
                            )
                    }
                }
            }

        return findings
    }

    // ---------------------------------------------------------------- raw-line rules

    private fun scanRawLines(
        file: File,
        root: File,
        findings: MutableList<Finding>,
    ) {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        file.readLines().forEachIndexed { index, line ->
            val lineNumber = index + 1
            val code = line.substringBefore('#')
            INTEGRATION_PATTERNS.firstOrNull { Regex(it).containsMatchIn(code) }?.let { pattern ->
                findings +=
                    Finding(
                        relative,
                        lineNumber,
                        "`$pattern` makes a workflow integrate automatically; merging, tagging, " +
                            "releasing and pushing are human-only actions (AC-REQ-FUNC-014-2, DEC-049)",
                    )
            }
            if (Regex("contents\\s*:\\s*write").containsMatchIn(code)) {
                findings +=
                    Finding(
                        relative,
                        lineNumber,
                        "`contents: write` gives a workflow the token it would need to push; the gate " +
                            "runs read-only (AC-REQ-FUNC-014-2, SECURITY.md 9)",
                    )
            }
            Regex("uses:\\s*(\\S*(?:action-gh-release|create-release|gh-release|gh-actions-release)[^\\s#]*)")
                .find(code)
                ?.groupValues
                ?.get(1)
                ?.let { action ->
                    findings +=
                        Finding(
                            relative,
                            lineNumber,
                            "`$action` publishes a release automatically; releasing is a human-only " +
                                "action (AC-REQ-FUNC-014-2, DEC-049)",
                        )
                }

            val action = Regex("uses:\\s*([^\\s#]+)").find(line)?.groupValues?.get(1) ?: return@forEachIndexed
            // A local action (`./…`) or a Docker reference is not a supply-chain risk of the same
            // kind; every remote action must be pinned by full commit SHA.
            if (action.startsWith("./") || action.startsWith("docker://")) return@forEachIndexed
            val sha = action.substringAfter('@', missingDelimiterValue = "")
            if (!Regex("^[0-9a-f]{40}$").matches(sha)) {
                findings +=
                    Finding(
                        relative,
                        lineNumber,
                        "`$action` is not pinned by a full commit SHA; a moving tag could change the code " +
                            "that executes with this repository's token (SECURITY.md 9, DEC-037)",
                    )
            }
        }
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Parses one workflow file.
     *
     * Malformed YAML is a finding naming the file; the guard never falls back to text searching,
     * because a configuration it cannot read is a configuration it cannot vouch for. Duplicate keys
     * are rejected: the last-wins default would let a second `jobs:` silently replace the first.
     */
    private fun parse(
        file: File,
        root: File,
        findings: MutableList<Finding>,
    ): Map<*, *>? {
        val settings =
            LoadSettings
                .builder()
                .setAllowDuplicateKeys(false)
                .setLabel(file.name)
                .build()
        return try {
            when (val loaded = Load(settings).loadFromString(file.readText())) {
                is Map<*, *> -> loaded
                else -> {
                    findings +=
                        Finding(
                            file.relativeTo(root).invariantSeparatorsPath,
                            1,
                            "the workflow parses to ${loaded?.let { it::class.simpleName } ?: "null"} rather than a " +
                                "mapping; a file the guard cannot read is a file it cannot vouch for",
                        )
                    null
                }
            }
        } catch (e: YamlEngineException) {
            findings +=
                Finding(
                    file.relativeTo(root).invariantSeparatorsPath,
                    1,
                    "the workflow is not valid YAML (${e.message?.lineSequence()?.first() ?: "parse error"}); " +
                        "a malformed gate is not a passing gate",
                )
            null
        }
    }

    // ---------------------------------------------------------------- trigger rules

    /**
     * The gate must be reachable from both a pull request and a push, and neither trigger may be
     * narrowed so that a required category of change escapes it.
     *
     * The `on:` node is recognised in every form YAML allows — block mapping, flow mapping, flow
     * sequence, scalar and quoted keys — because a guard that only understands one spelling is a
     * guard that a one-character edit defeats (`TASK-098`, `B2-R01` item 6).
     */
    private fun scanTriggers(
        documents: List<WorkflowDocument>,
        root: File,
        findings: MutableList<Finding>,
        iosApplications: List<String>,
    ) {
        val gateDocuments = documents.filter { document -> document.jobNames().any { it in REQUIRED_JOBS } }
        if (gateDocuments.isEmpty()) {
            // The missing-job finding below is the actionable one; do not pile a trigger finding on
            // a directory that has no gate at all.
            return
        }
        gateDocuments.forEach { document ->
            val mergeEvents = document.mergeGateEvents()
            requiredJobs(iosApplications.isNotEmpty()).forEach { job ->
                if (job !in document.jobNames()) {
                    val restoration =
                        if (job == "ios") {
                            "; `${iosApplications.first()}` declares an iOS application target, so the DEC-083 " +
                                "suspension has ended and the job is restored in the same change (TASK-051)"
                        } else {
                            ""
                        }
                    findings +=
                        Finding(
                            document.relative(root),
                            document.lineOfKey("jobs") ?: 1,
                            "the `$job` job is missing; its part of the required set is not run$restoration",
                        )
                }
            }
            if (mergeEvents.pullRequest == null) {
                findings +=
                    Finding(
                        document.relative(root),
                        document.lineOfKey("on") ?: 1,
                        "the gate is not triggered by `pull_request`; a required check that never runs on a " +
                            "pull request is not a gate (AC-REQ-NFR-011-1)",
                    )
            } else if (mergeEvents.pullRequestNarrowed) {
                findings +=
                    Finding(
                        document.relative(root),
                        mergeEvents.pullRequestLine,
                        "the `pull_request` trigger carries a filter that could omit required updates; the gate " +
                            "must evaluate every pull request targeting `main` (AC-REQ-NFR-011-1)",
                    )
            }
            if (mergeEvents.push == null) {
                findings +=
                    Finding(
                        document.relative(root),
                        document.lineOfKey("on") ?: 1,
                        "the gate is not triggered by `push`; `main` must be gated at every commit " +
                            "(AC-REQ-FUNC-014-1)",
                    )
            } else if (mergeEvents.pushNarrowed) {
                findings +=
                    Finding(
                        document.relative(root),
                        mergeEvents.pushLine,
                        "the `push` trigger carries a filter that could omit a required category of change; the " +
                            "gate must evaluate every push to `main` (AC-REQ-FUNC-014-1)",
                    )
            }
        }
    }

    // ---------------------------------------------------------------- job rules

    private fun scanJobs(
        documents: List<WorkflowDocument>,
        root: File,
        findings: MutableList<Finding>,
        iosRestored: Boolean,
    ) {
        documents.forEach { document ->
            val relative = document.relative(root)
            val jobEntries = document.jobEntries()
            jobEntries.forEach { (jobName, job) ->
                if (jobName !in REQUIRED_JOBS) return@forEach
                val expectedRunner = REQUIRED_RUNNERS.getValue(jobName)
                val runner = job.stringAt("runs-on")
                if (runner != expectedRunner) {
                    findings +=
                        Finding(
                            relative,
                            job.lineOf("runs-on") ?: document.lineOfKey("jobs") ?: 1,
                            "the `$jobName` job must run on `$expectedRunner`; it runs on " +
                                "`${runner ?: "nothing"}` (DEC-054, DEC-071)",
                        )
                }

                // A job-level condition that can skip the whole job neutralises every check in it.
                // It is read as a scalar: `if: false` parses to a Boolean, not a String.
                job.scalarAt("if")?.let { condition ->
                    findings +=
                        Finding(
                            relative,
                            job.lineOf("if") ?: 1,
                            "the `$jobName` job carries `if: $condition`, which can skip its required checks; a " +
                                "required check may not be conditional (AC-REQ-NFR-011-1)",
                        )
                }

                job.steps().forEachIndexed { index, step ->
                    if (step.isAdvisory()) {
                        findings +=
                            Finding(
                                relative,
                                step.lineOf("continue-on-error") ?: step.startLine,
                                "a step of the `$jobName` job is marked `continue-on-error`; a required check " +
                                    "cannot be made advisory (AC-REQ-NFR-011-1)",
                            )
                    }
                    step.skipCondition()?.let { condition ->
                        findings +=
                            Finding(
                                relative,
                                step.lineOf("if") ?: step.startLine,
                                "step ${index + 1} of the `$jobName` job carries `if: $condition`, which can " +
                                    "skip it; only artifact uploads may be conditional, and they must use " +
                                    "`if: always()` (AC-REQ-NFR-011-1)",
                            )
                    }
                    // GAP-028: a task the runner's platform disables may be excluded by name; every
                    // other exclusion is the narrowed gate this guard exists to reject.
                    val allowed = HOST_DISABLED_EXCLUSIONS[jobName].orEmpty()
                    step.stringAt("run")?.excludedTasks()?.forEach { excluded ->
                        if (excluded !in allowed) {
                            findings +=
                                Finding(
                                    relative,
                                    step.lineOf("run") ?: step.startLine,
                                    "step ${index + 1} of the `$jobName` job excludes `$excluded` with `-x`; only " +
                                        "a task this runner's platform disables may be excluded " +
                                        "(${allowed.sorted().joinToString(", ") { "`$it`" }.ifEmpty { "none" }}), " +
                                        "and excluding it otherwise hides a check that could have run " +
                                        "(AC-REQ-NFR-011-1, GAP-028)",
                                )
                        }
                    }
                }
            }
        }

        // Required commands are evaluated across the gate documents as a set: a command may live in
        // any workflow that carries a gate job, as long as it is an executable step of a job the
        // specification names for it. A command that exists only in an unrelated workflow — or only
        // in a comment, a step name or an `echo` — does not count (`TASK-098`, `B2-R01` item 4).
        val gateDocuments = documents.filter { document -> document.jobEntries().any { it.first in REQUIRED_JOBS } }
        if (gateDocuments.isEmpty()) return
        val executableByJob: Map<String, List<String>> =
            gateDocuments
                .flatMap { document -> document.jobEntries() }
                .filter { (name, _) -> name in REQUIRED_JOBS }
                .groupBy({ it.first }, { it.second })
                .mapValues { (jobName, jobs) ->
                    jobs
                        .flatMap { job -> job.executableRunTexts() }
                        .mapNotNull { text -> text.takeIf { it.actuallyExecutes(jobName) } }
                }
        val anchor = gateDocuments.first()
        val requiredCommands = if (iosRestored) REQUIRED_COMMANDS + IOS_RESTORED_COMMANDS else REQUIRED_COMMANDS
        requiredCommands.forEach { required ->
            val carriers: List<String> =
                executableByJob.filter { (_, texts) -> texts.any { it.contains(required.command) } }.keys.toList()
            if (carriers.isEmpty()) {
                findings +=
                    Finding(
                        anchor.relative(root),
                        anchor.lineOfKey("jobs") ?: 1,
                        "`${required.command}` is not an executable step of any gate job; an omitted check " +
                            "is a missing check, not a pass (AC-REQ-NFR-011-1)",
                    )
                return@forEach
            }
            if (carriers.intersect(required.allowedJobs).isEmpty()) {
                findings +=
                    Finding(
                        anchor.relative(root),
                        anchor.lineOfKey("jobs") ?: 1,
                        "`${required.command}` runs only in ${carriers.sorted().joinToString(", ") { "`$it`" }}, " +
                            "but it belongs to ${required.allowedJobs.sorted().joinToString(", ") { "`$it`" }}; " +
                            "the required set is not carried by the job the specification names",
                    )
            }
        }
    }

    /**
     * Whether a `run` text genuinely executes the commands it names.
     *
     * A text that contains a required command but also `echo`, a dry run, `-x`, or a failure
     * suppressor (`|| true`, `|| :`) would satisfy a substring check while proving nothing: the step
     * succeeds whether or not the check ran (`TASK-098`, `B2-R01` item 9).
     *
     * `GAP-028` refines the `-x` case. Excluding a task genuinely stops the invocation being
     * evidence that the command ran, and a step that excludes a *required* command is still caught
     * by the rule above. But a single step may legitimately carry both `./gradlew check` and an
     * exclusion of a suite the runner's platform disables; the exclusion must not then void the
     * whole step, because the check itself still ran. So the text is normalised by stripping the
     * exclusions this job is allowed to make, and the `-x` form is judged on what remains.
     */
    private fun String.actuallyExecutes(jobName: String? = null): Boolean {
        val allowed = jobName?.let { HOST_DISABLED_EXCLUSIONS[it] }.orEmpty()
        val normalised =
            if (allowed.isEmpty()) {
                this
            } else {
                allowed.fold(this) { text, task ->
                    text.replace(Regex("(?:^|\\s)-x\\s+${Regex.escape(task)}(?=\\s|$)"), " ").replace("  ", " ")
                }
            }
        return NON_EXECUTING_FORMS.none { Regex(it).containsMatchIn(normalised) }
    }
}
