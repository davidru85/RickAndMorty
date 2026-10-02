package io.github.davidru85.multiverse.buildlogic.testing

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * `TEST-CONTRACT-*` non-emptiness and report integrity (`TASK-026`, `DEC-073`; hardened by
 * `TASK-100`, `B2-R03`).
 *
 * A filtered test task that never starts — `NO-SOURCE` when the module has no test source yet, or
 * `SKIPPED` — does not fail on an empty selection, so an aggregate over such tasks is green with
 * zero contract cases executed. That is exactly the state `DEC-071` forbids: a check that cannot
 * fail. This task therefore verifies the **outcome**, not the invocation: it reads the JUnit XML
 * reports and decides on what actually ran.
 *
 * `GAP-017` showed the first implementation was too generous in three ways:
 *
 * - it counted `<testcase>` elements without reading `skipped`, `failures` or `errors`, so a
 *   skipped-only report was counted as one executed case;
 * - it combined both targets into a single total, so JVM evidence could mask a missing native
 *   report — the exact situation the two-runner gate exists to catch;
 * - it never checked that a report was well formed, so a truncated file contributed nothing and
 *   the aggregate could pass on the other target alone.
 *
 * The verification is therefore per target and per case:
 *
 * - every target the entry point claims to run must produce a report and at least one case that
 *   **actually executed** (not skipped, not failed, not errored);
 * - a case that failed or errored is reported as the failure it is, with the target named, rather
 *   than silently counting as evidence;
 * - a malformed report is a finding naming the file, never a silent zero.
 *
 * It reads reports, not build state, so it stays correct when a test task is cached or re-run.
 */
abstract class VerifyContractCasesTask : DefaultTask() {

    /** The `TEST-CONTRACT-*` reports produced by the JVM host and the Apple simulator targets. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    /** The id prefix a contract case must carry (`TESTING.md` §13.2). */
    @get:Input
    abstract val idPrefix: ListProperty<String>

    /**
     * Target name to the report directory it writes into.
     *
     * Per-target evaluation is what `GAP-017` demands: a total over both directories lets a green
     * JVM run stand in for a native run that never produced a report (`DEC-054`, `DEC-071`).
     */
    @get:Input
    abstract val targetReportDirectories: MapProperty<String, String>

    /** One case as the report describes it. */
    private data class Case(val target: String, val name: String, val status: CaseStatus)

    private enum class CaseStatus { EXECUTED, SKIPPED, FAILED, ERRORED }

    @TaskAction
    fun verify() {
        val prefixes = idPrefix.get()
        val findings = mutableListOf<String>()
        val executed = mutableMapOf<String, MutableList<String>>()

        // A caller that declares no per-target map keeps the aggregate semantics; the module does
        // declare it, and that is where the per-target rule bites (`TASK-100`, `GAP-017`).
        val targets = targetReportDirectories.get()
        if (targets.isEmpty()) {
            evaluateAggregate(prefixes, findings, executed)
        } else {
            evaluatePerTarget(prefixes, findings, executed)
        }

        if (findings.isEmpty() && executed.isEmpty()) {
            findings +=
                "contractTestReplay executed no ${prefixes.first()} case. The run is not evidence: a green " +
                    "entry point that ran nothing is the state DEC-071 forbids"
        }

        if (findings.isNotEmpty()) {
            throw IllegalStateException(
                "verifyContractCases found ${findings.size} contract-report problem(s):\n" +
                    findings.distinct().sorted().joinToString("\n") { "  TEST-CONTRACT: $it" },
            )
        }

        logger.lifecycle(
            "contractTestReplay executed ${executed.values.sumOf { it.size }} contract case(s) " +
                "across ${executed.size} target(s): " +
                executed.entries.joinToString(", ") { "${it.key}=${it.value.size}" },
        )
    }

    /** The single-set rule: every report present, every executed case counted once. */
    private fun evaluateAggregate(
        prefixes: List<String>,
        findings: MutableList<String>,
        executed: MutableMap<String, MutableList<String>>,
    ) {
        val files = reports.files.filter { it.isFile && it.extension == "xml" }
        files.sortedBy { it.path }.forEach { file ->
            parse(file, "aggregate", findings).forEach { case ->
                if (prefixes.any { case.name.startsWith(it) } && case.status == CaseStatus.EXECUTED) {
                    executed.getOrPut("aggregate") { mutableListOf() }.add(case.name)
                }
            }
        }
    }

    /** The per-target rule: every declared target must report, and only executed cases count. */
    private fun evaluatePerTarget(
        prefixes: List<String>,
        findings: MutableList<String>,
        executed: MutableMap<String, MutableList<String>>,
    ) {
        targetReportDirectories.get().toSortedMap().forEach { (target, directory) ->
            val dir = File(directory)
            val files =
                dir.listFiles { f: File -> f.isFile && f.extension == "xml" }?.sortedBy { it.name }
                    ?: emptyList()
            if (files.isEmpty()) {
                findings +=
                    "`$target` produced no JUnit report under `$directory`; the entry point claims to run " +
                        "it, and a target that wrote nothing is not evidence (GAP-017, DEC-054)"
                return@forEach
            }
            val cases = files.flatMap { file -> parse(file, target, findings) }
            val contractCases = cases.filter { case -> prefixes.any { case.name.startsWith(it) } }
            if (contractCases.isEmpty()) {
                findings +=
                    "`$target` ran no ${prefixes.first()} case; the run is not evidence for that target " +
                        "(GAP-017, DEC-071)"
                return@forEach
            }
            contractCases.forEach { case ->
                when (case.status) {
                    CaseStatus.EXECUTED ->
                        executed.getOrPut(target) { mutableListOf() }.add(case.name)
                    CaseStatus.SKIPPED ->
                        findings +=
                            "`$target` skipped `${case.name}`; a skipped case is not an executed case " +
                                "(GAP-017)"
                    CaseStatus.FAILED ->
                        findings += "`$target` failed `${case.name}`; the contract case did not pass (GAP-017)"
                    CaseStatus.ERRORED ->
                        findings += "`$target` errored in `${case.name}`; the contract case did not pass (GAP-017)"
                }
            }
        }
    }

    /**
     * Every contract case one report describes, with its real status.
     *
     * The status is read from the case element's own children: JUnit writes `<skipped/>`,
     * `<failure>` or `<error>` inside the `<testcase>` it belongs to. A count of `<testcase>` tags
     * — the previous rule — cannot distinguish those from a pass (`GAP-017`).
     */
    private fun parse(
        file: File,
        target: String,
        findings: MutableList<String>,
    ): List<Case> {
        val text = file.readText()
        if (!text.contains("<testsuite")) {
            findings +=
                "`$target`'s report `${file.name}` is not a JUnit report; a file the check cannot read is " +
                    "not a passing target (GAP-017)"
            return emptyList()
        }
        val caseRegex = Regex("""<testcase\b([^>]*?)(?:/>|>(.*?)</testcase>)""", RegexOption.DOT_MATCHES_ALL)
        return caseRegex.findAll(text).mapNotNull { match ->
            val attributes = match.groupValues[1]
            val body = match.groupValues[2]
            val name = Regex("""\bname="([^"]*)"""").find(attributes)?.groupValues?.get(1) ?: return@mapNotNull null
            val status =
                when {
                    body.contains("<error") -> CaseStatus.ERRORED
                    body.contains("<failure") -> CaseStatus.FAILED
                    body.contains("<skipped") -> CaseStatus.SKIPPED
                    else -> CaseStatus.EXECUTED
                }
            Case(target, name, status)
        }.toList()
    }
}
