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
 * `DEC-077` — the `buildHealth` exclusion register may not outlive the placeholder state.
 *
 * On a tree whose modules are still placeholders, every ADR-0001-mandated edge is "unused": no
 * source consumes it yet. `buildHealth` can therefore block only with explicit, per-module
 * exclusions, and an exclusion that survives the arrival of the consuming code would hide a real
 * defect. This task fails closed in three directions:
 *
 * - the module must exist;
 * - the module must still declare the excluded dependency;
 * - the module's own production source must not yet reference that dependency's package — the
 *   moment it does, the exclusion is stale and the task that consumed the edge must delete it.
 */
abstract class VerifyDependencyAdviceRegisterTask : DefaultTask() {

    /** The register, `gradle/dependency-advice-exclusions.txt`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val register: RegularFileProperty

    /** Module path to its declared project dependencies, taken from the build model. */
    @get:Input
    abstract val declaredProjectDependencies: MapProperty<String, List<String>>

    /** Module path to its production Kotlin sources, so package consumption can be decided. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    /** Module path to the Kotlin package prefix of its own code. */
    @get:Input
    abstract val packagePrefixes: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val entries = register.get().asFile.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split("|")
            check(parts.size == 3) {
                "the exclusion register entry `$trimmed` must read `<module>|<dependency>|<task>` (DEC-077)"
            }
            Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
        }

        val declared = declaredProjectDependencies.get()
        val prefixes = packagePrefixes.get()
        val findings = mutableListOf<String>()

        entries.forEach { (module, dependency, task) ->
            val moduleDependencies = declared[module]
            when {
                moduleDependencies == null ->
                    findings += "$module is not a module of this build; the register names a module that " +
                        "does not exist (DEC-077)"
                dependency !in moduleDependencies ->
                    findings += "$module no longer declares `$dependency`; the exclusion is stale and must " +
                        "be deleted (DEC-077)"
                else -> {
                    val consumed = productionSources.files
                        .filter { it.invariantSeparatorsPath.contains(moduleSourceSegment(module)) }
                        .any { file -> referencesPackage(file.readText(), prefixes[dependency]) }
                    if (consumed) {
                        findings += "$module already consumes `$dependency` in its own source, so the " +
                            "exclusion must be deleted in $task; a stale exclusion would hide a real unused " +
                            "dependency (DEC-077)"
                    }
                }
            }
        }

        check(findings.isEmpty()) {
            "verifyDependencyAdviceRegister found ${findings.size} stale exclusion(s):\n" +
                findings.sorted().joinToString("\n") { "  DEC-077: $it" }
        }
    }

    private fun moduleSourceSegment(module: String): String = module.removePrefix(":").replace(':', '/') + "/src/"

    private fun referencesPackage(source: String, prefix: String?): Boolean {
        if (prefix.isNullOrBlank()) return false
        return source.lineSequence().any { line ->
            val trimmed = line.trim()
            (trimmed.startsWith("import ") && trimmed.contains("$prefix.")) ||
                trimmed.contains("$prefix.")
        }
    }
}
