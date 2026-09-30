// :feature:episodes — the "coming soon" placeholder destination (DEC-005).
// It has no data layer in the MVP, so it declares no data edge (CONF-38).

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:presentation"))
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
        }
    }
}
