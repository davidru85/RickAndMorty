// :feature:character-detail — detail screen and favourite toggle.

plugins {
    id("multiverse.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
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
