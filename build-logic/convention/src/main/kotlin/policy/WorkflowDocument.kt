package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * A parsed workflow file, with the line each key sits on.
 *
 * The YAML parser returns values but throws the source position away, and a diagnostic without a
 * line sends the reviewer back to reading the file by hand. The mapping below is built once, from
 * the raw text, so every rule can report *where* it found the problem (`TASK-098`, `B2-R01`).
 *
 * Only the shapes a GitHub Actions workflow uses are modelled: mappings, sequences and scalars.
 * Anything the guard does not understand is treated as "present but unconstrained", never as
 * "absent", so an unusual spelling cannot silently satisfy a rule by being skipped.
 */
internal class WorkflowDocument(
    private val file: File,
    private val rootMap: Map<*, *>,
) {
    /** The `name:` of the workflow, for messages. */
    val name: String = rootMap["name"] as? String ?: file.name

    /** The `jobs:` mapping. */
    val jobs: Map<*, *> = rootMap["jobs"] as? Map<*, *> ?: emptyMap<Any, Any>()

    /** The names of the jobs declared in this workflow. */
    fun jobNames(): Set<String> = jobs.keys.filterIsInstance<String>().toSet()

    /** The `on:` node in whatever shape it was written. YAML 1.1 resolves the bare key to `true`. */
    private val triggers: Any? = rootMap["on"] ?: rootMap[true]

    fun relative(root: File): String = file.relativeTo(root).invariantSeparatorsPath

    // ------------------------------------------------------------- line lookup

    /**
     * The 1-based line of a top-level key, found in the raw text.
     *
     * The parser's marks are not exposed for every node type, and re-implementing YAML to get them
     * would be worse than this: a top-level key is a column-0 `key:` line by definition, which is
     * unambiguous in a workflow file.
     */
    fun lineOfKey(key: String): Int? = file.lineOfTopLevelKey(key)

    private fun File.lineOfTopLevelKey(key: String): Int? =
        readLines().indexOfFirst { it.startsWith("$key:") || it.startsWith("\"$key\":") || it.startsWith("'$key':") }
            .takeIf { it >= 0 }
            ?.plus(1)

    // ------------------------------------------------------------- trigger shape

    /**
     * The events this workflow is reachable from, normalised across every spelling YAML allows:
     * a block mapping, a flow mapping, a flow sequence, a scalar, and quoted keys.
     */
    fun eventNames(): Set<String> =
        when (val node = triggers) {
            null -> emptySet()
            is String -> setOf(node)
            is List<*> -> node.filterIsInstance<String>().toSet()
            is Map<*, *> -> node.keys.filterIsInstance<String>().toSet()
            else -> emptySet()
        }

    fun hasAnyTrigger(names: Collection<String>): Boolean = eventNames().any { it in names }

    /** What the merge-gate triggers look like, and whether they are narrowed. */
    data class MergeEvents(
        val pullRequest: String?,
        val pullRequestNarrowed: Boolean,
        val pullRequestLine: Int,
        val push: String?,
        val pushNarrowed: Boolean,
        val pushLine: Int,
    )

    fun mergeGateEvents(): MergeEvents {
        val node = triggers
        val map = node as? Map<*, *>
        val pr = eventNames().contains("pull_request")
        val push = eventNames().contains("push")
        return MergeEvents(
            pullRequest = if (pr) "pull_request" else null,
            pullRequestNarrowed = pr && isNarrowed(map?.get("pull_request")),
            pullRequestLine = lineOfKeyValue("pull_request"),
            push = if (push) "push" else null,
            pushNarrowed = push && isNarrowed(map?.get("push")),
            pushLine = lineOfKeyValue("push"),
        )
    }

    /**
     * A trigger is *narrowed* when it carries a filter that could omit a required change:
     * `paths`, `paths-ignore`, `branches-ignore` or `types` on `pull_request`. `branches: [main]`
     * is the opposite — it is the target the gate exists for — so it is accepted.
     */
    private fun isNarrowed(node: Any?): Boolean {
        val map = node as? Map<*, *> ?: return false
        val narrowing = listOf("paths", "paths-ignore", "branches-ignore")
        if (narrowing.any { map.containsKey(it) }) return true
        // `types:` on pull_request selects a subset of activity types; the default set is the one
        // the gate needs (opened/synchronize/reopened), so any explicit `types` narrows it.
        return map.containsKey("types")
    }

    /** The line of a key nested anywhere under `on:`, found near the `on:` block. */
    private fun lineOfKeyValue(key: String): Int {
        val lines = file.readLines()
        val onLine = lineOfKey("on") ?: 1
        // Search from the `on:` block downward while indentation stays inside it; a workflow's
        // `on:` block always ends at the next column-0 key.
        val end = lines.drop(onLine).indexOfFirst { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("#") }
            .let { if (it < 0) lines.size else onLine + it }
        val block = lines.subList(onLine - 1, minOf(end, lines.size))
        // A quoted key and a flow-sequence `on:` both keep the event name on some line of the block.
        val hit = block.indexOfFirst { it.contains(key) }
        return if (hit >= 0) onLine + hit else onLine
    }

    // ------------------------------------------------------------- jobs

    /** One job, with its own line lookups. */
    fun jobEntries(): List<Pair<String, JobNode>> =
        jobs.entries.mapNotNull { (key, value) ->
            val jobName = key as? String ?: return@mapNotNull null
            val body = value as? Map<*, *> ?: return@mapNotNull null
            jobName to JobNode(jobName, body, this)
        }

    /**
     * Every scalar in the document, with the line it appears on.
     *
     * Used by the live-mode rule: a reference can be a `run` string, a step `name`, an `env` value
     * or a job name, and a guard that only inspects `run` misses the others.
     */
    fun allScalarsWithLines(): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        val lines = file.readLines()
        collectScalars(rootMap, out, lines)
        return out.toList()
    }

    private fun collectScalars(
        node: Any?,
        out: MutableList<Pair<String, Int>>,
        lines: List<String>,
    ) {
        when (node) {
            is Map<*, *> -> node.values.forEach { collectScalars(it, out, lines) }
            is List<*> -> node.forEach { collectScalars(it, out, lines) }
            is String -> {
                // Report the first line that carries this text; a diagnostic that names the step
                // text and a nearby line is what a reviewer needs to act.
                val line = lines.indexOfFirst { it.contains(node) }.takeIf { it >= 0 }?.plus(1) ?: 1
                out += node to line
            }
            else -> Unit
        }
    }

    /** The raw lines, so a nested node can locate itself. */
    internal fun lines(): List<String> = file.readLines()
}

