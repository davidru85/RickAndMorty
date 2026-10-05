package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder

/**
 * `TEST-UNIT-063` — the documentation completeness gate decides the conditions of `DEFINITION.md` §6
 * that a build can decide (`TASK-068`).
 *
 * Each case writes a small documentation set and runs the task's action on it. A finding fails the
 * task with a message naming the document and the condition, so the cases assert on that message.
 */
class VerifyDocumentedCompletenessTaskTest {

    private val header =
        listOf(
            "# Fixture",
            "",
            "- **Status:** Active",
            "- **Last verified:** 2026-10-05",
            "- **Owner:** Documentation Maintainer",
            "- **Authoritative for:** the fixture.",
            "- **Inputs:** none.",
            "",
        )

    private fun document(vararg body: String): String = (header + body.toList()).joinToString("\n") + "\n"

    /** Runs the gate over [documents] (path to text) and returns its failure message, or null. */
    private fun verify(vararg documents: Pair<String, String>, extraFiles: List<String> = emptyList()): String? {
        val root = kotlin.io.path.createTempDirectory("documentation-completeness").toFile()
        documents.forEach { (path, text) -> File(root, path).apply { parentFile.mkdirs(); writeText(text) } }
        extraFiles.forEach { path -> File(root, path).apply { parentFile.mkdirs(); writeText("fixture") } }
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val task = project.tasks.register("verifyDocumentedCompleteness", VerifyDocumentedCompletenessTask::class.java).get()
        task.documents.from(documents.map { File(root, it.first) })
        task.rootDirectory.set(root)
        return runCatching { task.verify() }.exceptionOrNull()?.message
    }

    private fun assertFinding(message: String?, vararg fragments: String) {
        assertNotNull(message, "TEST-UNIT-063: the gate must fail; expected ${fragments.toList()}")
        fragments.forEach { fragment ->
            assertTrue(message.contains(fragment), "TEST-UNIT-063: expected `$fragment` in:\n$message")
        }
    }

    // --- the decisions the first implementation already made ---

    @Test
    fun `TEST-UNIT-063 a conforming set passes`() {
        assertNull(
            verify(
                "docs/A.md" to document("See [B](B.md) and [the section](B.md#one).", "", "| ID | Item |", "| --- | --- |", "| TASK-001 | Done |"),
                "docs/B.md" to document("## One", "", "Back to [A](A.md)."),
            ),
        )
    }

    @Test
    fun `TEST-UNIT-063 a missing header field is reported`() {
        val text = document("Body.").replace("- **Inputs:** none.\n", "")
        assertFinding(verify("docs/A.md" to text), "docs/A.md", "Inputs:", "DOC1")
    }

    @Test
    fun `TEST-UNIT-063 a broken Markdown link is reported`() {
        assertFinding(verify("docs/A.md" to document("See [missing](NO_SUCH_FILE.md).")), "docs/A.md", "NO_SUCH_FILE.md", "DOC2")
    }

    @Test
    fun `TEST-UNIT-063 a task defined twice is reported`() {
        val text = document("| ID | Item |", "| --- | --- |", "| TASK-048 | First |", "| TASK-048 | Second |")
        assertFinding(verify("docs/BACKLOG.md" to text), "docs/BACKLOG.md", "TASK-048", "DOC4")
    }

    @Test
    fun `TEST-UNIT-063 an open audit row without a severity is reported`() {
        val text =
            document(
                "### 6.2 Open gaps",
                "",
                "| ID | Sev | Gap | Owner | Blocks |",
                "| --- | --- | --- | --- | --- |",
                "| GAP-001 | High | A gap | Delivery Planner | TASK-001 |",
            )
        assertFinding(verify("docs/DOCUMENTATION_AUDIT.md" to text), "GAP-001", "severity", "DOC6")
    }

    // --- DOC8: a placeholder is found wherever prose can carry one, and only there ---

    @Test
    fun `TEST-UNIT-063 a placeholder inside a table row is reported`() {
        val text = document("| ID | Acceptance |", "| --- | --- |", "| TASK-001 | TBD |")
        assertFinding(verify("docs/BACKLOG.md" to text), "docs/BACKLOG.md:11", "TBD", "DOC8")
    }

    @Test
    fun `TEST-UNIT-063 a placeholder at the end of a line is reported`() {
        assertFinding(verify("docs/A.md" to document("The retry budget is TODO")), "docs/A.md:9", "TODO", "DOC8")
    }

    @Test
    fun `TEST-UNIT-063 a placeholder quoted inside a fenced block is prose about the rule`() {
        assertNull(verify("docs/A.md" to document("```text", "TODO: an example marker", "```")))
    }

    @Test
    fun `TEST-UNIT-063 a placeholder quoted in an inline code span is prose about the rule`() {
        assertNull(verify("docs/A.md" to document("A merged document carries no `TODO:` and no `TBD`.")))
    }

    @Test
    fun `TEST-UNIT-063 an angle-bracket template is reported`() {
        assertFinding(verify("docs/A.md" to document("- **Owner of the fix:** <owner>")), "docs/A.md:9", "<owner>", "DOC8")
    }

    @Test
    fun `TEST-UNIT-063 an html line break and an autolink are not templates`() {
        assertNull(verify("docs/A.md" to document("| A<br>B | <https://rickandmortyapi.com> |")))
    }

    @Test
    fun `TEST-UNIT-063 a template keeps the metavariables an author replaces`() {
        assertNull(
            verify(
                "docs/templates/task.md" to document("- **Task:** <TASK-###>"),
                "docs/adr/0000-adr-template.md" to document("# ADR-<NNNN>: <Decision title>"),
            ),
        )
    }

    // --- DOC2: every relative link resolves, not only a link to a Markdown file ---

    @Test
    fun `TEST-UNIT-063 a broken link to a non-Markdown file is reported`() {
        assertFinding(
            verify("docs/A.md" to document("The rule lives in [the guard](../build-logic/Missing.kt).")),
            "docs/A.md",
            "../build-logic/Missing.kt",
            "DOC2",
        )
    }

    @Test
    fun `TEST-UNIT-063 a link to an existing non-Markdown file passes`() {
        assertNull(
            verify(
                "docs/A.md" to document("The export is [here](figma/screen.png) and the script [here](../tools/x.sh)."),
                extraFiles = listOf("docs/figma/screen.png", "tools/x.sh"),
            ),
        )
    }

    // --- DOC4: an identifier a heading defines is defined once ---

    @Test
    fun `TEST-UNIT-063 a log entry defined twice is reported`() {
        val text = document("### LOG-0114 · 2026-10-04 · First", "", "### LOG-0114 · 2026-10-05 · Second")
        assertFinding(verify("docs/PROJECT_LOG.md" to text), "docs/PROJECT_LOG.md", "LOG-0114", "DOC4")
    }
}
