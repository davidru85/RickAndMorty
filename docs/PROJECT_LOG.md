# PROJECT_LOG.md — Project Event Log

- **Status:** Active. Entries `LOG-0001`…`LOG-0015` record pre-audit documentation work that was never executable and was not verified; see §1.3.
- **Last verified:** 2026-09-30
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

### LOG-0023 · 2026-09-30 · Settings replaces Locations; navigation bars become components; Settings gains three settings (DEC-055)

- **Event:** The repository owner redesigned the navigation in Figma: Locations was removed and Settings added as the fourth destination (Characters · Episodes · Favorites · Settings). The Android bottom bar was extracted into `Android/Navigation bar` (`117:887`, one variant per selected destination), which also fixed item colours that had fallen back to the kit's light scheme. The iOS tab bar now instances a new `iOS/Glass tab item` (`117:1369`). The former Locations screens became the Settings screens. Each platform's kit supplies the Settings controls:
  - Android (M3 Expressive): list items with a switch, a connected button group and an error-container button.
  - iOS (Liquid Glass): kit rows with a toggle, a segmented control and a destructive row button on glass panels.

  The controls are Sounds (off by default), the data source (REST API by default, or GraphQL) and "Delete favorites", which opens a confirmation frame on both platforms (`122:1293`, `123:529`). `:feature:settings` replaces `:feature:locations`; preferences persist through `AppSettingsRepository` in `:core:domain` and an `expect/actual` store in `:core:data`.
- **Rationale:** Owner directives of 2026-09-30. The owner chose "toggle only" for Sounds, so the sound set is deferred (`DEF-005`, `REQ-FUNC-036`), and chose a confirmation before a destructive, irreversible delete. The preferences live in the core modules because `:core:data` must read the protocol choice and no core module may depend on a feature (ADR-0001).
- **Affected artifacts:** Figma pages `0:1` and `4:134`; `docs/REQUIREMENTS.md` (`REQ-FUNC-008`, `REQ-FUNC-033`…`REQ-FUNC-036`, `DEF-003`, `DEF-005`, `REQ-NFR-009`), `docs/UI_SPEC.md` (§1, §4.1, §4.2, §6.4, new §6.5, §7, §8), `docs/DESIGN.md` (§3, §4.2, new §4.6, §5, §9), `docs/CONTRACTS.md` (`IC-008`, `IC-009`, `IC-013`, new `IC-021`…`IC-023`), `docs/SECURITY.md` (data inventory), `docs/TESTING.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `docs/TECHNICAL_PLAN.md`, `docs/GUIDELINES.md`, `docs/CONTRIBUTING.md`, `docs/OBSERVABILITY.md`, `docs/HANDOFF.md`, `docs/figma/README.md`, both READMEs, `docs/DECISION_BOARD.md`, `docs/DOCUMENTATION_AUDIT.md` (`CONF-34`).
- **Decision / ADR:** DEC-055, ADR-0010 (amends DEC-005 and the ADR-0001 module list).
- **Validation:** The Figma changes were checked with rendered screenshots through the Figma API: component sets, all four screens per platform and both confirmation frames. The documentation change was checked by searching the repository for stale Locations references. No build or test exists, so nothing is verified in code.

### LOG-0024 · 2026-09-30 · GraphQL ships as a user-selectable protocol through Ktor (DEC-056)

- **Event:** The Settings data-source option made GraphQL a shipped protocol. Both remote data sources implement `IC-011` and are selected per request from the stored preference. GraphQL uses hand-written documents over the single Ktor client, without Apollo. A switch resets the list to page 1 like a filter change, and cache entries stay isolated through the `protocol` key component ADR-0005 already reserved.
- **Rationale:** Owner directive of 2026-09-30 ("both via Ktor, no Apollo"). It reverses the 2026-09-29 rejection of GraphQL as the shipped protocol. The costs that justified that rejection (Apollo's generated models and normalized cache, `POST` defeating the engine cache) no longer apply to a Ktor path over the app-level cache.
- **Affected artifacts:** `docs/API_SPECS.md` (§2, §5.6, §7.2, §10.2, §14, §15), `docs/CONTRACTS.md` (`IC-011`, `IC-012`, `IC-021`), `docs/TESTING.md` (§4.3, `TEST-UNIT-048`, `TEST-UNIT-049`), `docs/DESIGN.md`, `docs/SECURITY.md`, `docs/DECISION_BOARD.md` (§3 rejection narrowed to Apollo), `docs/adr/0004-rest-client.md` (amendment pointer), `docs/DOCUMENTATION_AUDIT.md` (`CONF-33`).
- **Decision / ADR:** DEC-056, ADR-0011 (amends ADR-0004's GraphQL outcome).
- **Validation:** Documentation change only; no GraphQL request has been executed from this repository.

### LOG-0025 · 2026-09-30 · Data sources made explicit: two remote adapters plus the settings store

- **Event:** The owner restated the data-layer shape the Settings work implies: each module's data layer has two remote data sources — one for the REST API and one for GraphQL, both returning the same domain data — plus a separate data source for the datastore that persists the settings the user configures in the Settings view; the stored REST/GraphQL choice decides which remote source a request uses. The documentation already shipped this behaviour (DEC-056, `REQ-FUNC-034`) but described it in prose across several sections and still showed a single REST adapter in the architecture diagrams. This change names it once: `CONTRACTS.md` `IC-011` states the two implementations and points at `IC-022` for the settings store; `DESIGN.md` §1, §2, §4.6 and §6 show the per-request selector, the two adapters and the settings store; `API_SPECS.md` §2 lists the three-data-source inventory and the selection mechanism; ADR-0005's `protocol` key component is now `rest` or `graphql` with `POST` for GraphQL.
- **Rationale:** Owner directive of 2026-09-30. `README.md` §10 still claimed "GraphQL is documented as the alternative, not shipped", which contradicted `API_SPECS.md` §2, `IC-011`/`IC-021` and ADR-0011; the diagrams could be read as a single adapter. Stating the inventory once, in the owning documents, removes the contradiction and makes the "which source is used when" rule reviewable.
- **Affected artifacts:** `docs/CONTRACTS.md` (`IC-011`), `docs/DESIGN.md` (§1, §2, §4.6, §6, §9, §10), `docs/API_SPECS.md` (§2, §15), `docs/adr/0005-caching-strategy.md`, `README.md` §10, `README.es.md` §10, `docs/DOCUMENTATION_AUDIT.md` (`CONF-35`).
- **Decision / ADR:** DEC-055, DEC-056; ADR-0011 (amends ADR-0005's key components), ADR-0010 (the settings store).
- **Backlog:** `TASK-075` already owns the GraphQL remote data source and the runtime switch; no `TASK-###` row changed, because this change states existing behaviour rather than adding scope.
- **Validation:** Documentation change only; the repository has no build, so no code, test or app run was executed and none is claimed. Internal links in the edited documents were re-checked by inspection, and the four Mermaid diagrams in `DESIGN.md` were checked for undeclared nodes.

