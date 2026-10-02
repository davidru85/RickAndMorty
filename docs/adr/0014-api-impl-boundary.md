# ADR-0014 — The API/IMPL boundary: `:core:domain` is the API, `:core:data` the implementation

- **Status:** Accepted
- **Date:** 2026-10-02
- **Last verified:** 2026-10-02
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) and Implementation Engineer (iOS); consulted Delivery Planner (task placement).
- **Authoritative for:** which modules may consume the implementation module, where a cross-module seam a feature consumes is declared, and what the iOS framework exports. Not the module set as a whole, which belongs to [ADR-0001](0001-module-boundaries.md); not the signatures, which belong to [`../CONTRACTS.md`](../CONTRACTS.md); not the framework packaging, which belongs to [ADR-0012](0012-ios-framework-export.md).
- **Inputs:** `DEC-091`, `DEC-052`, `DEC-058`, `DEC-014`, `DEC-066` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `REQ-NFR-001`, `REQ-NFR-009`, `REQ-PLAT-001` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`DESIGN.md`](../DESIGN.md) §3, §5; `CONF-54` in [`DOCUMENTATION_AUDIT.md`](../DOCUMENTATION_AUDIT.md); the owner's request of 2026-10-02 to apply the API/IMPL pattern and the approved audit of the module graph at `b232363`.

## Owners

- **Decision owner:** System Architect — owns the boundary and the review trigger below.
- **Implementation owners:** Implementation Engineer (Android) for the boundary rules, the feature edges and the `:androidApp` composition root (`TASK-044`); Implementation Engineer (iOS) for the `:core:ios` export list (`TASK-078`).
- **Consulted:** Delivery Planner, because the change moves where `TASK-039` declares the pager contract.

## Decision

The API/IMPL pattern is applied with the existing modules rather than by splitting them: `:core:domain` is the **API** module and `:core:data` is the **implementation** module.

- `:core:domain` holds every seam a module outside the data layer consumes — repository interfaces, models, results, the logging contract and, from now on, the pager contract `IC-014` (`CharacterPager`, `PagerState`). It keeps the `DEC-066` dependency rule.
- A feature's production source sets MUST NOT depend on `:core:data`. They depend on `:core:domain` and `:core:presentation`, and Android UI source sets on `:core:designsystem`. A feature's test source sets reach implementations only through `:core:testing`.
- The composition roots are the only production consumers of `:core:data`: `:androidApp` (the Koin graph, `DESIGN.md` §5) and `:core:ios`. `:core:ios` declares `:core:data` as an `implementation` dependency and MUST NOT export it; it exports the five `:feature:*` modules, `:core:domain` and `:core:presentation`.
- `:core:data` keeps its implementation details `internal` (DTOs, mappers, the decoder). Its public types — the implementations, their factories and the data-internal seams `IC-011`/`IC-012`/`IC-013`/`IC-022` — are visible only to the composition roots and to the test harness.
- The boundary is executable: rule `R8` rejects a feature production edge to `:core:data`, `R11` admits `:androidApp` → `:core:data`, and `R12` rejects `:core:ios` exposing `:core:data` through an `api` configuration.

- **Board entry:** `DEC-091` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). It amends ADR-0001 (feature and shell edges) and ADR-0012 (the export list) by pointer, and resolves `CONF-54`.

## Context

At `b232363`, `:feature:discovery`, `:feature:character-detail` and `:feature:favorites` declared `implementation(project(":core:data"))`, which ADR-0001 permitted. Two things made that edge necessary. The pager contract `IC-014`, which the discovery state holder consumes, was planned in `:core:data`. And `R11` forbade `:androidApp` from depending on `:core:data`, so the Koin wiring of the data implementations had nowhere to live but the features. Since `TASK-037`, `:core:data` exposes the Ktor client as `api`; every feature with that edge would therefore compile against Ktor and the platform engines, recompile when the data layer's surface changes, and be able to construct a remote adapter directly.

