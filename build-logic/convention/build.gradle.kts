// The convention-plugin module.
//
// It compiles against the Android and Kotlin Gradle plugin APIs (`compileOnly`)
// and registers three plugins by id. The plugins are Kotlin classes, so they can
// read the version catalog through `VersionCatalogsExtension`; a precompiled
// script could not (OD-3).
//
// The plugins configure targets, namespaces and compiler options only. No
// project-to-project edge is declared here: every edge lives in the consuming
// module's own build script, so the dependency graph is reviewable per module.

plugins {
    `kotlin-dsl`
}

group = "io.github.davidru85.multiverse.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    // The ktlint extension type the convention plugins configure (TASK-029).
    compileOnly(libs.ktlint.gradle)

    // TASK-091: the durable regression suite for the boundary and policy logic. `kotlin-test`
    // and `junit4` are already pinned in the catalog for the shared test harness (TASK-024) and
    // are reused here rather than introducing a second framework; TestKit ships with Gradle.
    // `kotlin.gradle.plugin` is a test dependency so a TestKit fixture can apply the plugins
    // under test without the Android/Kotlin plugin classes missing at runtime.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "multiverse.kmp.library"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "multiverse.android.library"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "multiverse.android.application"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("dependencyPolicy") {
            id = "multiverse.dependency.policy"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.policy.DependencyPolicyPlugin"
        }
        register("repositoryHygiene") {
            id = "multiverse.repository.hygiene"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.hygiene.RepositoryHygienePlugin"
        }
        register("moduleBoundaries") {
            id = "multiverse.module.boundaries"
            implementationClass = "io.github.davidru85.multiverse.buildlogic.boundaries.ModuleBoundariesPlugin"
        }
    }
}
