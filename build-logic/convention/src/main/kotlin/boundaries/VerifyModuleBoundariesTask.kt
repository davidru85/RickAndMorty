package io.github.davidru85.multiverse.buildlogic.boundaries

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * `verifyModuleBoundaries` — the accepted module graph of ADR-0001, as amended by ADR-0010,
 * ADR-0012 and `DEC-066`…`DEC-069`, turned into an executable policy (`REQ-NFR-001`,
 * `REQ-NFR-009`, `AC-REQ-NFR-009-1`, `AC-REQ-NFR-009-3`; `TEST-UNIT-017`, `TEST-UNIT-012` and the
 * staged `TEST-UNIT-043` of `DEC-068`).
 *
 * The graph is captured at configuration time from each project's **own** declarations, so the
 * task executes with no `Project` access and is configuration-cache compatible. It has no
 * outputs, so it is never up to date and never answers from a cached result.
 *
 * Diagnostics are collected for every rule before the task fails, sorted, and repository-relative:
 * a failure names the `TEST-###` id, the rule id, the consumer, the configuration and source set,
 * the producer and the rule — never an absolute machine path.
 */
@DisableCachingByDefault(because = "verification task with no outputs; a cached policy result would be a false guarantee")
abstract class VerifyModuleBoundariesTask : DefaultTask() {

    /** The configuration-time snapshot of every project, its declared edges and its externals. */
    @get:Input
    abstract val graph: Property<ModuleGraphSnapshot>

    /** Feature production sources, read for the `S2` app-wide-`NavHost` scan only. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val featureSources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val log = ModuleBoundaryRules.evaluate(graph.get())
        scanForAppWideNavHost(log)

        if (!log.isEmpty()) {
            logger.error(log.render())
            throw GradleException(
                "verifyModuleBoundaries found ${log.size()} module-boundary violation(s); the rules are owned by " +
                    "docs/DESIGN.md §3.4 and docs/adr/0001-module-boundaries.md, and the diagnostics above name " +
                    "each violation's rule and location.",
            )
        }
        logger.lifecycle(
            "verifyModuleBoundaries passed: ${graph.get().projects.size} project(s) checked against the module " +
                "rules of ADR-0001 as amended (R1–R14, S1–S3; TEST-UNIT-017, TEST-UNIT-012, TEST-UNIT-043).",
        )
    }

    /** `S2` — the app-wide `NavHost` belongs to the shell; a feature source must not name it. */
    private fun scanForAppWideNavHost(log: BoundaryViolationLog) {
        featureSources.files.sortedBy { it.path }.forEach { file ->
            val relative = file.relativeToOrNull(rootDirectory.get().asFile)?.invariantSeparatorsPath ?: file.name
            val text = file.readText()
            KotlinSources.mask(text).let { code ->
                if (APP_WIDE_NAV_HOST.containsMatchIn(code)) {
                    val project = projectPathOf(relative)
                    log.add(
                        ModuleBoundaryViolation(
                            testId = BoundaryTestIds.STRUCTURE,
                            ruleId = "S2",
                            consumer = project,
                            configuration = "",
                            sourceSet = "",
                            producer = "",
                            reason = "`$relative` names the app-wide `NavHost`; the application shell owns the graph " +
                                "(ADR-0001 rule 7, AC-REQ-NFR-009-2)",
                        ),
                    )
                }
            }
        }
    }

    /** `src/...` under `feature/<name>/` belongs to `:feature:<name>`. */
    private fun projectPathOf(relative: String): String {
        val parts = relative.split('/')
        val featureIndex = parts.indexOf("feature")
        return if (featureIndex >= 0 && parts.size > featureIndex + 1) {
            ":feature:${parts[featureIndex + 1]}"
        } else {
            relative
        }
    }

    private fun File.relativeToOrNull(root: File): File? =
        runCatching { relativeTo(root) }.getOrNull()

    private companion object {
        val APP_WIDE_NAV_HOST = Regex("\\bNavHost(?:\\s*\\(|\\b)")
    }
}

/**
 * A minimal comments-and-strings mask, so a rule matches code rather than a comment or a string
 * literal. It is deliberately smaller than the policy plugin's lexer: the staged `S2` rule needs
 * only to avoid a false positive from a comment or a doc string.
 */
internal object KotlinSources {

    fun mask(text: String): String {
        val out = text.toCharArray()
        var i = 0
        var blockDepth = 0
        var lineComment = false
        var string = false
        var raw = false
        while (i < text.length) {
            val c = text[i]
            when {
                lineComment -> {
                    if (c == '\n') lineComment = false else out[i] = ' '
                    i++
                }

                blockDepth > 0 -> when {
                    c == '/' && text.getOrNull(i + 1) == '*' -> { out[i] = ' '; out[i + 1] = ' '; blockDepth++; i += 2 }
                    c == '*' && text.getOrNull(i + 1) == '/' -> { out[i] = ' '; out[i + 1] = ' '; blockDepth--; i += 2 }
                    c == '\n' || c == '\r' -> i++
                    else -> { out[i] = ' '; i++ }
                }

                raw -> {
                    if (c == '"' && text.startsWith("\"\"\"", i)) { raw = false; i += 3 } else { out[i] = ' '; i++ }
                }

                string -> when {
                    c == '\\' -> i += 2
                    c == '"' -> { string = false; i++ }
                    else -> { out[i] = ' '; i++ }
                }

                else -> when {
                    c == '/' && text.getOrNull(i + 1) == '/' -> { out[i] = ' '; out[i + 1] = ' '; lineComment = true; i += 2 }
                    c == '/' && text.getOrNull(i + 1) == '*' -> { out[i] = ' '; out[i + 1] = ' '; blockDepth = 1; i += 2 }
                    c == '"' && text.startsWith("\"\"\"", i) -> { raw = true; i += 3 }
                    c == '"' -> { string = true; i++ }
                    else -> i++
                }
            }
        }
        return String(out)
    }
}
