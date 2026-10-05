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
            // The shared splash gate takes the caller's dispatcher (`IC-026`, `DEC-136`).
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // The shared splash gate's policy is proved on virtual time (`IC-026`, `TEST-UI-006`).
            implementation(libs.kotlinx.coroutines.test)
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
