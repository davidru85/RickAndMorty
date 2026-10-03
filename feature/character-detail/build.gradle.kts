// :feature:character-detail — detail screen and favourite toggle.

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The detail screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Compose compiler runs for the **Android** compilation only, exactly as `feature/favorites`
 * does.
 *
 * The plugin applies to every Kotlin compilation by default, which includes the Apple targets where
 * no Compose runtime exists and none is wanted: every composable lives in `androidMain`, so the
 * compiler has nothing to transform there. `targetKotlinPlatforms` is the plugin's own scoping API.
 */
composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm))
}

/**
 * Robolectric reads `SharedSecrets` reflectively, which the pinned daemon JDK restricts, so the
 * Android host-test JVM gets the same grants `:core:designsystem` and `:androidApp` declare for
 * their own Robolectric runs (`TEST-UI-002`). The KMP host-test component has no `testOptions`
 * block; the `Test` task's JVM arguments are what Robolectric actually needs.
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
     * `androidResources.enable` is `false` for a multiplatform library by default, so the module's
     * own bundled detail glyphs under `androidMain/res/drawable` would be dropped; it is enabled
     * because the module ships the five vectors `UI_SPEC.md` §6.3 names and no icon dependency.
     *
     * `isIncludeAndroidResources` on the host-test compilation is what lets Robolectric (and the
     * Compose rule) resolve the one Android copy set `:core:designsystem` ships and this module's
     * own resources — without it `CopyResolver.copy(...)` cannot resolve in `TEST-UI-002`. The
     * convention plugin already created the compilation, so it is configured here rather than
     * created again; `compilations.withType` is the plugin's documented way to do that.
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
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            // `characterDetailModule` is a Koin module; the composition roots load it
            // (`DEC-091`, `DESIGN.md` §5), so the DI library is a shared-production dependency.
            implementation(libs.koin.core)
            // The `CharacterDetail` route is `@Serializable`.
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier`, Compose runtime and
            // painter types, and `CharacterDetailViewModel` exposes a `StateFlow`, so those are part
            // of what the module exposes and are declared `api`; the design system exports the BOM
            // and Material 3 the same way, so the feature never pins the alpha itself
            // (GUIDELINES.md 5.1, ADR-0008 rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            // The inline-error and portrait surfaces the screen composes take a `Painter`.
            api(libs.androidx.compose.ui.graphics)
            // The Android state holder of `IC-019` (`DESIGN.md` §5, ADR-0006).
            api(libs.androidx.lifecycle.viewmodel)
            // The detail screen scrolls; the module uses `foundation` rather than exposing it.
            implementation(libs.androidx.compose.foundation)
            // `characterDetailViewModelModule` declares the ViewModel with Koin's `viewModel` DSL.
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            // `DEC-089`: the harness is a test source set's dependency only.
            implementation(project(":core:testing"))
            // `R10` admits `:core:data` to a feature's **test** source sets (`DEC-089`), which is what
            // lets `TEST-CONTRACT-002` drive the committed detail fixture through the real REST
            // adapter instead of only through a double.
            implementation(project(":core:data"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // The contract case holds the harness's `HttpClient` and `MockEngine` routes, so the
            // artifact is declared where it is used (`onUsedTransitiveDependencies`, DEC-077).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
        }
        androidHostTest.dependencies {
            // `TEST-UI-002` runs on the JVM host with Robolectric and the Compose test rule.
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
