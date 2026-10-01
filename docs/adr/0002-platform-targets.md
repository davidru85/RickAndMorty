# ADR-0002 — Platform targets and floors

- **Status:** Accepted
- **Date:** 2026-09-29
- **Last verified:** 2026-09-29
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) and Implementation Engineer (iOS); consulted UI/UX Designer (form-factor baseline)
- **Authoritative for:** which platforms and which Kotlin Multiplatform targets the project builds, the OS floors, the supported form factor, and the delivery order between the two clients. Not the module set (`DESIGN.md` §3), not the visual specification (`UI_SPEC.md`), and not the versions of the artifacts each platform consumes (see ADR-0008).
- **Inputs:** `DEC-001`, `DEC-009`, `DEC-027`, `DEC-040` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-008` (ADR-0003), `DEC-010` (ADR-0008); `REQ-PLAT-001`…`REQ-PLAT-005`, `REQ-UX-001`, `REQ-NFR-006`, `NG-004`, `NG-005`, `DEF-004` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`DESIGN.md`](../DESIGN.md) §2; verified Android API 37 stable / Kotlin 2.4.20 / Liquid Glass `glassEffect` iOS 26.0+, checked 2026-09-29

## Owners

- **Decision owner:** System Architect — accountable for the target list, the floors and the delivery order.
- **Implementation owners:** Implementation Engineer (Android) for the Android targets and `:androidApp`; Implementation Engineer (iOS) for the iOS targets and the `iosApp/Features/*` packages.
- **Consulted:** UI/UX Designer for the 9:19.5 phone portrait baseline (`docs/design/`); Requirements Analyst for the platform requirements `REQ-PLAT-001`…`REQ-PLAT-005`.

## Decision

The project builds exactly two clients, Android first and iOS second, from one Kotlin Multiplatform core.

| Target | Declaration | Floor | Notes |
| --- | --- | --- | --- |
| `androidTarget` | Jetpack Compose app in `:androidApp` | `minSdk 26`, `compileSdk`/`targetSdk` 37 | DEC-009; API 37 is the stable Android API (verified 2026-09-29) |
| `iosArm64` | iOS application composed of the `iosApp/Features/*` packages under the `iosApp` app target | deployment target iOS 18.0 | DEC-008; Liquid Glass is used only where `glassEffect` exists (iOS 26.0+) |
| `iosSimulatorArm64` | same app, simulator slice | iOS 18.0 | required to run and snapshot-test the iOS milestone on the project's arm64 workstation |

Constraints that follow:

- No `jvm`, `js`, `wasmJs`, `linuxX64`, `macosX64`, `macosArm64` or `mingwX64` target MUST be declared: desktop, web, watch and TV are non-goals (`NG-005`).
- The shared targets are produced by `:core:domain`, `:core:data`, `:core:presentation`, `:core:testing` and the five `:feature:*` modules; the iOS application consumes them as a framework (ADR-0003, ADR-0001).
- Android MUST be a releasable deliverable on its own; the iOS packages are additive (`REQ-PLAT-004`). Android is the first milestone and iOS the second (DEC-040).
- The supported form factor is a phone in portrait at approximately 9:19.5. Tablet, foldable and landscape layouts are non-goals (`NG-004`, DEC-027).
- Every toolchain and dependency version MUST be pinned, including the platform floors; no dynamic version ranges (`REQ-NFR-006`, `AC-REQ-NFR-006-1`).

- **Board entry:** `DEC-001` (scope), `DEC-009` (Android floor), `DEC-027` (form factor), `DEC-040` (milestones) — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Context

`assessment.md:16` asks for Jetpack Compose or SwiftUI; the two Figma pages define two native design languages (`docs/design/`, `UI_SPEC.md` §2, §4), and the project frames the assignment as a KMP one with a shared core (DEC-001, `REQ-PLAT-001`). The repository contains no source code, no build files and no CI as of 2026-09-29, so the target list is a target-state decision that the first Gradle build materialises, and the first build file is itself part of what the assignment asks to be reviewed (`assessment.md:8`).

The platform facts are fixed and dated: Android API 37 is stable and Kotlin 2.4.20 is the stable compiler (checked 2026-09-29); Liquid Glass `glassEffect` exists only from iOS 26.0, so anything below that needs a documented fallback; and Kotlin's Swift export is still Alpha, which is why the interop path is the one recorded in ADR-0003.

## Decision drivers

- Two native design languages that must not be averaged into one — `UI_SPEC.md` §2, §4; `docs/design/`; DEC-001.
- Android deliverable first, iOS additive — `REQ-PLAT-004`, DEC-040.
- Code depth over surface area: one shared core, two thin clients — `assessment.md:8`, DEC-003.
- Dependency restraint: no runtime or tooling cost carried for a platform the app does not ship — `assessment.md:7`, `REQ-NFR-002`, `NG-005`.
- Image-first UX and per-platform image pipelines — `assessment.md:9`, DEC-026.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Android only | One Compose app, shared code reduced or removed | Smallest delivery and verification surface; discards the second platform the assignment's KMP framing and the iOS Figma page assume, and makes DEC-001 unachievable without a rewrite of the module edges | Rejected: contradicts DEC-001 and the design baseline; also removes `REQ-PLAT-003` |
| Compose Multiplatform for both clients | One Compose UI in `commonMain` rendered by both apps | One UI implementation and one test suite; erases Liquid Glass and Material 3 Expressive, so `docs/design/` and `UI_SPEC.md` §4 cannot be honoured, and the reviewer's main evaluation axis (`assessment.md:8`, DEC-003) becomes a shared-UI exercise instead of two native architectures | Rejected: discards the two native design languages (DEC-001, DECISION_BOARD.md §3) |
| Android-first staged, iOS second (chosen) | Shared core, Android Compose milestone, then an iOS SwiftUI milestone | Each platform gets its native language, sharing happens below the UI, and the Android milestone is independently releasable; costs the dual build, dual test and dual CI surface, and pushes iOS verification to the later milestone | Chosen: satisfies DEC-001, DEC-040 and `REQ-PLAT-004` without sacrificing either design language |
| Full dual-native delivery in parallel | Both clients built and verified in the same milestone | Shortest path to a two-platform demo; doubles verification cost immediately (`RISK-003`), and while the shared core is still moving, both thin clients are rebuilt on every core change | Rejected: DEC-040 sequences one milestone per platform precisely to avoid this cost |

## Consequences

**Positive**

- The Android milestone is releasable and reviewable while the iOS milestone is still absent, which satisfies `AC-REQ-PLAT-004-1` by construction.
- Each platform keeps its own navigation, motion and material vocabulary, so `UI_SPEC.md` §4 and §7 are implementable as written rather than approximated.
- The target list is small enough that the dependency-analysis and CI gates stay meaningful (DEC-032, DEC-054).
- The shared core is exercised by two real consumers, which is what makes the domain/data boundary of ADR-0001 load-bearing rather than decorative.

**Negative**

- iOS work needs macOS and Xcode, and the macOS runner now executes the iOS suites on every pull request (DEC-054, superseding DEC-028): the runner cost is accepted knowingly, and both platforms' suites are blocking for every increment.
- Two platforms must be built, tested and screenshotted, so `RISK-003` is accepted knowingly.
- The floors constrain what the shared code may assume: API 26 device behaviour and iOS 18 semantics, with Liquid Glass behind an availability check (DEC-008), so glass and non-glass paths both need baseline snapshots (`RISK-007`).
- `minSdk 26` and `compileSdk`/`targetSdk` 37 must be kept aligned across the version catalog; a floor bump is a new ADR, not an edit.

## Risks

- **`RISK-003`** (dual-platform verification cost may slip the iOS milestone) — this decision is the mitigation: Android-first sequencing (DEC-040) keeps M1 releasable alone.
- **`RISK-007`** (glass fallback diverges visually from the glass path) — both paths MUST be screenshot-tested (DEC-024, DEC-025) and the fallback is specified in `UI_SPEC.md` §4.2.
- **Local:** API 37 is the newest stable API, so Android Lint and library behaviour around it may lag — mitigated by pinning `compileSdk`/`targetSdk` at 37 with `minSdk` 26 and by the dependency-analysis gate check.
- **Local:** dropping every non-mobile target means a future table or watch request invalidates this ADR and the module edges with it — mitigated by the review trigger below.

## Validation criteria

- **Observable:** the APK installs on an API 26 device or emulator and runs on API 37 — observed by installing and launching the release APK on both (`AC-REQ-PLAT-002-1`).
- **Observable:** the iOS app builds with a deployment target of 18.0, runs on an iOS 18 simulator with the documented non-glass fallback, and uses glass on iOS 26+ — observed by running both simulator configurations and comparing against the committed snapshots (`AC-REQ-PLAT-003-1`, DEC-025).
- **Observable:** the Gradle task list exposes only the Android and the two Apple targets; no `jvm`/`js`/`wasmJs`/desktop target exists — observed in the `./gradlew tasks` report and in the module dependency declarations.
- **Observable:** every version, including the platform floors, is pinned with no dynamic version range — observed by the absence of `+`/`latest.release` in the version catalog (`AC-REQ-NFR-006-1`).
- **Observable:** `:androidApp` builds and installs from a tree with no iOS packages present (`AC-REQ-PLAT-004-1`).
- **Verification protocol (DEC-053):** each behaviour increment lands as a red commit (a test that was run and observed to fail), then a green commit, then an optional refactor commit, with history preserved (no squash; DEC-041 amended). The iOS build and simulator configuration checks above are the green-phase evidence for the iOS target configuration; a missing red phase is only acceptable for this ADR's build/CI/tooling changes, and that exception MUST be stated in the commit body.

## Related requirements

- `REQ-PLAT-001` (`AC-REQ-PLAT-001-1`): shared code is authored once in KMP targets used by both clients, and contains no platform UI.
- `REQ-PLAT-002` (`AC-REQ-PLAT-002-1`): the Android floors fixed here.
- `REQ-PLAT-003` (`AC-REQ-PLAT-003-1`): the iOS floor and the Liquid Glass availability split.
- `REQ-PLAT-004` (`AC-REQ-PLAT-004-1`): Android first, one definition of done per platform.
- `REQ-PLAT-005` (`AC-REQ-PLAT-005-1`): first launch without network, on both platforms, is a designed error rather than a crash.
- `REQ-UX-001`: one appearance, not system-driven, on both platforms.
- `REQ-NFR-006`: pinned toolchain and dependency versions.
- `NG-004`, `NG-005`: the form factor and platform non-goals this decision enforces.

## Related implementation areas

- Gradle target declarations (`androidTarget`, `iosArm64`, `iosSimulatorArm64`), the Android application module, the iOS application target.
- [`DESIGN.md`](../DESIGN.md) §2 for the shared/native split and §4.2 for the per-platform navigation split.
- [`UI_SPEC.md`](../UI_SPEC.md) §1 for the 9:19.5 baseline and §4.2 for the glass components.
- DEC-054 (both platforms' full test suite required on every pull request; supersedes DEC-028), DEC-040 (milestones).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Amendment — 2026-10-02: an opt-in JVM target (`TASK-027`, `DEC-079`)

The accepted decision above states the target set as Android plus the two Apple targets. The owner
amended it on 2026-10-02, and the amendment is recorded here as a pointer rather than as a rewrite,
because the original rationale is immutable once accepted (`AGENTS.md` §6).

**What changed.** A `:core:*` KMP module MAY declare a JVM target when it needs one to run a
JVM-only verification path. The target is **opt-in per module** through the module's own
`gradle.properties`:

```properties
multiverse.jvmTarget=true
```

**Why it was needed.** The scheduled live contract job (`TASK-027`, `DEC-074`) has to compile and run
Kotlin that issues live HTTP requests. Every alternative route was probed and rejected with
evidence (`PROJECT_LOG.md` LOG-0062):

| Route | Observed outcome |
| --- | --- |
| The probes on the Android host-test classpath | breaks the fixture/replay entry point's `TEST-CONTRACT-*` filter and the formatter's view of the test sources |
| A dedicated module or Gradle project for the probes | contradicts `DEC-070` ("a source set/directory, not a Gradle project") |
| A custom compilation on the existing Android KMP target | the AGP KMP library plugin allows only the compilations it declares |

**What did not change.** No other module gains a target: the opt-in is a per-module property, and the
two modules that set it today are `:core:domain` and `:core:data`, which the probes depend on.
No JVM target is added to `:androidApp`, to a `:feature:*` module or to `:core:designsystem`, and
`verifyModuleBoundaries` classifies targets, so an unintended opt-in fails the graph check rather
than passing quietly. The Apple targets, the Android target and the absence of `iosX64` are as the
decision states.

## Superseded and superseding ADRs

- **Supersedes:** none.
- **Superseded by:** none as of 2026-09-29. **Amended:** 2026-10-02 by `DEC-079` (an opt-in JVM target, recorded in the amendment section above).
- **Related:** ADR-0003 (iOS interop and the glass availability split), ADR-0008 (the alpha artifacts each platform consumes), ADR-0001 (the modules these targets create).
