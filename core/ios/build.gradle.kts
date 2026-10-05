// :core:ios — the single Kotlin framework the iOS app links.
//
// ADR-0012 (as amended by `DEC-091`, ADR-0014): this module exists only to export the shared
// modules to Swift. It carries **no source file** and no behaviour, and it is the one module in the
// build that declares a native framework binary. It has no Android variant, so an Android-only
// convention plugin must not be applied (`verifyModuleBoundaries` rule `R12`).
//
// It deliberately does not use `multiverse.kmp.library`: that plugin attaches the Android target,
// the Android namespace and the Android SDK levels, none of which can exist here. It applies the
// Kotlin Multiplatform plugin directly and declares its own target set, which the boundary rules
// then check against ADR-0002.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    // ADR-0002's two Apple targets and **no other** — no `iosX64`, no `androidTarget`, no `jvm`
    // (ADR-0012). `verifyModuleBoundaries` `R12`/`R17` reject a target set that disagrees.
    iosArm64()
    iosSimulatorArm64()

    // One framework, static, named for the product. `export` admits only `api` dependencies, which
    // is why the edges below are `api` while every other module in the build is `implementation`.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "MultiverseExplorer"
            isStatic = true
            // The Swift-visible surface is exactly the `api` graph (`DEC-091`): the five feature
            // modules (their state contracts and their route declarations), `:core:domain` and
            // `:core:presentation`. `:core:data` is an unexported implementation, so no
            // implementation type crosses into Swift.
            export(project(":feature:discovery"))
            export(project(":feature:character-detail"))
            export(project(":feature:favorites"))
            export(project(":feature:episodes"))
            export(project(":feature:settings"))
            export(project(":core:domain"))
            export(project(":core:presentation"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` because `export` requires it; the exported set is the Swift-visible surface.
            api(project(":feature:discovery"))
            api(project(":feature:character-detail"))
            api(project(":feature:favorites"))
            api(project(":feature:episodes"))
            api(project(":feature:settings"))
            api(project(":core:domain"))
            api(project(":core:presentation"))
            // Compiled into the framework but **not** exported (`DEC-091`): the feature modules need
            // their implementations at runtime, and `R12` rejects an `api` edge to `:core:data`.
            implementation(project(":core:data"))
            // The debug diagnostics sheet's recorder (`DEC-147`): linked, never exported, so no
            // diagnostic type reaches Swift, and attached to the logger only in the debug binary.
            implementation(project(":core:diagnostics"))
        }
    }
}
