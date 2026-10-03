// :feature:settings — Sounds preference, remote data-source choice and
// "Delete favorites" with confirmation (DEC-055, ADR-0010).

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The Settings screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
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
     */
    extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation::class.java).configureEach {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            // `settingsModule` is a Koin module; the graph is built in the composition roots
            // (`DEC-091`, `DESIGN.md` §5), so the DI library is a shared-production dependency here.
            implementation(libs.koin.core)
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
            // The screen's public surface is a composable over `Modifier` and Compose runtime types,
            // and `SettingsViewModel` exposes a `StateFlow`, so those are part of what the module
            // exposes and are declared `api`; the design system exports the BOM and Material 3 the
            // same way, so the feature never pins the alpha itself (GUIDELINES.md 5.1, ADR-0008 rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            api(libs.androidx.lifecycle.viewmodel)
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
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            // The Compose rule hosts its content in a provided activity; the artifact supplies it to
            // the test manifest, so a case needs no activity of the module's own.
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}
