package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `TEST-UNIT-015` — the documented local gate commands exist and run the checks they claim to run
 * (`AC-REQ-NFR-007-1`; `TASK-029`, `DEC-078`).
 *
 * `README.md` §9 is the single documented command list and `GUIDELINES.md` §1.3 names the tools
 * behind it. A command that no longer exists — a renamed task, a removed aggregate, or a task that
 * stopped depending on the check its row claims — turns the documentation into a lie while the
 * clean repository still passes. The values below are captured from the live build at configuration
 * time and passed in, so the task itself touches no `Project` and stays configuration-cache safe.
 */
abstract class VerifyDocumentedGateTask : DefaultTask() {

    /** The documents that carry the command tables. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documents: ConfigurableFileCollection

    /** Every task path the build registers, so a documented command can be resolved. */
    @get:Input
    abstract val registeredTaskPaths: ListProperty<String>

    /** Every bare task name the build registers; `./gradlew test` resolves by name, not by path. */
    @get:Input
    abstract val registeredTaskNames: ListProperty<String>

    /** Aggregate task path to the task paths its documentation row claims it runs. */
    @get:Input
    abstract val claimedAggregateDependencies: MapProperty<String, List<String>>

    /** Aggregate task path to its direct dependencies, captured from the real task graph. */
    @get:Input
    abstract val directDependencies: MapProperty<String, List<String>>

    private companion object {
        const val GATE_BLOCK_BEGIN = "<!-- local-gate:begin -->"
        const val GATE_BLOCK_END = "<!-- local-gate:end -->"
    }

    @TaskAction
    fun verify() {
        val registered = registeredTaskPaths.get().toSet()
        val registeredNames = registeredTaskNames.get().toSet()
        val direct = directDependencies.get()
        val findings = mutableListOf<String>()

        documents.files.sortedBy { it.path }.forEach { file ->
            val document = file.name
            // Only the marked gate block carries active commands: README §9 also lists target
            // state commands with their reason, and those must not be asserted yet.
            val lines = file.readLines()
            val begin = lines.indexOfFirst { it.trim() == GATE_BLOCK_BEGIN }
            val end = lines.indexOfFirst { it.trim() == GATE_BLOCK_END }
            val block = if (begin >= 0 && end > begin) lines.subList(begin, end) else lines
            block.forEach { line ->
                // A documented invocation is a backticked `./gradlew <tasks…>` cell.
                Regex("`\\./gradlew\\s+([^`]+)`").findAll(line).forEach { match ->
                    match.groupValues[1].trim().split(Regex("\\s+")).forEach { token ->
                        if (token.startsWith("-")) return@forEach
                        val wanted = token.removeSuffix("*")
                        if (wanted == "./gradlew") return@forEach
                        val resolved = wanted in registered ||
                            wanted in registeredNames ||
                            registered.any { path -> path.endsWith(":$wanted") }
                        if (!resolved) {
                            findings += "$document documents `./gradlew $wanted`, but this build registers " +
                                "no such task (TEST-UNIT-015; AC-REQ-NFR-007-1)"
                        }
                    }
                }
            }
        }

        claimedAggregateDependencies.get().forEach { (aggregatePath: String, claimed: List<String>) ->
            run {
                if (aggregatePath.isBlank()) return@run
                if (aggregatePath !in registered) {
                    findings += "`$aggregatePath` is not registered; a documented row names it"
                    return@run
                }
                val actual: List<String> = direct[aggregatePath] ?: emptyList()
                claimed.forEach { required: String ->
                    if (required !in actual) {
                        findings += "`$aggregatePath` does not depend on `$required`; the documented " +
                            "aggregate claims a check it no longer runs (TEST-UNIT-015; AC-REQ-NFR-007-1)"
                    }
                }
            }
        }

        if (findings.isEmpty()) return
        throw IllegalStateException(
            "verifyDocumentedGate found ${findings.size} gate documentation problem(s):\n" +
                findings.distinct().sorted().joinToString("\n") { "  TEST-UNIT-015: $it" },
        )
    }
}
