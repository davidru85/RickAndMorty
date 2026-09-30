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
 * `TEST-UNIT-014` — every external version is an exact pin, and the wrapper is pinned
 * by checksum (`AC-REQ-NFR-006-1`, `REQ-NFR-006`; DEC-061).
 *
 * The rules live in one function each (P1–P5) and the task walks them in order, so a
 * later task can contribute a rule of its own — `TASK-018` adds the single-`VERSION`
 * rule of `AC-REQ-NFR-006-2` to `verifyDependencyPins` without restructuring this class.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class DependencyPinsTask : DefaultTask() {

    @get:Input
    abstract val catalog: Property<CatalogSnapshot>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

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
     * The registered rules, in order. `TASK-018` appends the `VERSION` rule here; the
     * `catalogFile` input already exists for it.
     */
    private fun rules(): List<(ViolationLog) -> Unit> = listOf(
        ::exactPins,
        ::pluginsDeclareVersions,
        ::versionlessEntriesAreBomGoverned,
        ::noInlineVersionsOutsideTheCatalog,
        ::wrapperIsPinned,
    )

    /** P1 — every version constraint is an exact version: no range, no `+`, no `latest.*`, no qualifier list. */
    private fun exactPins(log: ViolationLog) {
        fun check(location: String, required: String, strict: String, preferred: String, rejected: List<String>) {
            if (required.isEmpty()) {
                log.add(TEST_ID, location, "has no required version")
            } else if (!EXACT_VERSION.matches(required)) {
                log.add(TEST_ID, location, "`$required` is not an exact version")
            }
            if (DYNAMIC.containsMatchIn(required)) {
                log.add(TEST_ID, location, "`$required` is dynamic: no `+`, `latest.*` or snapshot version is permitted")
            }
            if (FORBIDDEN_CHARS.any { required.contains(it) }) {
                log.add(TEST_ID, location, "`$required` is a rich version range, not a plain pin")
            }
            if (strict.isNotEmpty() || preferred.isNotEmpty() || rejected.isNotEmpty()) {
                log.add(TEST_ID, location, "uses a rich version form (strictly/require/prefer/reject), not a plain pin")
            }
        }

        val snapshot = catalog.get()
        snapshot.versions.forEach { check("version ${it.alias}", it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions) }
        snapshot.libraries.forEach {
            if (!it.versionless) check(it.accessor, it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions)
        }
        snapshot.plugins.forEach { check(it.accessor, it.requiredVersion, it.strictVersion, it.preferredVersion, it.rejectedVersions) }
    }

    /** P2 — every plugin declares a version. */
    private fun pluginsDeclareVersions(log: ViolationLog) {
        catalog.get().plugins.forEach { plugin ->
            if (plugin.requiredVersion.isEmpty()) {
                log.add(TEST_ID, plugin.accessor, "plugin `${plugin.pluginId}` declares no version")
            }
        }
    }

    /** P3 — a library without a version is governed by a BOM of the same group or a dot-prefix of it. */
    private fun versionlessEntriesAreBomGoverned(log: ViolationLog) {
        catalog.get().libraries.filter { it.versionless }.forEach { library ->
            if (!catalog.get().isBomGoverned(library)) {
                log.add(TEST_ID, library.accessor, "is versionless with no governing BOM (DEC-060: one BOM, the Compose BOM)")
            }
        }
    }

    /** P4 — no external version outside the catalog: no inline coordinate and no inline plugin version. */
    private fun noInlineVersionsOutsideTheCatalog(log: ViolationLog) {
        (buildScripts.files + policySources.files).sortedBy { it.path }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                BuildScripts.inlineVersionedCoordinates(line).forEach { found ->
                    log.add(TEST_ID, "${file.name}:${index + 1}", "declares an external version outside the catalog: $found")
                }
            }
        }
    }

    /** P5 — the wrapper names an exact Gradle release and pins its distribution by SHA-256. */
    private fun wrapperIsPinned(log: ViolationLog) {
        val location = wrapperProperties.get().asFile.name
        val properties = wrapperProperties.get().asFile.readLines()
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

    private companion object {
        const val TEST_ID = "TEST-UNIT-014"

        val EXACT_VERSION = Regex("^[0-9A-Za-z][0-9A-Za-z._-]*$")
        val DYNAMIC = Regex("\\+|latest\\.|snapshot", RegexOption.IGNORE_CASE)
        val FORBIDDEN_CHARS = listOf('[', ']', '(', ')', ',')
        val DISTRIBUTION = Regex(".*gradle-[0-9]+\\.[0-9]+(\\.[0-9]+)?-(bin|all)\\.zip$")
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
