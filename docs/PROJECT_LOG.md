# PROJECT_LOG.md — Project Event Log

- **Status:** Active. Entries `LOG-0001`…`LOG-0015` record pre-audit documentation work that was never executable and was not verified; see §1.3.
- **Last verified:** 2026-09-29
- **Owner:** Documentation Maintainer (see `AGENTS.md` §3.9)
- **Authoritative for:** the chronological record of *why* the project changed — one entry per meaningful event, with the event, its rationale, the artifacts it touched, the decision it belongs to, and what was actually verified. Identifier scheme: `LOG-####`.
- **Not authoritative for:** the current status of a decision (`DECISION_BOARD.md`), the current state of work (`BACKLOG.md`), requirement or contract content (`REQUIREMENTS.md`, `CONTRACTS.md`), release notes (GitHub Releases).
- **Inputs:** the repository commit history on `main` (14 commits, all dated 2026-09-29; `main` was the only branch until the current documentation change, which is made on `docs/documentation-system`), plus `assessment.md`, `docs/REQUIREMENTS.md`, `docs/DESIGN.md`, `docs/API_SPECS.md`, `docs/UI_SPEC.md`, `docs/DECISION_BOARD.md`, `docs/DOCUMENTATION_AUDIT.md`.

## 1. What this log is

### 1.1 Rules

- One entry per **meaningful event**: a change of scope, an architectural or tooling decision, an audit, a correction of a documented fact, or a merged deliverable step.
- The log records **why**, not what. It is not a duplicate changelog (DEC-042: there is no `CHANGELOG.md`) and not a task tracker (DEC-044: `docs/BACKLOG.md` plus GitHub Issues carry task state).
- The log is **append-only**. An entry is never rewritten; a correction of an earlier entry is a new entry that references it. Rationale for a decision lives in `docs/adr/`; this log only records that the decision was taken and why it was needed.
- Every entry states what was **observed**, not what was assumed. `Not verified` means exactly that; it is not a placeholder.
- `LOG-####` identifiers are allocated in order and never reused or renumbered.

### 1.2 Entry fields

| Field | Meaning |
| --- | --- |
| `LOG-####` and date | Identifier in the scheme above; ISO 8601 date (`YYYY-MM-DD`). |
| Event | What happened, in one or two sentences. |
| Rationale | Why it happened — the problem it solved or the constraint that forced it. |
| Affected artifacts | Documents (or, from M1, code) touched or consequently constrained. |
| Decision / ADR reference | The `DEC-###` and/or `ADR-####` the event belongs to, or an explicit note that it predates the register. |
| Validation | What was actually checked, by what method, and what was **not** checked. |

### 1.3 Reconstruction and honesty note

`LOG-0001`…`LOG-0015` were reconstructed on 2026-09-29 from the repository's commit history and from the documents those commits produced; they are the only events the history records. Rationales there describe the purpose the change served, derived from the resulting documents; where a motivation is not stated by either source it is marked `[INFERENCE]`.

The repository has never contained source code, a Gradle build, a CI workflow or a `.gitignore`. Consequently, no event before `LOG-0016` was **executed or verified**: those entries describe the documentation of intended behaviour, not verified implementation, and their `Validation` field says so individually. The first live verification of anything in this project was the set of API probes in `LOG-0016`.

Documentation is written against a **target** state (DEC-046). Where this log says a document "specifies" something, it does not imply that the thing exists.

### 1.4 What this log does not contain

- **No per-commit history.** The log groups the 14 pre-audit commits into one entry per meaningful event. The commit-by-commit record is the Git history itself (`git log` on `main`), which is the only place it exists; there is no `CHANGELOG.md` (DEC-042) and release notes are generated from history at release time.
- **No task state.** The log never records whether work is planned, in progress or done. Task state lives in `docs/BACKLOG.md` (canonical index, `TASK-###` ids) and in GitHub Issues (DEC-044).
- **No current decision status.** An entry records that a decision was taken and why; whether it is `Accepted`, amended or superseded today is owned by `docs/DECISION_BOARD.md`.
- **No normative content.** Requirements, contracts, remote behaviour, visual values, error copy, budgets and gates are restated nowhere here; each is referenced by the identifier or section of the file that owns it.
- **No test or requirement traceability.** Which test proves which requirement is owned by `docs/TESTING.md` and `docs/DOCUMENTATION_AUDIT.md` §6.

