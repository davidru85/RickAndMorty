// Root build script.
//
// It declares the third-party plugin aliases so that every module build script
// resolves them from the catalog, and applies the dependency-policy plugin, which
// turns the catalog pins, the per-entry rationale and the README inventory into
// build checks wired into `check` (DEC-061, TEST-UNIT-013/014/051). There is no
// `allprojects {}` or `subprojects {}` block anywhere in this build (OD-3).

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    id("multiverse.dependency.policy")
}
