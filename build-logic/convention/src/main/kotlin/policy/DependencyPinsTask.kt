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
 * - P6 [onlyTheComposeBom] — DEC-060's single BOM.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class DependencyPinsTask : DefaultTask() {

    @get:Input
    abstract val catalog: Property<CatalogSnapshot>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wrapperProperties: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildScripts: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policySources: ConfigurableFileCollection

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
        ::onlyTheComposeBom,
    )

    /**
     * P1 — every version constraint is an exact plain version. Each entry is evaluated
     * once, in a fixed order, so one defect yields exactly one violation line.
     */
    private fun exactPins(log: ViolationLog) {
        fun check(location: String, required: String, strict: String, preferred: String, rejected: List<String>, emptyReason: String?) {
            firstPinViolation(required, strict, preferred, rejected, emptyReason)?.let { reason ->
                log.add(TEST_ID, location, reason)
            }
        }

        val snapshot = catalog.get()
        snapshot.versions.forEach {
            check("version ${it.alias}", it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions, "has no version")
        }
        snapshot.libraries.forEach {
            // A library without any constraint is P3's concern, not P1's.
            check(it.accessor, it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions, null)
        }
        snapshot.plugins.forEach {
            // A plugin without a version is P2's concern, not P1's.
            check(it.accessor, it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions, null)
        }
    }

    /**
     * The single ordered decision behind P1: the first applicable condition wins, so a
     * rich form is reported as a rich form and a dynamic version as a dynamic version,
     * never both.
     */
    private fun firstPinViolation(
        required: String,
        strict: String,
        preferred: String,
        rejected: List<String>,
        emptyReason: String?,
    ): String? = when {
        strict.isNotEmpty() || preferred.isNotEmpty() || rejected.isNotEmpty() ->
            "uses a rich version form (strictly/prefer/reject); only a plain exact version is permitted"

        required.isEmpty() -> emptyReason

        FORBIDDEN_CHARS.any { required.contains(it) } -> "`$required` is a version range, not an exact pin"

        DYNAMIC.containsMatchIn(required) -> "`$required` is dynamic (`+`, `latest.*` or snapshot)"

        !EXACT_VERSION.matches(required) -> "`$required` is not an exact version"

        else -> null
    }

    /** P2 — every plugin declares a version. */
    private fun pluginsDeclareVersions(log: ViolationLog) {
        catalog.get().plugins.forEach { plugin ->
            if (plugin.requiredVersion.isEmpty()) {
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
        (buildScripts.files + policySources.files).sortedBy { it.path }.forEach { file ->
            // Comment-masked, line by line: string literals are kept, because coordinates
            // are string literals, and line numbers stay exact.
            KotlinSourceMask.mask(file.readText(), maskStrings = false).lines().forEachIndexed { index, line ->
                BuildScripts.inlineVersionedCoordinates(line).forEach { found ->
                    log.add(TEST_ID, "${file.name}:${index + 1}", "declares an external version outside the catalog: $found")
                }
            }
        }
    }

    /** P5 — the wrapper names an exact Gradle release and pins its distribution by SHA-256. */
    private fun wrapperIsPinned(log: ViolationLog) {
        val file = wrapperProperties.get().asFile
        val location = file.name
        val properties = file.readLines()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.take(separator).trim() to line.drop(separator + 1).trim()
            }
            .toMap()

        val url = properties["distributionUrl"]
        if (url == null) {
            log.add(TEST_ID, location, "declares no `distributionUrl`")
        } else if (!DISTRIBUTION.matches(url)) {
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
     * P6 — DEC-060's single BOM: the catalog contains at most one BOM library, and it is
     * [COMPOSE_BOM_COORDINATES]. A second BOM would let a versionless entry resolve from
     * a source the inventory does not name.
     */
    private fun onlyTheComposeBom(log: ViolationLog) {
        catalog.get().libraries.filter { it.isBom }.forEach { bom ->
            if (bom.coordinates != COMPOSE_BOM_COORDINATES) {
                log.add(
                    TEST_ID,
                    bom.accessor,
                    "`${bom.coordinates}` is a BOM, and DEC-060 permits one BOM, `$COMPOSE_BOM_COORDINATES`",
                )
            }
        }
    }

    private companion object {
        const val TEST_ID = "TEST-UNIT-014"

        /** DEC-060: the Compose BOM is the only BOM in the catalog. */
        const val COMPOSE_BOM_COORDINATES = "androidx.compose:compose-bom"

        val EXACT_VERSION = Regex("^[0-9A-Za-z][0-9A-Za-z._-]*$")
        val DYNAMIC = Regex("\\+|latest\\.|snapshot", RegexOption.IGNORE_CASE)
        val FORBIDDEN_CHARS = listOf('[', ']', '(', ')', ',')
        val DISTRIBUTION = Regex(".*gradle-[0-9]+\\.[0-9]+(\\.[0-9]+)?-(bin|all)\\.zip$")
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
