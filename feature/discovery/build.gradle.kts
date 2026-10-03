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

// Robolectric reads `SharedSecrets` reflectively, which the pinned daemon JDK restricts; the Android
// host-test JVM therefore gets the same grants `:core:designsystem` and `:androidApp` declare for
// their own Robolectric runs (`TEST-UI-001`, `TEST-UI-003`, `TEST-UI-009`).
//
// AGP points the host run at the manifest and resource APK it merges from this module's own
// `androidHostTest` manifest and the Android copy set `:core:designsystem` ships. Both are declared as
// inputs below, so a change to either re-runs the cases that read them.
val hostTestManifest = layout.projectDirectory.file("src/androidHostTest/AndroidManifest.xml")
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
    // A Kotlin Multiplatform library's host-test run has no test APK, so AGP does not create the
    // manifest-merge and resource pipeline Robolectric needs unless the compilation opts in. With it,
    // the module's committed host-test manifest (the `androidx.activity.ComponentActivity` the Compose
    // rule hosts its content in) and the Android copy set `:core:designsystem` ships are merged into
    // the packaged manifest and resource APK the host run is pointed at (`TEST-UI-003`).
    extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation::class.java).configureEach {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The feature's shared production types (`CharacterListUiState`, the use cases) are part
            // of what the iOS consumer links, so the API cores are `api` (GUIDELINES.md 5.1).
            api(project(":core:domain"))
            api(project(":core:presentation"))
            // `discoveryModule` is a Koin module; the graph is built in the composition roots
            // (`DEC-091`), so the DI library is a shared-production dependency and is part of the
            // module's exposed contract.
            api(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            // The design system's components are part of what the feature's Android screen exposes,
            // so the edge is `api` (GUIDELINES.md 5.1).
            api(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier` and Compose runtime types,
            // and `DiscoveryViewModel` exposes a `StateFlow`, so those are part of what the module
            // exposes and are declared `api`; the design system exports the BOM and Material 3 the
            // same way, so the feature never pins the alpha itself (GUIDELINES.md 5.1, ADR-0008 rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            // The screen draws with `Color`/`Painter` types, so `ui-graphics` is part of the module's
            // exposed surface (`DEC-077`).
            api(libs.androidx.compose.ui.graphics)
            // The ViewModel is bound with Koin's `viewModel` DSL and the screen resolves it with
            // `koinViewModel()`, so both surfaces are the module's own (DESIGN.md §5).
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            // The screens call `collectAsStateWithLifecycle` and resolve their ViewModel with
            // `koinViewModel()`, so the Compose integration and the Koin ViewModel artifacts are
            // declared where they are used rather than reached transitively (DEC-077).
            implementation(libs.androidx.lifecycle.common)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.core.viewmodel)
            api(libs.androidx.lifecycle.viewmodel)
            // `DiscoveryViewModel` exposes a `StateFlow`, so `kotlinx-coroutines-core` is part of what
            // the module exposes and is declared `api` (`DEC-077`).
            api(libs.kotlinx.coroutines.core)
            // The staggered grid, the search field and the chip row are composables over
            // `foundation`; the module uses them rather than exposing them, so the edge is
            // `implementation`.
            api(libs.androidx.compose.foundation)
            // The grid and the scroll container compose over `foundation-layout` primitives, which the
            // module uses (`DEC-077`).
            implementation(libs.androidx.compose.foundation.layout)
            // The `SharedTransitionLayout` the screen composes for the card transition (`DEC-077`).
            implementation(libs.androidx.compose.animation.core)
            // Material 3 components are the screen's own composition, not its exposed surface; the
            // design system exports the alpha, so the feature never pins it (ADR-0008 rule 2).
            implementation(libs.androidx.compose.material3)
            // Text and unit types are Compose's own; the module uses them (`DEC-077`).
            implementation(libs.androidx.compose.ui.text)
            implementation(libs.androidx.compose.ui.unit)
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
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            // The rule's own assertion API the cases call, declared where it is used (`DEC-077`).
            implementation(libs.androidx.compose.ui.test)
            // `AndroidJUnit4` is the runner the host cases execute on (`DEC-077`).
            implementation(libs.androidx.test.ext.junit)
            // The cases compose over `Color`/`ColorPainter`, so the graphics artifact is declared where
            // it is used (`DEC-077`).
            implementation(libs.androidx.compose.ui.graphics)
            // The screen cases drive suspending state holders, so the coroutines runtime is declared
            // where it is used (`DEC-077`).
            implementation(libs.kotlinx.coroutines.core)
            // `@Config` and the `Shadow` types the host run resolves (`DEC-077`).
            implementation(libs.robolectric.annotations)
            // Robolectric is only needed on the host-test **runtime** path: no case compiles against
            // it, so the edge is `runtimeOnly` (`DEC-077`).
            runtimeOnly(libs.robolectric)
        }
    }
}
