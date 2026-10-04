package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * `TEST-UNIT-031` — the vulnerability-reporting route is stated identically in `SECURITY.md` §10 and
 * `CONTRIBUTING.md` §10 (`REQ-SEC-007`, `AC-REQ-SEC-007-1`).
 *
 * `SECURITY.md` §10 owns the route as a block-quoted statement and `CONTRIBUTING.md` §10 reproduces
 * it verbatim. The rule extracts the block from both and compares it line for line, so a paraphrase,
 * a different channel or a missing block is a finding. Either document's absence fails closed rather
 * than passing on the other one.
 */
internal object ReportingRoutePolicy {
    const val TEST_ID = "TEST-UNIT-031"

    const val SECURITY = "docs/SECURITY.md"
    const val CONTRIBUTING = "docs/CONTRIBUTING.md"

    /** The block's title line, which marks where the route statement starts. */
    private val TITLE = Regex("""^>\s*###\s+Reporting a vulnerability\s*$""")

    /** A block-quoted line's one leading level, as the documents write it. */
    private val QUOTE = Regex("""^>\s?""")

    /**
     * @param security the `SECURITY.md` whose §10 owns the route.
     * @param contributing the `CONTRIBUTING.md` whose §10 mirrors it.
     */
    fun scan(security: File, contributing: File, root: File): List<Violation> = buildList {
        val owner = block(security, root, this)
        val copy = block(contributing, root, this)
        if (owner.isEmpty() || copy.isEmpty()) return@buildList
        if (owner != copy) {
            val differing = (0 until maxOf(owner.size, copy.size)).firstOrNull { index ->
                owner.getOrNull(index) != copy.getOrNull(index)
            }
            val description =
                if (differing == null) {
                    "the two statements hold a different number of lines (${owner.size} against ${copy.size})"
                } else {
                    "line ${differing + 1} differs: SECURITY.md states `${owner[differing]}`, " +
                        "CONTRIBUTING.md states `${copy.getOrNull(differing) ?: "<absent>"}`"
                }
            add(
                Violation(
                    TEST_ID,
                    CONTRIBUTING + " §10",
                    "$description; `SECURITY.md` §10 is authoritative and the copy must be corrected to it",
                ),
            )
        }
    }

    /**
     * The block-quoted route statement, with each line's single quote level removed.
     *
     * The block runs from its title to the last contiguous quoted line (blank lines included, so a
     * trailing paragraph separated by one blank line does not become part of it).
     */
    private fun block(file: File, root: File, sink: MutableList<Violation>): List<String> {
        if (!file.isFile) {
            sink += Violation(TEST_ID, file.location(root), "the document that states the reporting route is missing")
            return emptyList()
        }
        val lines = file.readLines()
        val start = lines.indexOfFirst { TITLE.containsMatchIn(it) }
        if (start < 0) {
            sink += Violation(TEST_ID, file.location(root), "the `Reporting a vulnerability` block is missing")
            return emptyList()
        }
        val statement = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith(">")) {
                statement += QUOTE.replaceFirst(line, "")
            } else if (line.isBlank()) {
                // A blank line belongs to the block only if the next line is still quoted.
                if (index + 1 < lines.size && lines[index + 1].startsWith(">")) {
                    statement += ""
                } else {
                    break
                }
            } else {
                break
            }
            index++
        }
        return statement
    }
}
