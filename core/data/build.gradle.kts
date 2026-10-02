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
            // `IC-011` returns domain types, and the REST adapter takes the one Ktor client (`DEC-011`)
            // and a dispatcher, so all three are part of the module's surface.
            api(project(":core:domain"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.coroutines.core)
            // The adapter builds and validates URLs with `io.ktor.http` types directly.
            implementation(libs.ktor.http)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            // The opt-in JVM target (`DEC-079`) is analysed on its own, and dependency analysis asks for
            // the domain edge to be stated for it as well; it is the same edge as `commonMain`'s.
            api(project(":core:domain"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            // `DEC-089`: the data layer's tests consume the shared harness — fixtures, `MockHttp`
            // and virtual time — from a test source set only.
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
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
        //
        // TASK-100 (`B2-R03`, `GAP-017`): the Kotlin/Native simulator task is a
        // `KotlinTest`-shaped task that also implements `TestFilter`, but it is NOT a JVM
        // `org.gradle.api.tasks.testing.Test`, so the previous cast silently left it unfiltered and
        // the native target executed the whole module's tests. The filter is applied through the
        // interface both task types implement.
        when (this) {
            is org.gradle.api.tasks.testing.Test -> filter.setIncludePatterns("*.*TEST-CONTRACT-*")
            else -> {
                // The Kotlin/Native simulator task is not a JVM `Test`, but it does expose the
                // Kotlin test filter. `setIncludePatterns` there takes an array, not a `Set`, so the
                // argument is adapted to the declared parameter type; a task without the setter is a
                // configuration error, not a silent no-op (`GAP-017`).
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
                    "the native test task exposes no setIncludePatterns; the contract filter cannot be applied"
                }
                val pattern = "*.*TEST-CONTRACT-*"
                val argument = if (setPatterns.parameterTypes[0].isArray) arrayOf(pattern) else listOf(pattern)
                setPatterns.invoke(filter, argument)
            }
        }
    }
}

tasks.named<io.github.davidru85.multiverse.buildlogic.testing.VerifyContractCasesTask>("verifyContractCases") {
    // The verification reads the reports, so it must not run before the tasks that write them.
    mustRunAfter(tasks.matching { it.name in contractTestTargets })
    outputs.upToDateWhen { false }
    reports.from(
        layout.buildDirectory.dir("test-results/testAndroidHostTest").map { dir -> fileTree(dir) { include("*.xml") } },
        layout.buildDirectory.dir("test-results/iosSimulatorArm64Test").map { dir -> fileTree(dir) { include("*.xml") } },
    )
    // TASK-100 (`B2-R03`, `GAP-017`): the decision is per target, so a green JVM run cannot stand in
    // for a native run that produced no report (`DEC-054`, `DEC-071`).
    val androidHostReports = layout.buildDirectory.dir("test-results/testAndroidHostTest")
    val iosSimulatorReports = layout.buildDirectory.dir("test-results/iosSimulatorArm64Test")
    targetReportDirectories.set(
        mapOf(
            "testAndroidHostTest" to androidHostReports.get().asFile.absolutePath,
            "iosSimulatorArm64Test" to iosSimulatorReports.get().asFile.absolutePath,
        ),
    )
}

kotlin.targets.named("jvm") {
    compilations.create("contractLive") {
        // The scheduled live source set and the case that pins its record shape, on one compilation
        // (TASK-027, DEC-074). Keeping them together is what lets the analyser see the edges: a
        // cross-compilation file collection is opaque to it, and a source set of its own would
        // either need the same trick or break the fixture/replay filter (TASK-026).
        defaultSourceSet.kotlin.srcDir("src/contractLive/kotlin")
        defaultSourceSet.dependencies {
            // The record type is part of the surface the case reads, so the codec is `api`.
            api(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlin.test.junit)
        }
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

/**
 * Runs the live compilation's own cases (`TEST-CONTRACT-006`, `TASK-027`).
 *
 * The probes reach the network, so this task is **not** wired into `check`: the scheduled workflow
 * runs it, and the fixture/replay entry point never selects it (`DEC-071`, `DEC-074`).
 */
tasks.register<org.gradle.api.tasks.testing.Test>("contractLiveTest") {
    group = "verification"
    description = "TEST-CONTRACT-006: the observation record shape, against the live service " +
        "(TASK-027, DEC-074); scheduled signal only."
    val live =
        kotlin.targets
            .getByName("jvm")
            .compilations
            .getByName("contractLive")
    testClassesDirs = live.output.classesDirs
    classpath = files(live.output.allOutputs, live.runtimeDependencyFiles)
    useJUnit()
    outputs.upToDateWhen { false }
}