On iOS, ADR-0012's export list included `:core:data`, so Swift would have seen the implementation types. `CONF-54` had already recorded that the export list had three incompatible definitions and needed an owner decision.

## Decision drivers

- Encapsulation: implementation details are not reachable from feature code — `REQ-NFR-001`.
- Faster builds: a feature's compile classpath carries no HTTP stack, so data-layer changes do not recompile features.
- One module per concern and no feature-to-feature edges — `DEC-052`, `REQ-NFR-009`.
- No new module unless it decouples something: every KMP module costs an Android and two Apple compile units, and the iOS export packages all of them (ADR-0012).

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Existing modules as API/IMPL | Domain is the API; data is consumed only by the composition roots; `IC-014` moves to domain | No new module; three edges removed; one contract relocated before it is implemented | Chosen |
| Split `:core:data` into `:core:data:api` and `:core:data:impl` | A new API module for the data seams | Its only feature-facing seam would be `IC-014`; the other data seams are internal to the data layer, so publishing them widens the surface; the new module duplicates `:core:domain`'s role and adds a KMP module | Rejected |
| `api`/`impl` per feature | Ten feature modules instead of five | No feature consumes another (`R7`), and the shells already compose each feature's own route declaration; doubles the native compile units for no consumer | Rejected |
| Keep the status quo | Features declare `:core:data` | Leaks the HTTP stack and the implementations into every feature | Rejected |

Modules the pattern does not apply to: `:core:domain` already is the API; `:core:presentation` is dependency-free presentation primitives with no implementation to hide; `:core:designsystem` ships the composables consumers need; `:core:testing` is test-only; the planned `:core:diagnostics` (ADR-0013) is already an implementation over a domain contract.

## Consequences

**Positive**

- A feature cannot import a remote adapter, a DTO-adjacent type or the Ktor client: its compile classpath does not contain them.
- The composition roots are the one place implementations are chosen, which is where `DESIGN.md` §5 already put the graph.
- The Swift-visible surface no longer includes implementation types, and `CONF-54` has one authoritative export list.

**Negative**

- `IC-014` moves from `:core:data` to `:core:domain` (a contract relocation, `CONTRACTS.md` §8.1); it is not implemented yet, so no code moves.
- `R8`, `R11` and `R12` change with their regression tests, and three exclusion-register rows disappear with the edges they covered.
- `:androidApp` declares `:core:data` when `TASK-044` builds the graph, not before: an unconsumed edge would only need a new exclusion.

## Risks

- No `RISK-###` row of `REQUIREMENTS.md` §13 bears on this decision directly.
- **Local:** a later feature could re-add the edge for convenience — mitigated by `R8`, which fails the build.
- **Local:** `:core:ios` could export `:core:data` by mistake — mitigated by `R12`'s `api` clause.

## Validation criteria

- `TEST-UNIT-017`: `R8` rejects a feature production edge to `:core:data`; `R11` admits `:androidApp` → `:core:data`; `R12` rejects an `api` edge from `:core:ios` to `:core:data`.
- **Observable:** no feature build script declares `:core:data` → `./gradlew verifyModuleBoundaries` and the dependency reports.

## Related requirements

- `REQ-NFR-001` (`AC-REQ-NFR-001-2`): implementation types stay out of consumer signatures.
- `REQ-NFR-009`: module structure and dependency direction.
- `REQ-PLAT-001`: the shared code both platforms consume.

## Related implementation areas

- `:core:domain`, `:core:data`, the three affected `:feature:*` build scripts, `:androidApp` (`TASK-044`), `:core:ios` (`TASK-078`), `build-logic/convention/src/main/kotlin/boundaries/`.
- [`../DESIGN.md`](../DESIGN.md) §3 and §5, [`../CONTRACTS.md`](../CONTRACTS.md) `IC-014` and §7.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none as a file; it amends ADR-0001 and ADR-0012 by pointer.
- **Superseded by:** none as of 2026-10-02.
- **Related:** [ADR-0001](0001-module-boundaries.md), [ADR-0012](0012-ios-framework-export.md), [ADR-0013](0013-observability-placement.md).
