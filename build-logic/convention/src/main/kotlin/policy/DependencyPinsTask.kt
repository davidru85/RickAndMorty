package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
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
 * `TEST-UNIT-014` — every external version is an exact plain pin, the catalog holds
 * one BOM, and the wrapper is pinned by checksum (`AC-REQ-NFR-006-1`, `REQ-NFR-006`;
 * DEC-060, DEC-061).
 *
 * The rules are one function each, registered in [rules] order, so a later task can
 * contribute one without restructuring this class:
 *
 * - P1 [exactPins] — every constraint is an exact plain version, evaluated once per entry;
 * - P2 [pluginsDeclareVersions] — every plugin declares a version;
 * - P3 [versionlessEntriesAreBomGoverned] — a versionless library is governed by a BOM;
 * - P4 [noInlineVersionsOutsideTheCatalog] — no external version outside the catalog;
 * - P5 [wrapperIsPinned] — the wrapper names an exact Gradle release with its SHA-256;
 * - P6 [onlyTheComposeBom] — DEC-060's single BOM;
 * - P7 [onlyTheLibsCatalog] — DEC-060's single catalog, `libs`;
 * - P8 [kotlinDslAndNoBuildSrc] — Kotlin DSL scripts only, and no `buildSrc`.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class DependencyPinsTask : DefaultTask() {

    @get:Input
    abstract val catalog: Property<CatalogSnapshot>

    /** The catalog **source**, whose shape the Gradle model cannot report (F-01). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wrapperProperties: RegularFileProperty

    /** The main build's scripts: the settings files and every project's build file. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainBuildScripts: ConfigurableFileCollection

    /** The included build's scripts, `*.gradle.kts` and `*.gradle`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildLogicScripts: ConfigurableFileCollection

    /** The included build's Kotlin sources: `*.kt` and precompiled `*.gradle.kts`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policySources: ConfigurableFileCollection

    /** Every project's own build file, for the Kotlin-DSL rule P8. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectBuildFiles: ConfigurableFileCollection

    /** Paths that must not exist: a Groovy settings file, either `buildSrc` directory. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val forbiddenRoots: ConfigurableFileCollection

    /** The main and included settings files, for P7's cross-build catalog check. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val settingsFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val log = ViolationLog()
        rules().forEach { rule -> rule(log) }
        if (!log.isEmpty()) throw GradleException(log.render())
    }

    /**
     * The registered rules, in order. `TASK-018` appends the single-`VERSION` rule of
     * `AC-REQ-NFR-006-2` here, together with its own `VERSION` input.
     */
    private fun rules(): List<(ViolationLog) -> Unit> = listOf(
        ::exactPins,
        ::pluginsDeclareVersions,
        ::versionlessEntriesAreBomGoverned,
        ::noInlineVersionsOutsideTheCatalog,
        ::wrapperIsPinned,
        ::catalogSourceForms,
        ::onlyTheComposeBom,
        ::onlyTheLibsCatalog,
        ::kotlinDslAndNoBuildSrc,
    )

    /**
     * P1 — every version constraint is an exact plain version. Each constraint is
     * evaluated once, in a fixed order, so **one line per `[versions]` alias and per
     * catalog entry**; a defective shared value is reported for the alias and for each
     * entry that uses it.
     */
    private fun exactPins(log: ViolationLog) {
        fun check(location: String, constraint: VersionConstraint, emptyReason: String?) {
            firstPinViolation(constraint, emptyReason)?.let { reason -> log.add(TEST_ID, location, reason) }
        }

        val snapshot = catalog.get()
        snapshot.versions.forEach { check("version ${it.alias}", it.constraint, "has no version") }
        // A library without any constraint is P3's concern, not P1's.
        snapshot.libraries.forEach { check(it.accessor, it.constraint, null) }
        // A plugin without a version is P2's concern, not P1's.
        snapshot.plugins.forEach { check(it.accessor, it.constraint, null) }
    }

    /**
     * The single ordered decision behind P1: the first applicable condition wins, so a
     * rich form is reported as a rich form and a dynamic version as a dynamic version,
     * never both.
     */
    private fun firstPinViolation(constraint: VersionConstraint, emptyReason: String?): String? = when {
        constraint.isRich ->
            "uses a rich version form (strictly/prefer/reject); only a plain exact version is permitted"

        constraint.required.isEmpty() -> emptyReason

        FORBIDDEN_CHARS.any { constraint.required.contains(it) } -> "`${constraint.required}` is a version range, not an exact pin"

        DYNAMIC.containsMatchIn(constraint.required) -> "`${constraint.required}` is dynamic (`+`, `latest.*` or snapshot)"

        !EXACT_VERSION.matches(constraint.required) -> "`${constraint.required}` is not an exact version"

        else -> null
    }

    /** P2 — every plugin declares a version. */
    private fun pluginsDeclareVersions(log: ViolationLog) {
        catalog.get().plugins.forEach { plugin ->
            if (plugin.constraint.required.isEmpty()) {
                log.add(TEST_ID, plugin.accessor, "declares no version")
            }
        }
    }

    /** P3 — a versionless library is governed by a BOM, and a BOM declares its own version. */
    private fun versionlessEntriesAreBomGoverned(log: ViolationLog) {
        catalog.get().libraries.filter { it.versionless }.forEach { library ->
            when {
                library.isBom -> log.add(TEST_ID, library.accessor, "a BOM must declare its own version")
                !catalog.get().isBomGoverned(library) -> log.add(TEST_ID, library.accessor, "is versionless and no BOM governs its group")
            }
        }
    }

    /** P4 — no external version outside the catalog: no inline coordinate and no inline plugin version. */
    private fun noInlineVersionsOutsideTheCatalog(log: ViolationLog) {
        val root = rootDirectory.get().asFile
        val scanned = mainBuildScripts.files + buildLogicScripts.files + policySources.files
        scanned.sortedBy { it.path }.forEach { file ->
            // Comment-masked, whole file: string literals are kept, because coordinates
            // are string literals, and a form split across lines is still one match.
            val code = KotlinSourceMask.mask(file.readText(), maskStrings = false)
            BuildScripts.inlineVersions(code).forEach { match ->
                log.add(TEST_ID, "${file.location(root)}:${BuildScripts.lineOf(code, match.range.first)}", "declares an external version outside the catalog: ${match.value}")
            }
        }
    }

    /** P5 — the wrapper names an exact Gradle release and pins its distribution by SHA-256. */
    private fun wrapperIsPinned(log: ViolationLog) {
        val file = wrapperProperties.get().asFile
        val location = file.location(rootDirectory.get().asFile)
        val properties = PropertiesFiles.read(file)

        val url = properties["distributionUrl"]
        if (url == null) {
            log.add(TEST_ID, location, "declares no `distributionUrl`")
        } else if (GradleWrapper.version(url) == null) {
            log.add(TEST_ID, location, "`distributionUrl` is not an exact Gradle release distribution: $url")
        }

        val checksum = properties["distributionSha256Sum"]
        if (checksum == null) {
            log.add(TEST_ID, location, "declares no `distributionSha256Sum`")
        } else if (!SHA_256.matches(checksum)) {
            log.add(TEST_ID, location, "`distributionSha256Sum` is not 64 hexadecimal characters")
        }
    }

    /**
     * P6 — DEC-060's single BOM, enforced as **exactly one entry**: the catalog must hold
     * `libs.androidx.compose.bom` at [COMPOSE_BOM_COORDINATES], versioned directly, and no
     * other BOM library. A second entry — including a second alias of the same coordinate —
     * would let a versionless entry resolve from a source the inventory does not name.
     *
     * P3 stays responsible for versionless-entry governance; this rule does not repeat its
     * messages.
     */
    private fun onlyTheComposeBom(log: ViolationLog) {
        val boms = catalog.get().libraries.filter { it.isBom }
        val canonical = boms.firstOrNull { it.accessor == COMPOSE_BOM_ACCESSOR }

        when {
            boms.isEmpty() -> log.add(TEST_ID, COMPOSE_BOM_ACCESSOR, "the catalog declares no BOM entry; DEC-060 requires `$COMPOSE_BOM_ACCESSOR` (`$COMPOSE_BOM_COORDINATES`)")

            canonical == null -> boms.forEach { bom ->
                log.add(
                    TEST_ID,
                    bom.accessor,
                    "the BOM entry must be `$COMPOSE_BOM_ACCESSOR` (`$COMPOSE_BOM_COORDINATES`); DEC-060 permits exactly one BOM",
                )
            }

            canonical.coordinates != COMPOSE_BOM_COORDINATES -> log.add(
                TEST_ID,
                canonical.accessor,
                "`${canonical.coordinates}` is a BOM, and DEC-060 permits one BOM, `$COMPOSE_BOM_COORDINATES`",
            )
        }

        boms.filterNot { it.accessor == COMPOSE_BOM_ACCESSOR }.forEach { bom ->
            log.add(
                TEST_ID,
                bom.accessor,
                "declares an additional BOM entry; DEC-060 permits exactly `$COMPOSE_BOM_ACCESSOR` (`$COMPOSE_BOM_COORDINATES`)",
            )
        }
    }

    /**
     * P1 (source half) — the catalog's **source shape**. Gradle resolves `{ require = "1.11.0" }`
     * and `"1.11.0"` to the same constraint, so the model cannot tell the forbidden rich form
     * from the permitted plain one; the source can (TASK-015 §6.2).
     */
    private fun catalogSourceForms(log: ViolationLog) {
        val file = catalogFile.get().asFile
        val location = file.location(rootDirectory.get().asFile)
        CatalogSource.inspect(file).richForms.forEach { form ->
            log.add(
                TEST_ID,
                "$location:${form.line}",
                "`${form.alias}` uses rich version key `${form.key}`; versions must be plain exact strings or `version.ref`",
            )
        }
    }

    /**
     * P7 — the only version catalog is `libs`. A second catalog would hold versions the
     * pin, rationale and inventory checks never see (DEC-060).
     */
    private fun onlyTheLibsCatalog(log: ViolationLog) {
        catalog.get().catalogNames.filter { it != LIB_CATALOG }.forEach { name ->
            log.add(TEST_ID, name, "the build declares a second version catalog; every external version lives in `$LIB_CATALOG` (DEC-060)")
        }

        // The included build has its own settings model, so a catalog declared there is
        // invisible to the main build's `VersionCatalogsExtension` (F-02).
        val root = rootDirectory.get().asFile
        settingsFiles.files.sortedBy { it.path }.forEach { file ->
            val location = file.location(root)
            CatalogSource.catalogsIn(file).forEach { declaration ->
                when {
                    declaration.name != LIB_CATALOG -> log.add(
                        TEST_ID,
                        "$location:${declaration.line}",
                        "declares version catalog `${declaration.name}`; both Gradle builds must consume only `$LIB_CATALOG` from `gradle/libs.versions.toml` (DEC-060)",
                    )

                    declaration.source != null && !declaration.source.endsWith(EXPECTED_CATALOG_SOURCE) -> log.add(
                        TEST_ID,
                        "$location:${declaration.line}",
                        "imports `$LIB_CATALOG` from `${declaration.source}`; both Gradle builds must consume `$EXPECTED_CATALOG_SOURCE` (DEC-060)",
                    )
                }
            }
        }
    }

    /**
     * P8 — the build is Kotlin DSL and has no `buildSrc`: P4's patterns assume Kotlin
     * DSL, so a Groovy script, a Groovy settings file or a `buildSrc` directory would
     * escape them (DEC-057, DEC-061).
     */
    private fun kotlinDslAndNoBuildSrc(log: ViolationLog) {
        val root = rootDirectory.get().asFile

        // Main-build projects, plus the included build's scripts, must be Kotlin DSL.
        projectBuildFiles.files.sortedBy { it.path }.forEach { file ->
            if (!file.name.endsWith(".gradle.kts")) {
                log.add(TEST_ID, file.location(root), "the build must use Kotlin DSL scripts (DEC-057, DEC-061)")
            }
        }
        buildLogicScripts.files.sortedBy { it.path }.forEach { file ->
            if (file.name.endsWith(".gradle")) {
                log.add(TEST_ID, file.location(root), "the build must use Kotlin DSL scripts; Groovy build logic is not covered by P4 (DEC-057, DEC-061)")
            }
        }

        // Forbidden directories, tracked even while absent so that creating one invalidates a
        // reusable configuration-cache entry.
        forbiddenRoots.files.sortedBy { it.path }.forEach { path ->
            if (!path.exists()) return@forEach
            when {
                path.name == "buildSrc" && path.parentFile == root ->
                    log.add(TEST_ID, "buildSrc", "build logic lives in `build-logic/` (DEC-057); `buildSrc` is not scanned by the policy")

                path.name == "buildSrc" ->
                    log.add(
                        TEST_ID,
                        path.location(root),
                        "build logic lives in `build-logic/convention`; nested `buildSrc` is forbidden and is not covered by the catalog policy (DEC-057, DEC-061)",
                    )

                else -> log.add(TEST_ID, path.location(root), "the build must use Kotlin DSL scripts (DEC-057, DEC-061)")
            }
        }
    }

    private companion object {
        const val TEST_ID = "TEST-UNIT-014"

        /** DEC-060: the only catalog is `libs`, sourced from this file. */
        const val EXPECTED_CATALOG_SOURCE = "gradle/libs.versions.toml"

        /** DEC-060: the Compose BOM is the only BOM in the catalog. */
        const val COMPOSE_BOM_ACCESSOR = "libs.androidx.compose.bom"
        const val COMPOSE_BOM_COORDINATES = "androidx.compose:compose-bom"

        val EXACT_VERSION = Regex("^[0-9A-Za-z][0-9A-Za-z._-]*$")
        val DYNAMIC = Regex("\\+|latest\\.|snapshot", RegexOption.IGNORE_CASE)
        val FORBIDDEN_CHARS = listOf('[', ']', '(', ')', ',')
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
