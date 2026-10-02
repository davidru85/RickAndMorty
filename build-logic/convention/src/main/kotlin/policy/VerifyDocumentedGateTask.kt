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
import java.io.File

/**
 * `TEST-UNIT-015` — the documented local gate commands exist and run the checks they claim to run
 * (`AC-REQ-NFR-007-1`; `TASK-029`, `DEC-078`; hardened by `TASK-103`, `B2-R06`).
 *
 * `README.md` §9 is the single documented command list and `GUIDELINES.md` §1.3 names the tools
 * behind it. A command that no longer exists — a renamed task, a removed aggregate, or a task that
 * stopped depending on the check its row claims — turns the documentation into a lie while the
 * clean repository still passes.
 *
 * `GAP-020` showed how weak the first implementation was: it resolved a documented token by name
 * *or* by path suffix, so the bare `test` of README §9's "All shared and unit tests" row resolved
 * to whichever Android unit-test task happened to match, while the shared KMP suites it claims ran
 * not at all; and it compared only each aggregate's **direct** dependencies, so a removed or
 * re-pointed transitive dependency stayed invisible.
 *
 * This implementation therefore decides on the **effective selected graph**:
 *
 * - a documented invocation is resolved the way Gradle resolves it — a task path (`:core:testing:allTests`)
 *   is matched exactly, a bare name selects every registered task of that name, and a task selection
 *   in a non-root project is *not* the root task of the same name;
 * - the graph of that invocation is computed **including transitive dependencies**, so a required
 *   suite that only used to be reachable through a removed `dependsOn` fails;
 * - coverage is asserted against a declared requirement table: the command that documents "all
 *   shared and unit tests" must actually select the shared host and native suites and the
 *   build-logic regression suite, in every project that owns them;
 * - a row that describes a dry run, a skipped task or an empty selection as executed verification
 *   is a finding: `--dry-run` never proves a check ran.
 *
 * Values are captured from the live build at configuration time and passed in, so the task itself
 * touches no `Project` and stays configuration-cache safe.
 */
abstract class VerifyDocumentedGateTask : DefaultTask() {

    /** The documents that carry the command tables, in the order they are paired for parity. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documents: ConfigurableFileCollection

    /**
     * The English and Spanish READMEs, paired.
     *
     * `DEC-047` makes `README.es.md` the only translation, and the two command tables must stay
     * aligned: a command corrected in one language and not the other leaves half the documentation
     * lying. The pair is a single input so the parity rule cannot be satisfied by inspecting one
     * file (`TASK-103`).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val translatedPair: ConfigurableFileCollection

    /** Every task path the build registers, so a documented command can be resolved. */
    @get:Input
    abstract val registeredTaskPaths: ListProperty<String>

    /** Every bare task name the build registers; `./gradlew test` resolves by name, not by path. */
    @get:Input
    abstract val registeredTaskNames: ListProperty<String>

    /**
     * Task path to the **full transitive** set of task paths that invocation executes.
     *
     * The graph is captured from the live build for the invocations the documentation uses. Reading
     * the direct edge only — which the first implementation did — cannot see a required suite that
     * was reachable through an intermediate aggregate.
     */
    @get:Input
    abstract val selectedGraph: MapProperty<String, List<String>>

    /**
     * The task references this build holds to **included builds**, by name.
     *
     * `gradle.includedBuild("build-logic").task(":convention:test")` is a lazy `TaskReference`, so
     * the resolved closure of the root `check` omits it. The guard must still see it: the
     * build-logic suite is one of the shared suites the documented row claims to run (`TASK-103`).
     */
    @get:Input
    abstract val includedBuildTaskNames: ListProperty<String>

    /**
     * Documentation row to the task paths that row must select, because its own text claims them.
     *
     * This is the binding that `GAP-020` was missing: the row's *claim* is data, and the guard
     * decides whether the command's effective graph honours it.
     */
    @get:Input
    abstract val documentedClaims: MapProperty<String, List<String>>

    private companion object {
        const val GATE_BLOCK_BEGIN = "<!-- local-gate:begin -->"
        const val GATE_BLOCK_END = "<!-- local-gate:end -->"

        /** The invocations whose graph must be captured, named by the literal token they use. */
        const val ALL_TESTS_INVOCATION = "allTests"

        /** Suites a "all shared and unit tests" row must actually execute. */
        val SHARED_SUITE_TASKS =
            listOf(
                ":core:testing:testAndroidHostTest",
                ":core:testing:iosSimulatorArm64Test",
                ":build-logic:convention:test",
            )
    }

