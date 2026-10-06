// :feature:settings — Sounds preference, remote data-source choice and
// "Delete favorites" with confirmation (DEC-055, ADR-0010).

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The Settings screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
    // The Settings screenshot baselines (`TEST-UI-012`, `TEST-UI-016`, `TASK-045`): Roborazzi
    // records the committed PNGs under `src/androidHostTest/snapshots` and verifies them on the JVM
    // host (DEC-034).
    alias(libs.plugins.roborazzi)
}

/**
 * The Compose compiler runs for the **Android** compilation only, exactly as `feature/favorites`
 * does.
 *
 * The plugin applies to every Kotlin compilation by default, which includes the Apple targets where
 * no Compose runtime exists and none is wanted: every composable lives in `androidMain`, so the
 * compiler has nothing to transform there. `targetKotlinPlatforms` is the plugin's own scoping API,
 * so this is the supported way to say where Compose is, rather than a workaround.
 */
composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm))
}

/**
 * Robolectric reads `SharedSecrets` reflectively, which the pinned daemon JDK restricts, so the
 * Android host-test JVM gets the same grants `:core:designsystem`, `:androidApp` and
 * `feature/character-detail` declare for their own Robolectric runs (`TEST-UI-017`). The KMP
 * host-test component has no `testOptions` block; the `Test` task's JVM arguments are what
 * Robolectric actually needs.
 */
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}

kotlin {
    /**
     * `isIncludeAndroidResources` on the host-test compilation is what lets Robolectric (and the
     * Compose rule) resolve the one Android copy set `:core:designsystem` ships — without it
     * `CopyResolver.copy(...)` cannot resolve in `TEST-UI-017`. The convention plugin already created
     * the compilation, so it is configured here rather than created again; `compilations.withType` is
     * the plugin's documented way to do that.
     *
     * `androidResources.enable` is `false` for a multiplatform library by default; it is enabled so the
     * module's own bundled row glyphs under `androidMain/res/drawable` ship (`TASK-113`).
     */
    extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        androidResources {
            enable = true
        }
        compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation::class.java).configureEach {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The screen names its copy through `CopyKeys` (`DEC-144`) and the module resolves the
            // composition root's named dispatcher through `PresentationBindings` (`DEC-145`).
            implementation(project(":core:presentation"))
            // `SettingsStateHolder`'s surface and the state contracts `IC-018`/`IC-019` are the module's
            // own public shape, so both edges it composes them from are `api`.
            api(project(":core:domain"))
            // `settingsModule` is a Koin module; the graph is built in the composition roots
            // (`DEC-091`, `DESIGN.md` §5), so the DI library is a shared-production dependency and
            // part of the module's surface.
            api(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            // The screen composes design-system components and resolves their copy; the edge is used
            // rather than exposed, so it stays `implementation` (dependency analysis, ADR-0001,
            // `DESIGN.md` §3.4 rule 5).
            implementation(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier` and Compose runtime types,
            // and `SettingsViewModel` exposes a `StateFlow`, so those are part of what the module
            // exposes and are declared `api`.
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            api(libs.androidx.lifecycle.viewmodel)
            // The state holder's coroutine-backed `StateFlow` is part of the surface too.
            api(libs.kotlinx.coroutines.core)
            // The screen is built from Material 3 components, which the module names directly and the
            // BOM governs. `:core:designsystem` owns the pin and exports Material 3, so this
            // declaration takes the same version from the same BOM (ADR-0008 rule 2, `GUIDELINES.md`
            // §5.1). Foundation and `foundation-layout` carry the scroll and layout primitives the
            // screen uses.
            api(libs.androidx.compose.material3)
            api(libs.androidx.compose.foundation.layout)
            implementation(libs.androidx.compose.foundation)
            implementation(libs.androidx.compose.ui.graphics)
            implementation(libs.androidx.compose.ui.text)
            // The ViewModel, lifecycle, Koin and coroutine types the route and state holder use.
            implementation(libs.androidx.lifecycle.common)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.core.viewmodel)
            // `SettingsRoute` resolves its ViewModel with `koinViewModel()`, which the module uses; the
            // module binds it with Koin's Android `viewModel` DSL.
            implementation(libs.koin.androidx.compose)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidHostTest.dependencies {
            // The UI cases run on the JVM host with Robolectric and the Compose test rule
            // (`TEST-UI-017`).
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.junit4)
            // Robolectric executes the cases; they never name its API, so it is a runtime dependency
            // (dependency analysis, `DEC-077`).
            runtimeOnly(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            // The rule's node-matching surface (`hasScrollAction`, `performScrollToNode`) and the
            // geometry types it returns.
            implementation(libs.androidx.compose.ui.test)
            implementation(libs.androidx.compose.ui.geometry)
            // The layout case measures heights and widths in `Dp` (`TEST-UI-033`).
            implementation(libs.androidx.compose.ui.unit)
            // The JUnit 4 runner adapter, the concurrency runtime the cases use, and Robolectric's
            // annotation and shadow artifacts, each declared where it is used instead of reached
            // transitively (`DEC-077`).
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.robolectric.annotations)
            implementation(libs.robolectric.shadows.framework)
            // The Compose rule hosts its content in a provided activity; the artifact supplies it to
            // the test manifest, and the cases never name its API, so it is a runtime dependency of
            // the host-test manifest merge (dependency analysis, `DEC-077`).
            runtimeOnly(libs.androidx.compose.ui.test.manifest)
            // The screenshot harness (`TASK-045`): the capture API and its JUnit rule, declared where
            // the screen snapshots use them (`DEC-077`).
            // The capture API (`captureRoboImage`) and the options type the calls name explicitly;
            // `roborazzi-compose` and `roborazzi-junit-rule` are unused here, and dependency analysis
            // rejects an unused edge (DEC-077), so only what is referenced is declared.
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.core)
        }
    }
}

// Compose Preview renders this module's previews — declared with the design system's preview set, which
// carries `@Preview` — through `ui-tooling` at run time. The Android-KMP plugin has no `debug` build type
// to scope it to, so it goes on the Android runtime classpath only: it is neither compiled against nor
// published to the app (`TASK-140`, `DEC-164`).
dependencies {
    "androidRuntimeClasspath"(libs.androidx.compose.ui.tooling)
}
