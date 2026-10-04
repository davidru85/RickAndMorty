package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-044` — the four bypasses `GAP-016` reproduced against `8e837e1`, each with **zero
 * findings** under the text-searching guard (`TASK-098`, `B2-R01`).
 *
 * These are not synthetic fixtures: each mutation is applied to the repository's own workflow, the
 * way the audit applied it, and the guard must report it. The suite is the red→green evidence for
 * the remediation — under the previous guard every case here returned an empty finding list.
 *
 * The reproductions stay in the test tree and are never written to the real checkout: a negative
 * fixture that outlives its test is a defect, because the next reader finds evidence of a state the
 * repository is no longer in.
 */
class WorkflowGateReproductionTest {
    private val workflowDirectory = File(System.getProperty("user.dir")).let { run ->
        generateSequence(run) { it.parentFile }.first { File(it, ".github/workflows").isDirectory.let { d -> d } }
    }.let { File(it, ".github/workflows") }

    /** The repository's real workflow files, as the build scans them. */
    private fun realWorkflows(): List<File> {
        val files = workflowDirectory.listFiles { f -> f.extension == "yml" || f.extension == "yaml" }?.toList()
        assertTrue(
            !files.isNullOrEmpty(),
            "the reproduction suite needs the repository's real workflows; found " +
                "${workflowDirectory.absolutePath} = ${workflowDirectory.exists()}",
        )
        return files
    }

    /**
     * Copies the real workflows into a scratch root, applies `mutation`, and returns the findings
     * the guard reports for the mutated configuration.
     */
    private fun findingsAfter(mutation: (String) -> String): List<String> {
        val root = kotlin.io.path.createTempDirectory("gate-reproduction").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        realWorkflows().forEach { file ->
            File(dir, file.name).writeText(mutation(file.readText()))
        }
        return WorkflowGateGuard.scan(
            dir.listFiles()?.toList() ?: emptyList(),
            root,
        ).map { "${it.path}:${it.line}: ${it.reason}" }
    }

    /**
     * Replaces the whole `on:` block — from the `on:` key to the next column-0 line — with
     * `replacement`, so a mutation produces valid YAML. Substituting only part of the block would
     * leave a block mapping after a flow value, and a parse error would mask the rule under test.
     */
    private fun String.replaceTriggerBlock(replacement: String): String {
        val lines = lines()
        val start = lines.indexOfFirst { it.startsWith("on:") || it.startsWith("'on':") }
        if (start < 0) return this
        val end = lines.drop(start + 1).indexOfFirst { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("#") }
            .let { if (it < 0) lines.size else start + 1 + it }
        return (lines.subList(0, start) + replacement.trimEnd().lines() + lines.subList(end, lines.size))
            .joinToString("\n") + "\n"
    }

    @Test
    fun `bypass 1 - a condition that disables both required jobs is reported`() {
        val findings =
            findingsAfter { text ->
                // Every required job carries `if: ${{ false }}`: the rollup is green and nothing ran.
                text.replace("    if: \${{ always() }}\n", "")
                    .replace("    runs-on: ubuntu-latest", "    if: \${{ false }}\n    runs-on: ubuntu-latest")
                    .replace("    runs-on: xcode-27", "    if: \${{ false }}\n    runs-on: xcode-27")
            }
        assertTrue(
            findings.any { it.contains("can skip its required checks") },
            "Bypass 1 (GAP-016): `if: \${{ false }}` on both required jobs must be reported; got $findings",
        )
    }

    @Test
    fun `bypass 2 - every gradlew invocation replaced by echo is reported`() {
        val findings =
            findingsAfter { text ->
                // Every command is printed instead of executed: the file still contains every literal.
                text.replace("./gradlew ", "echo ./gradlew ")
            }
        assertTrue(
            findings.isNotEmpty(),
            "Bypass 2 (GAP-016): replacing every invocation with `echo` must be reported",
        )
        assertTrue(
            findings.count { it.contains("is not an executable step") } >= 5,
            "Bypass 2 (GAP-016): each echoed command must be reported as unreachable; got $findings",
        )
    }

    @Test
    fun `bypass 3 - a gate reachable only by manual dispatch is reported`() {
        val findings =
            findingsAfter { text ->
                // Both merge-gate triggers removed, leaving only a manual entry point.
                text.replaceTriggerBlock("on:\n  workflow_dispatch:")
            }
        assertTrue(
            findings.any { it.contains("not triggered by `pull_request`") },
            "Bypass 3 (GAP-016): a gate with no pull-request trigger must be reported",
        )
        assertTrue(
            findings.any { it.contains("not triggered by `push`") },
            "Bypass 3 (GAP-016): a gate with no push trigger must be reported",
        )
    }

    @Test
    fun `bypass 4 - a flow-sequence on declaration carrying live mode is reported`() {
        val findings =
            findingsAfter { text ->
                // `on:` rewritten as a flow sequence (still a valid trigger declaration) and a
                // live-mode invocation added to an ordinary step of the gate.
                text.replaceTriggerBlock("on: [pull_request, push]")
                    .replace(Regex("(?m)^(\\s*)- run: (.*)$"), "$1- run: $2\n$1- run: ./gradlew :core:data:contractLiveProbe")
            }
        assertTrue(
            findings.any { it.contains("fixture/replay mode only") },
            "Bypass 4 (GAP-016): live mode reachable from a flow-sequence `on:` must be reported",
        )
    }

    @Test
    fun `the untouched repository workflows pass every rule`() {
        val root = kotlin.io.path.createTempDirectory("gate-reproduction-clean").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY)
        dir.mkdirs()
        realWorkflows().forEach { file -> File(dir, file.name).writeText(file.readText()) }
        val findings = WorkflowGateGuard.scan(dir.listFiles()?.toList() ?: emptyList(), root)
        assertTrue(
            findings.isEmpty(),
            "the guard must pass the repository's own configuration; got ${findings.map { it.reason }}",
        )
    }
}
