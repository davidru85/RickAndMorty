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

// `TEST-UNIT-036`'s real-resource case reads the one Android copy set that ships in
// `:core:designsystem` (`DEC-100`). The folder is declared as an input of the host test so a copy
// change cannot be masked by an up-to-date test run; without this the check would pass on a stale
// snapshot of the resources.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    inputs
        .dir(layout.projectDirectory.dir("../designsystem/src/main/res"))
        .withPropertyName("androidCopySet")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
