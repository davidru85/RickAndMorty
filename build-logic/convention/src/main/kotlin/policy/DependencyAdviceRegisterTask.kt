package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `DEC-081` — an exclusion expires when **the excluded edge's own consumer consumes it**
 * (`TASK-105`, `B2-R08`; resolving `CONF-62`).
 *
 * `buildHealth` fails on a dependency no source consumes. On a tree whose modules are still
 * placeholders, the ADR-0001-mandated edges are not consumed yet, so the advice would contradict
 * `DESIGN.md` §3.4 rather than report a defect. Each entry in
 * `gradle/dependency-advice-exclusions.txt` is therefore an explicit, reviewed exclusion with the
 * task that makes it vanish — and an exclusion that outlives its reason would hide a real defect.
 *
 * `GAP-022` showed the first implementation decided the wrong thing four ways:
 *
 * - it waited for the *module* to gain source, while the register is per **edge**; the accepted
 *   rule (`DEC-081`, option B) is per-edge consumption, because different edges activate in
 *   different tasks;
 * - it derived the package of a module from Gradle path spelling, so `:feature:character-detail`
 *   yielded `…feature.character-detail` where the code declares `…feature.characterdetail`;
 * - it ignored test consumption, although an excluded *test* configuration whose source set
 *   consumes the edge has served its purpose;
 * - it treated a missing package fact as "unused", the one direction that silently keeps a stale
 *   exclusion alive.
 *
 * The rule this task enforces:
 *
 * - the module must exist, and must still declare the excluded dependency — a removed declaration
 *   means the entry is stale;
 * - the edge's own consumer must not already consume the dependency's package in the source set of
 *   the excluded configuration, production or test according to that configuration;
 * - a fact the check cannot establish (no package information for a module or a dependency, an
 *   unreadable register, a malformed or duplicated entry, a `Done` removal task) is a **finding**,
 *   never a silent pass.
 */
abstract class VerifyDependencyAdviceRegisterTask : DefaultTask() {

    /** The register, `gradle/dependency-advice-exclusions.txt`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val register: RegularFileProperty

    /** Module path to its declared project dependencies, taken from the build model. */
    @get:Input
    abstract val declaredProjectDependencies: MapProperty<String, List<String>>

    /**
     * Module path to its **own Kotlin package**, read from the build model.
     *
     * The package is the one the module's source declares (`DESIGN.md` §3.4), not a string built by
     * replacing separators in the Gradle path; `GAP-022` reproduced the difference on
     * `:feature:character-detail`, whose real package is `…feature.characterdetail`.
     */
    @get:Input
    abstract val packageNames: MapProperty<String, String>

    /**
     * Modules of this build that own at least one Kotlin source file.
     *
     * A module with no source cannot consume anything, so its exclusion is live by the placeholder
     * state itself rather than by a missing fact (`DEC-081`).
     */
    @get:Input
    abstract val modulesWithSource: org.gradle.api.provider.SetProperty<String>

    /**
     * Dependency path to the Kotlin package roots its artifact exposes.
     *
     * A project dependency's package is the consuming module's own package root; an external
     * artifact's is taken from its group and artifact id. Either way it comes from the build model,
     * and a dependency with no resolvable package is a finding rather than an assumption.
     */
    @get:Input
    abstract val dependencyPackageRoots: MapProperty<String, String>

    /** Module path to its production Kotlin sources, so consumption can be decided. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    /** Module path to its test Kotlin sources, because an excluded test edge is consumed by tests. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    /**
     * Edges whose excluded configuration is a test configuration, as `<module>|<dependency>`.
     *
     * `DEC-081` records that test consumption counts: if the excluded configuration is a test
     * configuration and its source set consumes the edge, the exclusion has served its purpose.
     * This input is what makes "the excluded configuration's own source set" decidable without the
     * build model inside the task.
     */
    @get:Input
    abstract val testConfigurationEdges: org.gradle.api.provider.SetProperty<String>

    /**
     * Removal tasks that are already `Done`, as `<module>|<dependency>|<task>`.
     *
     * A `Done` task may not remain an unexplained future owner of an exclusion: either the edge is
     * consumed (and the entry is deleted) or the entry names a task that has not finished. The
     * caller passes the set of completed task ids it knows; an entry naming a completed task fails.
     */
    @get:Input
    abstract val completedTasks: org.gradle.api.provider.SetProperty<String>

    /** A parsed register entry. */
    private data class Exclusion(val module: String, val dependency: String, val task: String)

