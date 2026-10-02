# ADR-0012 — One iOS umbrella framework produced by a `:core:ios` export module

- **Status:** Accepted
- **Date:** 2026-09-30
- **Last verified:** 2026-10-02
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (iOS) for the export module and the Xcode linkage, Implementation Engineer (Android) as consulted for the `api` promotions the export forces on the shared modules; consulted UI/UX Designer for the Swift-visible surface the hand-written binding consumes.
- **Authoritative for:** which Gradle module produces the Kotlin framework the iOS app links, what that module exports, and how the Swift side reaches feature-owned types. Not the sharing boundary or the interop mechanism, which belong to [ADR-0003](0003-ui-sharing-strategy.md) and [`CONTRACTS.md`](../CONTRACTS.md) §7; not the target list and OS floors, which belong to [ADR-0002](0002-platform-targets.md); not the module set as a whole, which belongs to [ADR-0001](0001-module-boundaries.md); not the Swift-package split under `iosApp/Features/*`, which belongs to [`DESIGN.md`](../DESIGN.md) §3.2.
- **Inputs:** `DEC-058`, `DEC-013`, `DEC-019` (superseded), `DEC-052`, `DEC-055` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-013` (ADR-0003), `DEC-001`/`DEC-009` (ADR-0002), `DEC-052` (ADR-0001); `REQ-PLAT-001`, `REQ-PLAT-003`, `REQ-NFR-002`, `REQ-NFR-005` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`DESIGN.md`](../DESIGN.md) §2, §3.4, §4.2; [`CONTRACTS.md`](../CONTRACTS.md) §7, §8.2; `DEF-004`; verified 2026-09-30: the KMP binary documentation's export rule, Xcode 27.0 on the workstation, Kotlin 2.4.20's target set

## Owners

- **Decision owner:** System Architect — owns the export topology, the export list and the review trigger below.
- **Implementation owners:** Implementation Engineer (iOS) for `:core:ios`, the framework linkage in `iosApp/` and the Swift-visible surface; Implementation Engineer (Android) for the `api` declarations the export requires on `:core:*` and `:feature:*` and for confirming they do not widen the Android build.
- **Consulted:** UI/UX Designer for the Swift binding surface the hand-written bridge in ADR-0003 consumes.

## Decision

The iOS app links **exactly one** Kotlin framework. It is produced by a new module, `:core:ios`, and that module's only reason to exist is to export the feature modules to Swift.

- `:core:ios` MUST be a Kotlin Multiplatform module declaring the `iosArm64` and `iosSimulatorArm64` targets of ADR-0002 and **no other target** — no `androidTarget`, no `jvm`, no Apple target other than the two, and no `iosX64`.
- It MUST declare a single `binaries.framework` and MUST `export` the five `:feature:*` modules and the four other `:core:*` modules. The framework's `baseName` is the product name (`MultiverseExplorer`), and it MUST be `isStatic = true`.
- Its dependency declarations MUST use `api`, because `export` admits only `api` dependencies, and an Android-only convention plugin MUST NOT be used for the Android side — `:core:ios` has no Android variant at all.
- The Swift-visible surface is therefore exactly the `api` graph of `:core:ios`, and it MUST remain equal to the surface the per-feature bindings would have produced. Exporting a module MUST NOT add a type to the Swift-visible surface that a direct dependency would not have exposed.
- Navigation route types MUST cross the boundary. Each feature's own `@Serializable` destination (§6.7 of TASK-014) is part of `:core:ios`'s exported surface, so the iOS app shell composes the graph from the same declarations the Android shell uses. A second, Swift-only route enum MUST NOT be introduced (`CONTRACTS.md` §7.1 R4).
- `iosApp/` MUST link the produced framework and MUST NOT embed a second Kotlin framework. A per-feature framework topology is the recorded fallback (see *Considered options*), not a supported second path.
- No module other than `:core:ios` MUST declare a framework binary, and `:core:ios` MUST NOT be a dependency of `:androidApp`, `:core:designsystem` or any Android source set: the Android deliverable stays buildable without it (`REQ-PLAT-004`).

- **Board entry:** `DEC-058` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). It amends the module set of `DEC-052` (ADR-0001) by adding one module, and resolves `CONF-40`.

> **Amended 2026-10-02 by owner decision (`DEC-091`, [ADR-0014](0014-api-impl-boundary.md)), resolving `CONF-54`:** the export list above is narrowed. `:core:ios` exports the five `:feature:*` modules, `:core:domain` and `:core:presentation` through `api`, and declares `:core:data` as an `implementation` dependency that it does **not** export, so the Swift-visible surface carries no implementation type. `:core:designsystem` (Android-only) and `:core:testing` (test-only) are never dependencies. Every other clause of this decision is unchanged.

## Context

`CONF-40` recorded an unresolved contradiction: `README.md` §8 and `HANDOFF.md` §8 built the iOS framework from `:core:presentation`, but the iOS app consumes feature-owned state types (`IC-018`…`IC-023`), which `:core:presentation` may not depend on because a core module never depends on a feature (ADR-0001). No module existed that could reach both the state classes and the feature-owned route declarations, and ADR-0001 forbids adding one without amending itself.

Two facts decide the shape rather than the preference. First, the Kotlin Multiplatform documentation states the constraint directly: *"Usage of several Kotlin/Native frameworks in a Swift application is limited, but you can create an umbrella framework and export all these modules to it. You can export only `api` dependencies of the corresponding source set."* (`kotlinlang.org/docs/multiplatform-build-native-binaries.html`, *Export dependencies to binaries*, verified 2026-09-30). Per-feature frameworks are therefore the arrangement the toolchain documents as the workaround for a case it calls limited, not the default. Second, the same page notes that the `kotlin-multiplatform` plugin creates **no** production binary by default, so the framework is always an explicit declaration; the only question is which module owns it.

The module is named `:core:ios` rather than `:shared:ios` or `:ios:core` because it is cross-feature platform infrastructure, in the same family and for the same reason as `:core:designsystem`: it exists so that platform consumers can reach a capability whose per-module edges remain intact, and its presence changes no dependency rule. It carries no behaviour — it has no source file of its own, because all exported types already exist in the modules it exports.

`DEF-004` keeps Kotlin's Swift export as the re-entry path: when it leaves Alpha, it supersedes this ADR and the export module disappears with it, leaving the five feature modules and their `api` declarations untouched.

## Decision drivers

- One Kotlin runtime, one framework, one linkage step: five frameworks would each package `kotlin-stdlib` and `kotlinx-serialization`, which duplicates the runtime and risks duplicate-symbol linking failures — `REQ-NFR-002`, `assessment.md:7`.
- The Swift-visible surface must be exactly the feature-owned state contract, or the two platforms cannot share it — `REQ-PLAT-001`, `AC-REQ-PLAT-001-1`, `CONTRACTS.md` §7.1 R4.
- Android must remain deliverable with no iOS artifact — `REQ-PLAT-004`, `AC-REQ-PLAT-004-1`.
- The export topology must be replaceable without touching a feature module, because its correctness cannot be verified before Xcode is in the loop — `AGENTS.md` §13 (boring, reversible option), ADR-0008's containment pattern.
- The decision must be reachable from `main` before TASK-051 is Ready, because the M2 entry criteria require the artifact to be named (`DEFINITION.md` §2 R3, §4.2).

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Umbrella export module `:core:ios` (chosen) | One KMP module with the two Apple targets, one framework binary exporting the five features and four core modules via `api` | One runtime and one linkage step; one place to edit if the export behaves unexpectedly; costs one module and one amendment to ADR-0001, and the `api` promotions widen the exported surface in a way that must be checked against `CONTRACTS.md` §8.2 | Chosen: the arrangement the toolchain documents, the smallest reversible change, and the only one that keeps TASK-051's "compiles against the shared framework" literally satisfiable |
| One framework per feature | A framework from each `:feature:*`, assembled into an `XCFramework`; Swift links five | No new module and no ADR-0001 amendment; duplicates the Kotlin runtime and `kotlinx-serialization` per framework, inflates the IPA and the link time, and makes cross-module types non-interoperable between frameworks — the case the Kotlin documentation explicitly calls limited | Rejected: duplicated runtime against `REQ-NFR-002`, and the cross-module type problem is exactly what the shared state contract is for |
| One framework from `:core:presentation` | Add an edge from a core module to the features | No new module; makes the core depend on the features in the reverse direction, which ADR-0001 forbids because it lets a core module accumulate feature knowledge and destroys the compiler-enforced boundary | Rejected: inverts the dependency direction the whole layout exists to enforce |
| Defer the decision to M2 | Create `iosApp/` with no Kotlin linkage until the framework question is settled | Nothing is decided prematurely; Xcode arrives (and with it real evidence) at M2 anyway; but TASK-051's acceptance criterion and the M2 entry criteria both name the framework, so M2 would open with an undecided artifact, and the first Xcode session would carry an architectural decision on top of its own unknowns | Rejected: the decision is what must precede the work, and it is cheap to take now and cheap to reverse |
| Kotlin Swift export | Export directly to a Swift package, no Objective-C framework | The best long-term interop and the most idiomatic Swift surface; officially Alpha, so the milestone would wait on an unreleased toolchain feature | Rejected for the MVP, as in ADR-0003: `DEF-004` is the re-entry condition |

## Consequences

**Positive**

- The iOS app links one framework, so there is exactly one Kotlin runtime in the process and no duplicate-symbol risk between frameworks.
- The whole Swift-visible surface is declared in one build file, which makes `CONTRACTS.md` §8.2's breaking-change analysis reviewable against a single dependency declaration.
- The Android side is untouched: `:core:ios` has no Android variant, is reachable from no Android source set, and every `api` promotion is invisible to the Android compile classpath's direction.
- The decision is contained. If the export proves unable to carry a construct the binding needs, the change is one module's build file plus the Xcode linkage — no feature module changes, because no feature module knows the framework exists.
- The five route declarations of the feature modules serve both shells from one definition, so `DESIGN.md` §4.2's navigation ownership rule (rule 7 of §3.4) holds on iOS as well as Android.
- When `DEF-004` fires, deleting `:core:ios` and its framework declaration is the whole migration.

**Negative**

- ADR-0001's module set grows from eleven projects to twelve, and the ADR must record the amendment. A module exists whose source directory is empty by design, which a reviewer may read as a stub unless the ADR states otherwise.
- The `api` declarations required by `export` are wider than the modules would otherwise need: a consumer of `:feature:discovery` now sees its `:core:data` dependency. That is a real widening of the declared surface, accepted because the alternative is duplication.
- Everything the Swift side can see becomes, by construction, part of the public surface — so an accidental `public` declaration inside a feature is a change to the interop contract, not a private detail.
- The export mechanism's behaviour with `@Serializable data object` route declarations and with `sealed interface` intent hierarchies is unverified until Xcode is in the loop. This ADR therefore fixes the topology and defers the confirmation to a spike, rather than claiming the mechanism works.
- The iOS framework build now blocks on the two Apple targets compiling in the shared modules, which `S14` of `TECHNICAL_PLAN.md` already requires on every pull request.

## Risks

- **`RISK-003`** (dual-platform delivery doubles verification cost) — aggravated mildly: the macOS runner must now also link the framework, not only compile the shared source sets. Mitigated by keeping the linkage to one framework with one export list.
- **Local:** the exported Objective-C surface proves unusable for a construct the binding needs — most plausibly a `@Serializable data object` route declaration or a `sealed interface` intent hierarchy, which ADR-0003 and `CONTRACTS.md` §8.2 B3 assume cross as classes with `is`/`as` branching. Mitigated by the TASK-078 spike, which is scheduled in M1 rather than M2, and by the recorded fallback: if the spike fails, this ADR is superseded with the per-feature topology and the failure becomes a documented constraint on the shared types.
- **Local:** the `api` promotions drift, so a feature's declared surface grows beyond what the binding consumes — mitigated by TASK-017's dependency analysis and by the rule that the export list equals the per-feature bindings' surface.
- **Local:** a second Kotlin framework reaching the app through a transitive Swift package, reproducing the duplicate-symbol failure the decision exists to avoid — mitigated by the rule that `iosApp/` links exactly one Kotlin framework, checked when the Xcode project lands (TASK-051).
- **Local:** `isStatic` proves wrong for the linkage style the app needs (for example a future Swift package that cannot consume a static framework) — mitigated by treating `isStatic` as a linkage detail of this ADR rather than of ADR-0001, so changing it does not reopen the topology.

## Validation criteria

- **Observable:** exactly one Kotlin framework is produced by the build and linked by the app → the Gradle task list and the Xcode link phase both name one framework; a second one is a defect.
- **Observable:** `:core:ios` declares the two Apple targets and no other, and declares one framework binary → the module's task list and its build script.
- **Observable:** the exported surface equals the per-feature surface → the framework's generated Objective-C header contains the `IC-###` state types, the intent hierarchies, the five route declarations and no type that a direct dependency would not expose.
- **Observable:** the framework carries one Kotlin runtime → the produced binary contains one `kotlin-stdlib` and one `kotlinx-serialization-core`; a link of the app succeeds with no duplicate-symbol diagnostic.
- **Observable:** the Android build is unaffected → `./gradlew :androidApp:assembleDebug` succeeds and no Android configuration lists `:core:ios`; observed in the dependency report (`REQ-PLAT-004`).
- **Observable:** the iOS app reflects shared state and reaches the shared route types → driven by the ADR-0003 bridge test and by `TEST-UNIT-017`'s shared-source-set assertions, on the macOS runner (DEC-054).
- **Observable:** the module set is the twelve projects of ADR-0001 as amended → `./gradlew projects`.
- **Verification protocol (DEC-053):** the spike is a build/tooling change and states that exception; the framework linkage in `iosApp/` is build configuration and states it too. The bridge test that consumes the exported surface is a behaviour change and MUST begin as an observed failing test.

