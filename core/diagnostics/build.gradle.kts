// :core:diagnostics — the debug-only, read-only diagnostic API (DEC-088, ADR-0013): a fold over
// validated log records that exposes the last failure and the current data source. Release
// artifacts never link it: `:androidApp` may declare it from a `debug*` configuration only, and no
// release path reaches it (R11, TEST-UNIT-033); its production source sets depend on `:core:domain`
// only (R18).

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The recorder implements `LogSink` and exposes domain types (`IC-024`, `DEC-093`).
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            // The API is proved on the real request and pager paths (`TASK-047`, R18): the
            // production stack from `:core:data`, the fixtures and MockEngine from `:core:testing`.
            implementation(project(":core:data"))
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
        }
        androidHostTest.dependencies {
            implementation(libs.kotlin.test.junit)
        }
    }
}
