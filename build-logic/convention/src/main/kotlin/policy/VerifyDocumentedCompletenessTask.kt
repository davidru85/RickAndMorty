package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

/**
 * The documentation completeness gate (`DEFINITION.md` §6, `TASK-068`).
 *
 * `DEFINITION.md` owns the eight conditions (`DOC1`…`DOC8`) and states that the gate is evaluated on
 * the whole documentation set, re-run at each milestone and release. Until this task, only the
 * documented-command rows of `README.md` were machine-checked, so `DOC1`–`DOC8` were review habits.
 * A completeness gate that only a reader can apply decays exactly the way the policy checks of
 * `TESTING.md` §3.3 would if they were prose, so the conditions a build can decide are enforced here:
 *
 * - `DOC1` every document carries the header block of `AGENTS.md` §10;
 * - `DOC2` every relative link resolves, whatever kind of file it names;
 * - `DOC4` an identifier is defined once in the file that defines it: a `DEC-###`, `ADR-####` or
 *   `TASK-###` table row, and a heading that opens with an identifier (`LOG-####`, `REQ-*`, `IC-###`);
 * - `DOC6` every `CONF-###`/`GAP-###` row in the audit fills the columns its table declares, and an
 *   open one names a severity;
 * - `DOC8` no placeholder marker and no angle-bracket template survives in prose, table rows
 *   included (an assumption with an owner and a date is documentation and is not a placeholder,
 *   which `DEFINITION.md` §6 states explicitly; a marker quoted in code is prose about the rule).
 *
 * `DOC3`, `DOC5` and `DOC7` are review conditions — they need a judgement about whether two files
 * claim one topic, whether every requirement maps to a task and a test, and whether the audit matches
 * the tree. They stay with the review and are named in the task's report rather than approximated by
 * a text search that would pass without settling them.
 *
 * The task fails closed and reports a repository-relative path with a line where it has one; it never
 * rewrites a document. `TEST-UNIT-063` holds each decision.
 */
public abstract class VerifyDocumentedCompletenessTask : DefaultTask(), VerificationTask {
    /** The documentation files the gate reads. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val documents: ConfigurableFileCollection

    /** The repository root, for its own top-level documents and for relative-path rendering. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    init {
        group = "verification"
        description =
            "DEFINITION.md §6 documentation completeness: DOC1 headers, DOC2 links, DOC4 identifier " +
                "uniqueness, DOC6 audit rows and DOC8 placeholders (TASK-068)."
    }

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val files = documents.files.filter { it.isFile }.toList().sortedBy { it.path }
        val findings = mutableListOf<String>()

        files.forEach { file ->
            val text = file.readText()
            val lines = text.lines()
            val relative = file.relativeTo(root).path
            checkHeader(relative, text, findings)
            checkLinks(relative, file, root, text, findings)
            checkPlaceholders(relative, lines, findings)
            checkDefinitions(relative, text, findings)
            if (relative == "docs/DOCUMENTATION_AUDIT.md") checkAuditRows(relative, lines, findings)
        }

        val report =
            buildString {
                appendLine("DEFINITION.md §6 documentation completeness (TASK-068)")
                appendLine("documents inspected: ${files.size}")
                appendLine("findings: ${findings.size}")
                findings.forEach { appendLine("  - $it") }
            }
        val reportFile = root.resolve("build/reports/documentation-completeness/report.txt")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(report)

        check(findings.isEmpty()) {
            "the documentation completeness gate failed (DEFINITION.md §6):\n" +
                findings.joinToString("\n") { "  $it" } +
                "\nThe report is at ${reportFile.absolutePath}."
        }
    }

    /** `DOC1`: the header block of `AGENTS.md` §10, on every document. */
    private fun checkHeader(
        relative: String,
        text: String,
        findings: MutableList<String>,
    ) {
        // The translated README is the one documented exception: it mirrors `README.md` and owns no
        // topic of its own (`DEC-047`), so it carries no `Authoritative for:` block.
        if (relative == "README.es.md") return
        val head = text.take(HEADER_WINDOW)
        MISSING_HEADER_FIELDS.filterNot { head.contains(it) }.forEach { field ->
            findings += "$relative: the header block is missing `$field` (AGENTS.md §10, DOC1)"
        }
    }