/** One job of a workflow, with helpers the rules need. */
internal class JobNode(
    val name: String,
    private val body: Map<*, *>,
    private val document: WorkflowDocument,
) {
    /** The string value of a key, if it is a string scalar. */
    fun stringAt(key: String): String? = body[key] as? String

    /**
     * Any scalar value of a key, rendered as text.
     *
     * `runs-on` is always a string, but a condition can be `false`, `0` or `null`, which YAML
     * resolves before Kotlin sees them. Reading a condition with `stringAt` would silently treat
     * "never run this job" as "no condition at all".
     */
    fun scalarAt(key: String): String? = body[key]?.toString()

    /** The line of a key inside this job, found by searching the job's own region. */
    fun lineOf(key: String): Int? {
        val lines = document.lines()
        val jobStart = lines.indexOfFirst { it.trimStart().startsWith("$name:") }
        if (jobStart < 0) return null
        // The job body is indented under the job key; stop at the next key at the same indentation.
        val end = lines.drop(jobStart + 1).indexOfFirst { it.isNotBlank() && !it.startsWith("    ") && !it.startsWith("      ") && !it.startsWith("  #") }
            .let { if (it < 0) lines.size else jobStart + 1 + it }
        val hit = lines.subList(jobStart, minOf(end, lines.size)).indexOfFirst { it.trimStart().startsWith("$key:") }
        return if (hit >= 0) jobStart + hit + 1 else null
    }

    /** The steps of this job, as nodes. */
    fun steps(): List<StepNode> {
        val raw = body["steps"] as? List<*> ?: return emptyList()
        return raw.mapIndexed { index, step ->
            StepNode(index, step as? Map<*, *> ?: emptyMap<Any, Any>(), this)
        }
    }

    /**
     * The `run` texts that are genuinely executed: a step's `run`, minus the steps whose own
     * condition or `continue-on-error` makes execution uncertain (those are reported separately, so
     * the command is still credited as reachable while the advisory setting is flagged).
     */
    fun executableRunTexts(): List<String> =
        steps()
            .filter { it.isExecutable() }
            .mapNotNull { it.stringAt("run") }

    internal fun lines0(): List<String> = document.lines()
}

/** One step of a job. */
internal class StepNode(
    val index: Int,
    private val body: Map<*, *>,
    private val job: JobNode,
) {
    val startLine: Int = job.lineOf("steps") ?: 1

    fun stringAt(key: String): String? = body[key] as? String

    /** The line of a key inside this step, searched from the step's own start. */
    fun lineOf(key: String): Int? {
        val lines = job.lines0()
        val stepIndex = lines.indexOfFirst { it.trimStart().startsWith("run:") || it.trimStart().startsWith("uses:") }
        // A step has no stable name to anchor on, so the line is located from the job region.
        val jobStart = lines.indexOfFirst { it.trimStart().startsWith("${job.name}:") }
        if (jobStart < 0) return null
        val from = if (stepIndex >= 0) minOf(jobStart, stepIndex) else jobStart
        val hit = lines.drop(from).indexOfFirst { it.trimStart().startsWith("$key:") }
        return if (hit >= 0) from + hit + 1 else null
    }

    fun isAdvisory(): Boolean = body["continue-on-error"] == true || body["continue-on-error"] == "true"

    /**
     * A condition that can skip the step. `if: always()` is the documented exception for artifact
     * upload, and `if: ${{ always() }}` is the same thing spelled in an expression.
     *
     * The condition is read as a scalar, not as a string: YAML resolves `false`, `0` and `null` to
     * their own types, and a guard that only understood quoted strings would miss the most obvious
     * spelling of "never run this" (`TASK-098`, `B2-R01` item 4).
     */
    fun skipCondition(): String? {
        val condition = body["if"] ?: return null
        val text = condition.toString()
        val normalised = text.replace("\${" + "{", "").replace("}" + "}", "").trim()
        return if (normalised.equals("always()", ignoreCase = true)) null else text
    }

    /** Whether the step is a `run` step that is not made uncertain by its own settings. */
    fun isExecutable(): Boolean = stringAt("run") != null && skipCondition() == null && !isAdvisory()
}
