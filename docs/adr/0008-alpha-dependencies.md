# ADR-0008 — Accepted alpha-dependency risk and the artifact budget

- **Status:** Accepted
- **Date:** 2026-09-29
- **Last verified:** 2026-09-29
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementation owner Implementation Engineer (Android) for `:core:designsystem`; consulted Implementation Engineer (iOS) for the pinned Apple-side versions in the same manifest
- **Authoritative for:** which unstable artifact versions the project accepts, the exact pins, the upgrade policy, the containment plan if an alpha breaks the build, and the review trigger. Not the design tokens themselves (`UI_SPEC.md` §3) and not the dependency-restraint rule (`DESIGN.md` §3).
- **Inputs:** `DEC-010` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `CON-004`, `RISK-002`, `REQ-NFR-006`, `REQ-UX-002` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); `DEC-037`, `DEC-054`, `DEC-028` (superseded); [`DESIGN.md`](../DESIGN.md) §3, §4.3; [`UI_SPEC.md`](../UI_SPEC.md) §3, §4.1; verified 2026-09-29: Material 3 stable 1.4.0 vs Expressive 1.5.0-alpha29, Compose BOM 2026.09.00, `androidx.lifecycle` KMP latest 2.12.0-alpha04 (no stable), multiplatform DataStore latest 1.3.0-alpha11 (no stable)

## Owners

- **Decision owner:** System Architect — accountable for the accepted risk, the containment plan and the review trigger.
- **Implementation owners:** Implementation Engineer (Android) for the pinned Android artifacts and the design-system containment boundary; Implementation Engineer (iOS) for the Apple-side pins in the same dependency manifest.
- **Consulted:** UI/UX Designer for the component approximation the fallback would use; Requirements Analyst for `CON-004` and `RISK-002`.

## Decision

The project accepts **exactly one alpha artifact on the UI path**, and explicitly declines two others.

| Artifact | Version | Status | Where it is used |
| --- | --- | --- | --- |
| `androidx.compose.material3:material3` | `1.5.0-alpha29` (Material 3 Expressive) | alpha, accepted | `:core:designsystem` only |
| `androidx.compose:compose-bom` | `2026.09.00` | stable BOM, pins the Compose set | Android modules |
| Kotlin | `2.4.20` | stable | toolchain |
| Ktor | `3.6.0` | stable | `:core:data` |
| Coil (`coil-network-ktor3`) | `3.6.3` | stable | `:androidApp` image loader |
| Koin | `4.2.2` | stable | DI graph |
| Navigation Compose | `2.10.2` | stable | `:androidApp` |
| SplashScreen | `1.2.0` | stable | `:androidApp` |
| Android API | `compileSdk`/`targetSdk` 37, `minSdk` 26 | stable | Android modules |
| iOS deployment target | 18.0 | stable | `iosApp` |

Explicitly **not adopted**:

- `androidx.lifecycle` KMP `2.12.0-alpha04` — not used, because there are no shared ViewModels (ADR-0003, ADR-0006). Its alpha status is the reason DEC-013 was taken.
- Multiplatform DataStore `1.3.0-alpha11` — not used, because favorites are persisted through `expect/actual` stores (ADR-0007).

Rules that follow:

1. **Pinning.** Every version MUST be pinned exactly in the version catalog; dynamic and range versions (`+`, `latest.release`, `[1.0,)`) MUST NOT appear (`AC-REQ-NFR-006-1`).
2. **Containment.** The accepted alpha MUST be referenced only by `:core:designsystem`. No feature module and no app shell MUST declare `material3` directly; they consume the tokens and components of `:core:designsystem`. This keeps a rollback to a non-alpha version a change to one module.
3. **Upgrade policy.** An alpha or Compose-BOM bump MUST be its own pull request that (a) states why the bump is needed, (b) references this ADR, (c) changes nothing else, and (d) is green on the full both-platform check set (DEC-054). Alpha bumps MUST NOT be bundled with feature work, MUST NOT be applied automatically by the dependency automation of DEC-037 (those PRs are to be closed or held for the deliberate bump), and MUST NOT be taken mid-milestone.
4. **Fallback plan.** If the pinned alpha breaks the build, tests or a release candidate, the fallback is, in order: (i) hold the previous green pin and continue on the last known-good version; (ii) if the breakage is version-specific, pin the Compose BOM down to the last combination that built; (iii) if the alpha cannot be used at all, drop to the stable Material 3 `1.4.0` and keep the component API of `:core:designsystem` unchanged, approximating the Expressive-only details (for example the contained loading indicator, the emphasized type styles and the expressive FAB shapes) with stable Material 3 equivalents. The components named in `UI_SPEC.md` §4.1 MUST keep their names and parameters either way, so no feature module changes. Option (iii) is a decision change and MUST be recorded as a superseding ADR with a `DECISION_BOARD.md` row.
5. **Review trigger.** This ADR MUST be re-reviewed when any of the following happens: Material 3 `1.5.0` reaches stable; the alpha causes a second build or test breakage; a Kotlin or Compose-Compiler upgrade requires a new alpha; the alpha has been pinned for six months without reaching stable; or the Android CI job fails twice consecutively with a failure traceable to the alpha.
6. **Evidence.** The accepted pins MUST be visible in one place (the version catalog) and mirrored in `README.md`'s dependency inventory (`AC-REQ-NFR-002-1`), and `RISK-002` MUST remain the tracked risk for this decision.