    /** `DOC2`: every relative link in prose resolves, to a document, a source file or an export. */
    private fun checkLinks(
        relative: String,
        file: File,
        root: File,
        text: String,
        findings: MutableList<String>,
    ) {
        prose(text.lines()).forEach { (_, line) ->
            LINK.findAll(line).forEach { match ->
                val target = match.groupValues[1].substringBefore('#')
                // An anchor in the same document and a URL with a scheme are not relative links.
                if (target.isEmpty() || SCHEME.containsMatchIn(target)) return@forEach
                if (!File(file.parentFile, target).canonicalFile.exists()) {
                    findings += "$relative: the link `$target` does not resolve (DOC2)"
                }
            }
        }
    }

    /**
     * `DOC8`: no placeholder marker and no angle-bracket template, in any line of prose — a table row
     * is prose too, and is where most of the set's content lives.
     *
     * A marker quoted in a fenced block or an inline code span is prose *about* the rule, which the
     * gate's own documents need. A template (`docs/templates/`, the ADR template) keeps the
     * metavariables its author replaces (`AGENTS.md` §3). An assumption with an owner and a date is
     * not a placeholder.
     */
    private fun checkPlaceholders(
        relative: String,
        lines: List<String>,
        findings: MutableList<String>,
    ) {
        val template = relative.startsWith("docs/templates/") || relative == "docs/adr/0000-adr-template.md"
        prose(lines).forEach { (index, line) ->
            PLACEHOLDER.findAll(line).forEach { match ->
                findings += "$relative:${index + 1}: the placeholder `${match.value}` remains (DOC8)"
            }
            if (template) return@forEach
            ANGLE_BRACKETS.findAll(line).forEach { match ->
                val content = match.groupValues[1]
                val element = content.substringBefore(' ').trimEnd('/').lowercase()
                if (element !in HTML_ELEMENTS && !content.contains("://") && !content.contains('@')) {
                    findings += "$relative:${index + 1}: the template `${match.value}` remains (DOC8)"
                }
            }
        }
    }

