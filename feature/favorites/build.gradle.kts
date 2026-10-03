// :feature:favorites — favorites list and its empty state.

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The placeholder screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Compose compiler runs for the **Android** compilation only.
 *
 * The plugin applies to every Kotlin compilation by default, which includes the Apple targets where no
 * Compose runtime exists and none is wanted: the composable lives in `androidMain`, so the compiler has
 * nothing to transform there. `targetKotlinPlatforms` is the plugin's own scoping API, so this is the
 * supported way to say where Compose is, rather than a workaround.
 */
composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm))
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            api(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
            // The placeholder's public surface is a composable, so the Compose runtime and UI are part
            // of what the module exposes and are declared `api`; the design system exports the BOM and
            // Material 3 the same way, so the feature never pins the alpha itself
            // (GUIDELINES.md 5.1, ADR-0008 rule 2).
            api(libs.androidx.compose.runtime)
            api(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.graphics)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
