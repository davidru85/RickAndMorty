package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The Markdown table reader shared by the three policy tasks (`TEST-UNIT-013`,
 * `TEST-UNIT-014`, `TEST-UNIT-051`; DEC-061).
 *
 * The grammar is enforced rather than assumed (`F-06`):
 *
 * - exactly one begin marker followed by one end marker; a missing, reversed or
 *   duplicate marker is a violation and never an exception;
 * - between the markers, blank lines are allowed only before the header and after the
 *   final data row, and the table itself is one contiguous run of lines beginning with
 *   `|`, so non-table content inside that run is a violation;
 * - the separator row follows the header, has the same number of cells, and every cell
 *   is a Markdown separator;
 * - at least a header, a separator and one data row are required.
 *
 * Structure problems are reported through [Result.problems] so the owning task runs every
 * independent rule; the parser never throws.
 */
internal object MarkdownTable {

    /** One data row with its source line number, so a violation can name `file:line`. */
    data class Row(val line: Int, val cells: List<String>)

    /** The parsed table, plus whatever is structurally wrong with it. */
    data class Result(
        val header: List<String>,
        val rows: List<Row>,
        val problems: List<Problem>,
    )

    /** A structural problem, located by line. */
    data class Problem(val line: Int, val reason: String)

    /** The separator row's cell, as the current tables write it. */
    private val SEPARATOR_CELL = Regex("^:?-{3,}:?$")

    /**
     * Reads the marked table. `problems` is empty only for a well-formed table; callers
     * report them and continue.
     */
    fun parse(file: File, beginMarker: String, endMarker: String): Result {
        val lines = file.readLines()
        // A marker inside a fenced example is documentation, not the real table (`TEST-UNIT-030`,
        // `TEST-UNIT-027`): only a marker outside every fenced block can delimit a parsed table.
        val fenced = fencedLines(lines)
        val begins = lines.withIndex().filter { !fenced[it.index] && it.value.trim() == beginMarker }.map { it.index }
        val ends = lines.withIndex().filter { !fenced[it.index] && it.value.trim() == endMarker }.map { it.index }
        val problems = mutableListOf<Problem>()

        if (begins.isEmpty() || ends.isEmpty()) {
            problems += Problem(1, "the marked table is missing (markers `$beginMarker` / `$endMarker`)")
            return Result(emptyList(), emptyList(), problems)
        }
        if (begins.size > 1 || ends.size > 1) {
            problems += Problem(begins.first() + 1, "the marked table declares its markers more than once")
        }
        val begin = begins.first()
        val end = ends.first()
        if (end < begin) {
            problems += Problem(begin + 1, "the marked table's end marker precedes its begin marker")
            return Result(emptyList(), emptyList(), problems)
        }

        val block = lines.subList(begin + 1, end)
        val firstTable = block.indexOfFirst { it.trimStart().startsWith("|") }
        if (firstTable < 0) {
            problems += Problem(begin + 1, "the marked table has no rows")
            return Result(emptyList(), emptyList(), problems)
        }
        val lastTable = block.indexOfLast { it.trimStart().startsWith("|") }

        // Blank lines are allowed only before the header and after the final data row.
        block.take(firstTable).forEachIndexed { offset, text ->
            if (text.isNotBlank()) {
                problems += Problem(begin + 2 + offset, "non-table content precedes the marked table")
            }
        }
        block.drop(lastTable + 1).forEachIndexed { offset, text ->
            if (text.isNotBlank()) {
                problems += Problem(begin + 2 + lastTable + offset, "non-table content interrupts the marked table")
            }
        }
        block.subList(firstTable, lastTable + 1).forEachIndexed { offset, text ->
            if (!text.trimStart().startsWith("|")) {
                problems += Problem(begin + 2 + firstTable + offset, "non-table content interrupts the marked table")
            }
        }

        val tableStart = begin + 1 + firstTable
        val tableLines = lines.subList(tableStart, begin + 1 + lastTable + 1)
        if (tableLines.size < 3) {
            problems += Problem(tableStart + 1, "the marked table needs a header, a separator and at least one data row")
            val header = cells(tableLines.firstOrNull().orEmpty())
            return Result(header, emptyList(), problems)
        }

        val header = cells(tableLines.first())
        val separator = cells(tableLines[1])
        if (separator.size != header.size) {
            problems += Problem(tableStart + 2, "malformed Markdown separator row")
        } else if (separator.any { !SEPARATOR_CELL.matches(it.trim()) }) {
            problems += Problem(tableStart + 2, "malformed Markdown separator row")
        }

        val rows = tableLines.drop(2).withIndex()
            .map { (offset, text) -> Row(tableStart + 3 + offset, cells(text)) }
        return Result(header, rows, problems)
    }

    /** Splits a table row on `|`, trims each cell and drops the two outer empty cells. */
    fun cells(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split('|').map { it.trim() }
    }

    /**
     * The single code-span contents of a cell, or `null` when the cell is not **exactly**
     * one code span: surrounding prose disqualifies it (`F-06`).
     */
    fun singleCodeSpan(cell: String): String? {
        val text = cell.trim()
        val match = CODE_SPAN.matchEntire(text) ?: return null
        return match.groupValues[1]
    }

    /**
     * The code-span contents of a cell that is exactly a comma-and-space-separated list of
     * code spans, or `null` when the cell carries prose or a malformed list.
     */
    fun codeSpanList(cell: String): List<String>? {
        val text = cell.trim()
        if (text == "—") return emptyList()
        return text.split(", ").map { it.trim() }.takeIf { parts -> parts.all { CODE_SPAN.matches(it) } }
            ?.map { CODE_SPAN.matchEntire(it)!!.groupValues[1] }
    }

    /** Every code-span contents in a cell, in order, wherever they appear. */
    fun codeSpans(cell: String): List<String> = CODE_SPAN.findAll(cell).map { it.groupValues[1] }.toList()

    /**
     * One boolean per line: whether the line sits inside a fenced code block (``` or ~~~).
     *
     * The markers live in the prose around the block, and a block that documents the table must not
     * be read as the table itself. The fences do not nest, so a toggle is enough.
     */
    internal fun fencedLines(lines: List<String>): BooleanArray {
        val fenced = BooleanArray(lines.size)
        var inside = false
        lines.forEachIndexed { index, line ->
            if (FENCE.containsMatchIn(line)) {
                // Both fence lines are marked, so neither the opener nor the closer can act as a marker.
                fenced[index] = true
                inside = !inside
            } else {
                fenced[index] = inside
            }
        }
        return fenced
    }

    private val CODE_SPAN = Regex("`([^`]+)`")
    private val FENCE = Regex("^\\s*(```|~~~)")
}
