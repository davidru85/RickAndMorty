// :core:data — remote data sources, DTOs, mappers, response cache, pager,
// favorites and app-settings stores, repository implementations.

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