### LOG-0026 · 2026-09-30 · First build: the Gradle/KMP repository skeleton (TASK-014)

- **Event:** The repository became a buildable Kotlin Multiplatform project. The change adds the committed Gradle wrapper (9.7.0, distribution checksum pinned), `settings.gradle.kts`, the root `build.gradle.kts`, `gradle.properties`, a minimal `gradle/libs.versions.toml`, the included build `build-logic/` with three convention plugins, the 11 project modules of ADR-0001 as amended by ADR-0010 with their declared project edges, the five feature route declarations and a minimal `androidApp/src/main/AndroidManifest.xml`. `:androidApp` plus `:core:*` plus `:feature:*` builds with no `iosApp/` present.
- **Rationale:** TASK-014 exists to turn a documentation-only repository into something a reviewer can clone and build, and to materialise the module boundaries of ADR-0001 so that the dependency rules are declared in the build rather than asserted in prose. The edges are declared ahead of the code that will consume them, because declaring them is the value of the change.
- **Affected artifacts:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `build-logic/**`, `core/{domain,data,presentation,designsystem,testing}/build.gradle.kts`, `feature/{discovery,character-detail,favorites,episodes,settings}/**` (build script plus one `navigation` declaration each), `androidApp/**`; documentation: `AGENTS.md` §1, `README.md`, `README.es.md`, `docs/DESIGN.md` (§3 diagram, new §3.5, §10), `docs/DECISION_BOARD.md` (DEC-057, §5), `docs/DOCUMENTATION_AUDIT.md` (§5, `GAP-001`, §6.3, §7, §9), `docs/BACKLOG.md` (§2.4, TASK-014 row, §9), `docs/HANDOFF.md`, `docs/GUIDELINES.md` §1.4, `docs/TESTING.md` preamble, `docs/CONTRIBUTING.md`, `docs/CONTRACTS.md`, `docs/ERROR_FLOW.md`, `docs/PERFORMANCE.md`, `docs/SECURITY.md` (l.360), `docs/TECHNICAL_PLAN.md` (§1, current delivery state).
- **Decision / ADR:** DEC-052, DEC-055, DEC-057 (new: convention plugins in `build-logic/`, project edges declared in the consuming module's build script), ADR-0001, ADR-0002, ADR-0010. No new ADR: DEC-057 is a tooling decision.
- **Validation:** Executed on 2026-09-30 from the branch `build/gradle-kmp-skeleton`, JDK 21, Android SDK with platform 37 and build-tools 37.0.0. Observed, in this order: `./gradlew --version` → Gradle 9.7.0, Kotlin DSL, JVM 21.0.11; the SHA-256 of `gradle/wrapper/gradle-wrapper.jar` matches the checksum Gradle publishes for 9.7.0; `./gradlew projects` → exactly the 11 modules of the module table and no other; `./gradlew help --warning-mode=all` → success with no deprecation warning from this repository's scripts; `./gradlew :androidApp:assembleDebug` with no `iosApp/` present → BUILD SUCCESSFUL and the debug APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`; `./gradlew assemble` → success including the `iosArm64` and `iosSimulatorArm64` klib compilations; `./gradlew build` → success including the Android Lint and test tasks Gradle wires by default, with no Lint error; the per-module `dependencies` reports for `commonMainImplementation`, `androidMainImplementation`, `commonTestImplementation`, `implementation` and `:androidApp`'s `debugRuntimeClasspath` → exactly the edges of the module table, the only library being `kotlinx-serialization-core` in the features; `./gradlew tasks --all` → no task belonging to a prohibited target (`jvm`, `js`, `wasm`, `linux`, `mingw`, `macos`, `tvos`, `watchos`, `iosX64`); `aapt2 dump badging` → `package: name='io.github.davidru85.multiverse'`, `minSdkVersion:'26'`, `targetSdkVersion:'37'`, no `uses-permission`; a clean clone of the branch → `:androidApp:assembleDebug` succeeded with no `iosApp/` (re-run after the documentation commit); `./gradlew buildEnvironment` → AGP 9.3.1's built-in Kotlin resolves the Kotlin Gradle plugin to `2.4.20`, so `android.builtInKotlin` needs no opt-out and `org.jetbrains.kotlin.android` is correctly absent; text scans → no dynamic version, no tracked generated file, no placeholder, no forbidden library reference and 0 broken relative links across the 43 Markdown files. **Not verified:** CI does not exist (TASK-025), the iOS app and framework are not built (CONF-40), nothing is installed or launched on a device (the APK has no activity), and ktlint, detekt, Android Lint configuration, dependency-analysis and the test suites do not exist yet (TASK-017, TASK-029).

### LOG-0027 · 2026-09-30 · One iOS umbrella framework from a `:core:ios` export module (DEC-058)

- **Event:** `CONF-40` was resolved by owner decision: the iOS app links exactly one Kotlin framework, produced by a new Kotlin Multiplatform module `:core:ios` that declares the two Apple targets of ADR-0002 and one `binaries.framework` exporting the five `:feature:*` modules and the four other `:core:*` modules through `api`. ADR-0001's module set is amended by one module; ADR-0003's sharing boundary and hand-written binding are untouched; a new backlog row `TASK-078` (M2) carries the work, and `TASK-051` now depends on it.
- **Rationale:** `CONF-40` recorded that no module could both reach the feature-owned state types the iOS app consumes and stay inside ADR-0001's dependency direction. Per-feature frameworks — the alternative — are the arrangement the Kotlin Multiplatform documentation itself calls limited (*"Usage of several Kotlin/Native frameworks in a Swift application is limited, but you can create an umbrella framework and export all these modules to it"*, `multiplatform-build-native-binaries.html`, verified 2026-09-30) and would each package `kotlin-stdlib` and `kotlinx-serialization`, duplicating the runtime against `REQ-NFR-002` and risking duplicate-symbol linkage. The umbrella module also keeps the decision reversible in one build file: no feature module references the framework, so the recorded fallback is a change to `:core:ios` and the Xcode linkage only.
- **Affected artifacts:** new `docs/adr/0012-ios-framework-export.md`; `docs/DECISION_BOARD.md` (`DEC-058`, §5); `docs/DOCUMENTATION_AUDIT.md` (`CONF-40` moved to §6.1, §6.3 row updated, §9); `docs/adr/0001-module-boundaries.md` (amendment pointer and the two diagram edges); `docs/DESIGN.md` (§3 diagram, §3.1 table, §3.4 rule 10, §3.5, §10); `docs/BACKLOG.md` (`TASK-078` added, `TASK-051` dependency, §2.4, §8.2, §9); `README.md` §5 and §8, `README.es.md` §5 and §8, `docs/HANDOFF.md` §3 and §8.
- **Decision / ADR:** DEC-058 (new), amending DEC-052 / ADR-0001; relates to ADR-0002, ADR-0003 and ADR-0008's containment pattern. `DEF-004` is the expiry condition.
- **Validation:** Documentation change only: no code was executed for it, and no framework has been produced. The decision's claims are cited from the Kotlin Multiplatform binary documentation (retrieved 2026-09-30) and from the repository's own ADR-0001 and ADR-0003. The export topology's behaviour with `@Serializable data object` route declarations and `sealed interface` intent hierarchies is **not** verified and is the first thing `TASK-078`'s spike must observe; an ADR-0012 whose exported surface proves unusable is superseded rather than patched. Internal links in the edited documents were re-checked, and the two modified Mermaid diagrams were checked for undeclared nodes.
### LOG-0028 · 2026-09-30 · Publication authorised, commit identity pinned to the owner account

- **Event:** The repository owner instructed that the repository be published and that every commit from now on be authored and committed as `David Ruiz <davidru85@gmail.com>`. The identity is pinned **repository-locally** (`file:.git/config`), not globally, so it applies to this project only. The owner also asked for a project-log note, which is this entry; the publication itself follows this commit.
- **Rationale:** `DEC-045` already fixes a **public** repository with a public read-only Figma link and committed PNG exports, so the private state of the repository contradicted an accepted, Blocking decision. Publication also removes the reviewer's need for a GitHub account, which is the same accessibility problem `CON-005` records for the Figma file. On identity: an agent working in this repository inherits the git configuration, so without a pinned identity a commit could carry an unintended author — that is a defect in the record, not a cosmetic issue, and it is now prevented at the repository level.
- **Affected artifacts:** the git configuration's `user.name`/`user.email` for this repository; the repository's visibility and About metadata (external to the tree); this log. No document content changes.
- **Decision / ADR:** `DEC-045` (public repository) becomes true rather than aspirational; the commit-identity instruction is an owner directive of 2026-09-30 with no `DEC-###` of its own yet, because it is a repository setting rather than a project decision. Whether it becomes a standing rule — and therefore needs a `DEC-###` row and a line in `CONTRIBUTING.md` §3 — is recorded below as an open point for the owner.
- **Validation:** Observed on 2026-09-30, before publishing. The identity is confirmed in place: `git config --show-origin --get user.email` → `file:.git/config` → `davidru85@gmail.com`, and the same for `user.name` → `David Ruiz`. A secret scan was re-run over the **entire history** and the working tree — 159 text blobs, 12 credential patterns (private keys, AWS, GitHub, Slack, Google, Stripe, JWT, keystores, credential assignments, basic-auth URLs, `.env` assignments) — and found **0 matches**. The author/committer inventory of all branches was taken and **published with LOG-0029**, because it is a fact about the published artifact.
- **Not verified:** the publication itself, and the About metadata, are not claimed here; that is `LOG-0029`. Whether `DEC-045`'s Figma-access clause (`CON-005`) is resolved is not claimed either: publishing the repository does not grant access to the Figma file.
- **Consequence the owner accepted explicitly:** the history of the repository **will contain `davidru85@gmail.com` in 30 author positions and 26 committer positions** — 18 pre-audit commits, the three `docs/documentation-system` commits authored as `davidru85@users.noreply.github.com`, and every commit of TASK-014 and after. The four merge commits `8026eb2`, `6b8f028`, `f48dea5` and `9c3193a` carry `noreply@github.com` as committer, which is GitHub's own bot. The owner was told that publishing makes that address harvestable and still authorised publication; the alternative — rewriting history with a mailmap — is a prohibited action for an agent (`AGENTS.md` §4.2) and was not performed.
### LOG-0029 · 2026-09-30 · Repository published, About metadata completed, branch protection available but not configured

- **Event:** The repository was switched from private to public and its About metadata was completed: description, homepage `https://rickandmortyapi.com/`, and thirteen topics. Anonymous access to the repository and to both `README.md` and `README.es.md` was then confirmed without credentials. Branch protection became available as a consequence of publication and was **deliberately not configured**, because there is no CI to satisfy required checks and a required-check rule with no workflow would deadlock every pull request. The default branch remains `main` with no protection and no ruleset.
- **Rationale:** `DEC-045` fixes a public repository, so the private state contradicted an accepted, Blocking decision; publication also removes the reviewer's need for a GitHub account, the same class of accessibility problem `CON-005` records for the Figma file. Required checks were withheld because `TESTING.md` §14.2's check names are provisional, no workflow exists (`GAP-002`, TASK-025), and marking a non-existent check required leaves a pull request permanently "Expected — waiting for status to be reported", which is exactly the deadlock `DEFINITION.md` §7 warns against.
- **Affected artifacts:** the repository's visibility, its About metadata and the availability of branch protection and rulesets (all external to the tree); this log. No document in the tree describes the repository as private, so no documentation change follows from the visibility switch itself.
- **Decision / ADR:** `DEC-045` is now true rather than aspirational. `CON-005` is **not** resolved: publishing the repository does not grant access to the Figma file, which still returns 403 to anonymous clients, and `docs/figma/` exports remain pending (`TASK-035`).
- **Validation:** Observed on 2026-09-30, after publication. `gh api repos/davidru85/RickAndMorty` → `visibility: public`, `default_branch: main`, `license: none`. The same call returns description, homepage and thirteen topics as set. Anonymous probes with no credentials: `GET api.github.com/repos/davidru85/RickAndMorty` → **HTTP 200**; `raw.githubusercontent.com/.../README.md` → **HTTP 200**; `raw.githubusercontent.com/.../README.es.md` → **HTTP 200**, which confirms the language variant is served publicly. `GET branches/main/protection` fails with *"Upgrade to GitHub Pro or make this repository public"* before publication and, after it, the endpoint is reachable, which is how the availability change was observed.
- **Not verified:** nothing about the applications, the build or the test suites, which are unchanged by this entry — the state recorded in LOG-0026 stands. No CI run exists, and no required check has ever reported on this repository.
- **Consequences observed on publication, none of which block the review:**
  - **Anyone can fork, clone and index the full history.** The `davidru85@gmail.com` exposure recorded in LOG-0028 is now realised, not hypothetical.
  - **`docs/PROJECT_LOG.md` is public**, including `LOG-0016`'s note about `API_SPECS.md` §9 still carrying pre-audit rate-limit wording. That inconsistency is public now, so correcting it in `API_SPECS.md` §9 has a higher value than it did while the repository was private.
  - **Community health files do not show.** GitHub presents `CONTRIBUTING.md`, `SECURITY.md` and `SUPPORT` only from the repository root, `.github/`, or `docs/`. `docs/CONTRIBUTING.md` and `docs/SECURITY.md` exist, but GitHub looks for `CONTRIBUTING.md` and `SECURITY.md` by that exact name, so the community-profile checklist still reports them missing. Surfacing them is a repository-layout decision, not a metadata field, and is left to the owner.
  - **`license: none`** is reported by GitHub. No `LICENSE` file exists and none was invented; the choice between no licence, MIT and Apache-2.0 is the owner's, and `assessment.md` contains material authored at ZARA, which is a factor in that choice.
  - **No branch protection and no ruleset** exist, so `DEC-054`'s mandatory check set is a convention rather than a gate, exactly as `CONTRIBUTING.md` §5.3 and `DEFINITION.md` §7 state. `GAP-002` remains open and unchanged.
### LOG-0030 · 2026-09-30 · `main` protected by a ruleset, without required checks or approvals

- **Event:** The owner instructed that `main` be protected, having been told both the policy position and the deadlock risk. A **repository ruleset** (`main protection`, id `24241444`, enforcement `active`) now applies to `refs/heads/main` with four rules: `deletion`, `non_fast_forward`, `required_linear_history` and `pull_request` (`required_approving_review_count: 0`, `required_review_thread_resolution: true`, `allowed_merge_methods: ["rebase", "merge"]`). The owner holds a permanent bypass (`actor_type: User`, `actor_id: 472324`, `bypass_mode: always`). The classic branch-protection API was not used because the ruleset API is GitHub's current replacement for it.
- **Rationale:** `required_linear_history` plus `allowed_merge_methods` of rebase and merge is the mechanical enforcement of `DEC-053`/`DEC-041`: squash merges are prohibited, so the TDD phase commits survive integration. Two rules are deliberately **absent**: required status checks, because no workflow exists (`GAP-002`, TASK-025) and a check that never reports leaves a pull request permanently "Expected — waiting for status to be reported"; and required approvals, because the repository has one collaborator (`davidru85`), GitHub does not permit self-approval, and one required approval would make every pull request unmergeable. The permanent bypass exists so the sole maintainer can always land a fix on `main`.
- **Affected artifacts:** the repository's ruleset (external to the tree); `docs/HANDOFF.md` §6, `docs/TECHNICAL_PLAN.md` §2.1, `docs/GUIDELINES.md` §8 and `docs/TESTING.md` §14.2, each of which stated that no protection existed, plus this log.
- **Decision / ADR:** `DEC-054` is now **partly** enforced: the pull-request requirement and the linear-history requirement are real, the mandatory check set is not. `DEC-049` is unaffected — repository settings remain human actions, and this one was instructed by the owner. `TASK-028` owns the remaining half (required checks bound to branch protection), and `GAP-002` stays open.
- **Validation:** Observed on 2026-09-30 from the API, not from the UI. `GET repos/davidru85/RickAndMorty/rulesets/24241444` → `enforcement: active`, rules `deletion`, `non_fast_forward`, `required_linear_history`, `pull_request`, `allowed_merge_methods: ["rebase","merge"]`, `required_approving_review_count: 0`, bypass `User:472324 always`. `GET repos/davidru85/RickAndMorty/rules/branches/main` → the four rule types, which confirms the ruleset is **effective** on the branch rather than merely stored. `GET repos/.../commits/<sha>` for `ea525df`, `6e507ca`, `d673a0a`, `2c7b4b9`, `fc6f15b` and `dd694f9` → every one reports `author.login = davidru85`, so the ruleset's `require_extra_approval_for_unattributed_changes` default does not create a second approval requirement. PR #6 reports `mergeable: MERGEABLE`, `mergeStateStatus: CLEAN`, which is the evidence that the configuration did not lock the pending review.
- **Not verified:** that a direct push to `main` is refused, and that a squash merge is refused, were **not** executed: attempting either would write to the default branch or to a pull request's history, and `git push --dry-run` does not evaluate server-side rulesets, so it cannot serve as evidence. The ruleset's effectiveness is therefore evidenced by the rules endpoint and by the four named rules, not by a failed write. No check has ever reported on this repository and none is required.
### LOG-0031 · 2026-09-30 · Integration switched to merge commits; rebase merging disabled (DEC-059)

- **Event:** The owner instructed that every pull request be merged into `main` **without** a rebase merge, so that the previous branch and its commits are always preserved as a record. Executed on the repository: the `main` ruleset's `pull_request` rule now allows `allowed_merge_methods: ["merge"]` only, the `required_linear_history` rule was removed from it, and the repository-level merge settings became `allow_merge_commit: true`, `allow_rebase_merge: false`, `allow_squash_merge: false`, with `delete_branch_on_merge: false` confirmed rather than changed. The ruleset keeps `deletion`, `non_fast_forward` and `pull_request`, its `active` enforcement, its zero required approvals and its owner bypass.
- **Rationale:** Owner directive of 2026-09-30. A rebase merge rewrites the branch's commits with new SHAs and the source branch is then deleted, so the pre-merge branch as a named, fetchable reference stops existing — which is exactly the record the owner wants kept. `required_linear_history` is what GitHub uses to refuse merge commits, so it had to be removed for the instruction to be satisfiable; leaving it in place would have made the setting inert. Squash merging remains disabled, because it destroys the red/green/refactor commit sequence DEC-053 rests on.
- **Affected artifacts:** the `main` ruleset (id `24241444`) and the repository's merge settings (both external to the tree); `docs/DECISION_BOARD.md` (`DEC-059`, `DEC-041` marked superseded in its merge-method half, the §3 superseded table, §5); `docs/CONTRIBUTING.md` §3.5, `docs/TECHNICAL_PLAN.md` §8.2 and `docs/GUIDELINES.md` §7.5, each of which mandated a linear history and a rebase merge and was false after the change; this log.
- **Decision / ADR:** `DEC-059` (new, Accepted) supersedes the **merge-method half** of `DEC-041` and supersedes the rebase-merge row added to §3. `DEC-053`'s phase-commit protocol is untouched: a merge commit preserves those commits in the merge's second parent, whereas a rebase merge rewrote them. Trunk-based development and Conventional Commits, the other half of DEC-041, are unchanged. No ADR is required: this is a process decision, not architecture (`DECISION_BOARD.md` §1).
- **Trade-off accepted knowingly:** history on `main` is no longer linear. `main` gains one merge commit per pull request, which is the cost of keeping the branch record. The previous configuration had chosen the opposite trade-off.
- **Validation:** Observed on 2026-09-30 from the API after the change. `GET repos/.../rulesets/24241444` → `enforcement: active`, rules `deletion`, `non_fast_forward`, `pull_request` (**`required_linear_history` absent**), `allowed_merge_methods: ["merge"]`, bypass `User:472324`. `GET repos/.../rules/branches/main` → the same three rule types, confirming the ruleset is effective on the branch rather than merely stored. `GET repos/davidru85/RickAndMorty` → `allow_merge_commit: true`, `allow_rebase_merge: false`, `allow_squash_merge: false`, `delete_branch_on_merge: false`. Links and placeholders were re-checked across the touched documents.
- **Not verified:** that the GitHub UI now offers only "Create a merge commit" and that a rebase merge is refused were **not** executed, because both would require opening a pull request and merging it, and the branch record would be the thing at risk. The evidence is the configuration read back from the API, plus the fact that `required_linear_history` is the rule GitHub uses to reject merge commits.
- **Consequence for the next pull request:** the first change merged after this entry will produce a merge commit on `main`, so `git log --first-parent main` becomes the reviewable list of integrated changes while plain `git log main` still shows the branch commits. That is the intended shape, and the earlier note in `LOG-0030` that the protection existed partly to enforce linear history no longer applies.
- **Observed limitation this decision cannot repair retroactively:** the owner also asked for the branch history to remain readable from the graph. That holds for the merges done *after* this entry, and it already holds for #1–#4, which are real merge commits (`8026eb2`, `6b8f028`, `f48dea5`, `9c3193a`). It does **not** hold for merges **#6 and #7**: both were rebase merges, performed while `required_linear_history` was still in force and before the setting changed, so `main` has no branch structure across them and their branch tips (`044767d`, `51c7ab0`) are unreachable from `main`'s graph; `51c7ab0` survives only in the reflog of the machine that deleted it. Making that retroactive would require rewriting `main`'s history, which is a prohibited action for an agent (`AGENTS.md` §4.2) and is not proposed. The mitigation is that the **names** are not lost: every historical branch remains listed by its pull request (`gh pr list --state all`), which is where the branch name lives permanently even after the ref is deleted.
- **Verified by a controlled experiment, not by assertion:** in a throwaway clone, a branch with two commits was merged with `--no-ff` and then deleted with `git branch -D`. Afterwards `git log --graph --oneline --all` still showed both of its commits on a side line converging at the merge commit, `git branch --contains <branch-tip>` reported `main`, and `git log --merges` still printed the branch name in the merge subject. That is the property DEC-059 buys, and it is the property a rebase merge does not have.
## 3. Verification performed on this repository

Verification was documentation-only for the whole lifetime of the repository up to `LOG-0025`. The first executed verification of any artifact is `LOG-0026` (2026-09-30), and the build was re-verified under the pinned daemon JDK in the same change, which built the Gradle/KMP skeleton and ran the commands it lists; before that entry, no build, test, lint, static-analysis, benchmark or application run had ever been executed here, because the repository contained no source code and no build files.

No feature code exists yet, so no test, lint, static-analysis, benchmark or application run is possible beyond the build checks recorded in `LOG-0026`. Nothing in this log is evidence that the described product compiles, runs or behaves as specified; the build skeleton proves the module graph and the toolchain pins, and nothing else. The documentation checks referenced above are recorded in `docs/DOCUMENTATION_AUDIT.md` and were re-run on 2026-09-30.
