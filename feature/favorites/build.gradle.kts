// :feature:favorites — the favourites list, its empty state and its read path.

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The placeholder screen and the real one are composables, so the module needs the Compose
    // compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Compose compiler runs for the **Android** compilation only, exactly as
 * `feature/character-detail` does.
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
 * Android host-test JVM gets the same grants `:core:designsystem`, `:feature:character-detail` and
 * `:androidApp` declare for their own Robolectric runs (`TEST-UI-005`). The KMP host-test component
 * has no `testOptions` block; the `Test` task's JVM arguments are what Robolectric actually needs.
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
     * The two Android options a KMP module must state itself.
     *
     * `androidResources.enable` is `false` for a multiplatform library by default, so a module's own
     * bundled resources would be dropped; the favourites copy is resolved from the one Android copy
     * set `:core:designsystem` ships, and enabling the module's own resources keeps that resolution
     * identical to the sibling detail module's.
     *
     * `isIncludeAndroidResources` on the host-test compilation is what lets Robolectric (and the
     * Compose rule) resolve that copy set — without it `CopyResolver.copy(...)` cannot resolve in
     * `TEST-UI-005`. The convention plugin already created the compilation, so it is configured here
     * rather than created again; `compilations.withType` is the plugin's documented way to do that.
     */
    extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        androidResources {
            enable = true
        }
        compilations.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation>().configureEach {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            // `favoritesModule` is a Koin module; the composition roots load it (`DEC-091`,
            // `DESIGN.md` §5), so the DI library is a shared-production dependency.
            implementation(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier`, Compose runtime and painter
            // types, and `FavoritesViewModel` exposes a `StateFlow`, so those are part of what the
            // module exposes and are declared `api`; the design system exports the BOM and Material 3
            // the same way, so the feature never pins the alpha itself (GUIDELINES.md 5.1, ADR-0008
            // rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            // The illustration the caller passes in is a `Painter`, so `ui-graphics` is part of the
            // module's public surface.
            api(libs.androidx.compose.ui.graphics)
            // The Android state holder of `IC-020` (`DESIGN.md` §5, ADR-0006).
            api(libs.androidx.lifecycle.viewmodel)
            // The favourites grid is a `LazyVerticalStaggeredGrid`; the module uses `foundation`
            // rather than exposing it.
            implementation(libs.androidx.compose.foundation)
            // `favoritesViewModelModule` declares the ViewModel with Koin's `viewModel` DSL, and
            // `FavoritesRoute` resolves it with `koinViewModel()`.
            implementation(libs.koin.android)
            api(libs.koin.androidx.compose)
        }
        commonTest.dependencies {
            // `DEC-089`: the harness is a test source set's dependency only.
            implementation(project(":core:testing"))
            // `R10` admits `:core:data` to a feature's **test** source sets (`DEC-089`), which is what
            // lets the cache case drive the real repository over the real cache instead of only a
            // double.
            implementation(project(":core:data"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidHostTest.dependencies {
            // `TEST-UI-005`/`TEST-UI-013` run on the JVM host with Robolectric and the Compose rule.
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            // The Compose rule hosts its content in a provided activity; the artifact supplies it to
            // the test manifest, so a case needs no activity of the module's own.
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}
