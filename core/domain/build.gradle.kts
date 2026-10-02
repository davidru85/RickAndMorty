// :core:domain — domain models, repository interfaces, DataResult and ApiFailure.
// ADR-0001: depends on nothing, and `TEST-UNIT-012` asserts it.

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // `DEC-089`: a domain test uses the approved test libraries, never the HTTP-bearing
            // `:core:testing` harness (`R1`, `R14`).
            implementation(libs.kotlin.test)
        }
    }
}
