// :feature:discovery — character list, search, status filter, paging.
// The Android design-system edge is declared from the Android source set only
// (ADR-0001: "from Android UI source sets only").

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
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
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
