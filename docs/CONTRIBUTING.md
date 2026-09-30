# CONTRIBUTING.md — Contribution Process

- **Status:** Active — target state. The Gradle/KMP build skeleton exists (TASK-014); no feature code, tests or CI exist yet (`README.md` §14), so the feature and test commands below are the documented interface to work that has not landed.
- **Last verified:** 2026-09-29
- **Owner:** Documentation Maintainer (see `../AGENTS.md` §3.9)
- **Authoritative for:** the contribution process — prerequisites, branching, the TDD phase-and-commit protocol and the merge policy (DEC-053, amending DEC-041), Conventional Commits and how release notes are derived, the issue workflow, pull-request expectations including the required-check list, review, agent permissions, and the contribution completion checklist.
- **Not authoritative for:** code conventions and tool-enforced rules (`GUIDELINES.md`); gates, Ready/Done and waivers (`DEFINITION.md`); test strategy, layers, ids and tooling (`TESTING.md`); the vulnerability-reporting route (`SECURITY.md` §10); module boundaries and dependency direction (`DESIGN.md`, `adr/0001-module-boundaries.md`, DEC-052); operating rules for agents (`../AGENTS.md`).
- **Inputs:** [`assessment.md`](../assessment.md), [`AGENTS.md`](../AGENTS.md), [`README.md`](../README.md), [`REQUIREMENTS.md`](REQUIREMENTS.md), [`DECISION_BOARD.md`](DECISION_BOARD.md), [`TESTING.md`](TESTING.md), [`DEFINITION.md`](DEFINITION.md), [`GUIDELINES.md`](GUIDELINES.md), [`SECURITY.md`](SECURITY.md), [`TECHNICAL_PLAN.md`](TECHNICAL_PLAN.md), [`BACKLOG.md`](BACKLOG.md), `templates/`
- **Normative terms:** `MUST` mandatory · `SHOULD` strong recommendation · `MAY` optional.

> This file documents the process as a **team** process (DEC-048): it is written for a future collaborator or AI agent joining the project, not for a single author working alone. Where process content is owned by another document, this file names it instead of restating it.

## Table of contents

