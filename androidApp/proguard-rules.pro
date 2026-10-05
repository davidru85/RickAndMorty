# The release build's R8 rules for the app (`PERF-009`, `GAP-034`, `DEC-148`).
#
# Every library this app links — Compose, Navigation, Koin, Ktor, Coil, kotlinx.serialization,
# DataStore — ships its own consumer rules, which R8 applies on its own. This file holds only what
# the app itself needs on top of them, each rule with the reason it exists.

# `TEST-UNIT-033` (`verifyReleaseArtifact`) proves the diagnostic surface absent from the release by
# finding its types' names in the dex. R8 would rename or inline a diagnostic type that ever leaked
# into the release, and the check would then pass on an artifact that carries it. Pinning the names —
# without keeping the types, so nothing is added to a release that does not link them — keeps a leak
# visible to the check (`DEC-148`).
-keepnames class io.github.davidru85.multiverse.core.diagnostics.**
-keepnames class io.github.davidru85.multiverse.app.debug.**
