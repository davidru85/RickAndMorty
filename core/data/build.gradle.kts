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
        jvmTest.dependencies {
            // Dependency analysis reads the opt-in JVM target's tests (`DEC-079`) as using this module's
            // classes through the harness's `api` edge to `:core:data`, not through the associated main
            // compilation, and asks for the edge to be declared. It is the module's own output, and the
            // boundary check ignores a self-edge.
            implementation(project(":core:data"))
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
 * `TEST-CONTRACT-*` fixture/replay entry points (`TASK-026`, `DEC-073`; one per target since
 * `TASK-037`, `DEC-090`).
 *
 * `contractTestReplayAndroidHost` and `contractTestReplayIosSimulator` each run exactly the
 * `TEST-CONTRACT-*` cases of their own target and verify that target's reports only, so a green JVM
 * run cannot stand in for a native one; `contractTestReplay` keeps requiring both. Every entry point
 * fails when its target executes zero contract cases (`DEC-071`). The `android` job runs the host
 * entry point; the native one runs locally on macOS while `DEC-083` suspends the `ios` job.
 */
contractTests {
    replay("AndroidHost", "testAndroidHostTest")
    replay("IosSimulator", "iosSimulatorArm64Test")
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
