package io.github.davidru85.multiverse.buildlogic.testing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * `multiverse.contract.tests` — the fixture/replay contract entry point (`TASK-026`, `DEC-073`).
 *
 * Applying it registers `verifyContractCases`, which fails when the `TEST-CONTRACT-*` cases did not
 * run. The module wires it under its own aggregate so the aggregate's success means "the case set
 * is non-empty and green", not "some task was scheduled".
 */
class ContractTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register<VerifyContractCasesTask>("verifyContractCases") {
            group = "verification"
            description = "Fails when no TEST-CONTRACT-* case executed (TASK-026, DEC-073)."
            idPrefix.set(listOf("TEST-CONTRACT-"))
        }
    }
}
