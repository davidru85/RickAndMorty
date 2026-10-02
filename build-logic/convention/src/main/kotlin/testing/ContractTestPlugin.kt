package io.github.davidru85.multiverse.buildlogic.testing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

/**
 * `multiverse.contract.tests` — the fixture/replay contract entry points (`TASK-026`, `DEC-073`;
 * one entry point per target since `TASK-037`, `DEC-090`).
 *
 * Applying it registers the two-target aggregate — `verifyContractCases` and `contractTestReplay` —
 * and the `contractTests` extension. Each `replay(suffix, testTask)` call adds one target: a
 * `contractTestReplay<suffix>` entry point and a `verifyContractCases<suffix>` verifier that reads
 * **that target's reports only**, so a green JVM run can never stand in for a native one. The same
 * target joins the aggregate, which keeps requiring every declared target.
 *
 * A verifier fails unless its target executed at least one `TEST-CONTRACT-*` case, and reads the
 * outcome from the JUnit reports, so a filtered task that never starts is not evidence (`DEC-071`).
 */
class ContractTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register<VerifyContractCasesTask>(AGGREGATE_VERIFIER) {
            group = "verification"
            description = "Fails when no TEST-CONTRACT-* case executed (TASK-026, DEC-073)."
            idPrefix.set(listOf(ID_PREFIX))
            outputs.upToDateWhen { false }
        }
        target.tasks.register(AGGREGATE_ENTRY_POINT) {
            group = "verification"
            description = "TEST-CONTRACT-* fixture/replay cases on every declared target; fails when a target " +
                "executes none (TASK-026, DEC-073)."
            dependsOn(AGGREGATE_VERIFIER)
        }
        target.extensions.create<ContractTestExtension>("contractTests", target)
    }

    internal companion object {
        const val ID_PREFIX = "TEST-CONTRACT-"
        const val AGGREGATE_VERIFIER = "verifyContractCases"
        const val AGGREGATE_ENTRY_POINT = "contractTestReplay"

        /** `TESTING.md` §13.2 puts the id first in the test name, so the class-method form is needed. */
        const val CASE_PATTERN = "*.*TEST-CONTRACT-*"
    }
}

/** The `contractTests` extension: one fixture/replay entry point per target (`DEC-090`). */
abstract class ContractTestExtension
    @Inject
    constructor(
        private val project: Project,
    ) {
        /**
         * Registers `contractTestReplay<suffix>`, which runs exactly the `TEST-CONTRACT-*` cases of
         * [testTask] and verifies that target's own reports, and adds the target to the aggregate.
         *
         * The case filter is applied only when a contract entry point or verifier is requested, so an
         * ordinary run of [testTask] — `check`, `allTests` — keeps every case.
         */
        fun replay(
            suffix: String,
            testTask: String,
        ) {
            val reportDirectory = project.layout.buildDirectory.dir("test-results/$testTask")
            val testTasks = project.tasks.matching { it.name == testTask }

            val verifier =
                project.tasks.register<VerifyContractCasesTask>("${ContractTestPlugin.AGGREGATE_VERIFIER}$suffix") {
                    group = "verification"
                    description = "Fails unless $testTask executed a TEST-CONTRACT-* case (TASK-037, DEC-090)."
                    idPrefix.set(listOf(ContractTestPlugin.ID_PREFIX))
                    mustRunAfter(testTasks)
                    outputs.upToDateWhen { false }
                    reports.from(reportDirectory.map { dir -> project.fileTree(dir) { include("*.xml") } })
                    targetReportDirectories.put(testTask, reportDirectory.map { it.asFile.absolutePath })
                }
            project.tasks.register("${ContractTestPlugin.AGGREGATE_ENTRY_POINT}$suffix") {
                group = "verification"
                description = "TEST-CONTRACT-* fixture/replay cases on $testTask only (TASK-037, DEC-090)."
                dependsOn(testTasks)
                dependsOn(verifier)
            }

            project.tasks.named<VerifyContractCasesTask>(ContractTestPlugin.AGGREGATE_VERIFIER) {
                mustRunAfter(testTasks)
                reports.from(reportDirectory.map { dir -> project.fileTree(dir) { include("*.xml") } })
                targetReportDirectories.put(testTask, reportDirectory.map { it.asFile.absolutePath })
            }
            project.tasks.named(ContractTestPlugin.AGGREGATE_ENTRY_POINT) { dependsOn(testTasks) }

            val requested =
                project.gradle.startParameter.taskNames.any { name ->
                    name.substringAfterLast(':').let {
                        it.startsWith(ContractTestPlugin.AGGREGATE_ENTRY_POINT) ||
                            it.startsWith(ContractTestPlugin.AGGREGATE_VERIFIER)
                    }
                }
            if (requested) testTasks.configureEach { selectContractCases() }
        }

        private fun Task.selectContractCases() {
            when (this) {
                is Test -> {
                    filter.setIncludePatterns(ContractTestPlugin.CASE_PATTERN)
                    // A selection that matches nothing must reach the verifier, whose diagnostic names
                    // the target and the missing case family (`DEC-071`); Gradle's own message would not.
                    filter.isFailOnNoMatchingTests = false
                }
                else -> {
                    // TASK-100 (`B2-R03`, `GAP-017`): the Kotlin/Native simulator task is a
                    // `KotlinTest`-shaped task that exposes the Kotlin test filter but is not a JVM
                    // `Test`. `setIncludePatterns` there takes an array, so the argument is adapted to
                    // the declared parameter type; a task without the setter is a configuration error,
                    // not a silent no-op.
                    val filter =
                        javaClass.methods
                            .firstOrNull { it.name == "getFilter" && it.parameterCount == 0 }
                            ?.invoke(this)
                    val setPatterns =
                        filter
                            ?.javaClass
                            ?.methods
                            ?.firstOrNull { it.name == "setIncludePatterns" && it.parameterCount == 1 }
                    requireNotNull(setPatterns) {
                        "the test task `$name` exposes no setIncludePatterns; the contract filter cannot be applied"
                    }
                    val pattern = ContractTestPlugin.CASE_PATTERN
                    val argument = if (setPatterns.parameterTypes[0].isArray) arrayOf(pattern) else listOf(pattern)
                    setPatterns.invoke(filter, argument)
                }
            }
        }
    }
