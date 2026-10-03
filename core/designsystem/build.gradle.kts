// :core:designsystem — Material 3 Expressive tokens and components.
// ADR-0001: an Android-only module that depends on no project module.

plugins {
    id("multiverse.android.library")
}

dependencies {
    // DEC-106: the module's test source sets may declare exactly these test libraries, which
    // carry no image, HTTP, DI or persistence code. The production source set is Compose-only
    // (R15) and every production dependency is declared with the components in phase 4.2.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.serialization.json)
}
