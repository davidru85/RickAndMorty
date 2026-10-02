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
            // The recorder implements `LogSink` and exposes domain types (`IC-024`, `DEC-093`), and
            // its snapshot is a `StateFlow`.
            api(project(":core:domain"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            // The API is proved on the real request and pager paths (`TASK-047`, R18): the
            // production stack from `:core:data`, the fixtures and MockEngine from `:core:testing`;
            // the client and virtual-time types the tests touch are declared where used (DEC-077).
            implementation(project(":core:data"))
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.core)
        }
        androidHostTest.dependencies {
            implementation(libs.kotlin.test.junit)
        }
    }
}
