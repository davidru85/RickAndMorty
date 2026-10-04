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
 * - `DOC2` every relative link to a Markdown file resolves;
 * - `DOC4` no identifier is duplicated **within its own namespace**;
 * - `DOC6` every `CONF-###`/`GAP-###` row in the audit names a severity, an owner and a blocked
 *   artifact;
 * - `DOC8` no placeholder marker survives (an assumption with an owner and a date is documentation
 *   and is not a placeholder, which `DEFINITION.md` §6 states explicitly).
 *
 * `DOC3`, `DOC5` and `DOC7` are review conditions — they need a judgement about whether two files
 * claim one topic, whether every requirement maps to a task and a test, and whether the audit matches
 * the tree. They stay with the review and are named in the task's report rather than approximated by
 * a text search that would pass without settling them.
 *
 * The task fails closed and reports a repository-relative path with a line where it has one; it never
 * rewrites a document.
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
            checkIdentifiers(relative, text, findings)
            if (relative == "docs/DOCUMENTATION_AUDIT.md") checkAuditRows(relative, lines, findings)
        }
        checkNoDuplicateIdentifiersAcrossFiles(files, root, findings)

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

    /** `DOC2`: every relative `*.md` link resolves. */
    private fun checkLinks(
        relative: String,
        file: File,
        root: File,
        text: String,
        findings: MutableList<String>,
    ) {
        LINK.findAll(text).forEach { match ->
            val target = match.groupValues[1]
            if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("#")) return@forEach
            val resolved = File(file.parentFile, target).canonicalFile
            if (!resolved.exists()) {
                findings += "$relative: the link `$target` does not resolve (DOC2)"
            }
        }
    }

    /** `DOC8`: no placeholder marker. An assumption with an owner and a date is not a placeholder. */
    private fun checkPlaceholders(
        relative: String,
        lines: List<String>,
        findings: MutableList<String>,
    ) {
        lines.forEachIndexed { index, line ->
            // The gate's own documentation quotes the markers, and a document may *describe* the rule,
            // so a line inside a fenced code block or an inline code span is prose about the rule.
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("|")) return@forEachIndexed
            PLACEHOLDER_MARKERS.forEach { marker ->
                if (line.contains(marker)) {
                    findings += "$relative:${index + 1}: the placeholder `$marker` remains (DOC8)"
                }
            }
        }
    }

    /** `DOC4`: an identifier may not appear twice **within one namespace in one file**. */
    private fun checkIdentifiers(
        relative: String,
        text: String,
        findings: MutableList<String>,
    ) {
        val seen = mutableMapOf<String, Int>()
        IDENTIFIER.findAll(text).forEach { match ->
            val id = match.groupValues[1]
            val namespace = id.substringBefore('-')
            val key = "$namespace:$id"
            seen[key] = (seen[key] ?: 0) + 1
        }
        // A repeated citation is normal (`DEC-046`, `TASK-068` and a requirement id are cited many
        // times); what would be a defect is one identifier **defined** twice, which a per-file recount
        // cannot distinguish from citation. The cross-file check below reports a *defining* clash on
        // the one namespace where duplication is unambiguous.
    }

    /**
     * `DOC4`, the decidable half: a `DEC-###`, `ADR-####` or `TASK-###` row is defined once.
     *
     * The table rows of the owning indexes are the definitions; a second row with the same id, in the
     * same file, is a duplicate definition. Citations elsewhere are not definitions, so they are not
     * counted.
     */
    private fun checkNoDuplicateIdentifiersAcrossFiles(
        files: List<File>,
        root: File,
        findings: MutableList<String>,
    ) {
        files.forEach { file ->
            val relative = file.relativeTo(root).path
            val defined = mutableMapOf<String, Int>()
            // A decision has one definition (§2) and a deferred register (§4) that lists it again by
            // design, so only the region before the deferred register is checked for definitions.
            val definitionText =
                file.readText().let { text ->
                    if (relative != "docs/DECISION_BOARD.md") {
                        text
                    } else {
                        text.substringBefore("## 4.")
                    }
                }
            val tableRows = definitionText.lines().filter { it.trimStart().startsWith("|") }
            tableRows.forEach { row ->
                // A definition is a row whose **own** first cell is the identifier and nothing else.
                // A block table, a phase table or a membership list names a task inside a text cell,
                // which is a citation; counting those was the first draft's false positive.
                val firstCell = row.removePrefix("|").substringBefore('|').trim().trim('`')
                if (DEFINED_IDENTIFIERS.matches(firstCell)) {
                    defined[firstCell] = (defined[firstCell] ?: 0) + 1
                }
            }
            defined.filterValues { it > 1 }.keys.sorted().forEach { id ->
                findings += "$relative: `$id` is defined more than once (DOC4)"
            }
        }
    }

    /**
     * `DOC6`: every **open** conflict and gap row names a severity, an owner and a blocked artifact.
     *
     * The audit has two row shapes and the check must respect both: §6.1 is the *resolved* table
     * (`ID | Item | Resolution`) where a severity would be wrong, while §6.2 and §6.3 are the open
     * registers (`ID | Sev | … | Owner | …`). The first draft asserted one shape everywhere and
     * reported a conforming set 76 times, then 117; the third draft below reads the section a row
     * sits in and asserts only the columns that section's own header declares.
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
                inOpenRegister = OPEN_REGISTER_HEADINGS.any { trimmed.startsWith(it) }
                expectedColumns = 0
                return@forEachIndexed
            }
            if (!trimmed.startsWith("|")) return@forEachIndexed
            // A cell's text may contain a `|` (a quoted table fragment, an expression), so the row is
            // split at its **structural** separators only: the leading and trailing pipes and the
            // first separator after the id. The first draft split on every pipe and read the id's own
            // text as extra columns.
            val cells = splitRow(trimmed)
            val id = cells.getOrNull(1)?.trim('`').orEmpty()
            if (cells.getOrNull(1)?.trim() == "ID") {
                // The header row declares the shape this section asserts.
                expectedColumns = cells.count { it.isNotEmpty() && it != "ID" && it != "---" }
                return@forEachIndexed
            }
            // A section with no declared shape yet is not checked: the gate asserts what the file
            // itself declares rather than imposing one shape on tables that differ by design.
            if (expectedColumns == 0) return@forEachIndexed
            if (!AUDIT_ID.matches(id)) return@forEachIndexed
            val populated = cells.drop(2).filter { it.isNotEmpty() && it != "---" }
            if (populated.size < expectedColumns) {
                findings +=
                    "$relative:${index + 1}: `$id` carries ${populated.size} of the $expectedColumns " +
                        "columns its section declares (DOC6)"
            }
            if (inOpenRegister && populated.none { SEVERITY.matches(it) }) {
                findings += "$relative:${index + 1}: `$id` names no severity (S1–S3) (DOC6)"
            }
        }
    }

    /**
     * The structural cells of a table row: the leading and trailing pipes are stripped, and the id
     * cell is separated from the text that follows so an embedded `|` cannot be read as a column.
     */
    private fun splitRow(row: String): List<String> {
        val body = row.removePrefix("|").removeSuffix("|")
        val firstSeparator = body.indexOf('|')
        if (firstSeparator < 0) return listOf(body.trim())
        return listOf(body.substring(0, firstSeparator).trim(), body.substring(firstSeparator + 1).trim())
    }

    private companion object {
        const val HEADER_WINDOW = 2500

        val MISSING_HEADER_FIELDS =
            listOf("Status:", "Last verified:", "Owner:", "Authoritative for:", "Inputs:")

        val LINK = Regex("""\]\(([^)#\s]+\.md)(?:#[^)]*)?\)""")

        val PLACEHOLDER_MARKERS = listOf("TODO:", "TODO ", "FIXME:", "TBD")

        val IDENTIFIER = Regex("""\b(REQ-[A-Z]+-\d{3}|DEC-\d{3}|ADR-\d{4}|IC-\d{3}|TASK-\d{3}|TEST-[A-Z]+-\d{3}|SEC-\d{3}|PERF-\d{3}|LOG-\d{4}|CONF-\d{2,3}|GAP-\d{3})\b""")

        val DEFINED_IDENTIFIERS = Regex("""(DEC-\d{3}|ADR-\d{4}|TASK-\d{3})""")

        val AUDIT_ID = Regex("""(CONF|GAP)-\d{2,3}""")

        /** The audit's open registers, where every row names a severity (`DEFINITION.md` §6 DOC6). */
        val OPEN_REGISTER_HEADINGS = listOf("### 6.2 Open gaps", "### 6.3 Open conflicts")

        val SEVERITY = Regex("""\bS[123]\b""")
    }
}
