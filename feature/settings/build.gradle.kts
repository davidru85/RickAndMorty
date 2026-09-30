// :feature:settings — Sounds preference, remote data-source choice and
// "Delete favorites" with confirmation (DEC-055, ADR-0010).

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(project(":core:designsystem"))
        }
    }
}
