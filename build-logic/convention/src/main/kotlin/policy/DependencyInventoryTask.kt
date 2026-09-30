package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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

    @get:Input
    abstract val projectDirectory: Property<String>

    @TaskAction
    fun verify() {
        val log = ViolationLog()
        val snapshot = catalog.get()
        val rootDir = java.io.File(projectDirectory.get())

        val references = BuildScripts.references(
            scripts = buildScripts.files.sortedBy { it.path },
            rootDir = rootDir,
            accessors = snapshot.accessors.toSet(),
        )

        val english = read(readme.get().asFile, ENGLISH_HEADER, log) ?: run {
            throw GradleException(log.render())
        }
        val spanish = read(readmeEs.get().asFile, SPANISH_HEADER, log) ?: run {
            throw GradleException(log.render())
        }

        checkBundles(snapshot, log)
        checkCatalogLookups(log)
        checkRows(english, snapshot, references, readme.get().asFile, log)
        checkRows(spanish, snapshot, references, readmeEs.get().asFile, log)
        checkMirror(english, spanish, log)

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
        val root = java.io.File(projectDirectory.get())
        catalogLookupSources.files.sortedBy { it.path }.forEach { file ->
            KotlinSourceMask.mask(file.readText(), maskStrings = false).lines().forEachIndexed { index, line ->
                NAME_LOOKUP.findAll(line).forEach {
                    log.add(
                        TEST_ID,
                        "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}",
                        "looks up a catalog entry by name; declarations belong in module build scripts (DEC-057)",
                    )
                }
            }
        }
    }

    /** I1 — the marked table exists with the exact header, or the violation is recorded. */
    private fun read(file: java.io.File, header: List<String>, log: ViolationLog): MarkdownTable.Parsed? {
        val table = MarkdownTable.parse(file, BEGIN, END)
        if (table == null) {
            log.add(TEST_ID, file.name, "the marked dependency inventory is missing (markers `$BEGIN` / `$END`)")
            return null
        }
        if (table.header != header) {
            log.add(TEST_ID, "${file.name}:${table.beginLine}", "the inventory header is not the one the policy fixes")
        }
        return table
    }

    /** I2–I5 — row set, coordinates, effective version and declaration state. */
    private fun checkRows(
        table: MarkdownTable.Parsed,
        snapshot: CatalogSnapshot,
        references: Map<String, List<String>>,
        file: java.io.File,
        log: ViolationLog,
    ) {
        val seen = mutableSetOf<String>()
        table.rows.forEach { row ->
            if (row.cells.size != COLUMNS) {
                log.add(TEST_ID, "${file.name}:${row.line}", "expected $COLUMNS cells, found ${row.cells.size}")
                return@forEach
            }
            val location = "${file.name}:${row.line}"
            val accessor = MarkdownTable.singleCodeSpan(row.cells[0])
            if (accessor == null) {
                log.add(TEST_ID, location, "the Entry cell does not carry exactly one accessor")
                return@forEach
            }
            if (!seen.add(accessor)) log.add(TEST_ID, location, "`$accessor` is listed more than once")
            if (accessor !in snapshot.accessors) {
                log.add(TEST_ID, location, "`$accessor` is not in the catalog")
                return@forEach
            }

            val coordinates = MarkdownTable.singleCodeSpan(row.cells[1])
            val expectedCoordinates = snapshot.pluginIds()[accessor] ?: snapshot.libraries.first { it.accessor == accessor }.coordinates
            if (coordinates != expectedCoordinates) {
                log.add(TEST_ID, location, "Artifact or plugin id is `$coordinates`, but the catalog declares `$expectedCoordinates`")
            }

            val versionCell = row.cells[2]
            val version = MarkdownTable.singleCodeSpan(versionCell)
            val bomSuffix = versionCell.trim().endsWith("(BOM)")
            val expectedVersion = snapshot.effectiveVersion(accessor)
            if (version != expectedVersion) {
                log.add(TEST_ID, location, "Version is `$version`, but the effective version of `$accessor` is `$expectedVersion`")
            }
            if (bomSuffix != snapshot.isVersionless(accessor)) {
                val expected = if (snapshot.isVersionless(accessor)) "must be present" else "must not be present"
                log.add(TEST_ID, location, "the ` (BOM)` suffix $expected for `$accessor`")
            }

            val declaredBy = references[accessor].orEmpty()
            val state = row.cells[3].trim()
            val expectedState = if (declaredBy.isEmpty()) "Pinned" else "Declared"
            if (state != expectedState) {
                log.add(TEST_ID, location, "State is `$state`, but the build scripts say `$expectedState`")
            }

            val declaredByCell = row.cells[4].trim()
            if (declaredBy.isEmpty()) {
                if (declaredByCell != "—") {
                    log.add(TEST_ID, location, "a Pinned entry must carry `—` in Declared by, found `$declaredByCell`")
                }
            } else {
                val listed = MarkdownTable.codeSpans(row.cells[4]).sorted()
                if (listed != declaredBy.sorted()) {
                    log.add(
                        TEST_ID,
                        location,
                        "Declared by is [${listed.joinToString()}], but the build scripts reference it from [${declaredBy.sorted().joinToString()}]",
                    )
                }
            }
        }

        snapshot.accessors.forEach { accessor ->
            if (accessor !in seen) log.add(TEST_ID, file.name, "`$accessor` is missing from the inventory")
        }
    }

    /** I6 — columns 1–5 of the Spanish table equal the English table, row for row. */
    private fun checkMirror(english: MarkdownTable.Parsed, spanish: MarkdownTable.Parsed, log: ViolationLog) {
        val englishRows = english.rows.associateBy { MarkdownTable.singleCodeSpan(it.cells.firstOrNull().orEmpty()) }
        val spanishRows = spanish.rows.associateBy { MarkdownTable.singleCodeSpan(it.cells.firstOrNull().orEmpty()) }
        englishRows.forEach { (accessor, englishRow) ->
            val spanishRow = spanishRows[accessor] ?: run {
                log.add(TEST_ID, readmeEs.get().asFile.name, "`$accessor` is missing from the Spanish inventory")
                return@forEach
            }
            (0..4).forEach { column ->
                val englishCell = englishRow.cells.getOrNull(column)
                val spanishCell = spanishRow.cells.getOrNull(column)
                if (englishCell != spanishCell) {
                    log.add(
                        TEST_ID,
                        "${readmeEs.get().asFile.name}:${spanishRow.line}",
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
        val ENGLISH_HEADER = listOf("Entry", "Artifact or plugin id", "Version", "State", "Declared by", "Planned for")
        val SPANISH_HEADER = listOf("Entrada", "Artefacto o id de plugin", "Versión", "Estado", "Declarada en", "Prevista para")
    }
}
