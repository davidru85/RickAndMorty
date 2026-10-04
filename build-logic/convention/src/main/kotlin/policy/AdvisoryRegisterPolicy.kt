package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * `TEST-UNIT-030` — the advisory register is complete or legitimately empty (`REQ-SEC-006`,
 * `AC-REQ-SEC-006-1`).
 *
 * `SECURITY.md` §11.2 defines the register's columns and §11.3 holds the rows. Every data row must
 * carry the §11.2 column set with no blank cell and no placeholder, and the register is allowed to
 * be empty — indeed §11.1 requires it to stay empty until a real, evidenced finding exists — so an
 * empty table passes and the check cannot be satisfied by inventing a row.
 */
internal object AdvisoryRegisterPolicy {
    const val TEST_ID = "TEST-UNIT-030"

    /** The document that owns the section and the register. */
    const val DOCUMENT = "docs/SECURITY.md"

    /** The column definition the register must instantiate. */
    private val COLUMNS_HEADING = Regex("""^###\s+11\.2\b""")

    /** The register itself. */
    private val REGISTER_HEADING = Regex("""^###\s+11\.3\b""")

    /** A placeholder marker `SECURITY.md` §11.1 forbids in a committed row. */
    private val PLACEHOLDER = Regex("""(?i)(<[^>]+>|\bTODO\b|\bTBD\b|\bexample\b)""")

    private const val EXPECTED_COLUMNS = 12

    /** @param document the `SECURITY.md` whose §11.2 and §11.3 are read. */
    fun scan(document: File, root: File): List<Violation> = buildList {
        val lines = document.readLines()
        val location = document.location(root)
        val columns = columnNames(lines)
        if (columns.isEmpty()) {
            add(Violation(TEST_ID, "$location §11.2", "the register's column definition is missing or is not a table"))
        } else if (columns.size != EXPECTED_COLUMNS) {
            add(
                Violation(
                    TEST_ID,
                    "$location §11.2",
                    "the column definition declares ${columns.size} column(s); §11.2 fixes $EXPECTED_COLUMNS",
                ),
            )
        }

        val registerStart = lines.indexOfFirst { REGISTER_HEADING.containsMatchIn(it) }
        if (registerStart < 0) {
            add(Violation(TEST_ID, "$location §11.3", "the register section is missing"))
            return@buildList
        }
        val headerIndex = (registerStart + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith("|") }
        if (headerIndex == null) {
            add(Violation(TEST_ID, "$location:${registerStart + 1}", "the register holds no table"))
            return@buildList
        }
        val header = MarkdownTable.cells(lines[headerIndex])
        if (columns.isNotEmpty()) {
            val expected = columns.map { it.lowercase() }
            val actual = header.map { it.lowercase() }
            if (expected != actual) {
                add(
                    Violation(
                        TEST_ID,
                        "$location:${headerIndex + 1}",
                        "the register's columns are $header; §11.2 fixes $columns",
                    ),
                )
            }
        }

        var row = headerIndex + 2 // Skip the header and the separator row.
        while (row < lines.size && lines[row].trimStart().startsWith("|")) {
            val cells = MarkdownTable.cells(lines[row])
            val line = row + 1
            if (cells.size != header.size) {
                add(
                    Violation(
                        TEST_ID,
                        "$location:$line",
                        "the register row has ${cells.size} cell(s); the header declares ${header.size}",
                    ),
                )
            } else {
                cells.forEachIndexed { index, cell ->
                    val column = header.getOrNull(index) ?: "column ${index + 1}"
                    when {
                        cell.isBlank() ->
                            add(Violation(TEST_ID, "$location:$line", "the `$column` cell is blank"))
                        PLACEHOLDER.containsMatchIn(cell) ->
                            add(
                                Violation(
                                    TEST_ID,
                                    "$location:$line",
                                    "the `$column` cell carries the placeholder `${PLACEHOLDER.find(cell)?.value}`; " +
                                        "§11.1 admits only a real, evidenced finding",
                                ),
                            )
                    }
                }
            }
            row++
        }
    }

    /**
     * The §11.2 column names: the first cell of each definition row, in order.
     *
     * `SECURITY.md` writes each column as a single code span in the definition row's first cell, so
     * reading those cells recovers the list verbatim; an empty result means the section is absent or
     * malformed.
     */
    private fun columnNames(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { COLUMNS_HEADING.containsMatchIn(it) }
        if (start < 0) return emptyList()
        val header = (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith("|") } ?: return emptyList()
        val columns = mutableListOf<String>()
        var index = header + 2 // Skip the header and the separator row.
        while (index < lines.size && lines[index].trimStart().startsWith("|")) {
            MarkdownTable.cells(lines[index]).firstOrNull()?.let(MarkdownTable::singleCodeSpan)?.let { columns += it }
            index++
        }
        return columns
    }
}
