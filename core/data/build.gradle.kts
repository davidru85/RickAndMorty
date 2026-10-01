// :core:data — remote data sources, DTOs, mappers, response cache, pager,
// favorites and app-settings stores, repository implementations.

plugins {
    id("multiverse.kmp.library")
    id("multiverse.contract.tests")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            // TEST-CONTRACT-006 instantiates the probes' @Serializable record, so the codec runtime
            // is on the test classpath (TASK-027, DEC-074).
            implementation(libs.kotlinx.serialization.json)
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

kotlin.targets.named("jvm") {
    compilations.create("contractLive") {
        // The scheduled live source set, with its own compilation so it never shares a task with
        // the fixture/replay entry point above or the formatter's view of the test sources
        // (TASK-027, DEC-074). ADR-0002 as amended by DEC-079 is what allows the JVM target.
        defaultSourceSet.kotlin.srcDir("src/contractLive/kotlin")
        defaultSourceSet.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
    // Declared after the compilation exists: TEST-CONTRACT-006 asserts the probes' record shape
    // without the probes leaking into a production or ordinary test source set.
    compilations.getByName("test").defaultSourceSet.dependencies {
        implementation(files(compilations.getByName("contractLive").output.classesDirs))
    }
}

/**
 * Records the live API's current shape (`TEST-CONTRACT-006`, `TASK-027`, `DEC-074`).
 *
 * Scheduled signal only: the workflow that calls it runs from `schedule` and `workflow_dispatch`,
 * and `verifyWorkflowGate` proves no pull-request or push trigger can reach it. The captures it
 * writes are the artifact a human uses for the fixture-refresh pull request (`TESTING.md` §11.4).
 */
tasks.register<JavaExec>("contractLiveProbe") {
    group = "verification"
    description = "Observation probes against rickandmortyapi.com; scheduled signal only " +
        "(TASK-027, DEC-074)."
    val contractLive =
        kotlin.targets
            .getByName("jvm")
            .compilations
            .getByName("contractLive")
    classpath = files(contractLive.output.allOutputs, contractLive.runtimeDependencyFiles)
    mainClass.set("io.github.davidru85.multiverse.data.live.ContractLiveMainKt")
    val outputDir = layout.buildDirectory.dir("observations")
    outputs.dir(outputDir)
    args(outputDir.get().asFile.absolutePath)
}
