// :core:domain — domain models, repository interfaces, DataResult and ApiFailure.
// ADR-0001: depends on nothing, and `TEST-UNIT-012` asserts it.

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `IC-008` and `IC-021` expose `Flow`, which is why `DEC-066` admits this library.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            // `DEC-089`: a domain test uses the approved test libraries, never the HTTP-bearing
            // `:core:testing` harness (`R1`, `R14`).
            implementation(libs.kotlin.test)
        }
        // The JUnit binding each JVM test task runs on is used directly, so it is declared where it is
        // used rather than reached transitively (`DEC-077`).
        androidHostTest.dependencies {
            implementation(libs.kotlin.test.junit)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit)
        }
    }
}
