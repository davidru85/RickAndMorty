package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-030` — the advisory register is complete or legitimately empty (`REQ-SEC-006`,
 * `AC-REQ-SEC-006-1`).
 *
 * `SECURITY.md` §11.2 defines the twelve columns and §11.3 holds the register. A row must carry all
 * twelve, none may be blank and none may be a placeholder; the empty register §11.1 requires passes,
 * so the check cannot be satisfied by inventing a row.
 */
class AdvisoryRegisterTest {

    private val columns =
        listOf(
            "Advisory id",
            "Affected dependency / component",
            "Severity",
            "Affected versions",
            "Detection source",
            "Impact",
            "Mitigation",
            "Owner",
            "Due date",
            "Status",
            "Verification evidence",
            "Related issue / PR",
        )

    private fun columnDefinition(columns: List<String> = this.columns): String =
        buildString {
            appendLine("### 11.2 Columns")
            appendLine()
            appendLine("| Column | Meaning |")
            appendLine("| --- | --- |")
            columns.forEach { appendLine("| `$it` | what the `$it` column means |") }
        }

    private fun cells(): List<String> = columns.mapIndexed { index, column -> if (index == 0) "`SEC-001`" else "value for $column" }

    private fun row(cells: List<String> = cells()): String = "| " + cells.joinToString(" | ") + " |"

    private fun document(
        register: List<String> = emptyList(),
        columns: List<String> = this.columns,
        registerColumns: List<String> = columns,
    ): String =
        buildString {
            appendLine("## 11. Security advisory register")
            appendLine()
            appendLine(columnDefinition(columns))
            appendLine("### 11.3 Register")
            appendLine()
            appendLine("| " + registerColumns.joinToString(" | ") + " |")
            appendLine("| " + registerColumns.joinToString(" | ") { "---" } + " |")
            register.forEach { appendLine(it) }
        }

    private fun scan(contents: String): List<Violation> {
        val root = kotlin.io.path.createTempDirectory("advisory-register").toFile()
        val file = File(root, AdvisoryRegisterPolicy.DOCUMENT).apply { parentFile.mkdirs(); writeText(contents) }
        return AdvisoryRegisterPolicy.scan(file, root)
    }

    @Test
    fun `TEST-UNIT-030 the empty register passes`() {
        assertEquals(emptyList(), scan(document()).map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-030 a complete row passes`() {
        assertEquals(emptyList(), scan(document(register = listOf(row()))).map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-030 a row with the wrong cell count is reported`() {
        val findings = scan(document(register = listOf(row(cells().dropLast(1)))))

        assertTrue(findings.any { it.reason.contains("12") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-030 a blank cell is reported`() {
        val cells = cells().toMutableList()
        cells[5] = ""
        val findings = scan(document(register = listOf(row(cells))))

        assertTrue(findings.any { it.reason.contains("blank") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-030 each placeholder marker is reported`() {
        listOf("<impact>", "TODO", "TBD", "example").forEach { marker ->
            val cells = cells().toMutableList()
            cells[5] = marker
            val findings = scan(document(register = listOf(row(cells))))

            assertTrue(findings.any { it.reason.contains(marker) }, "$marker must be reported: ${findings.map { it.toString() }}")
        }
    }

    @Test
    fun `TEST-UNIT-030 a §11_2 column list that is not the twelve columns is reported`() {
        val findings = scan(document(register = listOf(row(cells().take(11) + "`TASK-001`")), columns = columns.dropLast(1)))

        assertTrue(findings.isNotEmpty(), "a §11.2 list with eleven columns must be reported")
        assertTrue(findings.any { it.reason.contains("11") || it.reason.contains("12") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-030 a register whose columns differ from §11_2 is reported`() {
        val findings =
            scan(
                document(
                    register = listOf(row()),
                    registerColumns = columns.dropLast(1) + columns.first(),
                ),
            )

        assertTrue(findings.isNotEmpty(), "a register table whose columns are not §11.2's must be reported")
    }
}
