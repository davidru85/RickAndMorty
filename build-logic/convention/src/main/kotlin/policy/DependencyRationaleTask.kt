package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `TEST-UNIT-013` — every catalog entry has a named rationale, and no concern is served
 * by more than two solutions (`REQ-NFR-002`, `AC-REQ-NFR-002-2`; DEC-061).
 *
 * The checked table is the one in `docs/DESIGN.md` §3.5, between the markers
 * `<!-- dependency-rationale:begin -->` and `<!-- dependency-rationale:end -->`, in the
 * header order §6.4 of the TASK-015 specification fixes.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class DependencyRationaleTask : DefaultTask() {

    @get:Input
    abstract val catalog: Property<CatalogSnapshot>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val designDocument: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wrapperProperties: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val daemonJvmProperties: RegularFileProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val log = ViolationLog()
        val table = MarkdownTable.parse(designDocument.get().asFile, BEGIN, END)
        if (table == null) {
            log.add(TEST_ID, document, "the marked rationale table is missing (markers `$BEGIN` / `$END`)")
            throw GradleException(log.render())
        }
        if (table.header != HEADER) {
            log.add(TEST_ID, "$document:${table.beginLine}", "the table header is not the one the policy fixes")
        }

        val snapshot = catalog.get()
        val entries = rowEntries(table)
        checkEveryEntryHasExactlyOneRow(entries, snapshot, log)
        checkRows(table, snapshot, log)
        checkToolchainRows(table, log)
        checkConcernSolutions(table, log)
        checkNoPlaceholders(table, log)

        if (!log.isEmpty()) throw GradleException(log.render())
    }

    /** R2 — every catalog accessor appears in exactly one row, and every listed accessor exists. */
    private fun checkEveryEntryHasExactlyOneRow(entries: Map<String, List<Int>>, snapshot: CatalogSnapshot, log: ViolationLog) {
        val known = snapshot.accessors.toSet()
        snapshot.accessors.forEach { accessor ->
            when (entries[accessor]?.size ?: 0) {
                0 -> log.add(TEST_ID, accessor, "appears in no rationale row")
                1 -> Unit
                else -> log.add(TEST_ID, accessor, "appears in ${entries.getValue(accessor).size} rationale rows; one entry, one row")
            }
        }
        entries.keys.filterNot { it in known }.forEach { unknown ->
            log.add(TEST_ID, "$document:${entries.getValue(unknown).first()}", "rationale row lists `$unknown`, which is not in the catalog")
        }
    }

    /** R3, R4 and R6 — cell shape, effective version and placeholders, row by row. */
    private fun checkRows(table: MarkdownTable.Parsed, snapshot: CatalogSnapshot, log: ViolationLog) {
        table.rows.forEach { row ->
            if (row.cells.size != HEADER.size) {
                log.add(TEST_ID, "$document:${row.line}", "expected ${HEADER.size} cells, found ${row.cells.size}")
                return@forEach
            }
            val component = row.cells[0]
            val entriesCell = row.cells[1]
            val versionCell = row.cells[2]
            val concern = row.cells[3]
            val solution = row.cells[4]
            val rationale = row.cells[5]
            val source = row.cells[6]
            val verified = row.cells[7]

            if (concern.isBlank() || solution.isBlank() || rationale.isBlank()) {
                log.add(TEST_ID, "$document:${row.line}", "row `$component` has an empty Concern, Solution or Rationale cell")
            }
            if (!IDENTIFIER.containsMatchIn(rationale)) {
                log.add(TEST_ID, "$document:${row.line}", "row `$component` cites no DEC/ADR/REQ/CON identifier or § reference")
            }
            if (!source.contains("https://")) {
                log.add(TEST_ID, "$document:${row.line}", "row `$component` lists no https:// primary source")
            }
            if (!VERIFIED_DATE.matches(verified.trim())) {
                log.add(TEST_ID, "$document:${row.line}", "row `$component` has no ISO 8601 verification date")
            }

            val version = MarkdownTable.singleCodeSpan(versionCell)
            if (version == null) {
                log.add(TEST_ID, "$document:${row.line}", "row `$component` does not carry exactly one version code span")
                return@forEach
            }
            if (entriesCell.trim() == "—") return@forEach

            MarkdownTable.codeSpans(entriesCell).forEach { accessor ->
                if (accessor !in snapshot.accessors) {
                    // R2 alone reports an unknown accessor; this rule would duplicate it.
                    return@forEach
                }
                if (snapshot.effectiveVersion(accessor) != version) {
                    log.add(
                        TEST_ID,
                        "$document:${row.line}",
                        "row `$component` declares version $version but `$accessor` pins `${snapshot.effectiveVersion(accessor)}`",
                    )
                }
            }
        }
    }

    /** R5 — at most two distinct solutions per concern (`REQ-NFR-002`). */
    private fun checkConcernSolutions(table: MarkdownTable.Parsed, log: ViolationLog) {
        table.rows
            .filter { it.cells.size == HEADER.size }
            .groupBy { it.cells[3].trim() }
            .forEach { (concern, rows) ->
                val solutions = rows.map { it.cells[4].trim() }.filter { it.isNotEmpty() }.distinct()
                if (solutions.size > MAX_SOLUTIONS_PER_CONCERN) {
                    log.add(
                        TEST_ID,
                        "$document:${rows.first().line}",
                        "concern `$concern` is served by ${solutions.size} solutions (${solutions.joinToString()}); REQ-NFR-002 caps it at two",
                    )
                }
            }
    }

    /** R6 — no unresolved placeholder in any cell. */
    private fun checkNoPlaceholders(table: MarkdownTable.Parsed, log: ViolationLog) {
        table.rows.forEach { row ->
            row.cells.forEach { cell ->
                PLACEHOLDER.findAll(cell).forEach { match ->
                    log.add(TEST_ID, "$document:${row.line}", "cell contains the placeholder `${match.value}`")
                }
            }
        }
    }

    /**
     * Accessor -> every row line declaring it, so a duplicate row is detectable. A row
     * with the wrong cell count is skipped **silently**: [checkRows] alone reports it.
     */
    private fun rowEntries(table: MarkdownTable.Parsed): Map<String, List<Int>> {
        val entries = mutableMapOf<String, MutableList<Int>>()
        table.rows.filter { it.cells.size == HEADER.size }.forEach { row ->
            MarkdownTable.codeSpans(row.cells[1]).forEach { accessor ->
                entries.getOrPut(accessor) { mutableListOf() }.add(row.line)
            }
        }
        return entries
    }

    /**
     * R7 — the two toolchain rows. Each appears exactly once, its version equals its
     * source's value, and a missing row or a missing source value is a violation rather
     * than a silent pass: the sources are the wrapper's `distributionUrl` and the daemon
     * JVM's `toolchainVersion`.
     */
    private fun checkToolchainRows(table: MarkdownTable.Parsed, log: ViolationLog) {
        val root = rootDirectory.get().asFile
        val documentLocation = designDocument.get().asFile.location(root)
        val wrapper = wrapperProperties.get().asFile
        val daemon = daemonJvmProperties.get().asFile
        val wrapperVersion = GradleWrapper.version(PropertiesFiles.read(wrapper)["distributionUrl"])
        val daemonVersion = PropertiesFiles.read(daemon)["toolchainVersion"]

        val rows = table.rows.filter { it.cells.size == HEADER.size && it.cells[1].trim() == "—" }
            .groupBy { it.cells[0].trim() }

        TOOLCHAIN_ROWS.forEach { (component, kind) ->
            val expectedVersion = if (kind == WRAPPER) wrapperVersion else daemonVersion
            val source = if (kind == WRAPPER) wrapper else daemon
            val sourceKey = if (kind == WRAPPER) "distributionUrl" else "toolchainVersion"

            val occurrences = rows[component].orEmpty()
            if (occurrences.isEmpty()) {
                log.add(TEST_ID, documentLocation, "the toolchain row `$component` is missing")
            }
            occurrences.drop(1).forEach { row ->
                log.add(TEST_ID, "$documentLocation:${row.line}", "the toolchain row `$component` appears more than once")
            }

            if (expectedVersion == null) {
                val reason = if (kind == WRAPPER) {
                    "`distributionUrl` names no Gradle release"
                } else {
                    "declares no `$sourceKey`"
                }
                log.add(TEST_ID, source.location(root), reason)
                return@forEach
            }

            occurrences.forEach { row ->
                val version = MarkdownTable.singleCodeSpan(row.cells[2])
                if (version != expectedVersion) {
                    log.add(
                        TEST_ID,
                        "$documentLocation:${row.line}",
                        "row `$component` declares `$version`, but `${source.location(root)}` pins `$expectedVersion`",
                    )
                }
            }
        }

        rows.keys.filterNot { it in TOOLCHAIN_ROWS.keys }.forEach { component ->
            rows.getValue(component).forEach { row ->
                log.add(
                    TEST_ID,
                    "$documentLocation:${row.line}",
                    "a non-catalog row must be `$WRAPPER_ROW` or `$DAEMON_ROW`",
                )
            }
        }
    }

    private val document: String get() = designDocument.get().asFile.location(rootDirectory.get().asFile)

    private companion object {
        const val TEST_ID = "TEST-UNIT-013"
        const val BEGIN = "<!-- dependency-rationale:begin -->"
        const val END = "<!-- dependency-rationale:end -->"
        const val MAX_SOLUTIONS_PER_CONCERN = 2

        val HEADER = listOf(
            "Component",
            "Catalog entries",
            "Version",
            "Concern",
            "Solution",
            "Rationale",
            "Primary source",
            "Verified",
        )
        val IDENTIFIER = Regex("DEC-\\d{3}|ADR-\\d{4}|REQ-[A-Z]+-\\d{3}|CON-\\d{3}|§\\d")
        val VERIFIED_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        const val WRAPPER = "wrapper"
        const val WRAPPER_ROW = "Gradle (wrapper)"
        const val DAEMON_ROW = "Gradle daemon JVM"
        val TOOLCHAIN_ROWS = mapOf(WRAPPER_ROW to WRAPPER, DAEMON_ROW to "daemon")
        val PLACEHOLDER = Regex("TODO|TBD|FIXME|<[^>]*>")
    }
}
