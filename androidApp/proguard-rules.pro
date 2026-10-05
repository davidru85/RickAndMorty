# The release build's R8 rules for the app (`PERF-009`, `GAP-034`, `DEC-148`).
#
# Every library this app links — Compose, Navigation, Koin, Ktor, Coil, kotlinx.serialization,
# DataStore — ships its own consumer rules, which R8 applies on its own. This file holds only what
# the app itself needs on top of them, each rule with the reason it exists.