## Related requirements

- `REQ-PLAT-001` (`AC-REQ-PLAT-001-1`): the shared modules carry no platform UI code, and the iOS client consumes them unchanged.
- `REQ-PLAT-003` (`AC-REQ-PLAT-003-1`): the deployment target of the app that links this framework.
- `REQ-PLAT-004` (`AC-REQ-PLAT-004-1`): Android stays deliverable with the iOS artifact absent.
- `REQ-NFR-002`: one implementation per concern; the duplicate-runtime option is rejected on this requirement.
- `REQ-NFR-005` (`AC-REQ-NFR-005-1`): the risky shared logic is tested in `commonTest`, which is what the iOS milestone consumes through the framework.

## Related implementation areas

- `:core:ios` (new), the four existing `:core:*` modules and the five `:feature:*` modules whose `api` declarations the export requires, `iosApp/` and its Xcode linkage, `androidApp` (confirmed unaffected).
- [`DESIGN.md`](../DESIGN.md) §3 (module table and edges), §3.4 (dependency rules), §3.5 (build logic), §4.2 (navigation ownership).
- [`CONTRACTS.md`](../CONTRACTS.md) §7 (consumption by platform state holders), §8.2 (what counts as breaking for the Swift consumer).
- [`adr/0001-module-boundaries.md`](0001-module-boundaries.md) (the amended module set), [`adr/0002-platform-targets.md`](0002-platform-targets.md) (the two Apple targets), [`adr/0003-ui-sharing-strategy.md`](0003-ui-sharing-strategy.md) (the binding mechanism this ADR packages for).
- `TASK-078` (the export module and its spike, M1), `TASK-051` (the iOS app target, M2), `TASK-017` (dependency analysis that keeps the `api` surface honest).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none.
- **Superseded by:** none as of 2026-09-30. `DEF-004` is the expiry condition: when Kotlin's Swift export leaves Alpha, the export module is deleted and this ADR is superseded rather than amended, because the packaging problem disappears with it.
- **Amends:** ADR-0001 (the `:core:ios` module and its edges only; every other boundary, rule and rationale there is unchanged).
- **Related:** ADR-0002 (target list), ADR-0003 (no SKIE, hand-written binding, glass availability), ADR-0008 (the containment pattern this ADR copies).
