// :core:diagnostics — the debug-only, read-only diagnostic API (DEC-088, ADR-0013): a fold over
// validated log records that exposes the last failure and the current data source. Release
// artifacts never link it: `:androidApp` may declare it from a `debug*` configuration only, and no
// release path reaches it (R11, TEST-UNIT-033); its production source sets depend on `:core:domain`
// only (R18).

plugins {
    id("multiverse.kmp.library")
}