- **Board entry:** `DEC-010` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). `CON-004` records the three alpha-only components; this ADR is the reason two of them are not used.

## Context

The design baseline is Material 3 Expressive, and the Android components are specified against it (`UI_SPEC.md` §3.4, §4.1): emphasized type styles, the contained loading indicator's morphing shape, and expressive FAB shapes. On 2026-09-29 the Expressive release line existed only as `androidx.compose.material3:material3:1.5.0-alpha29`; the stable line was `1.4.0`. Following the design therefore means shipping one alpha artifact on the UI path, and the project records that as an accepted risk rather than discovering it later.

The alternative — building the UI against stable `1.4.0` — would make several specified components approximations of themselves, which conflicts with the image-and-detail expectations of the assignment (`assessment.md:9`) and with the component tables in `UI_SPEC.md` §4.1.

Two further artifacts were alpha-only on the same date and are not needed: `androidx.lifecycle` KMP (relevant only if ViewModels were shared, which ADR-0003 rejects) and multiplatform DataStore (relevant only if favourites used it, which ADR-0007 rejects). Recording this distinction matters, because `CON-004` lists all three as constraints and a reader could otherwise conclude the project depends on three alpha artifacts.

## Decision drivers

- The design baseline is Material 3 Expressive, specified component by component — `UI_SPEC.md` §3.4, §4.1; `assessment.md:9`.
- An unstable dependency must be contained to one module and to one upgrade path — `REQ-NFR-002`, `REQ-NFR-001`.
- Reproducible builds: exact pins only, no ranges — `REQ-NFR-006`, `AC-REQ-NFR-006-1`.
- The known risk must be tracked and mitigated, not accepted implicitly — `RISK-002`, `CON-004`.
- The other two alpha artifacts are unnecessary because of other decisions — ADR-0003 (no shared ViewModels), ADR-0007 (no multiplatform DataStore).
- Every accepted risk needs a measurable exit; the whole test suite is blocking on every pull request, so a breakage surfaces immediately (DEC-054).

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Pin the alpha (chosen) | `material3:1.5.0-alpha29` pinned in the version catalog, referenced only by `:core:designsystem` | Ships the specified components exactly and keeps the design/code mapping 1:1 (`UI_SPEC.md` §1.2); accepts that an alpha may change its API or behaviour under the project, and that an unreleased artifact is on the critical path of the primary deliverable | Chosen: the design specification is a commitment, the containment boundary exists, and the fallback is written down |
| Stable `1.4.0` with approximated components | Build against stable Material 3 and approximate the Expressive details | No alpha risk and a well-tested library; the loading indicator, the emphasized type styles and the FAB shapes become approximations, `UI_SPEC.md` §4.1 stops matching the code, and the visual-review axis of the assignment suffers | Rejected as the baseline: it trades a contained build risk for a permanent, visible specification gap. It is retained as fallback step (iii) |
| Dual stable/alpha paths | Both a stable and an alpha variant of the design-system components, selected by a build flag | The app builds even if the alpha breaks; doubles the design-system surface, its screenshot baselines (DEC-024) and its review burden, and creates a combination that no CI run would fully cover | Rejected: it doubles the design-system cost to hedge a risk that already has a one-module fallback |

## Consequences

**Positive**

- The Android components match the specified design without approximation, so `UI_SPEC.md` §4.1 and `:core:designsystem` stay 1:1 and screenshot review is meaningful.
- The blast radius of an alpha regression is one module, and because features depend on the design system and not on `material3`, the fallback needs no feature edits.
- The pin set is explicit, so the project can state exactly which artifact is unstable rather than describing a general "some dependencies are alpha" condition.
- Two adjacent alpha-only artifacts are declined with reasons, which keeps `REQ-NFR-002`'s "no two solutions for the same concern" rule intact.
- Re-review is triggered by named events rather than by memory.

**Negative**

