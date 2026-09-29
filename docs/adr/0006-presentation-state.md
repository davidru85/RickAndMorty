# ADR-0006 — Presentation-state ownership and dependency injection

- **Status:** Accepted
- **Date:** 2026-09-29
- **Last verified:** 2026-09-29
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) and Implementation Engineer (iOS); consulted UI/UX Designer (copy keys and formatters)
- **Authoritative for:** which module owns presentation state, where state holders live, and how the dependency graph is assembled. Not the state class contents themselves (`DESIGN.md` §4.1, `CONTRACTS.md`) and not the sharing/interop boundary (ADR-0003).
- **Inputs:** `DEC-015`, `DEC-014`, `DEC-052` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-013` (ADR-0003), `DEC-020`; `REQ-NFR-001`, `REQ-PLAT-001`, `REQ-UX-006`, `REQ-UX-008`, `REQ-UX-009`, `REQ-FUNC-013` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`DESIGN.md`](../DESIGN.md) §1, §4, §5, §9 (decisions D2, D3); verified Koin 4.2.2 release and `androidx.lifecycle` KMP 2.12.0-alpha04 (no stable) as of 2026-09-29

## Owners

- **Decision owner:** System Architect — accountable for the ownership rule and for the DI style.
- **Implementation owners:** Implementation Engineer (Android) for the per-feature ViewModels and the `:androidApp` graph; Implementation Engineer (iOS) for the `ObservableObject`s and the iOS graph assembly.
- **Consulted:** UI/UX Designer for formatter output and the canonical copy keys shared through `:core:presentation`.

## Decision

**Shared presentation state lives in the shared core; state holders are platform objects; DI is a runtime Koin graph.**

- `:core:presentation` MUST own only cross-feature presentation primitives: `LoadState`, display formatters, the canonical copy key list, and the small shared helpers both platforms consume. It MUST depend only on `:core:domain`.
- Each feature module MUST own its own UI-state classes and intents in the feature's `presentation` package under `commonMain`, composed from `:core:presentation` primitives and `:core:domain` types. State classes MUST be immutable data classes, and every user action MUST be an intent in a sealed hierarchy. No platform type (a lifecycle object, a UI toolkit type, a colour) MUST appear in this package.
- State holders MUST be per platform: an Android ViewModel in the feature's `ui` package under `androidMain` and an iOS `ObservableObject` in the mirroring `iosApp/Features` package for that feature. No module in the shared core MUST depend on a ViewModel or lifecycle artifact.
- Both state holders MUST expose the same state type and the same intent vocabulary, and MAY add platform-only presentation affordances (for example an Android snackbar trigger) without changing the shared state class.
- Feature-specific use cases MUST live in the feature's own `domain` package under `commonMain`; only genuinely cross-feature use cases MUST live in `:core:domain`.
- **DI:** Koin 4.2.2, runtime DSL only. A Koin compiler plugin or annotation processor MUST NOT be added. Each feature module MUST declare its own Koin module (definitions for its state holder dependencies and its use cases); the app shell (`:androidApp`) and the iOS app target MUST assemble those feature modules plus the `:core:*` definitions. Singletons that must be shared process-wide (Ktor client, response cache, favorites store, repositories, pager factory, Android `ImageLoader`, `CharacterAccentResolver`) MUST be declared once in the core/App graph, not per feature.
- Graph wiring MUST be verifiable at start-up: the app MUST fail loudly if a definition is missing rather than degrade silently.

- **Board entry:** `DEC-015` (shared presentation ownership), `DEC-014` (Koin runtime DSL) — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Context

UDF is the architectural pattern (`DESIGN.md` §1): screens render an immutable state and send intents, and the state owner is the only writer. The two platforms render the same information architecture (`UI_SPEC.md` §2), so the state *shape* and the formatting rules must not be duplicated — otherwise "Unknown" casing, the dimension derivation and the copy would drift between Android and iOS, which `REQ-UX-008` and `AC-REQ-UX-008-1` forbid. But the state *holder* is bound to a platform runtime (Android lifecycle and `ViewModelStore`; SwiftUI observation and structured concurrency), and the candidate artifact for sharing it — `androidx.lifecycle` KMP — had no stable release on 2026-09-29 (latest 2.12.0-alpha04). ADR-0003 resolves the sharing question; this ADR fixes where each piece lives and how it is assembled.

The repository has no source code as of 2026-09-29, so the package layout, the state contract and the Koin graph are target state. `DESIGN.md` §9 leaves the DI framework open (decision D2), recommending Koin with Hilt only as an Android-only substitute.

## Decision drivers

- One state shape and one formatting rule set for both platforms — `REQ-UX-008`, `REQ-FUNC-013`, DEC-020, DEC-015.
- State must be testable without a platform test runner — `REQ-NFR-005`, `AC-REQ-NFR-005-1`, DEC-030.
- No alpha artifact and no compiler plugin on the critical path — ADR-0003, `CON-004`, `REQ-NFR-002`.
- DI must work for a Kotlin shared graph consumed from Swift, not only for Android — `DESIGN.md` §5 (D2).
- Feature isolation: a feature's state, intents and use cases are owned by that feature — `DEC-052`, ADR-0001.
- Formatters must serve text scaling and localisation without hard-coded copy — `REQ-UX-006`, `REQ-FUNC-013`.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Shared ViewModels | `androidx.lifecycle` KMP ViewModels in the shared core, observed from Swift | One state holder and one behaviour implementation; requires an alpha lifecycle artifact (`CON-004`) and a bridging mechanism (ADR-0003), and puts Android lifecycle semantics in the shared core, which `AC-REQ-PLAT-001-1` was written to prevent | Rejected: DEC-013; the artifact is alpha and the coupling reaches into the shared domain-facing layer |
| Shared state primitives + per-platform state holders (chosen) | `:core:presentation` primitives, feature-owned state classes and intents, one state holder per platform | No alpha artifact, each platform's idioms intact, state and formatting shared and testable in `commonTest`; costs a second state holder per screen and a hand-written observation bridge (ADR-0003) | Chosen: satisfies DEC-015 and DEC-013, and keeps the drift-prone content shared while the runtime-specific part stays native |
| State in the domain layer | Put `LoadState`, view state and presentation formatting in `:core:domain` | Fewest modules and no extra layer; the domain would then know how the UI renders (`LoadState` is a UI concern), and `AC-REQ-NFR-001-1` (domain independent of UI concepts) would be violated | Rejected: breaks the inward-dependency rule of `REQ-NFR-001` and mixes presentation vocabulary into the domain contract |
| Hilt instead of Koin | Android-only DI | Familiar Android tooling and compile-time verification; cannot construct the shared graph for iOS, so the iOS app would need a second DI mechanism (`REQ-NFR-002`) | Rejected: `DESIGN.md` §5 D2; Koin is the multiplatform option. Hilt remains acceptable only if the project became Android-only, which DEC-001 rules out |
| Koin with the compiler plugin | Annotation-based definitions resolved at compile time | Compile-time graph verification and less boilerplate; adds a Kotlin compiler plugin, which DEC-014 excludes and which must track the Kotlin version (2.4.20) on the critical path | Rejected: DEC-014; the runtime DSL is sufficient at this graph size |

## Consequences

**Positive**

- State classes and formatters are written once, so the copy key list and the display rules have a single source and `AC-REQ-UX-008-1` is checkable.
- State transitions are testable in `commonTest` against the shared state classes, and the shared fixtures of `:core:testing` serve both platforms (DEC-030).
- `:core:presentation` carries no platform dependency, so `REQ-PLAT-001` and `REQ-NFR-001` hold at the presentation boundary.
- Each feature owns its own Koin module, so a feature's DI additions are local and the app shell stays a composition point.
- The DI graph is runtime-constructed, so a missing definition is a start-up failure with a clear message rather than a silent null.

**Negative**

- Two state holders per screen exist (one per platform), so behaviour that is not expressible in the shared state class is implemented twice and can drift.
- The iOS state holder must bridge the shared state by hand (ADR-0003), which is project code to maintain.
- Runtime DI means graph mistakes are found at start-up or in a test, not by the compiler; the graph therefore needs an explicit start-up verification test.
- Koin definitions are split between feature modules and app shells, so a reader must follow the assembly to see the whole graph; `DESIGN.md` §5 MUST list the graph's shape (DEC-046).
- Platform-only presentation affordances create a small deliberate asymmetry between the two state holders, which MUST be documented in the feature's package rather than left implicit.

## Risks

- **Local:** shared state classes accumulating platform concerns — mitigated by the feature `presentation` dependency check of ADR-0001 and by `AC-REQ-PLAT-001-1`.
- **Local:** the two state holders drifting (different debounce, different refresh semantics) — mitigated by keeping every behavioural rule in the shared state class and in `:core:data`, and by the platform state-holder tests consuming the same fixtures.
- **Local:** a feature declaring a singleton that another feature also declares, producing two instances of a shared dependency — mitigated by the rule above (process-wide singletons belong to the core/App graph) and by a graph test asserting single instances.
- **Local:** copy keys living in two places (a formatter key and a platform string resource) — mitigated by `:core:presentation` owning the canonical key list and by the copy parity test (DEC-020, `AC-REQ-UX-008-1`).

## Validation criteria

- `TEST-UNIT-001`, `TEST-UNIT-002`, `TEST-UNIT-003` — state sequences for the list and detail state machines, debounce and cancellation, driven through the shared state classes with the fixtures from `:core:testing`.
- `TEST-UNIT-008` — every canonical copy key exists in both `en` and `es`, so a formatter or key added in `:core:presentation` cannot ship unlocalised (`AC-REQ-FUNC-013-1`).
- **Observable:** no module in the shared core declares a lifecycle, ViewModel, Compose or SwiftUI dependency — observed in the dependency-analysis report (DEC-032).
- **Observable:** the app starts with the full graph resolved, and a removed definition fails the start-up check — observed by the graph verification test and a smoke launch on both platforms.
- **Observable:** process-wide singletons (Ktor client, response cache, favorites store, pager) are single instances across features — observed by a graph test that resolves them from two features and compares identity.
- **Observable:** the same intent sequence produces the same shared state on both platforms — observed by running the Android and iOS state-holder tests against the same fixtures (both suites are required on every pull request, DEC-054).
- **Verification protocol (DEC-053):** each state-machine rule, formatter and graph definition lands as a red commit (a test observed to fail), then a green commit, then an optional refactor commit, with history preserved (no squash; DEC-041 amended). Build or tooling changes MAY skip the red phase and MUST state the exception in the commit body.

## Related requirements

- `REQ-NFR-001` (`AC-REQ-NFR-001-1`): the shared presentation layer carries no platform dependency.
- `REQ-PLAT-001` (`AC-REQ-PLAT-001-1`): shared modules contain no platform UI code; state holders are native.
- `REQ-NFR-005` (`AC-REQ-NFR-005-1`): state transitions are covered by direct behavioural tests in `commonTest`.
- `REQ-UX-006` (`AC-REQ-UX-006-1`): formatters and layouts must survive the largest text scale.
- `REQ-UX-008` (`AC-REQ-UX-008-1`): identical copy from the canonical key list on both platforms.
- `REQ-UX-009` (`AC-REQ-UX-009-1`): `LoadState` covers loading, empty, stale, error and partial cases on both surfaces.
- `REQ-FUNC-013` (`AC-REQ-FUNC-013-1`, `AC-REQ-FUNC-013-2`): every user-visible string comes from localisable resources in `en` and `es`.

## Related implementation areas

- `:core:presentation` (`LoadState`, formatters, canonical copy keys), each feature's `presentation` and `domain` packages under `commonMain`, each feature's `ui` package under `androidMain` (ViewModels), `iosApp/Features/*` (`ObservableObject`s and the bridge), `:androidApp` and the iOS app target (graph assembly).
- [`DESIGN.md`](../DESIGN.md) §1 (UDF), §4.1 (state contract), §4.2 (navigation), §5 (DI graph), §9 decisions D2 and D3.
- [`UI_SPEC.md`](../UI_SPEC.md) §8 for the states `LoadState` maps to; [`ERROR_FLOW.md`](../ERROR_FLOW.md) for the canonical failure→state→copy chain (DEC-021).
- DEC-020 (canonical key list + parity test), DEC-030 (fixtures), DEC-053 (TDD protocol), DEC-054 (both platform suites required on every pull request).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none as a file. This ADR closes `DESIGN.md` §9 decision D2 (DI framework) in favour of Koin.
- **Superseded by:** none as of 2026-09-29.
- **Related:** ADR-0003 (sharing boundary, no shared ViewModels), ADR-0001 (the module that owns the primitives and the feature packages), ADR-0009 (the pager whose state this layer renders), ADR-0008 (the alpha policy that keeps lifecycle KMP out).