## 2. Log

### LOG-0001 · 2026-09-29 · Repository initialised

- **Event:** The repository was created and its first commit added `README.md`.
- **Rationale:** The assessment deliverable has to be reviewable as version-controlled history rather than a snapshot, so the repository exists before its content.
- **Affected artifacts:** `README.md`.
- **Decision / ADR:** None — predates the decision register.
- **Validation:** Not verified — repository initialisation only; no executable artifact.

### LOG-0002 · 2026-09-29 · Assignment added to the repository

- **Event:** `assessment.md`, the ZARA mobile assignment text, was committed.
- **Rationale:** The assignment had to become a repository artifact rather than external context, because every later document traces to it (`docs/REQUIREMENTS.md` §4).
- **Affected artifacts:** `assessment.md`; later `docs/REQUIREMENTS.md` §4 (traceability table) and `AGENTS.md` §2 (precedence).
- **Decision / ADR:** The precedence rule that `assessment.md` overrides everything on conflict.
- **Validation:** Not verified — source document only. Observation carried forward: the file is truncated at l.4 and l.10, recorded as `CON-003`, and its intent is stated rather than guessed.

### LOG-0003 · 2026-09-29 · Initial documentation set added

- **Event:** The first specification set was committed at the repository root: `AGENTS.md`, `API_SPECS.md`, `DESIGN.md`, `REQUIREMENTS.md`, `UI_SPEC.md`.
- **Rationale:** The assignment needed an explicit specification baseline and operating rules for AI agents before any code was written.
- **Affected artifacts:** Those five files; the later `docs/` set replaced their content in place.
- **Decision / ADR:** None — predates the register. `REQUIREMENTS.md` was a 27-line draft, rewritten in `LOG-0017`.
- **Validation:** Not verified — specifications only; no build, test or app run existed then or now.

### LOG-0004 · 2026-09-29 · API specification expanded to a full REST and GraphQL contract

- **Event:** `API_SPECS.md` grew from a short endpoint list into the full contract: purpose and scope, protocol comparison, shared domain types, endpoint map, pagination, DTOs, error taxonomy and mapping, caching policy, verification strategy and contract-test lists.
- **Rationale:** The committed extras — error handling and response caching — cannot be specified without a dated remote contract and an explicit choice of protocol.
- **Affected artifacts:** `API_SPECS.md`; later `docs/DECISION_BOARD.md` §3.
- **Decision / ADR:** The pre-audit document recommended Retrofit/OkHttp and left the protocol and cache policy open; DEC-011 (Ktor 3.6.0 as the single remote stack) and DEC-012 (24 h / 7 d / 30 d freshness) settled both.
- **Validation:** Not verified at the time — the live-API facts this document now carries were probed only in `LOG-0016`, which also corrected one pre-audit claim.

### LOG-0005 · 2026-09-29 · Specification documents moved into `docs/`

- **Event:** `API_SPECS.md`, `DESIGN.md`, `REQUIREMENTS.md` and `UI_SPEC.md` were moved from the repository root to `docs/` as pure renames, with links updated in the same commit.
- **Rationale:** The deliverable's entry points (`README.md`, `assessment.md`, `AGENTS.md`) grew alongside the specification set; separating them keeps the root readable and the specifications together.
- **Affected artifacts:** The four moved files; the path convention now stated in `README.md` §5.
- **Decision / ADR:** None formal — a layout convention.
- **Validation:** History records the moves as R100 (content-identical renames). No link check was run at the time and none is re-run here.

### LOG-0006 · 2026-09-29 · Design briefs added for Figma generation

