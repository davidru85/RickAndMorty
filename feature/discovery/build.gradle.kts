// :feature:discovery — character list, search, status filter, paging.
// The Android design-system edge is declared from the Android source set only
// (ADR-0001: "from Android UI source sets only").

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The Discovery screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Compose compiler runs for the **Android** compilation only (`feature/favorites` is the same).
 *
 * The plugin applies to every Kotlin compilation by default, which includes the Apple targets where
 * no Compose runtime exists and none is wanted: every composable lives in `androidMain`, so the
 * compiler has nothing to transform there. `targetKotlinPlatforms` is the plugin's own scoping API.
 */
composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm))
}

// The UI cases resolve the Android copy set `:core:designsystem` ships and the Compose rule needs the
// merged test resources, so the host-test component the convention plugin creates is configured to
// carry them. `withHostTestBuilder` reaches the component that already exists; a second
// `withHostTest` would be a second component, which AGP rejects.

// Robolectric reads `SharedSecrets` reflectively, which the pinned daemon JDK restricts; the Android
// host-test JVM therefore gets the same grants `:core:designsystem` and `:androidApp` declare for
// their own Robolectric runs (`TEST-UI-001`, `TEST-UI-003`, `TEST-UI-009`).
//
// A Kotlin Multiplatform library's host-test run gets no `com/android/tools/test_config.properties`
// from AGP (there is no test APK to point at), so this module commits one under its host-test
// resources: its own test manifest, and the one Android copy set that ships (`:core:designsystem`).
// Both are declared as inputs below, so a change to either re-runs the cases that read them.
val hostTestManifest = layout.projectDirectory.file("src/androidHostTest/resources/AndroidManifest.xml")
val androidCopySet = layout.projectDirectory.dir("../../core/designsystem/src/main/res")
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    inputs.file(hostTestManifest).withPropertyName("hostTestManifest").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(androidCopySet).withPropertyName("androidCopySet").withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            // `discoveryModule` is a Koin module; the graph is built in the composition roots
            // (`DEC-091`), so the DI library is a shared-production dependency here.
            implementation(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier` and Compose runtime types,
            // and `DiscoveryViewModel` exposes a `StateFlow`, so those are part of what the module
            // exposes and are declared `api`; the design system exports the BOM and Material 3 the
            // same way, so the feature never pins the alpha itself (GUIDELINES.md 5.1, ADR-0008 rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            api(libs.androidx.lifecycle.viewmodel)
            // The staggered grid, the search field and the chip row are composables over
            // `foundation`; the module uses them rather than exposing them, so the edge is
            // `implementation`.
            implementation(libs.androidx.compose.foundation)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidHostTest.dependencies {
            // The UI cases run on the JVM host with Robolectric and the Compose test rule
            // (`TEST-UI-001`, `TEST-UI-003`, `TEST-UI-009`).
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            // The Compose rule hosts its content in a provided activity; the artifact supplies it to
            // the test manifest, so a case needs no activity of the module's own.
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}