    @TaskAction
    fun verify() {
        val findings = mutableListOf<String>()
        val rawLines = register.get().asFile.readLines()

        val entries = mutableListOf<Exclusion>()
        rawLines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            val parts = trimmed.split("|")
            if (parts.size != 3 || parts.any { it.isBlank() }) {
                findings +=
                    "line ${index + 1} of the register, `$trimmed`, does not read " +
                        "`<module>|<dependency>|<task>` (DEC-081)"
                return@forEachIndexed
            }
            entries += Exclusion(parts[0].trim(), parts[1].trim(), parts[2].trim())
        }

        // Malformed input is a finding, never ignored: a duplicated entry means two removal owners
        // for one edge, and the register's own header says one.
        entries.groupBy { "${it.module}|${it.dependency}" }.filterValues { it.size > 1 }.forEach { (edge, _) ->
            findings += "the register lists `$edge` more than once; one edge has one removal owner (DEC-081)"
        }

        val declared = declaredProjectDependencies.get()
        val packages = packageNames.get()
        val dependencyPackages = dependencyPackageRoots.get()
        val completed = completedTasks.get()
        val withSource = modulesWithSource.get()
        val testEdges = testConfigurationEdges.get()

        entries.distinctBy { "${it.module}|${it.dependency}" }.forEach { (module, dependency, task) ->
            val moduleDependencies = declared[module]
            if (moduleDependencies == null) {
                findings +=
                    "$module is not a module of this build; the register names a module that does not " +
                        "exist (DEC-081)"
                return@forEach
            }
            if (dependency !in moduleDependencies) {
                findings +=
                    "$module no longer declares `$dependency`; the exclusion is stale and must be " +
                        "deleted (DEC-081)"
                return@forEach
            }
            // Missing facts are diagnostics. Treating an unknown package as "unused" is the one
            // direction that keeps a stale exclusion alive, which is what this check exists to stop.
            if (module !in withSource) {
                // The module has no source yet, so it cannot consume the edge.
                return@forEach
            }
            if (packages[module].isNullOrBlank()) {
                findings +=
                    "the package of `$module` could not be read from its source, so whether it " +
                        "consumes `$dependency` cannot be decided (DEC-081)"
                return@forEach
            }
            val dependencyPackage = dependencyPackages[dependency]
            if (dependencyPackage.isNullOrBlank() && dependency.startsWith(":") && dependency !in withSource) {
                // A project dependency with no source declares no package, so it cannot be consumed.
                return@forEach
            }
            if (dependencyPackage.isNullOrBlank()) {
                findings +=
                    "the package roots of `$dependency` could not be read from the build model, so " +
                        "whether `$module` consumes it cannot be decided (DEC-081)"
                return@forEach
            }

            val edge = "$module|$dependency"
            val consumerIsTest = edge in testEdges
            val candidates =
                (if (consumerIsTest) testSources.files else productionSources.files)
                    .filter { it.invariantSeparatorsPath.contains(moduleSourceSegment(module)) }
            val consumed = candidates.any { file -> referencesPackage(file.readText(), dependencyPackage) }
            if (consumed) {
                findings +=
                    "$module already consumes `$dependency` in its ${if (consumerIsTest) "test" else "production"} " +
                        "source, so the exclusion must be deleted in $task; a stale exclusion would hide a " +
                        "real unused dependency (DEC-081)"
            }
            if (task in completed) {
                findings +=
                    "$module's exclusion names `$task`, which is already Done, but the edge is still " +
                        "excluded; the entry must be deleted with the change that consumes the edge " +
                        "(DEC-081)"
            }
        }

        if (findings.isNotEmpty()) {
            throw IllegalStateException(
                "verifyDependencyAdviceRegister found ${findings.size} register problem(s):\n" +
                    findings.distinct().sorted().joinToString("\n") { "  DEC-081: $it" },
            )
        }
    }

    private fun moduleSourceSegment(module: String): String = module.removePrefix(":").replace(':', '/') + "/src/"

    /**
     * Whether a source file consumes the dependency's package.
     *
     * Comments and string literals are ignored, because a mention in a comment or a fixture path is
     * not an import (`DEC-081` records this qualification).
     */
    private fun referencesPackage(
        source: String,
        prefix: String,
    ): Boolean =
        source.lineSequence().any { line ->
            val code = line.substringBefore("//").trim()
            if (code.isEmpty()) {
                false
            } else {
                code.startsWith("import $prefix.") ||
                    code.contains(" $prefix.") ||
                    code.contains("($prefix.") ||
                    code.contains(":$prefix.") ||
                    code.contains("\"$prefix.") ||
                    code.startsWith("$prefix.")
            }
        }
}