- One unreleased artifact is on the path of the primary deliverable; a regression can block Android work until it is pinned back or the fallback is invoked.
- The alpha's API can change between alpha releases, so a bump is a deliberate, isolated pull request rather than a routine automated update.
- The project's dependency automation must special-case the alpha: automatic bump PRs for it are not acceptable, which is friction against DEC-037's default policy.
- If fallback step (iii) is ever taken, the visual specification and the code diverge until the Expressive line stabilises, and the ADR must be superseded rather than quietly amended.
- The design-system containment rule is a convention that review must enforce in feature modules.

## Risks

- **`RISK-002`** (pinned alpha toolchain components break on upgrade) — this ADR is the mitigation: exact pins, isolated upgrade pull requests, one-module containment, a written fallback, and named review triggers.
- **Local:** an alpha regression that also affects the stable Compose BOM combination, making a pin-back insufficient — mitigated by fallback step (iii) and by the fact that the components keep their names and parameters.
- **Local:** a feature module bypassing `:core:designsystem` and declaring `material3` directly, which would widen the blast radius — mitigated by the ADR-0001 dependency rules and by the dependency-analysis check (DEC-032).
- **Local:** screenshot baselines churn on every alpha bump, obscuring real visual regressions — mitigated by treating a bump as its own pull request whose diff is exactly the pin and the baseline updates, and by the full-suite gate (DEC-054).
- **Local:** the fallback path being unreachable in CI, so a broken build has an untested plan — mitigated by keeping the component surface of `:core:designsystem` stable and by the approximation being specified against `UI_SPEC.md` §4.1.

## Validation criteria

- **Observable:** the resolved Android dependency graph contains `material3:1.5.0-alpha29`, the Compose BOM `2026.09.00`, and no other alpha artifact from the declined list — observed in the dependency-analysis report and in `README.md`'s dependency inventory (`AC-REQ-NFR-002-1`, `AC-REQ-NFR-002-2`).
- **Observable:** no version in the catalog is a dynamic or range version — observed by inspection and by the reproducibility check (`AC-REQ-NFR-006-1`).
- **Observable:** only `:core:designsystem` declares the alpha artifact — observed in the per-module dependency declarations and the dependency-analysis report.
- **Observable:** the components named in `UI_SPEC.md` §4.1 all exist in `:core:designsystem` with their specified roles — observed in the component screenshot suite (`TEST-UI-004`, `TEST-UI-001`) and by reviewing the design-system surface.
- **Observable:** an Android build from a clean clone succeeds with the pinned versions, so the accepted risk is at least currently contained — observed by the required Android check on the pull request (DEC-054).
- **Verification protocol (DEC-053):** a design-system component or token change lands as a red commit (a failing screenshot or semantics test), then a green commit, then an optional refactor commit, with history preserved (no squash; DEC-041 amended). Dependency-pin and tooling changes are the stated exception to the red phase and MUST say so in the commit body.

## Related requirements

- `REQ-NFR-006` (`AC-REQ-NFR-006-1`, `AC-REQ-NFR-006-2`): exact pins, no ranges, one build command from a clean clone.
- `REQ-NFR-002` (`AC-REQ-NFR-002-1`, `AC-REQ-NFR-002-2`): each dependency has a rationale; the inventory matches the resolved graph.
- `REQ-UX-002` (`AC-REQ-UX-002-1`): tokens come from `UI_SPEC.md` §3, checked against `tokens.json` (DEC-022) — this is the mechanism that detects an alpha changing a component's defaults.
- `REQ-PLAT-002` (`AC-REQ-PLAT-002-1`): the Android floors the pinned artifacts must support (API 26 to API 37).
- `REQ-NFR-007` (`AC-REQ-NFR-007-1`): the local gate that verifies a pinned combination before it is merged.
- `CON-004`, `RISK-002`: the constraint and risk this decision owns.

## Related implementation areas

- `:core:designsystem` (the only module that references the alpha), the Android version catalog, `:androidApp` (Compose BOM and the stable Android artifacts), `iosApp` (the Apple-side pins in the same manifest).
- [`UI_SPEC.md`](../UI_SPEC.md) §3 (tokens) and §4.1 (the components the alpha implements).
- [`DESIGN.md`](../DESIGN.md) §3 (design-system boundary) and §4.3 (token pipeline that detects drift).
- DEC-022 (token parity test), DEC-024 / DEC-034 (screenshot baselines affected by a bump), DEC-037 (dependency automation policy this ADR special-cases), DEC-053 (TDD protocol), DEC-054 (full suite blocking on every pull request).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none.
- **Superseded by:** none as of 2026-09-29. This ADR MUST be superseded when Material 3 `1.5.0` reaches stable, or when fallback step (iii) is invoked; the review triggers above state when that review is due.
- **Related:** ADR-0003 (which declines the lifecycle alpha), ADR-0007 (which declines the DataStore alpha), ADR-0001 (the design-system containment boundary this decision relies on).