1. [Before you start](#1-before-you-start)
2. [Branching](#2-branching)
3. [Commit protocol and messages](#3-commit-protocol-and-messages)
4. [Issue workflow](#4-issue-workflow)
5. [Pull requests and required checks](#5-pull-requests-and-required-checks)
6. [Testing requirements for a contribution](#6-testing-requirements-for-a-contribution)
7. [Documentation requirements](#7-documentation-requirements)
8. [Review process](#8-review-process)
9. [AI-agent contributions](#9-ai-agent-contributions)
10. [Reporting a security vulnerability](#10-reporting-a-security-vulnerability)
11. [Contribution completion criteria](#11-contribution-completion-criteria)

## 1. Before you start

Read the repository before writing anything. The mandatory reading order and the precedence chain are owned by [`../AGENTS.md`](../AGENTS.md) §2; the short version is `assessment.md` → `REQUIREMENTS.md` → the design and contract documents → the ADRs → the log. Do not begin from memory or from a similar project (`../AGENTS.md` §2.1).

### 1.1 Prerequisites

| Tool | Version | Why it is needed |
| --- | --- | --- |
| JDK | 17 or newer | Required by the Gradle build |
| Android SDK | Platform 37 (Android 17), Build Tools current | The Android app uses `minSdk` 26 with `compileSdk`/`targetSdk` 37 (DEC-009) |
| Xcode | 26 or newer, with the iOS 26 SDK | Needed to compile the iOS 26 Liquid Glass APIs behind the availability check (DEC-008) |
| Kotlin | 2.4.20 | Supplied by the Gradle toolchain; no local install is required |

No API key, account or credential is needed: the Rick and Morty API is public, unauthenticated and read-only (`REQ-SEC-002`, `CON-001`). No secret is committed, and none is required (`../AGENTS.md` §4.2).

### 1.2 Clone and build

```bash
git clone git@github.com:davidru85/RickAndMorty.git
cd RickAndMorty
```

Build, run and quality commands are **owned by [`README.md`](../README.md)** §8 (build and run) and §9 (test and quality commands). This file does not duplicate them; a command copied into two documents drifts. The Android build is milestone M1 and the iOS build is M2 (DEC-040), and both platforms are required on every pull request (§5.3).

The current documentation change is made on branch `docs/documentation-system` and merged into `main` through the normal pull-request path (§5).

`README.md` §8 is authoritative for the command names; if the build lands and a command changes, `README.md` changes in the same pull request (DEC-046) and this file is only updated if the process around it changes.

## 2. Branching

Development is **trunk-based** (DEC-041, retained after DEC-053). Branches are short-lived, exist only for one logical change (§5.1), and are deleted after merge.

| Rule | Detail |
| --- | --- |
| Base | Branch from the current `main`, not from another feature branch. |
| Name | `<type>/<short-slug>`, where `<type>` is one of `feat`, `fix`, `docs`, `test`, `build`, `chore` and `<short-slug>` is lowercase, hyphenated and descriptive of the change (for example `feat/discovery-status-filter`, `docs/documentation-system`, `fix/cache-key-collision`). |
| Length | Short-lived: one logical change, merged as soon as it is green and reviewed. A branch alive long enough to need regular rebases is a signal that the change is too large. |
| `main` | `main` `MUST` always build. A change that leaves `main` broken is treated as a defect in the change, not as transient state. |
| Direct commits | No direct commits or pushes to `main`. Every change arrives through a pull request (§5). |
| Deletion | Delete the branch after merge; the phase commits (§3) remain in `main`'s history. |

The repository owner (human) may create branches and pull requests; an agent may open a pull request on a branch it created (DEC-049, `../AGENTS.md` §4.3). Branch protection on `main` is a repository setting and therefore a human-only action (`../AGENTS.md` §4.2).

## 3. Commit protocol and messages

### 3.1 TDD phase protocol (DEC-053)

Behaviour changes follow red → green → refactor, and the commit history preserves the cycle. The phase meanings are fixed:

| Phase | What happens | Commit message | Note |
| --- | --- | --- | --- |
| Red | Write the failing test for the next behaviour increment, run it, and **observe** the failure. | `test(<scope>): add failing test for <behaviour>` | The message `MUST` state the observed failure, not the expected one. |
| Green | The minimal implementation that makes the test pass; run it and observe the pass. | `feat(<scope>): <behaviour>` or `fix(<scope>): <defect>` | Minimal means minimal: no opportunistic refactor, no unrelated cleanup (`../AGENTS.md` §4.2). |
| Refactor | Improve structure with the tests still green. | `refactor(<scope>): <change>` | If the phase produces no change, say so and commit nothing. Do not manufacture a commit to fill the phase. |
| Push | Push the phase commits. | — | Pushed phase commits `MUST NOT` be amended or force-pushed away. |

Rules that hold across the protocol:

- One phase per commit; commit granularity follows the phase, not the file. A behaviour increment therefore produces one red, one green and (optionally) one refactor commit.
- A change that alters behaviour without a preceding, observed failing test is incomplete (`DEFINITION.md` §3, D3/D4). "The test would have failed" is not evidence; the recorded red output is.
- **Exceptions** are limited to pure documentation, build/CI configuration, and tooling changes. When the exception is used, the pull request says so explicitly (§5.2). The exception is part of the TDD decision (DEC-053) and is not a licence to skip a test for a behaviour change.
- Which test layer owns the red test, and what may and may not be asserted, are owned by [`TESTING.md`](TESTING.md) §1.1 and §2. A snapshot baseline `MUST NOT` be recorded from a state that does not exist, and no snapshot is ever recorded to make a red run green.
- The CI gate evaluates the **final state of the pull request**, not each commit (§5.4). The red commit is expected to fail tests by design; that is not a gate violation.

### 3.2 Commit message format (Conventional Commits, DEC-041)

```text
<type>(<scope>): <description>

<body>

<footers>
```

| Element | Rule |
| --- | --- |
| `type` | One of the allowed types in §3.3. |
| `scope` | The module or area the change belongs to, using the DEC-052 layout: `core-domain`, `core-data`, `core-presentation`, `core-designsystem`, `core-testing`, `feature-discovery`, `feature-character-detail`, `feature-favorites`, `feature-episodes`, `feature-settings`, `androidApp`, `iosApp`, plus the non-code scopes `docs`, `ci`, `build`, `tokens`, `copy`. |
| `description` | Imperative mood, no trailing period, 72 characters or fewer. |
| `body` | What changed and why. Explain the decision when it is not obvious from the diff; do not narrate the diff. |
| `footers` | Identifier references and the breaking-change marker: `Refs: TASK-###`, `REQ-###`, `AC-REQ-...-n`, `TEST-###`, `DEC-###`, `ADR-####`. |

### 3.3 Allowed types

| Type | Meaning | Phase / class |
| --- | --- | --- |
| `test` | Add or change a test without changing production behaviour | Red phase (DEC-053) |
| `feat` | A new behaviour | Green phase |
| `fix` | A defect fix | Green phase (carries a regression test where practical — §6) |
| `refactor` | Structure only, behaviour unchanged, tests still green | Refactor phase |
| `docs` | Documentation only | Explicit TDD exception |
| `build` | Build configuration, version catalog, wrapper, module wiring | Explicit TDD exception |
| `ci` | CI workflows and their configuration | Explicit TDD exception |
| `perf` | A change whose purpose is a measured performance improvement | Green phase or explicit exception, stated in the pull request |
| `chore` | Repository housekeeping that changes nothing a user or the build consumes | No red phase |
| `revert` | Revert of a previous commit; the reverted commit's hash goes in the body | No red phase |

### 3.4 Breaking changes

A breaking change is marked twice, in the same commit:

1. a `!` immediately after the type or scope — `feat(core-domain)!: rename CharacterSummary field`;
2. a `BREAKING CHANGE:` footer in the body naming what breaks and what replaces it.

A breaking change to a public contract also updates the owning contract document in the same change (`CONTRACTS.md` for `IC-###`, `API_SPECS.md` for a remote contract) — `DEFINITION.md` §3, D7 — and is reflected in the version derived from `VERSION` (DEC-043) when it reaches a release.

### 3.5 Merge policy

- A pull request is integrated with a **merge commit**, and with no other method: rebase merging and squash merging are disabled at the repository level and the `main` ruleset accepts only `merge` (DEC-059, superseding the merge-method half of DEC-041). The merged branch is preserved, so the previous branch and its individual commits stay reachable from the merge commit and remain part of the record.
- Squash merging stays **not** used: it would collapse the phase commits into one and destroy the red/green/refactor sequence (DEC-053).
- Conventional Commits remain mandatory and carry the phase meaning: `test:` is red, `feat:`/`fix:` is green, `refactor:` is refactor.
- Reviewing the phase commits in sequence `MUST` be possible from `main`'s history after the merge; that sequence is the evidence for the red/green/refactor gate (`DEFINITION.md` §3, D3). A merge commit preserves it because the branch commits appear in the merge's second parent; a rebase merge rewrote the SHAs instead, which is what DEC-059 traded away.
- History on `main` is therefore **not** linear: it carries one merge commit per pull request. That is a deliberate consequence of DEC-059, not a drift.
- Merge itself is a human action (§9); the author — human or agent — prepares the branch and the pull request.

### 3.6 Release notes and versioning (DEC-042, DEC-043)

Release notes are **generated from the Conventional Commits in the range since the previous tag**, not written by hand. There is no `CHANGELOG.md` (DEC-042): the narrative of *why* the project changed lives in [`PROJECT_LOG.md`](PROJECT_LOG.md), and the released *what* is the generated note.

| Commit type in the range | Where it appears in the release note |
| --- | --- |
| `feat` | Features |
| `fix` | Fixes |
| `perf` | Performance |
| Any commit with `!` or a `BREAKING CHANGE:` footer | A Breaking changes section, leading the note, naming what breaks and the replacement (§3.4) |
| `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `revert` | Maintenance, or omitted when the range contains only these — the note is for a reader of the release, not for the author of the change |

Rules:

- The version is bumped in the single `VERSION` file, which feeds `versionName` and `CFBundleShortVersionString`; no platform-specific version literal is changed by hand (DEC-043, `REQ-NFR-006`, `DEFINITION.md` §5, REL1).
- The bump follows the commit types in the range: a breaking change increments `MAJOR`, a `feat` increments `MINOR`, any other changed range increments `PATCH`.
- The release is a tag named `vMAJOR.MINOR.PATCH` on the released commit, and the GitHub Release publishes the Android APK (DEC-043).
- Release notes `MUST NOT` be edited to describe something the tagged commit does not contain, and a note `MUST NOT` claim a fix that is not in the range. `DEFINITION.md` §5, REL7 requires the documented limitations to match the release.
- Tagging, publishing and releasing are **human-only** actions (DEC-049); preparing the version bump and the release-note range is agent-eligible (`DEFINITION.md` §5).

## 4. Issue workflow

[`BACKLOG.md`](BACKLOG.md) is the **canonical work index** (DEC-044). One row per task, with the `TASK-###` id, its requirement ids, its acceptance criterion, its test intent and its impacted documents (`DEFINITION.md` §2, R1–R6).

| Object | Ownership |
| --- | --- |
| `TASK-###` row — title, requirement ids, acceptance criteria, size, dependencies, impacted documents, state | `BACKLOG.md` |
| Execution state — who is working on it, what blocks it, review state, linked pull request | The GitHub Issue |
| Requirement text and acceptance criteria | `REQUIREMENTS.md` |
| Test ids and traceability | `TESTING.md` |

Rules:

- Every issue links back to its `TASK-###` id: the id appears in the issue title and in the first line of the body. An issue without a backlog id is either a duplicate of one or a request that needs a backlog row first.
- Labels follow the type vocabulary, mirroring the `Type` column of [`BACKLOG.md`](BACKLOG.md) §2.1: `type:product` (product scope), `type:tech` (implementation, tooling, architecture), `type:test` (verification), `type:doc` (documentation), `type:risk` (risk, security, dependency hygiene).
- Milestones follow the delivery sequence in [`TECHNICAL_PLAN.md`](TECHNICAL_PLAN.md) §2: `M0` repository and documentation baseline, `M1` Android, `M2` iOS, `M3` hardening and release. A task belongs to exactly one milestone (DEC-040).
- Issues are opened from a template: backlog item for work, bug report for a defect (`docs/templates/`, DEC-051).
- Work `MUST NOT` start without a `TASK-###` row and a ready check against `DEFINITION.md` §2. The issue is the tracking surface, not the specification.

### 4.1 Proposing a new feature

A new feature is proposed **before any code exists**, in this order:

1. Open an issue describing the user-visible outcome and why it belongs in scope.
2. Name the requirement: an existing `REQ-*` id, or a new one added to `REQUIREMENTS.md` with its identifier and acceptance criteria (D1/D2). Scope is traceable to `assessment.md` or to an accepted decision — a proposal that is neither is out of scope (`REQUIREMENTS.md` §3, §1.2).
3. Get the acceptance criteria written as `AC-*` items that two people would implement the same way.
4. Confirm the work is not `Deferred` or `Could have` (`REQUIREMENTS.md` §1.3, `DECISION_BOARD.md` §4/§5) — if it is, it needs a new accepted decision first (R8).
5. Only then create the `TASK-###` row in `BACKLOG.md`, link the issue, and begin the TDD cycle (§3.1) with the red test.

### 4.2 Reporting a bug

A defect is reported with the bug-report template (`docs/templates/bug-report.md`, DEC-051) and linked to a `TASK-###` row. The fix carries a regression test where practical (`TESTING.md` §1, P5); where a regression test is impractical, the bug report records the manual reproduction that was used instead, and the pull request says why. A suspected **vulnerability** is not reported this way — it follows §10.

## 5. Pull requests and required checks

Every change reaches `main` through a pull request. The template is [`docs/templates/pull-request.md`](templates/pull-request.md) (DEC-051) and it `MUST` be used as-is; the reviewer relies on its fields.

### 5.1 One logical change

A pull request carries one logical change: one requirement, one defect, or one refactor. A change spanning two requirements is split unless the requirements cannot be satisfied independently. Opportunistic refactors, unrelated formatting, dependency bumps and documentation tidy-ups belong in their own pull requests with their own justification (`../AGENTS.md` §4.2, §14).

### 5.2 Description requirements

The description `MUST` state:

| Element | Why |
| --- | --- |
| The `TASK-###` id and the linked issue | Connects the change to the index |
| The `REQ-*` id and the `AC-*` criteria it satisfies | Requirement traceability (`DEFINITION.md` §3, D12) |
| The `TEST-###` ids that now cover the behaviour, and the observed red/green output | Evidence for the TDD phases (§3.1) |
| The `DEC-###` and `ADR-####` ids that constrain or record the change | Decision traceability |
| The documents touched | Required for every change, and explicitly required for agent-authored pull requests (§9) |
| The checks actually run, with the command and the observed result | A claim without a command and an observed result is a defect (`../AGENTS.md` §4.3) |
| The TDD exception, when one is used, and its class — documentation, build/CI, tooling | `DEFINITION.md` §3, D4 |
| The platform impact — Android, iOS, shared, or all three | Describes which surfaces the change touches. It does not narrow the required set: every required check runs on every pull request (§5.3, DEC-054) |
| Screenshots for a UI change | See below |

**Screenshots are required for UI changes**: a before/after pair where an existing surface changed, or the rendered new state where a surface is new. Committed snapshot baselines (Roborazzi on Android, swift-snapshot-testing on iOS) are the evidence of record; the screenshot in the pull request makes the change reviewable without checking out the branch. A baseline `MUST NOT` be recorded or updated wholesale to make a red run green (`TESTING.md` §8.2).

Every affected document is updated **in the same pull request** (DEC-046) — never in a follow-up. The mapping from change kind to documents is §7.

### 5.3 Required checks

The gate definition is owned by [`DEFINITION.md`](DEFINITION.md) §3 and §7; what each test contributes to the gate is owned by [`TESTING.md`](TESTING.md) §14. This section lists only the **names** of the required checks so a reviewer and the branch-protection configuration agree on terms.

Every pull request requires the full suite on **both platforms**; the checks below are blocking, and a pull request is not approvable while any of them is failing, skipped or absent (DEC-054, superseding DEC-028).

| Required check | Content owner | Covers |
| --- | --- | --- |
| `shared-tests` | `TESTING.md` §3, §14 | `commonTest` across targets: `TEST-UNIT-###`, the contract suite in fixture/replay mode (`TEST-CONTRACT-###`), `TEST-PERF-003` |
| `android-unit-tests` | `TESTING.md` §8.1, §14 | Android unit tests, `TEST-INT-###`, Compose semantics assertions |
| `android-snapshots` | `TESTING.md` §8.2 | Roborazzi verification against committed baselines (DEC-034) |
| `ios-verify` | `TESTING.md` §8.3 | iOS build, unit tests, SwiftUI/state-holder tests and swift-snapshot-testing baselines (DEC-025) |
| `static-analysis` | `GUIDELINES.md`, `TESTING.md` §14 | ktlint, detekt, Android Lint, SwiftLint, swift-format (DEC-032) |
| `dependency-analysis` | `GUIDELINES.md` | dependency-analysis, exact pinning, no dynamic version ranges (DEC-032, DEC-037, `REQ-NFR-006`) |
| `contract-fixture` | `TESTING.md` §11 | The API contract suite in fixture/replay mode inside the gate (DEC-054) |
| `android-assemble` | `README.md` §8 | The Android build the change affects (`DEFINITION.md` §3, D1) |
| `a11y-checks` | `TESTING.md` §9.1 | Automated accessibility assertions (`TEST-A11Y-001`…`006`, DEC-023), token parity (`TEST-UNIT-035`, DEC-022) and copy-key parity (`TEST-UNIT-036`, DEC-020) |
| `milestone-independence` | `TESTING.md` §14 | The Android milestone builds and is releasable without the iOS app (`TEST-UNIT-019`, `REQ-PLAT-004`, DEC-040) |
| `quarantine-tests` | `TESTING.md` §15 | Quarantined cases only; non-blocking, never cited as evidence, and the only permitted exclusion from the required set |

Notes that keep the list honest:

- **The check names are provisional until the workflow files land.** The authoritative definitions are `DEFINITION.md` §3/§7 and `TESTING.md` §14; where a workflow job name diverges from this table, `TESTING.md` and the workflow are the truth and this table is corrected in the same change.
- **Branch protection is a repository setting and therefore a human action** (DEC-049). The required-check set above `MUST` be configured as required status checks on `main`, each named individually; until that configuration exists the gate is a convention that reviewers and authors honour, not an enforced one, and an agent `MUST NOT` change repository settings.
- **No retry-to-green.** Automatic retries are forbidden on the blocking gate, and re-running a failing check until it passes is not an acceptable response (`TESTING.md` §15). A test that fails intermittently is quarantined with an owner and a deadline, with the justification recorded, and is excluded from the required set only on that basis.
- **Both platform runners are mandatory.** The Android suites and the macOS runner that executes the iOS suites are required on every pull request; the cost of the macOS runner is accepted (DEC-054). A job that is skipped for cost, or absent, is a missing required check, not a pass.
- **The live-network contract job is not in this list.** It stays a separate scheduled job that is a signal, not a merge blocker; the gate's contract run uses fixture/replay mode, so the required set never depends on an unversioned live service (`TESTING.md` §11, DEC-054).
- **Performance measurement** is a scheduled, non-blocking job; the budget assertion that can run deterministically is part of the shared tests (`TESTING.md` §10, `PERFORMANCE.md`).
- The red commit of a TDD sequence fails by design; the gate evaluates the final state of the pull request (§5.4).

### 5.4 Approval and merge

- A pull request is approvable only when every required check is green on the final state of the pull request and at least one human review has approved it (§8). The TDD red commit is expected to fail and is not a violation: DEC-054 evaluates the final state of the pull request, not each individual commit.
- The gate is not waived by re-running it. A gate that cannot be met follows the escalation path in `DEFINITION.md` §8.
- **Merging is human-only** (DEC-049). An agent may open the pull request, respond to review and push further phase commits, but not merge.
- After merge, the branch is deleted and `main` `MUST` build (§2).

## 6. Testing requirements for a contribution

[`TESTING.md`](TESTING.md) owns the strategy, layers, tooling, ids and traceability; [`DEFINITION.md`](DEFINITION.md) §3 owns the gate. This section states only **which layer is expected for which kind of change**, so a contribution is planned against the right test set.

| Kind of change | Expected layer | Notes |
| --- | --- | --- |
| Shared logic — mappers, response cache, pager, `ApiFailure` mapping, formatters | `commonTest` of the owning `:core:*` or `:feature:*` module | The bulk of the suite (`TESTING.md` §2); the red test is written here first |
| A shared `IC-###` contract, or its implementation | Contract test in the owning module's `commonTest` | `TESTING.md` §3.1; a contract change updates the contract document in the same change (D7) |
| Remote decoding, mapping and error semantics | `TEST-CONTRACT-###` in fixture mode with committed JSON fixtures | `TESTING.md` §4, §11; no test performs real network I/O (`REQ-REL-004`) |
| Cache and persistence behaviour, including `expect/actual` stores | Shared contract suite plus platform unit tests | `TESTING.md` §6; injected clock and `TestDispatcher` only (`TESTING.md` §5) |
| Platform behaviour — Android ViewModel, iOS `ObservableObject`, dispatcher and lifecycle seams | That platform's unit test source set | `TESTING.md` §2 |
| UI behaviour and screen state | Compose semantics assertions (Android) / state-holder tests (iOS) **before** any snapshot | A snapshot is evidence of a rendered state, not a substitute for a behaviour test (`TESTING.md` §8.2) |
| A visual state — loading, empty, stale, error, partial data | A semantics or state test first, then a committed snapshot baseline | Baselines are recorded only once the state is correct (`TESTING.md` §8.2, §8.3) |
| Accessibility-relevant UI | Automated assertions plus the recorded manual checklist | `TESTING.md` §9; DEC-023 |
| A defect fix | A regression test in the layer that failed, failing before the fix and passing after | `TESTING.md` §1, P5; where impractical, the bug report records the manual reproduction instead |
| Pure documentation, build/CI configuration, tooling | No new test required | The explicit TDD exception (§3.1); the change still passes the static-analysis and dependency checks |

Rules that apply to every contribution:

- Tests are deterministic: no wall-clock time, no real network I/O, no real device clock. Use the injected clock and test dispatchers (`TESTING.md` §1, P6).
- Tests assert behaviour, boundaries, invariants, transitions, precedence or error outcomes. Tautologies, mock echoes, "did not throw" assertions, source-text assertions and snapshotting an unconfigured tree are defects, and an existing test that pins incidental behaviour `MUST` be deleted rather than re-pinned (`TESTING.md` §1, P1–P4, P8).
- A new test id is allocated in the `TEST-*` namespace and the requirement→test row in `TESTING.md` is updated in the same change (`DEFINITION.md` §3, D12).
- A test that cannot be made deterministic is quarantined with an owner, a tracking issue and a deadline of at most 14 days; it is never silently retried, skipped without a record, or cited as evidence while quarantined, and quarantine is the only permitted exclusion from the required set (`TESTING.md` §15).
- Test names carry the `TEST-###` id as their first token (`TESTING.md` §13.2), so traceability is greppable.

## 7. Documentation requirements

Documentation is part of the change, not a follow-up: the owning document is updated in the same pull request that alters the behaviour it describes (DEC-046). The documentation gates are `DEFINITION.md` §3 (D5–D12) and §6.

| Kind of change | Documents that `MUST` change in the same pull request |
| --- | --- |
| A new or changed requirement | `REQUIREMENTS.md` (the `REQ-*` id and its `AC-*` criteria), `BACKLOG.md` (the `TASK-###` row), `TESTING.md` (the requirement→test traceability row) |
| A new or superseded architectural choice, module boundary, public contract, tooling decision or scope change | An ADR in `docs/adr/` (when architecturally significant) plus the `DEC-###` row in `DECISION_BOARD.md` |
| A shipped feature | Every specification that describes it: `UI_SPEC.md` for a visual or interaction surface, `API_SPECS.md` for the remote contract, `CONTRACTS.md` for an internal interface, `ERROR_FLOW.md` for a failure path, `OBSERVABILITY.md` for a log or diagnostic surface — whichever the feature touches (DEC-046) |
| A change to a module, dependency or build interface | `DESIGN.md` §3 and the version catalog justification; also `README.md` if the documented commands or layout change |
| A change to the gate, test layer or tooling | `DEFINITION.md` or `TESTING.md` respectively, per which one owns the rule |
| Any change whose motivation is decision-relevant — a decision, a scope change, a contract change, a milestone result | `PROJECT_LOG.md`, one `LOG-####` entry (`DEFINITION.md` §3, D11) |
| Any change that touches this process | This file, and the `DEC-###` row that authorises it |
| A user-visible string change | The canonical copy key list and both platform resource files, per `UI_SPEC.md` and DEC-020 |
| Repository-level documentation entry points | `README.md`; `README.es.md` only when `README.md` changes (DEC-047) |

Every document header carries `Status:`, `Last verified:`, `Owner:`, `Authoritative for:` and `Inputs:` (`../AGENTS.md` §10, `DEFINITION.md` §6, DOC1). A document is refreshed only when its content changes: do not bump `Last verified` mechanically.

## 8. Review process

### 8.1 What a reviewer checks

| Area | What the reviewer verifies |
| --- | --- |
| Correctness | The change does what the requirement says, in the specified failure and edge conditions — not merely that the tests pass. The reviewer reads the code against `REQUIREMENTS.md` and `ERROR_FLOW.md` rather than against the diff summary. |
| Requirement traceability | Every behaviour in the change is traceable to a `REQ-*`/`AC-*` pair, and the pull request names the `TASK-###`, `TEST-###` and `DEC-###`/`ADR-####` ids involved. An untraceable behaviour is either scope that was not agreed or a missing requirement row. |
| Test quality | The tests assert behaviour, not implementation detail: no tautology, no mock echo, no "did not throw", no snapshot of an unconfigured state (`TESTING.md` §1). The reviewer checks that the red phase was observed, not just that the suite is green. |
| TDD evidence | The phase commits exist in order, the red commit's message states the observed failure, and the green commit makes exactly that test pass. The final state of the pull request is what the gate evaluated (§5.4). |
| Documentation | Every impacted document named at Ready (R5) is updated in the same pull request, links resolve, identifier schemes are respected, and no normative statement is duplicated across two files. |
| Security-sensitive patterns | Anything that touches networking, decoding, storage, logging or permissions is reviewed against `SECURITY.md` §12, and is additionally reviewed by the Security Reviewer role (`SECURITY.md` §12.4). Treat every API value as untrusted; no `!!`, unchecked cast or `require` on remote input; no secret, no permission added without a decision, and no logging of search text, response bodies or stack traces. |
| Process hygiene | Branch name, commit types and phase mapping, one logical change, no unrelated cleanup, no placeholder or commented-out code, no generated or build output committed. |

The reviewer reports findings against the file and line, or against the identifier, and states a recommendation rather than an unexplained objection (`../AGENTS.md` §6). Style findings that a tool enforces are not raised as prose: the tool decides (`GUIDELINES.md`), and the reviewer runs it.

### 8.2 Turnaround expectation

A reviewer `SHOULD` respond to a pull request within two working days, and a pull request `SHOULD` be reviewed in the order it becomes ready. This is a **norm for a small team, not a promise, and not a gate**: the repository currently has a single author, so a review may be delayed, and a delay does not authorise merging without review or bypassing a required check. A pull request that cannot be reviewed in the norm's window `MAY` be explicitly marked as awaiting review; it is never merged while a required check is red (§5.3).

### 8.3 Resolving disagreements

| Disagreement | Route |
| --- | --- |
| The change is correct but the reviewer would design it differently | Discuss in the pull request. If the difference is consequential and architectural, it needs an ADR before it is implemented. |
| The disagreement is about an architectural, module-boundary, contract, tooling or scope decision | Write an ADR in `docs/adr/` and add the `DEC-###` row to `DECISION_BOARD.md`; an accepted ADR is immutable and is superseded by a new ADR rather than rewritten (`../AGENTS.md` §6). |
| The disagreement is about process, a gate, or the correct interpretation of a document | Escalate per `DEFINITION.md` §8 and the escalation rules in `../AGENTS.md` §6; a gate that is wrong is fixed in its owning file, not worked around. |
| Two authoritative documents contradict each other | Stop, record the conflict in `DOCUMENTATION_AUDIT.md` with a `CONF-###` id, severity and the blocked artifact, and escalate. Never resolve a conflict silently (`../AGENTS.md` §6, §12). |
| The requirement itself is contested | `assessment.md` wins over every other source; a product-scope conflict requires a human decision (`../AGENTS.md` §12). |

A decision reached in a conversation is not binding until it is recorded in the owning document and, where required, in the decision board.

## 9. AI-agent contributions

Agents are first-class contributors to the repository, with a hard permission boundary (DEC-049, `../AGENTS.md` §4.3).

| An agent MAY | Human-only — an agent `MUST NOT` |
| --- | --- |
| Read any file; run the documented build, test and analysis commands | Merge a pull request |
| Create and edit files, including the authoritative document for a topic it changed | Create a tag or publish a release |
| Create a branch and open a pull request (§5) | Change repository settings, branch protection, CI credentials or secrets |
| Push further phase commits to its own pull request in response to review | Force-push or rewrite pushed phase commits (§3.1) |
| Record an escalation with options and a recommendation (`../AGENTS.md` §6) | Delete or rewrite another author's work to make its own change easier |

While the project is in its documentation phase, agent writes are limited to `docs/**`, `README.md`, `README.es.md` and `AGENTS.md` (`../AGENTS.md` §4.3). That restriction lifts when the build exists, and it does not relax any other rule here.

An agent-authored pull request `MUST` state, in the description and without prompting:

1. **which documents it touched**, and why each one was in scope;
2. **which checks it actually ran**, each as a command plus the observed result;
3. **what it did not verify**, explicitly, when something could not be executed;
4. the **TDD exception** it used, if any, and its class (§3.1).

An agent `MUST NOT` claim a result it did not observe: never state that a build, test, benchmark or app run succeeded unless the command was executed and its output observed (`../AGENTS.md` §4.2, §7). "Not verified" is an acceptable and expected statement; an unverified success claim is a defect in the contribution and a review blocker. This applies to generated prose, commit messages, pull-request descriptions and documentation equally.

## 10. Reporting a security vulnerability

The block below is **duplicated deliberately** from [`SECURITY.md`](SECURITY.md) §10 so that a contributor reading only this file finds the route, and so the two copies are identical (`REQ-SEC-007`, `AC-REQ-SEC-007-1`). If the two ever diverge, **`SECURITY.md` §10 is authoritative** and this copy is corrected in the same change. The route is reproduced verbatim and `MUST NOT` be paraphrased or re-scoped here.

> ### Reporting a vulnerability
>
> Report a suspected vulnerability **privately**. Do not open a public issue, discussion or pull request, and do not put details in a commit message, branch name or PR title.
>
> **Route:** use GitHub's Private Vulnerability Reporting form on this repository — open the **Security** tab and choose **Report a vulnerability**. This opens a private advisory visible only to you and the maintainers.
>
> **Include:** what you found and where (file, endpoint or screen); the affected version, tag or commit; the platform and OS version; the steps to reproduce; what you observed and what you expected; whether any part of it is already public; and how you would like to be credited.
>
> **Response:** the maintainer will try to acknowledge a report within 3 working days and will say whether it is accepted as a finding. This project has no bug-bounty programme and promises no remediation timeline. A report that is accepted becomes a row in the advisory register in `SECURITY.md` §11.
>
> **Disclosure:** keep the report private until a fix has been released. There is no fixed embargo period; the maintainer will agree one with you in the private advisory.
>
> **Scope:** this repository ships two read-only mobile clients for a public API. It has no server, no accounts and no user data. A report about the Rick and Morty API service itself belongs with the API's own maintainers, not here.

Two consequences for this process, stated here because they change what a contributor does:

- A security fix is never prepared in the open. The private advisory is the working surface; the public pull request that lands the fix describes the mitigation, not the exploit.
- Private Vulnerability Reporting being enabled is an assumption on the repository owner's side, not a verified fact — see the assumption note in `SECURITY.md` §10. Enabling it is a repository-settings change, and therefore human-only (§9).

## 11. Contribution completion criteria

A contribution is complete when the Definition of Done in [`DEFINITION.md`](DEFINITION.md) §3 holds for that change. Use this checklist as a pointer, not as a substitute:

- [ ] D1–D4 — the affected platforms build; every required check (§5.3) is green on the final state of the pull request; the TDD red/green/refactor evidence exists, or the exception is stated.
- [ ] D5–D6 — static analysis, formatting and dependency analysis are clean; every dependency is justified and pinned.
- [ ] D7–D8 — contracts and every impacted document are updated in the same change.
- [ ] D9–D11 — links resolve, no placeholder remains, `PROJECT_LOG.md` is updated when the change is decision-relevant.
- [ ] D12–D14 — traceability holds, no quarantine without a recorded justification, no unrecorded waiver.

The change-level completion rule in `../AGENTS.md` §11 governs if the two ever differ. If any item cannot be met, the work is not done: state precisely what is missing, what was tried, and what is needed — do not present a partial change as complete, and do not reduce scope without an approved decision.

## Change log

| Date | Change | Decision |
| --- | --- | --- |
| 2026-09-29 | Created: setup and prerequisites, trunk-based branching with the allowed branch types, the TDD phase-and-commit protocol with rebase-merge policy, Conventional Commits and release-note derivation, the issue workflow against `BACKLOG.md`, pull-request expectations with the required-check list, per-change testing and documentation requirements, the review process, the agent permission boundary, the mirrored vulnerability-reporting route and the completion checklist. | DEC-041, DEC-042, DEC-043, DEC-044, DEC-046, DEC-048, DEC-049, DEC-051, DEC-052, DEC-053, DEC-054 |
