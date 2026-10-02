package io.github.davidru85.multiverse.buildlogic.testing

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `TEST-CONTRACT-*` non-emptiness (`TASK-026`, `DEC-073`).
 *
 * A filtered test task that never starts — `NO-SOURCE` when the module has no test source yet, or
 * `SKIPPED` — does not fail on an empty selection, so an aggregate over such tasks is green with
 * zero contract cases executed. That is exactly the state `DEC-071` forbids: a check that cannot
 * fail. This task therefore verifies the outcome rather than the invocation: it reads the JUnit XML
 * reports of the two targets and fails when no case whose name starts with a `TEST-CONTRACT-` id
 * actually ran.
 *
 * It reads reports, not build state, so it stays correct when a test task is cached or re-run.
 */
abstract class VerifyContractCasesTask : DefaultTask() {

    /** The `TEST-CONTRACT-*` reports produced by the JVM host and the Apple simulator targets. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    /** The id prefix a contract case must carry (`TESTING.md` §13.2). */
    @get:Input
    abstract val idPrefix: ListProperty<String>

    @TaskAction
    fun verify() {
        val prefixes = idPrefix.get()
        val executed = mutableMapOf<String, MutableList<String>>()
        var caseCount = 0

        reports.files.filter { it.isFile && it.extension == "xml" }.sortedBy { it.path }.forEach { file ->
            val text = file.readText()
            // The JUnit XML reports each case as `<testcase name="…">`; the id is the first token
            // of the name, which is what makes traceability greppable (`TESTING.md` §13.2).
            Regex("""<testcase\b[^>]*\bname="([^"]+)"""").findAll(text).forEach { match ->
                val name = match.groupValues[1]
                prefixes.firstOrNull { name.startsWith(it) }?.let { prefix ->
                    executed.getOrPut(prefix) { mutableListOf() }.add(name)
                    caseCount++
                }
            }
        }

        if (caseCount == 0) {
            throw IllegalStateException(
                "contractTestReplay executed no ${prefixes.first()} case. The run is not evidence: " +
                    "a green entry point that ran nothing is the state DEC-071 forbids. Add the contract " +
                    "cases (TASK-037 activates this row in CI with the first one) or the entry point " +
                    "stays out of the required set.",
            )
        }
        logger.lifecycle(
            "contractTestReplay executed $caseCount contract case(s): " +
                executed.entries.joinToString(", ") { "${it.key}*=${it.value.size}" },
        )
    }
}
