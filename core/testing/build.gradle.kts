// :core:testing — shared fakes, fixtures and time-control helpers.
// Consumed from test source sets only; DESIGN.md §3.1 places it on
// `:core:domain` and `:core:data` (see CONF-39 for the ADR-0001 wording).

plugins {
    id("multiverse.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
        }
    }
}
