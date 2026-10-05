// :feature:favorites — the favourites list, its empty state and its read path.

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The placeholder screen and the real one are composables, so the module needs the Compose
    // compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
    // The Favorites screenshot baselines (`TEST-UI-012`, `TEST-UI-016`, `TASK-045`): Roborazzi
    // records the committed PNGs under `src/androidHostTest/snapshots` and verifies them on the JVM
    // host (DEC-034).
    alias(libs.plugins.roborazzi)
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
            // `FavoritesStateHolder`'s surface and the state contracts `IC-018`/`IC-019` are the
            // module's own public shape, so the three edges it composes them from are `api`.
            api(project(":core:domain"))
            api(project(":core:presentation"))
            // `favoritesModule` is a Koin module; the composition roots load it (`DEC-091`,
            // `DESIGN.md` §5), so the DI library is a shared-production dependency and part of the
            // module's surface.
            api(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            // The screen composes the design-system components and resolves their copy, so the
            // design-system edge is part of the module's exposed surface (ADR-0001, `DESIGN.md` §3.4
            // rule 5), as are Compose runtime/UI and the state holder.
            api(project(":core:designsystem"))
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
            // `FavoritesStateHolder` exposes a coroutine-backed `StateFlow`, so the concurrency
            // runtime is part of its surface too.
            api(libs.kotlinx.coroutines.core)
            // The favourites grid is a `LazyVerticalStaggeredGrid`; the module uses `foundation` and
            // the grid's own layout primitives, and the staggered grid's public parameter types come
            // from `foundation-layout`, so that one is `api` while the rest of foundation is not.
            api(libs.androidx.compose.foundation.layout)
            implementation(libs.androidx.compose.foundation)
            // The screen names no Material 3 or text type since its title is the design system's
            // `ScreenTitle` (`TASK-113`). The route's `collectAsStateWithLifecycle` and Koin
            // `viewModel`/`koinViewModel` resolution; dependency analysis asks the module to state
            // each where it uses it.
            implementation(libs.androidx.lifecycle.common)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.core.viewmodel)
            // `FavoritesRoute` resolves its ViewModel with Koin's Compose `koinViewModel()`, which the
            // module *uses* rather than exposes, so the edge is `implementation` (dependency
            // analysis).
            implementation(libs.koin.androidx.compose)
            // `favoritesViewModelModule` declares the ViewModel with Koin's Android `viewModel` DSL.
            implementation(libs.koin.android)
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
            // Robolectric is the runtime that executes these cases; they never name its API, so it is
            // a runtime dependency rather than a compile one (dependency analysis, `DEC-077`).
            runtimeOnly(libs.robolectric)
            // The Compose rule's own surface, which the cases name directly; the BOM governs it, so
            // the version comes from `:core:designsystem`'s platform edge.
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test)
            // The JUnit 4 runner adapter the Android host tests execute on.
            implementation(libs.androidx.test.ext.junit)
            // `FavoritesStateHolder` and the reducer are coroutine-backed, so the cases use the
            // concurrency runtime directly.
            implementation(libs.kotlinx.coroutines.core)
            // Robolectric's annotation surface (`@Config`) and the shadows it loads, both declared
            // where the cases use them instead of reached transitively (`DEC-077`).
            implementation(libs.robolectric.annotations)
            implementation(libs.robolectric.shadows.framework)
            // The Compose rule hosts its content in a provided activity; the artifact supplies it to
            // the test manifest. The cases never name the artifact's API, so it is a runtime
            // dependency that the Android host-test manifest merge consumes (dependency analysis,
            // `DEC-077`).
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
