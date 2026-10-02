// :core:data — remote data sources, DTOs, mappers, response cache, pager,
// favorites and app-settings stores, repository implementations.

plugins {
    id("multiverse.kmp.library")
    id("multiverse.contract.tests")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
    }
}

/**
 * `TEST-CONTRACT-*` fixture/replay entry point (`TASK-026`, `DEC-073`).
 *
 * The suite is selected by the naming convention of `TESTING.md` §13.2 — every case begins with its
 * id — and runs on both targets of the module. The entry point fails when it executes **zero**
 * contract cases, which is what `DEC-071` requires from a newly activated check: a filtered task
 * that never starts is green, so the aggregate carries its own verification over the reports.
 */
val contractTestTargets = listOf("testAndroidHostTest", "iosSimulatorArm64Test")

val contractTestReplay =
    tasks.register("contractTestReplay") {
        group = "verification"
        description = "TEST-CONTRACT-* fixture/replay cases on both targets; fails when none executes " +
            "(TASK-026, DEC-073)."
        dependsOn(tasks.matching { it.name in contractTestTargets })
        dependsOn(tasks.named("verifyContractCases"))
    }

tasks.matching { it.name in contractTestTargets }.configureEach {
    val invokedForContracts =
        gradle.startParameter.taskNames.any { requested ->
            requested.substringAfterLast(':').let {
                it == "contractTestReplay" || it == "verifyContractCases"
            }
        }
    // A selection that matches nothing makes Gradle fail the task with its own message, which would
    // mask the `DEC-071` diagnostic `verifyContractCases` produces. The filter is therefore applied
    // only when a `TEST-CONTRACT-*` case exists in the sources.
    val hasContractCase =
        fileTree("src") {
            include("**/*Test*/**/*.kt")
        }.files.any { file ->
            file.readText().contains("TEST-CONTRACT-")
        }
    if (invokedForContracts && hasContractCase) {
        // `includeTestsMatching` treats the pattern as a class name; a case id lives in the test
        // *name*, so the class-method form is required (`TESTING.md` §13.2 puts the id first).
        (this as? org.gradle.api.tasks.testing.Test)?.filter?.setIncludePatterns("*.*TEST-CONTRACT-*")
    }
}

tasks.named<io.github.davidru85.multiverse.buildlogic.testing.VerifyContractCasesTask>("verifyContractCases") {
    // The verification reads the reports, so it must not run before the tasks that write them.
    mustRunAfter(tasks.matching { it.name in contractTestTargets })
    outputs.upToDateWhen { false }
    reports.from(
        layout.buildDirectory.dir("test-results/testAndroidHostTest").map { dir ->
            fileTree(dir) { include("*.xml") }
        },
        layout.buildDirectory.dir("test-results/iosSimulatorArm64Test").map { dir ->
            fileTree(dir) { include("*.xml") }
        },
    )
}
