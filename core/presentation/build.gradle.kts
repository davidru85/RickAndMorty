// :core:presentation — cross-feature presentation primitives only: LoadState,
// display formatters and the canonical copy keys.

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `LoadState`, `CharacterCardUi` and the formatters carry domain types in their signatures.
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidHostTest.dependencies {
            // The public-surface case reads the compiled Android classes on the JVM host (`TEST-UNIT-012`).
            implementation(libs.kotlin.test.junit)
        }
    }
}