- **Event:** `docs/design/01 · Android — M3 Expressive.md` and `docs/design/02 · iOS — Liquid Glass.md` were added.
- **Rationale:** The two native design languages had to be described in writing before the Figma file could be generated, and the generated file then had to be checked against those briefs.
- **Affected artifacts:** `docs/design/*`; later `docs/UI_SPEC.md` §1 and `AGENTS.md` §3.4.
- **Decision / ADR:** DEC-001 (staged dual-native delivery) is why there are two briefs and not one. The briefs are historical inputs and are **not** normative where `docs/UI_SPEC.md` decided otherwise.
- **Validation:** Not verified — prompt documents; nothing executable.

### LOG-0007 · 2026-09-29 · UI and architecture specifications aligned with the Figma designs

- **Event:** `UI_SPEC.md` was rewritten against the generated Figma file — file map with page and node identifiers, tokens, typography, effects, per-platform component specifications, imagery and dynamic colour, screen behaviour, motion, required states, accessibility — and `DESIGN.md` was rewritten around a Kotlin Multiplatform layout with a shared UI-state contract, navigation, token pipeline, portrait accent colour, favorites, Koin DI, a class diagram and failure-to-UI mapping.
- **Rationale:** A Figma file is not an implementable specification; both native clients need stated values and stated behaviour, and the shared/native split had to be fixed before modules could be planned.
- **Affected artifacts:** `docs/UI_SPEC.md`, `docs/DESIGN.md`.
- **Decision / ADR:** Laid the ground for DEC-001, DEC-013 and DEC-019. Note that DEC-019 (the eight-module layout) has since been **superseded by DEC-052** (`LOG-0020`).
- **Validation:** Not verified — no client exists. Figma-to-implementation parity remains manual (DEC-024).

### LOG-0008 · 2026-09-29 · App icons, shared Figma page and single appearance documented

- **Event:** The shared `Brand & Sample Data` page, the Android adaptive launcher icon and the iOS Liquid Glass app icon were documented, and the app was fixed to a **single appearance** — one fixed palette, no light/dark variants — and recorded as a non-functional requirement in `REQUIREMENTS.md`.
- **Rationale:** Icon assets must not be improvised independently per platform, and one appearance means one token set, one accent-resolution rule and one screenshot baseline instead of two of each.
- **Affected artifacts:** `docs/UI_SPEC.md` (Figma map, tokens, app icons, accessibility), `docs/DESIGN.md`, `docs/REQUIREMENTS.md`.
- **Decision / ADR:** DEC-022 (tokens checked against a committed export), DEC-024 and DEC-034 (committed screenshot baselines). `[INFERENCE]` The single-appearance requirement exists to keep the two clients visually identical and testable with one baseline per screen.
- **Validation:** Not verified — specification only.

### LOG-0009 · 2026-09-29 · Discovery top bars simplified and status filters aligned

- **Event:** The Android app bar was reduced to the search bar (menu icon and avatar removed), the iOS trailing toolbar buttons were removed, and both platforms were given the same single-select status filter — All, Alive, Dead, Unknown (Android filter chips, iOS segmented control). The Species and Gender filters were dropped.
- **Rationale:** `[INFERENCE]` Status is the only filter whose domain is fixed by the API, and the removed controls had no function in a read-only client on a portrait-only layout.
- **Affected artifacts:** `docs/UI_SPEC.md` (app bar, chips, content order, filter mapping), `docs/DESIGN.md` (`CharacterFilter` and the list intents).
- **Decision / ADR:** DEC-027 (phone portrait only); the surviving filter is `REQ-FUNC-004`.
- **Validation:** Not verified — specification only.

### LOG-0010 · 2026-09-29 · Voice search documented on both platforms

- **Event:** The Android search bar gained a trailing microphone button matching iOS; the shared behaviour (the dictated transcript replaces the query and then follows the normal debounce), the per-platform implementation (Android `RecognizerIntent` without `RECORD_AUDIO` plus a `<queries>` entry; iOS Speech framework with usage descriptions) and the accessibility labels were documented, and voice search was added to `REQUIREMENTS.md` as a Could-have.
- **Rationale:** The feature was drawn in the design, and documenting the permission-free Android path kept the privacy surface minimal.
- **Affected artifacts:** `docs/UI_SPEC.md`, `docs/DESIGN.md`, `docs/REQUIREMENTS.md`.
- **Decision / ADR:** Reversed in scope later the same day by DEC-002: voice search is deferred as `DEF-001` / `REQ-FUNC-030`, and no microphone or speech permission may be requested (`REQ-SEC-004`).
- **Validation:** Not verified — no implementation then or now; the behaviour documented here is a **deferred item**, not a target.

