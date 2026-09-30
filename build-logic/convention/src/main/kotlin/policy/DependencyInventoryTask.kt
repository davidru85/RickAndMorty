package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
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
 * `TEST-UNIT-051` — the README dependency inventory lists exactly the catalog's libraries
 * and plugins, with their pinned versions and their declaration state derived from the
 * build scripts, and `README.es.md` mirrors it (`AC-REQ-NFR-002-1`; DEC-061).
 *
 * A row is `Declared` if and only if at least one build script references its accessor;
 * otherwise it is `Pinned`, and its "Declared by" cell is `—`.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class DependencyInventoryTask : DefaultTask() {

    @get:Input
    abstract val catalog: Property<CatalogSnapshot>

    /** The catalog source, whose key order the inventory must follow (`F-06`). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val readme: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val readmeEs: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildScripts: ConfigurableFileCollection

    /**
     * Build-logic Kotlin sources whose name lookups are refused (I8). The policy package
     * is excluded on purpose: `CatalogCapture` must call `findLibrary` and `findPlugin` to
     * read the catalog this task verifies.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogLookupSources: ConfigurableFileCollection

    /** Root-relative build-file path -> project path, taken from `Project.path` (F-05). */
    @get:Input
    abstract val projectPaths: MapProperty<String, String>

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val log = ViolationLog()
        val snapshot = catalog.get()
        val rootDir = rootDirectory.get().asFile

        val references = BuildScripts.references(
            scripts = buildScripts.files.sortedBy { it.path },
            accessors = snapshot.accessors.toSet(),
            rootDir = rootDir,
            projectPaths = projectPaths.get(),
        )

        // Every independent rule runs, even when a README table is missing: a missing
        // table only skips the checks that need it.
        val english = read(readme.get().asFile, ENGLISH_HEADER, log)
        val spanish = read(readmeEs.get().asFile, SPANISH_HEADER, log)

        val expectedOrder = CatalogSource.inspect(catalogFile.get().asFile).let { source ->
            source.libraryOrder.map(CatalogSource::libraryAccessor) + source.pluginOrder.map(CatalogSource::pluginAccessor)
        }

        checkBundles(snapshot, log)
        checkCatalogLookups(log)
        checkAccessorAliasing(log)
        english?.let { checkRows(it, snapshot, references, expectedOrder, readme.get().asFile, log) }
        spanish?.let { checkRows(it, snapshot, references, expectedOrder, readmeEs.get().asFile, log) }
        if (english != null && spanish != null) checkMirror(english, spanish, log)

        if (!log.isEmpty()) throw GradleException(log.render())
    }

    /**
     * I7 — the catalog declares no bundle: a bundle hides which entries a module
     * declares, so the inventory state could not be derived (TASK-015 spec §6.2, DEC-060).
     */
    private fun checkBundles(snapshot: CatalogSnapshot, log: ViolationLog) {
        snapshot.bundles.forEach { bundle ->
            log.add(
                TEST_ID,
                bundle,
                "the catalog declares a bundle; bundles hide which entries a module declares, " +
                    "so the inventory state could not be derived (TASK-015 spec §6.2, DEC-060)",
            )
        }
    }

    /**
     * I8 — no build-logic Kotlin source outside the policy package looks a catalog entry
     * up by name. Declarations belong in module build scripts (DEC-057); a name lookup
     * hides the declaration from the inventory.
     */
    private fun checkCatalogLookups(log: ViolationLog) {
        val root = rootDirectory.get().asFile
        catalogLookupSources.files.sortedBy { it.path }.forEach { file ->
            val code = KotlinSourceMask.mask(file.readText(), maskStrings = true)
            NAME_LOOKUP.findAll(code).forEach {
                log.add(
                    TEST_ID,
                    "${file.location(root)}:${BuildScripts.lineOf(code, it.range.first)}",
                    "looks up a catalog entry by name; declarations belong in module build scripts (DEC-057)",
                )
            }
        }
    }

    /**
     * I9 — the catalog accessor is never aliased. A bare `libs` bound to another name hides
     * every declaration that goes through that name, so the inventory state stops being
     * derivable (TEST-UNIT-051, `AC-REQ-NFR-002-1`).
     */
    private fun checkAccessorAliasing(log: ViolationLog) {
        val root = rootDirectory.get().asFile
        buildScripts.files.sortedBy { it.path }.forEach { file ->
            val code = KotlinSourceMask.mask(file.readText(), maskStrings = true)
            BARE_LIBS.findAll(code).forEach {
                log.add(
                    TEST_ID,
                    "${file.location(root)}:${BuildScripts.lineOf(code, it.range.first)}",
                    "the catalog accessor `libs` is used indirectly; reference entries as `libs.<alias>` " +
                        "so the declaration state stays derivable (TEST-UNIT-051)",
                )
            }
        }
    }

    /** I1 — the marked table exists with the exact header, or the violation is recorded. */
    private fun read(file: java.io.File, header: List<String>, log: ViolationLog): MarkdownTable.Result? {
        val location = file.location(rootDirectory.get().asFile)
        val table = MarkdownTable.parse(file, BEGIN, END)
        val structural = table.problems.isNotEmpty()
        table.problems.forEach { problem -> log.add(TEST_ID, "$location:${problem.line}", problem.reason) }
        if (structural) return null
        if (table.header != header) {
            log.add(TEST_ID, "${location}:${table.rows.firstOrNull()?.let { it.line - 2 } ?: 1}", "the inventory header is not the one the policy fixes")
        }
        return table
    }

    /** I2–I5 — row set, coordinates, effective version and declaration state. */
    private fun checkRows(
        table: MarkdownTable.Result,
        snapshot: CatalogSnapshot,
        references: Map<String, List<String>>,
        expectedOrder: List<String>,
        file: java.io.File,
        log: ViolationLog,
    ) {
        val seen = mutableSetOf<String>()
        val listed = mutableListOf<String>()

        // I2 (order) — the rows follow catalog declaration order, not merely the same set.
        table.rows.forEachIndexed { index, row ->
            val accessor = MarkdownTable.singleCodeSpan(row.cells.firstOrNull().orEmpty()) ?: return@forEachIndexed
            listed += accessor
            val expected = expectedOrder.getOrNull(index)
            if (expected != null && accessor != expected) {
                log.add(
                    TEST_ID,
                    "${file.location(rootDirectory.get().asFile)}:${row.line}",
                    "inventory rows are not in catalog declaration order; expected `$expected`, found `$accessor`",
                )
            }
        }

        table.rows.forEach { row ->
            if (row.cells.size != COLUMNS) {
                log.add(TEST_ID, "${file.location(rootDirectory.get().asFile)}:${row.line}", "expected $COLUMNS cells, found ${row.cells.size}")
                return@forEach
            }
            val location = "${file.location(rootDirectory.get().asFile)}:${row.line}"
            val entryCell = row.cells[0]
            val accessor = MarkdownTable.singleCodeSpan(entryCell)
            if (accessor == null) {
                log.add(TEST_ID, location, "Entry must be exactly one accessor code span; found `$entryCell`")
                return@forEach
            }
            if (!seen.add(accessor)) log.add(TEST_ID, location, "`$accessor` is listed more than once")
            if (accessor !in snapshot.accessors) {
                log.add(TEST_ID, location, "`$accessor` is not in the catalog")
                return@forEach
            }

            val idCell = row.cells[1]
            val coordinates = MarkdownTable.singleCodeSpan(idCell)
            val expectedCoordinates = snapshot.pluginIds()[accessor] ?: snapshot.libraries.first { it.accessor == accessor }.coordinates
            if (coordinates == null) {
                log.add(TEST_ID, location, "Artifact or plugin id must be exactly one code span; found `$idCell`")
            } else if (coordinates != expectedCoordinates) {
                log.add(TEST_ID, location, "Artifact or plugin id is `$coordinates`, but the catalog declares `$expectedCoordinates`")
            }

            val versionCell = row.cells[2].trim()
            val hasBomSuffix = versionCell.endsWith(BOM_SUFFIX)
            val versionText = if (hasBomSuffix) versionCell.removeSuffix(BOM_SUFFIX).trim() else versionCell
            val version = MarkdownTable.singleCodeSpan(versionText)
            val expectedVersion = snapshot.effectiveVersion(accessor)
            if (version == null) {
                log.add(TEST_ID, location, "Version must be exactly one code span, plus `$BOM_SUFFIX` only for a versionless entry; found `$versionCell`")
            } else if (version != expectedVersion) {
                log.add(TEST_ID, location, "Version is `$version`, but the effective version of `$accessor` is `$expectedVersion`")
            }
            if (hasBomSuffix != snapshot.isVersionless(accessor)) {
                val expected = if (snapshot.isVersionless(accessor)) "must be present" else "must not be present"
                log.add(TEST_ID, location, "the `$BOM_SUFFIX` suffix $expected for `$accessor`")
            }

            val declaredBy = references[accessor].orEmpty()
            val state = row.cells[3].trim()
            val expectedState = if (declaredBy.isEmpty()) "Pinned" else "Declared"
            if (state !in setOf("Declared", "Pinned")) {
                log.add(TEST_ID, location, "State must be exactly `Declared` or `Pinned`; found `$state`")
            } else if (state != expectedState) {
                log.add(TEST_ID, location, "State is `$state`, but the build scripts say `$expectedState`")
            }

            val declaredByCell = row.cells[4].trim()
            if (declaredBy.isEmpty()) {
                if (declaredByCell != "—") {
                    log.add(TEST_ID, location, "a Pinned entry must carry `—` in Declared by, found `$declaredByCell`")
                }
            } else {
                val listedCell = MarkdownTable.codeSpanList(row.cells[4])
                if (listedCell == null) {
                    log.add(TEST_ID, location, "Declared by must be `—` or a comma-separated list of code spans; found `${row.cells[4]}`")
                    return@forEach
                }
                val listed = listedCell.sorted()
                if (listed != declaredBy.sorted()) {
                    log.add(
                        TEST_ID,
                        location,
                        "Declared by is [${listed.joinToString()}], but the build scripts reference it from [${declaredBy.sorted().joinToString()}]",
                    )
                }
            }
        }

        val location = file.location(rootDirectory.get().asFile)
        snapshot.accessors.forEach { accessor ->
            if (accessor !in seen) log.add(TEST_ID, location, "`$accessor` is missing from the inventory")
        }
        if (listed.isNotEmpty() && listed.toSet() != expectedOrder.toSet()) {
            val unknown = (listed.toSet() - expectedOrder.toSet()).joinToString()
            if (unknown.isNotEmpty()) log.add(TEST_ID, location, "inventory lists accessors that are not in the catalog: $unknown")
        }
    }

    /** I6 — columns 1–5 of the Spanish table equal the English table, row for row. */
    private fun checkMirror(english: MarkdownTable.Result, spanish: MarkdownTable.Result, log: ViolationLog) {
        val englishRows = english.rows.associateBy { MarkdownTable.singleCodeSpan(it.cells.firstOrNull().orEmpty()) }
        val spanishRows = spanish.rows.associateBy { MarkdownTable.singleCodeSpan(it.cells.firstOrNull().orEmpty()) }
        englishRows.forEach { (accessor, englishRow) ->
            val spanishRow = spanishRows[accessor] ?: run {
                log.add(TEST_ID, readmeEs.get().asFile.location(rootDirectory.get().asFile), "`$accessor` is missing from the Spanish inventory")
                return@forEach
            }
            (0..4).forEach { column ->
                val englishCell = englishRow.cells.getOrNull(column)
                val spanishCell = spanishRow.cells.getOrNull(column)
                if (englishCell != spanishCell) {
                    log.add(
                        TEST_ID,
                        "${readmeEs.get().asFile.location(rootDirectory.get().asFile)}:${spanishRow.line}",
                        "column ${column + 1} is `$spanishCell`, but README.md has `$englishCell`",
                    )
                }
            }
        }
    }

    private companion object {
        const val TEST_ID = "TEST-UNIT-051"
        const val BEGIN = "<!-- dependency-inventory:begin -->"
        const val END = "<!-- dependency-inventory:end -->"
        const val COLUMNS = 6

        val NAME_LOOKUP = Regex("\\bfind(?:Library|Bundle|Plugin)\\s*\\(")
        val BARE_LIBS = Regex("(?<![\\w.])libs\\b(?!\\s*\\.)")
        const val BOM_SUFFIX = " (BOM)"
        val ENGLISH_HEADER = listOf("Entry", "Artifact or plugin id", "Version", "State", "Declared by", "Planned for")
        val SPANISH_HEADER = listOf("Entrada", "Artefacto o id de plugin", "Versión", "Estado", "Declarada en", "Prevista para")
    }
}
