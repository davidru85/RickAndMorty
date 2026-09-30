package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The Markdown table reader shared by the three policy tasks (`TEST-UNIT-013`,
 * `TEST-UNIT-014`, `TEST-UNIT-051`; DEC-061).
 *
 * A marked table is the contiguous block of lines starting with `|` between a
 * `begin` and an `end` HTML comment marker. Row 1 is the header, row 2 the
 * separator, and every later row is a data row. A malformed table yields an
 * empty result rather than an exception, so a violation is reported as a policy
 * failure and never as a crash.
 */
internal object MarkdownTable {

    /** One parsed table: the begin/end marker line numbers and the raw cell rows. */
    data class Parsed(
        val beginLine: Int,
        val endLine: Int,
        val header: List<String>,
        val rows: List<Row>,
    ) {
        /** One data row with its source line number, so a violation can name `file:line`. */
        data class Row(val line: Int, val cells: List<String>)
    }

    /** Reads the marked table, or `null` when the markers are absent. */
    fun parse(file: File, beginMarker: String, endMarker: String): Parsed? {
        val lines = file.readLines()
        val begin = lines.indexOfFirst { it.trim() == beginMarker }
        if (begin < 0) return null
        val end = lines.withIndex().drop(begin + 1).firstOrNull { it.value.trim() == endMarker }?.index ?: return null

        val block = lines.subList(begin + 1, end).withIndex()
            .map { (offset, value) -> (begin + 1 + offset) to value }
            .filter { (_, value) -> value.trimStart().startsWith("|") }

        if (block.isEmpty()) return Parsed(begin + 1, end + 1, emptyList(), emptyList())

        val header = cells(block.first().second)
        val dataRows = block.drop(2).map { (index, text) -> Parsed.Row(index + 1, cells(text)) }
        return Parsed(begin + 1, end + 1, header, dataRows)
    }

    /** Splits a table row on `|`, trims each cell and drops the two outer empty cells. */
    fun cells(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split('|').map { it.trim() }
    }

    /** The single code-span contents of a cell, or `null` when the cell does not hold exactly one. */
    fun singleCodeSpan(cell: String): String? {
        val spans = codeSpans(cell)
        return spans.singleOrNull()
    }

    /** Every code-span contents in a cell, in order. */
    fun codeSpans(cell: String): List<String> = CODE_SPAN.findAll(cell).map { it.groupValues[1] }.toList()

    private val CODE_SPAN = Regex("`([^`]+)`")
}
