// :core:presentation — cross-feature presentation primitives only: LoadState,
// display formatters and the canonical copy keys.

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
    }
}
