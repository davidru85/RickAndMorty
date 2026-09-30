// Root build script.
//
// It declares the third-party plugin aliases so that every module build script
// resolves them from the catalog, and nothing else: there is no `allprojects {}`
// or `subprojects {}` block anywhere in this build (OD-3).

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}
