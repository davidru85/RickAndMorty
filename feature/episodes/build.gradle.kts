// :feature:episodes — the "coming soon" placeholder destination (DEC-005).
// It has no data layer in the MVP, so it declares no data edge (CONF-38).

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    // The placeholder screen is a composable, so the module needs the Compose compiler (ADR-0008).
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
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
    }
}