### LOG-0011 · 2026-09-29 · Specifications synced with Figma: card data, splash rotation, shared copy

- **Event:** Discovery card content was fixed to photo, full name (up to two lines), status and species, with species read from the API `species` field and never from `type` (the Android card property `Meta` and `DESIGN.md`'s `CharacterCardUi.meta` were renamed to `species`); Discovery copy was unified across platforms; the splash was redefined as a clockwise rotation with an accelerating ease-in (360° in 1.2 s, `cubic-bezier(0.32, 0, 0.67, 0)`); the iOS "Appears in N episodes" line became informative rather than a button and the Android "About" heading was removed.
- **Rationale:** The two prototypes disagreed on card fields and copy, and implementation cannot start from two sources of truth; the API guarantees `species` and not `type`.
- **Affected artifacts:** `docs/UI_SPEC.md`, `docs/DESIGN.md`.
- **Decision / ADR:** DEC-015 (shared UI-state data classes, formatters and copy keys), DEC-020 (canonical key list plus parity test).
- **Validation:** Not verified — specification only.

### LOG-0012 · 2026-09-29 · Favorite states, shared sample characters and splash loader resolved

- **Event:** Favorite was specified as unmarked by default on both platforms (Android outline heart FAB, iOS Glass + heart) and filled when marked; both prototypes were set to the same six characters in the same order; and the splash loader was resolved — the rotating portal **is** the loading indicator, accelerating for 1.2 s and then holding constant until data arrives, with a Reduce Motion fallback (opacity pulse) and indeterminate-progress accessibility semantics.
- **Rationale:** An unmarked/marked pair and a loading affordance had to be unambiguous for two independent implementations, and animated feedback needs a non-animated path that states the same thing (`REQ-UX-003`…`REQ-UX-007`).
- **Affected artifacts:** `docs/UI_SPEC.md` — this **supersedes** the earlier "no loading indicator" wording recorded in `LOG-0011` — and its motion and accessibility sections.
- **Decision / ADR:** DEC-023 (automated accessibility checks plus manual checklist), DEC-024 (committed snapshot baselines).
- **Validation:** Not verified — specification only.

### LOG-0013 · 2026-09-29 · Episodes, Locations and Favorites placeholder screens documented

- **Event:** The Figma placeholder screens for the three remaining tabs were documented with identical copy on both platforms and platform-specific presentation: Android empty state plus selected navigation item; iOS glass empty state, a new glass text button, and the glass tab bar defined as a variant set keyed by the selected tab. `UI_SPEC.md` gained the screens, the components and a new §6.4; `DESIGN.md` gained the tab routes and the placeholder behaviour.
- **Rationale:** Four destinations are required (`REQ-FUNC-008`), so each tab needs a defined, designed destination rather than an undefined route; Favorites must also show an empty state until the user has favourites.
- **Affected artifacts:** `docs/UI_SPEC.md` §1, §6.4; `docs/DESIGN.md` (navigation).
- **Decision / ADR:** DEC-005 (Episodes and Locations ship as placeholders), DEC-004 (favorites in the MVP).
- **Validation:** Not verified — specification only.

### LOG-0014 · 2026-09-29 · iOS glass tab bar unselected-tab colour specified

- **Event:** Unselected tabs in the iOS Liquid Glass tab bar were specified as white.
- **Rationale:** `[INFERENCE]` The tab bar is a glass surface over dynamic content, so the unselected label colour cannot be left to the platform default without a contrast and consistency guarantee; one value is pinned for the glass path and its material fallback.
- **Affected artifacts:** `docs/UI_SPEC.md` (iOS component specification, tab bar).
- **Decision / ADR:** DEC-008 (Liquid Glass with a material fallback), DEC-022 (hand-written tokens).
- **Validation:** Not verified — specification only; contrast is to be proven by the accessibility checks of DEC-023 once a client exists.

### LOG-0015 · 2026-09-29 · Pull request #1 merged

- **Event:** The documentation branch `docs/figma-designs` was merged into `main` as PR #1 with a merge commit, carrying the design briefs and the Figma-alignment work (`LOG-0006`…`LOG-0014`). `main` is the only branch; the repository then held 14 commits.
- **Rationale:** DEC-041 fixes trunk-based development with pull-request review; the Figma-alignment work was one reviewable unit.
- **Affected artifacts:** `main` history; `docs/DESIGN.md`, `docs/UI_SPEC.md`, `docs/design/*`.
- **Decision / ADR:** DEC-041, which is **amended** by DEC-053 (`LOG-0021`): trunk-based development and Conventional Commits stand, squash merge does not.
- **Validation:** The merge is present in repository history. No CI check ran — the repository has no workflows, and none existed at that time.

### LOG-0016 · 2026-09-29 · Documentation audit and decision interview

- **Event:** The whole documentation set was audited and a decision interview concluded: `DEC-001`…`DEC-051` were recorded across scope, platform, toolchain, data, architecture, verification, CI, security, observability and documentation policy. Pre-audit claims that could not be verified were corrected, including the assertion that the GraphQL gateway exposes rate-limit headers on a normal query.
- **Rationale:** The pre-audit documents mixed verified facts, observations and intentions; they left the protocol, the cache policy and the toolchain open, and named libraries that cannot serve a shared Kotlin data layer. Implementation could not start from that baseline without deciding each of those points.
- **Affected artifacts:** Every document in `docs/`, plus `docs/DECISION_BOARD.md` and `docs/DOCUMENTATION_AUDIT.md`.
- **Decision / ADR:** `DEC-001`…`DEC-051`; architecturally significant ones carry ADRs in `docs/adr/`.
- **Validation:** Live API probes were executed on 2026-09-29 and their results are recorded with that date in `docs/API_SPECS.md` §1.1: list totals and page size, filtered-empty `404` that is itself cacheable and immutable, detail `404` immutable, out-of-range page `404`, batch requests returning only existing resources, single-object versus array responses, GraphQL null-for-missing, `info.next` as `Int`, GraphQL empty-filter shape, and `400 GRAPHQL_VALIDATION_FAILED` for an unknown field. Toolchain versions were checked on 2026-09-29. No build, test or app run was performed — none is possible in this repository. Reported contradiction: `docs/API_SPECS.md` §9 still states that the gateway "exposes rate-limit headers", which is the pre-audit wording the probes corrected; this log reports it rather than editing a document it does not own.

### LOG-0017 · 2026-09-29 · `REQUIREMENTS.md` rewritten with stable identifiers

- **Event:** The 27-line draft was replaced by the current document: stable identifiers (`REQ-FUNC-###`, `REQ-NFR-###`, `REQ-PLAT-###`, `REQ-UX-###`, `REQ-REL-###`, `REQ-SEC-###`, `REQ-OBS-###`), `AC-<REQ-ID>-n` acceptance criteria, MoSCoW priorities rebuilt against `assessment.md`, scope, non-goals, deferred items, platform, UX, security and observability requirements, constraints, risks and assessment traceability.
- **Rationale:** Requirement identifiers are the join key for every other document — tasks, tests and contracts reference them — and a prose draft cannot carry that.
- **Affected artifacts:** `docs/REQUIREMENTS.md`.
- **Decision / ADR:** DEC-046 (documentation rule), DEC-002, DEC-004, DEC-007 (scope).
- **Validation:** Documentation review only (identifier uniqueness and cross-references). No test exists.

### LOG-0018 · 2026-09-29 · `DECISION_BOARD.md` created

- **Event:** The decision index was created: one row per decision with category, status, urgency, blocking impact, ADR and source, plus sections for superseded and rejected alternatives, deferred decisions, and a change log.
- **Rationale:** Decisions were scattered across documents with no single statement of their status, so no reference could resolve reliably and rationale risked being restated in several places.
- **Affected artifacts:** `docs/DECISION_BOARD.md`; every document that references a `DEC-###`.
- **Decision / ADR:** DEC-046; rationale stays in `docs/adr/`, the board owns status only.
- **Validation:** Documentation review only — cross-reference of `DEC-###` identifiers across the set. No test exists.

### LOG-0019 · 2026-09-29 · Documentation system established

- **Event:** The second documentation set was authored as one change on the branch `docs/documentation-system`: `CONTRACTS.md`, `ERROR_FLOW.md`, `TESTING.md`, `SECURITY.md`, `OBSERVABILITY.md`, `PERFORMANCE.md`, `DEFINITION.md`, `GUIDELINES.md`, `CONTRIBUTING.md`, `TECHNICAL_PLAN.md`, `BACKLOG.md`, `DOCUMENTATION_AUDIT.md`, `PROJECT_LOG.md` (this file), `HANDOFF.md`, `docs/adr/*`, `docs/templates/*` and `README.es.md`. It reaches `main` through a pull request merged without squashing.
- **Rationale:** Every topic needs exactly one authoritative owner before implementation starts (DEC-046, `AGENTS.md` §2), and the assignment asks how the project is structured — part of that answer is that the process itself is written down. The change is made on the branch `docs/documentation-system` and reaches `main` through a pull request.
- **Affected artifacts:** The files listed above; `README.md` and `README.es.md` document the system.
- **Decision / ADR:** DEC-036, DEC-042, DEC-044, DEC-045, DEC-047, DEC-048, DEC-049, DEC-050, DEC-051.
- **Validation:** Documentation-only and, at the time of the audit, a work in progress: the set was authored concurrently. Verification performed on this repository is limited to the checks listed in §3. No build, test, linter or application run was executed, because none can be.

### LOG-0020 · 2026-09-29 · Module layout changed to feature-per-module (DEC-052 supersedes DEC-019)

- **Event:** The repository owner replaced the eight-module layout (three shared KMP modules, one Android design-system module, one Android feature module and the iOS app) with feature-per-module plus a Clean Architecture package layout inside each feature. Core modules: `:core:domain`, `:core:data`, `:core:presentation`, `:core:designsystem` (Android), `:core:testing`. Feature modules: `:feature:discovery`, `:feature:character-detail`, `:feature:favorites`, `:feature:episodes`, `:feature:locations`. App shells: `:androidApp` and the `iosApp` target, with one Swift package per feature on iOS plus `iosApp/DesignSystem`. DEC-019 is superseded; `docs/adr/0001-module-boundaries.md` now documents DEC-052.
- **Rationale:** One module per user-facing capability makes each capability independently buildable, testable and reviewable, prevents the shared modules from accumulating feature knowledge, and mirrors the per-feature Swift package structure iOS needs. The dependency rules (a feature module must not depend on another feature module; `:core:domain` depends on nothing; `:core:data` and `:core:presentation` depend only on `:core:domain`) keep cross-feature needs in the core modules.
- **Affected artifacts:** `docs/DESIGN.md` §3 (being updated) and §0, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `AGENTS.md` §8, the build commands in `README.md`, and any document naming `:shared:*` or `:android:feature:characters`.
- **Decision / ADR:** DEC-052 supersedes DEC-019; ADR-0001 now records DEC-052.
- **Validation:** Documentation change only. No Gradle build exists, so the module graph and the dependency rules are stated, not enforced; enforcement is expected from the build and from the dependency-analysis check (DEC-032). Decision status is owned by `docs/DECISION_BOARD.md`.

### LOG-0021 · 2026-09-29 · TDD phase-and-commit protocol adopted (DEC-053 amends DEC-041)

- **Event:** Development must follow test-driven development with one commit per phase: a red commit that adds the failing test and states the observed failure (`test(<scope>): add failing test for <behaviour>`), a green commit with the minimal implementation (`feat(<scope>): <behaviour>` or `fix(<scope>): ...`), and a refactor commit (`refactor(<scope>): <change>`), after which the change is pushed. DEC-041 is amended: trunk-based development and Conventional Commits stand, squash merge is replaced by a merge that preserves the phase commits with linear history. CI runs on the pull request and evaluates its final state, not each commit; at the time of this decision that CI was the DEC-028 gate, which `LOG-0022` later superseded with DEC-054.
- **Rationale:** The deliverable is judged on engineering process as much as on the running app (DEC-003); requiring an observed red failure and an observed green pass as gate evidence makes "the tests pass" verifiable rather than asserted, and preserving the phase commits puts that evidence in history.
- **Affected artifacts:** `docs/CONTRIBUTING.md` (branching and commit section), `docs/GUIDELINES.md` (testing conventions), `docs/TESTING.md` (TDD workflow and its relation to the CI gate), `docs/DEFINITION.md` (the Done gate requires red/green/refactor evidence), `docs/TECHNICAL_PLAN.md` and `docs/BACKLOG.md` (a task is not started until its first failing test exists), and this log.
- **Decision / ADR:** DEC-053; amends DEC-041. Exception, stated explicitly when used: pure documentation, build/CI configuration and tooling changes do not require a preceding failing test. Phase commits that have been pushed MUST NOT be amended or force-pushed.
- **Validation:** Documentation change only; the protocol is not yet exercised in this repository because no code or test suite exists. Its first application is the first implementation task of M1.

### LOG-0022 · 2026-09-29 · Full both-platform CI made mandatory for every pull request (DEC-054 supersedes DEC-028)

- **Event:** The gate changed from "full Android gate on every change, iOS job only on `main` and on demand" to a mandatory blocking gate on every pull request: shared `commonTest` suites, each platform's unit tests, Compose semantics and accessibility tests, Roborazzi screenshot verification, swift-snapshot-testing, SwiftUI and state-holder tests, static analysis and formatting (DEC-032) and dependency analysis. Both platform runners are required on every pull request, including the macOS runner for the iOS suites. A pull request is not approvable while any required check is failing, skipped or absent. The live-API contract suite became part of the gate in fixture/replay mode; its live-network mode stays a separate scheduled, non-blocking signal.
- **Rationale:** The deliverable is dual-native, so a gate that defers iOS verification to `main` lets iOS regressions reach trunk unverified. The runner cost of the macOS job is accepted rather than traded against coverage. The contract suite had to be split because "all tests required" and "no network-dependent flakiness in the gate" cannot both hold against an unversioned live service.
- **Affected artifacts:** `docs/TESTING.md` (CI gate, fixture/replay mode, contract-suite split), `docs/CONTRIBUTING.md` (required-check list and approval rule), `docs/DEFINITION.md` (Done gate and release readiness), `docs/TECHNICAL_PLAN.md` (quality gates per phase; CI and the test harness precede the first feature merge), `docs/BACKLOG.md` (both workflows, fixture/replay mode, branch protection as a human task, flake quarantine), and `.github/workflows/` once it exists.
- **Decision / ADR:** DEC-054 supersedes DEC-028. The TDD protocol (DEC-053) still means the red commit fails tests by design; the gate evaluates the final state of the pull request, not each commit. Flaky tests are quarantined with an owner, a deadline and a recorded justification, never retried to green. Branch protection is a repository setting and therefore a human action (DEC-049).
- **Validation:** Documentation change only. No workflow file exists and no check has ever run in this repository. The required-check list is stated in `docs/TESTING.md`; the gate definition is owned by `docs/DEFINITION.md`. Decision status is owned by `docs/DECISION_BOARD.md`.

## 3. Verification performed on this repository

As of 2026-09-29, and for the whole lifetime of the repository, verification has been documentation-only. The checks that were run and their outcomes are recorded in `docs/DOCUMENTATION_AUDIT.md`; the classes of check that apply to this log are: internal link resolution, `LOG-####` identifier uniqueness and ordering, and consistency review against `docs/REQUIREMENTS.md`, `docs/DECISION_BOARD.md` and `docs/DOCUMENTATION_AUDIT.md`.

No build, test, lint, static-analysis, benchmark or application run has ever been executed here, because the repository contains no source code and no build files. Nothing in this log is evidence that the described software compiles, runs or behaves as specified.
