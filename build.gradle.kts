// Root build script.
//
// It declares the third-party plugin aliases so that every module build script
// resolves them from the catalog, and applies the two policy families, whose checks are
// wired into `check`:
//
// - `multiverse.dependency.policy` turns the catalog pins, the per-entry rationale and the
//   README inventory into build checks (DEC-061, TEST-UNIT-013/014/051);
// - `multiverse.repository.hygiene` holds the tracked-path and credential rules over the
//   commit-eligible working set and every blob reachable from all local refs
//   (DEC-062, TEST-UNIT-026, REQ-SEC-002);
// - `multiverse.module.boundaries` turns the accepted module graph of ADR-0001 as amended
//   (DEC-066, DEC-069) into an executable policy: project edges, source-set kinds, the
//   :core:domain external allow-list and the staged destination/package rules
//   (TASK-017, TEST-UNIT-017/012/043, REQ-NFR-001, REQ-NFR-009).
//
// There is no `allprojects {}` or `subprojects {}` block anywhere in this build (OD-3).

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    id("multiverse.dependency.policy")
    id("multiverse.repository.hygiene")
    id("multiverse.module.boundaries")
}