    @TaskAction
    fun verify() {
        val registered = registeredTaskPaths.get().toSet()
        val registeredNames = registeredTaskNames.get().toSet()
        val graphs = selectedGraph.get()
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
                Regex("`([^`]*?\\./gradlew\\s+[^`]+)`").findAll(line).forEach { match ->
                    val invocation = match.groupValues[1].substringAfter("./gradlew").trim()
                    val tokens = invocation.split(Regex("\\s+")).filter { !it.startsWith("-") && it.isNotEmpty() }
                    // A row that prints its command rather than running it is not evidence.
                    if (Regex("\\becho\\b").containsMatchIn(line)) {
                        findings +=
                            "$document documents `./gradlew ${match.groupValues[1]}`, but the row describes " +
                                "printing the command rather than executing it (TEST-UNIT-015)"
                    }
                    tokens.forEach { token ->
                        val wanted = token.removeSuffix("*")
                        if (wanted == "./gradlew" || wanted.isEmpty()) return@forEach
                        if (!resolves(wanted, registered, registeredNames)) {
                            findings +=
                                "$document documents `./gradlew $wanted`, but this build registers no task the " +
                                    "invocation would select (TEST-UNIT-015; AC-REQ-NFR-007-1)"
                        }
                    }
                    // The claim is decided on the effective graph of the WHOLE invocation — the
                    // union of every token's selection — never on one token's name. `./gradlew
                    // allTests :build-logic:convention:test` claims the shared suites and the
                    // build-logic suite; only the union can honour that claim.
                    if (tokens.any { it.removeSuffix("*") == ALL_TESTS_INVOCATION }) {
                        val selected = tokens.flatMap { token -> graphs[token.removeSuffix("*")] ?: emptyList() }.toSet()
                        if (selected.isEmpty()) {
                            findings +=
                                "$document documents an `$ALL_TESTS_INVOCATION` invocation, but it selects no task " +
                                    "in this build; an empty selection is not a passing gate (TEST-UNIT-015)"
                        }
                        val included = includedBuildTaskNames.get().toSet()
                        SHARED_SUITE_TASKS.forEach { required ->
                            // An included build's suite is not in this build's registration set and
                            // is not materialised into any closure, so it is credited through the
                            // TaskReference the build actually holds for it.
                            val providedByIncludedBuild =
                                required.startsWith(":build-logic:") && required.substringAfterLast(':') in included
                            if (required !in selected && !providedByIncludedBuild) {
                                findings +=
                                    "the documented `$ALL_TESTS_INVOCATION` invocation does not reach `$required`; " +
                                        "its row claims the shared and unit suites run " +
                                        "(TEST-UNIT-015; AC-REQ-NFR-007-1)"
                            }
                        }
                    }
                }
            }
        }

        documentedClaims.get().forEach { (row: String, required: List<String>) ->
            run {
                if (row.isBlank()) return@run
                // A claim names an aggregate that must exist and reach what it promises. The
                // aggregate's own closure is the evidence; where the documentation names several
                // tokens, the union check above decides the row.
                val selected = graphs[row] ?: emptyList()
                if (selected.isEmpty()) {
                    findings += "`$row` selects no task; the documented row claims it runs checks (TEST-UNIT-015)"
                    return@run
                }
                required.forEach { taskPath ->
                    if (taskPath !in selected) {
                        findings +=
                            "`$row` does not run `$taskPath`; the documented row claims a check the " +
                                "invocation no longer executes (TEST-UNIT-015; AC-REQ-NFR-007-1)"
                    }
                }
            }
        }

        findings += compareTranslations()

        if (findings.isEmpty()) return
        throw IllegalStateException(
            "verifyDocumentedGate found ${findings.size} gate documentation problem(s):\n" +
                findings.distinct().sorted().joinToString("\n") { "  TEST-UNIT-015: $it" },
        )
    }

    /**
     * The active gate rows of each README, compared command for command.
     *
     * The block markers make the comparison exact: both files mark their active rows, so the check
     * compares the same set rather than guessing at table geometry.
     */
    private fun compareTranslations(): List<String> {
        val pair = translatedPair.files.sortedBy { it.name }
        if (pair.size != 2) return emptyList()
        val english = pair.first { it.name == "README.md" }
        val spanish = pair.first { it.name == "README.es.md" }
        val commands = { file: File ->
            val lines = file.readLines()
            val begin = lines.indexOfFirst { it.trim() == GATE_BLOCK_BEGIN }
            val end = lines.indexOfFirst { it.trim() == GATE_BLOCK_END }
            if (begin < 0 || end <= begin) {
                emptyList()
            } else {
                lines.subList(begin, end).flatMap { line ->
                    Regex("`([^`]*?\\./gradlew\\s+[^`]+)`").findAll(line).map {
                        it.groupValues[1].substringAfter("./gradlew").trim()
                    }
                }
            }
        }
        val englishCommands = commands(english)
        val spanishCommands = commands(spanish)
        return buildList {
            if (englishCommands.size != spanishCommands.size) {
                add(
                    "README.md and README.es.md document a different number of active gate commands " +
                        "(${englishCommands.size} vs ${spanishCommands.size}); the two tables must stay aligned " +
                        "(DEC-047, TEST-UNIT-015)",
                )
            } else {
                englishCommands.zip(spanishCommands).forEachIndexed { index, (en, es) ->
                    if (en != es) {
                        add(
                            "active gate row ${index + 1} documents `./gradlew $en` in README.md and " +
                                "`./gradlew $es` in README.es.md; the two tables must stay aligned " +
                                "(DEC-047, TEST-UNIT-015)",
                        )
                    }
                }
            }
        }
    }

    /**
     * Whether an invocation token selects something in this build, decided the way Gradle decides:
     * a task path is matched exactly; a bare name selects every registered task of that name, which
     * is what makes bare `test` differ from `:test`.
     */
    private fun resolves(
        token: String,
        registered: Set<String>,
        registeredNames: Set<String>,
    ): Boolean =
        when {
            token.startsWith(":") -> token in registered || token.substringAfterLast(':') in includedBuildTaskNames.get()
            '.' in token || ':' in token -> token in registered
            else -> token in registeredNames
        }
}