    /**
     * The lines outside a fenced block, with their index. Unless [keepCode] is set, an inline code
     * span is removed, so a quoted marker, path or template is not read as the thing it quotes; a
     * definition keeps it, because an identifier cell or heading is often written in code.
     */
    private fun prose(
        lines: List<String>,
        keepCode: Boolean = false,
    ): List<Pair<Int, String>> {
        var fenced = false
        return lines.mapIndexedNotNull { index, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fenced = !fenced
                return@mapIndexedNotNull null
            }
            when {
                fenced -> null
                keepCode -> index to line
                else -> index to CODE_SPAN.replace(line, "")
            }
        }
    }

    /**
     * `DOC4`, the decidable half: an identifier is defined once in the file that defines it.
     *
     * A definition is a table row whose **own** first cell is a `DEC-###`, `ADR-####` or `TASK-###`
     * and nothing else, or a heading that opens with an identifier (`### LOG-0114 · …`,
     * `#### REQ-FUNC-001 — …`, `### IC-007 …`). A block table, a phase table or a membership list
     * names a task inside a text cell, which is a citation; counting those was the first draft's
     * false positive. Whether one id was given to two different artefacts in two different tables
     * stays with the review.
     */
    private fun checkDefinitions(
        relative: String,
        text: String,
        findings: MutableList<String>,
    ) {
        // A decision has one definition (§2) and a deferred register (§4) that lists it again by
        // design, so only the region before the deferred register is checked for definitions.
        val definitionText = if (relative == "docs/DECISION_BOARD.md") text.substringBefore("## 4.") else text
        val defined = mutableMapOf<String, Int>()
        prose(definitionText.lines(), keepCode = true).forEach { (_, line) ->
            val trimmed = line.trimStart()
            val id =
                when {
                    trimmed.startsWith("|") ->
                        trimmed.removePrefix("|").substringBefore('|').trim().trim('`').takeIf { DEFINED_IN_ROWS.matches(it) }
                    trimmed.startsWith("#") -> DEFINED_IN_HEADINGS.find(trimmed)?.groupValues?.get(1)
                    else -> null
                }
            if (id != null) defined[id] = (defined[id] ?: 0) + 1
        }
        defined.filterValues { it > 1 }.keys.sorted().forEach { id ->
            findings += "$relative: `$id` is defined more than once (DOC4)"
        }
    }

    /**
     * `DOC6`: every conflict and gap row fills the columns its table declares, and every row of an
     * open register names a severity.
     *
     * The audit has two row shapes and the check respects both: §6.1 is the *resolved* table
     * (`ID | Item | Resolution`) where a severity would be wrong, while §6.2 and §6.3 are the open
     * registers (`ID | Sev | … | Owner | …`). Each table's header row declares the shape its rows are
     * held to, and a table whose header is not an `ID` table is not an id register.
     */
    private fun checkAuditRows(
        relative: String,
        lines: List<String>,
        findings: MutableList<String>,
    ) {
        var inOpenRegister = false
        var expectedColumns = 0
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) {
                // Only the open registers carry a severity column; the resolved table does not.
                inOpenRegister = OPEN_REGISTER_HEADINGS.any { trimmed == it }
                expectedColumns = 0
                return@forEachIndexed
            }
            if (!trimmed.startsWith("|")) return@forEachIndexed
            val cells = cells(trimmed)
            if (cells.first() == "ID") {
                // The header row declares the shape this table asserts.
                expectedColumns = cells.size - 1
                return@forEachIndexed
            }
            val id = cells.first().trim('`')
            if (expectedColumns == 0 || !AUDIT_ID.matches(id)) return@forEachIndexed
            val columns = cells.drop(1)
            val populated = columns.count { it.isNotEmpty() }
            if (populated < expectedColumns) {
                findings +=
                    "$relative:${index + 1}: `$id` carries $populated of the $expectedColumns " +
                        "columns its table declares (DOC6)"
            }
            if (inOpenRegister && columns.none { SEVERITY.matches(it.trim('`', '*')) }) {
                findings += "$relative:${index + 1}: `$id` names no severity (S1–S3) (DOC6)"
            }
        }
    }

    /**
     * The cells of a table row. A literal `|` inside a cell is escaped (`\|`) in a GitHub table,
     * even inside a code span, so only an unescaped pipe separates cells.
     */
    private fun cells(row: String): List<String> =
        row
            .removePrefix("|")
            .removeSuffix("|")
            .split(CELL_SEPARATOR)
            .map { it.trim() }

    private companion object {
        const val HEADER_WINDOW = 2500

        val MISSING_HEADER_FIELDS =
            listOf("Status:", "Last verified:", "Owner:", "Authoritative for:", "Inputs:")

        /** An inline or image link's destination, up to the first space (a title may follow it). */
        val LINK = Regex("""\]\(([^)\s]+)""")

        /** A destination with a scheme (`https:`, `mailto:`) is not a relative link. */
        val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")

        /** An inline code span, of any backtick run length. */
        val CODE_SPAN = Regex("""(`+).+?\1""")

        val PLACEHOLDER = Regex("""\b(TODO|FIXME|TBD)\b""")

        /** A bracketed name: a template metavariable, unless it is an HTML element or an autolink. */
        val ANGLE_BRACKETS = Regex("""<([A-Za-z][^<>]*)>""")

        val HTML_ELEMENTS =
            setOf("a", "b", "br", "code", "details", "em", "i", "img", "kbd", "p", "pre", "strong", "sub", "summary", "sup")

        val DEFINED_IN_ROWS = Regex("""DEC-\d{3}|ADR-\d{4}|TASK-\d{3}""")

        val DEFINED_IN_HEADINGS = Regex("""^#{2,6}\s+`?(LOG-\d{4}|REQ-[A-Z]+-\d{3}|IC-\d{3}|ADR-\d{4}|DEC-\d{3}|TASK-\d{3})\b""")

        val CELL_SEPARATOR = Regex("""(?<!\\)\|""")

        val AUDIT_ID = Regex("""(CONF|GAP)-\d{2,3}""")

        /** The audit's open registers, where every row names a severity (`DEFINITION.md` §6 DOC6). */
        val OPEN_REGISTER_HEADINGS = listOf("### 6.2 Open gaps", "### 6.3 Open conflicts")

        val SEVERITY = Regex("""S[123]""")
    }
}
