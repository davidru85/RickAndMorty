# HANDOFF.md — Current State and Continuation Guide

- **Status:** Active. Describes the repository as of 2026-10-01; must be updated on every handoff (`AGENTS.md` §5 step 7).
- **Last verified:** 2026-10-02
- **Owner:** Delivery Planner (see `AGENTS.md` §3.8)
- **Authoritative for:** the current state of the project, what has and has not been verified, the next actions in priority order, handoff-specific operational risks, and the environment prerequisites for continuing.
- **Not authoritative for:** the plan and its milestones (`TECHNICAL_PLAN.md`), task state and acceptance criteria (`BACKLOG.md`), decision status (`DECISION_BOARD.md`), requirements (`REQUIREMENTS.md`), test strategy and the CI check list (`TESTING.md`), the gate (`DEFINITION.md`).
- **Inputs:** `README.md`, `docs/REQUIREMENTS.md`, `docs/DESIGN.md`, `docs/API_SPECS.md`, `docs/UI_SPEC.md`, `docs/DECISION_BOARD.md`, `docs/DOCUMENTATION_AUDIT.md`, `docs/TESTING.md`, `docs/DEFINITION.md`, the repository tree and its commit history.

> Read this file first if you are taking over. Then read `AGENTS.md` (operating rules and precedence) and the authoritative document for the area you are about to touch. Do not start from memory or from a similar project (`AGENTS.md` §2.1).

## 1. Current state (2026-10-01)

The repository contains the **documentation set**, the **Gradle/KMP build skeleton** (TASK-014, PR #6), the **five feature route declarations**, the tracked `.gitignore` and the **repository-hygiene check** (TASK-016, PR #13), the **pinned version catalog with its policy checks** (TASK-015, PR #10), the **shared test harness in `:core:testing`** and the **both-runner pull-request gate** (`TASK-024`/`TASK-025`/`TASK-028`, PR #52, merged as `7044869`), the **accepted contract baseline** `docs/CONTRACTS.md` (TASK-019, PR #31), the **executable module-boundary check** `verifyModuleBoundaries` (TASK-017, PR #33, hardened by TASK-088 so it reads effective inherited edges and fails a source file whose Kotlin package contradicts its path) and the **single `VERSION` source** (`0.1.0`, PR #34), whose validation every Android artifact task now consumes (TASK-089). There is still **no feature behaviour, no product test, no Xcode project and no `iosApp/`**; the APK has no activity, so the product has never been launched. The pull-request gate (PR #52) and the quality toolchain (PRs #63, #65) are merged, and the workflow guard rejects automated integration and any merge-gate reference to live mode (PRs #64, #67).

Delivery model changed on 2026-10-01: `TASK-016` was the last task executed individually, and the remaining work is delivered in nine execution blocks (`B1`…`B9`, `DEC-063`) owned by `docs/BACKLOG.md` §2.6. **Block 1 is integrated and locally verified, not `Done` under D2**: it merged before the gate existed, so no B1 pull request had a status-check rollup (`GAP-002` resolved by `TASK-025`). `DEC-071` resolves that state model — a check suite becomes mandatory in the change that creates its harness — and the post-merge review's four corrections are merged: `TASK-087` (PR #42), `TASK-088` (PR #43), `TASK-089` (PR #44) and `TASK-090` (PR #45). The dependency-safe source reconciliation keeps `TASK-031` and `TASK-073` together in B2, places `TASK-020` before `TASK-012` and `TASK-075` in B5, and keeps `TASK-021` in B4; no task depends on a later block (`CONF-52`, resolved).


| Area | State | Notes |
| --- | --- | --- |
| Assignment | Present and frozen | `assessment.md`; partially truncated at l.4 and l.10 — its intent is recorded, not guessed (`CON-003`) |
| Documentation system | Merged | The set is integrated on `main` (TASK-032 PR #25, TASK-034 PR #32, TASK-090 PR #45); inventory and open gaps in `docs/DOCUMENTATION_AUDIT.md` §6 |
| Requirements | Written, with acceptance criteria | `docs/REQUIREMENTS.md` — target state |
| Decisions | Recorded | `DEC-001`…`DEC-071` in `docs/DECISION_BOARD.md`; rationale in `docs/adr/` |
| Remote contract | Documented and probed | `docs/API_SPECS.md`; live observations dated 2026-09-29 |
| Visual specification | Written | `docs/UI_SPEC.md`; Figma file access required, PNG exports not committed |
| Architecture | Documented as target | `docs/DESIGN.md`; module layout is DEC-052 (see §3) |
| Implementation | Not started | Plan: `docs/TECHNICAL_PLAN.md`; work index: `docs/BACKLOG.md`. The build skeleton and the M0 policy checks are merged (TASK-014…TASK-018, PRs #6/#10/#13/#31/#33/#34); no feature code exists |
| Build, CI, tooling | Build skeleton, catalog, hygiene, boundary and `VERSION` checks merged | Gradle wrapper 9.7.0, AGP 9.3.1, Kotlin 2.4.20 and the 11 modules of ADR-0001 build (LOG-0026); the version catalog pins the full planned inventory and `verifyDependencyPolicy` enforces it in `check` (TASK-015, LOG-0032); `verifyRepositoryHygiene` (`TEST-UNIT-026`) completes `.gitignore` and runs in `check`, merged to `main` in PR #13 (TASK-016, LOG-0036, LOG-0037, LOG-0038, LOG-0039). `verifyModuleBoundaries` enforces the module graph and `verifyDependencyPins` owns the single `VERSION` source (`0.1.0`, all tasks TASK-017/TASK-018/TASK-088/TASK-089 merged). Still absent: CI, lint/detekt/dependency-analysis configuration and every product test |
| Screenshots | Not started | Figma exports under `docs/figma/` and in-app screenshots of the running apps |

### 1.1 Verified versus intended

**Verified (observed in this repository, or against a live system on 2026-09-29; technical checks re-run 2026-10-01):**

- The repository tree, its contents and its 14-commit history on `main` (all dated 2026-09-29).
- The live behaviour of the remote API recorded with that date in `docs/API_SPECS.md` §1.1: list totals and page size, `404` for a filtered empty result that is itself cacheable and immutable, immutable detail `404`, out-of-range page `404`, batch requests returning only existing resources, one identifier yielding an object and two an array, GraphQL null for a missing character, `info.next` as `Int`, the empty-filter GraphQL shape, and `400 GRAPHQL_VALIDATION_FAILED` for an unknown field.
- Toolchain versions and platform API availability checked on 2026-09-29 and recorded in `local://decision-brief.md` §4 (not committed to the repository).
- Documentation checks on this repository: link resolution, identifier uniqueness and ordering, and consistency review. See §7.
- The B1 technical checks on 2026-10-01: `./gradlew projects` (14 projects), `verifyModuleBoundaries` (clean, and red under every seeded violation of `TEST-UNIT-017`/`TEST-UNIT-043`), `verifyDependencyPins` (clean, `VERSION` `0.1.0`) and `lintDebug` (clean). The boundary and version seed matrices are recorded in `docs/TESTING.md` §3.3 and `PROJECT_LOG.md` LOG-0049…LOG-0051.
- The Gradle/KMP build (2026-09-30, branch `build/gradle-kmp-skeleton`, then merged): `./gradlew --version`, `projects`, `help --warning-mode=all`, `assemble`, `build`, `:androidApp:assembleDebug` (with no `iosApp/` and from a clean clone), the per-module dependency reports, the `tasks --all` target scan and the APK badging check. Commands and observed results: `PROJECT_LOG.md` LOG-0026. The dependency policy was verified on 2026-09-30 on branch `build/version-catalog-inventory`: `verifyDependencyPolicy` passes and runs in `check`. The repository-hygiene check was verified on 2026-09-30 on branch `build/repository-hygiene` and **corrected** review: every **positive** seed of the original S1–S14 matrix failed its owning rule (S13 and S14 are negative cases and must pass, and explicit `HYG-05` evidence is supplied by R12), the review-regression matrices R1–R15 and then T1–T14 closed the false negatives and fail-open paths two independent reviews found, and the corrected head passes with 0 findings and runs in `check` (`PROJECT_LOG.md` LOG-0036, LOG-0037 for the first two rounds and LOG-0038 for the current evidence). Two review rounds hardened the checks to P1–P8, R1–R7 and I1–I9, and the seeded-violation matrix S1–S53 failed the owning rule and passed once reverted (`PROJECT_LOG.md` LOG-0033, corrected by LOG-0034). The source-text limits that remain are `GAP-011`.

**Intended, not verified (documented target state):**

- Everything about the applications: architecture, module boundaries, contracts, screens, motion, copy, error states, accessibility behaviour, performance budgets, security posture, logging.
- The product commands: feature, iOS-app, snapshot, contract, performance and release commands in §8 and in `README.md` §8–§9 remain target state, because the features they call do not exist. The commands marked **executed** — the Gradle/KMP skeleton build of LOG-0026 and the repository-policy tasks of LOG-0032…LOG-0038 — do exist and were run.
- Product acceptance: every **product/application** `AC-*` criterion of `REQUIREMENTS.md` remains unproven, because no feature code exists; the repository/tooling criteria that a build check can decide are implemented and locally evidenced instead (`AC-REQ-SEC-002-1` by `TEST-UNIT-026`, plus `AC-REQ-NFR-002-1`, `AC-REQ-NFR-006-1` and the exact-pin half of `TEST-UNIT-014`). That is not the same as nothing having been exercised: `TASK-014`, `TASK-015` and `TASK-016` are `Done` (`TEST-UNIT-026` merged in PR #13), and `TASK-015` has local evidence for `TEST-UNIT-013`, the exact-pin half of `TEST-UNIT-014`, and `TEST-UNIT-051` (`PROJECT_LOG.md` LOG-0032…LOG-0038).
- The Figma-derived visual specification: the Figma file itself could not be fetched anonymously on 2026-09-29 (HTTP 403), so its content is reproduced from the design work, not re-verified from the source.

Beyond the build checks of LOG-0026 and the repository-policy verification tasks of LOG-0032…LOG-0038, no product test, benchmark or application run has been executed here: no product test source set exists, and detekt, the snapshot suites, the benchmark harness and every app run remain `TASK-029`'s recorded target state, `TASK-045`/`TASK-059` and the M1 feature work. ktlint, Android Lint, `buildHealth` and the repository-policy checks have executed. Statements to the contrary would be false.

## 1.2 Block state (2026-10-02)

**Block 2 is complete, its own remediation included.** All nine of its original rows are `Done` under D2 against the checks active at their stage: `TASK-024`,
`TASK-025` and `TASK-028` in PR #52; `TASK-031` in #62; `TASK-030` in #63; `TASK-093` in #64; `TASK-029` in #65; `TASK-073` in #66; `TASK-026` in #67
(merged as `8e58d77`); `TASK-027` in #70 (merged as `4059e1d`). `TASK-094` recorded the closure reconciliation in #69, and `TASK-082` closed with the
detekt outcome (`DEC-075`). An audit of the merged block then reproduced ten defects; all ten (`TASK-098`…`TASK-107`, `B2-R01`…`B2-R10`) are merged
between `4953bde` (#88) and `c57ae85` (#104).

The two repository settings that were human-only (`DEC-049`) are **applied**: the `main protection` ruleset requires the `android` and `ios` checks and
carries no bypass actor, so a head without them reads `BLOCKED`. The configuration and the observed enforcement are in `CONTRIBUTING.md` §5.3 (`LOG-0076`).

`TASK-096` is `Done` (PR #74, merged as `72332a5`): it wires `contractLiveTest` (which holds `TEST-CONTRACT-006`)
into the scheduled job and makes `WorkflowGateGuard` recognise every registered live entry point, proved red →
green with a seed that runs the real task name from a pull-request workflow. `TASK-097`
(`docs/b2-final-reconciliation`) closes the residue the block audit found: six stale current-state claims and
`TESTING.md` §14.2's per-row activation owners. The scheduled job was observed by `workflow_dispatch` on merged
`main` (run `36983953859`, success, both steps green); the cron trigger first fires Monday 06:00 UTC.

## 2. Completed work

1. **Documentation baseline.** The specification set exists and each topic has exactly one authoritative owner (`AGENTS.md` §2): requirements, architecture, remote contract, visual specification, internal contracts, failure→state→copy chain, performance, observability, security, testing, gates, guidelines, contribution process, plan, backlog, decision board, this file, and the audit. `README.md` and `README.es.md` are the entry points.
2. **Decision baseline.** `DEC-001`…`DEC-071` are recorded with category, status, urgency, blocking impact and ADR pointer, together with rejected and superseded alternatives and deferred decisions (`docs/DECISION_BOARD.md`).
3. **Requirements with acceptance criteria.** `docs/REQUIREMENTS.md` carries stable identifiers, `AC-<REQ-ID>-n` criteria, MoSCoW priorities, scope, non-goals, deferred items, constraints, risks and assessment traceability.
4. **Process baseline.** The TDD phase-and-commit protocol (DEC-053, amending DEC-041), the mandatory both-platform pull-request gate (DEC-054, superseding DEC-028) and the definitions of Ready and Done are decided and documented.
5. **Figma-aligned visual baseline.** The Figma file map, tokens, per-platform component specifications, screens, motion, states, iconography and accessibility expectations are written down (`docs/UI_SPEC.md`), on the basis of the two design briefs in `docs/design/`.

6. **Navigation and Settings redesign (2026-09-30).** In Figma, Settings replaced Locations as the fourth destination, and both navigation bars became reusable components: `Android/Navigation bar` `117:887` and `iOS/Glass tab item` `117:1369` inside `iOS/Glass tab bar` `102:255`. The Settings screens hold three settings built from each platform's kit: Sounds (off by default), a REST API/GraphQL choice (REST by default) and "Delete favorites", which opens a confirmation frame. Recorded as DEC-055 (ADR-0010) and DEC-056 (ADR-0011), with `REQ-FUNC-033`…`REQ-FUNC-035`, `IC-021`…`IC-023` and `TASK-074`…`TASK-077`. The Figma edits were checked by screenshot through the Figma API; the "Delete favorites" disabled state is specified in `docs/UI_SPEC.md` §8 but not drawn.

## 3. In progress and not started

- **Block 1 (Definition and Governance) is complete** — all seven tasks and their corrections are merged, and the two topology-hardening tasks that followed them too. Its own pull requests predate the CI gate, so the block is recorded as *integrated and locally verified* rather than with a rollup; the gate that makes D2 satisfiable for everything after it landed in `TASK-025` (PR #52, `7044869`).
- **Plan:** the M0→M3 milestone plan, sequencing and quality gates live in `docs/TECHNICAL_PLAN.md`, authored in the same change as this file. Treat that document, not this summary, as the plan.
- **Work index:** the first implementation tasks with their acceptance criteria live in `docs/BACKLOG.md`, authored in the same change. Take work from there, not from this file.
- **Module layout is propagated and complete (DEC-052, superseding DEC-019):** **feature-per-module plus Clean Architecture packages inside each feature**. The core modules are `:core:domain`, `:core:data`, `:core:presentation`, `:core:designsystem` (Android-only) and `:core:testing`; the five feature modules are `:feature:discovery`, `:feature:character-detail`, `:feature:favorites`, `:feature:episodes` and `:feature:settings` (the latter replacing `:feature:locations` per `DEC-055`); the app shell is `:androidApp`. The build, `docs/DESIGN.md` §3, ADR-0001 and the boundary check all describe this same set, and `R16` of `verifyModuleBoundaries` fails when a required module is absent, so a silent deletion can no longer pass. `iosApp` and `:core:ios` (ADR-0012) remain **target state**: the export module is `TASK-078` and the app is `TASK-051`, neither started.
- **The gate is real and merged (PR #52, `7044869`):** `.github/workflows/pull-request.yml` runs on every pull request and every push to `main`, on an Ubuntu and a macOS runner with every action pinned by commit SHA. Both jobs were observed **red** on a seeded failing test and **green** on the merged head (`android`, `ios`), and `TASK-028` verifies the configuration cannot silently narrow (`TEST-UNIT-044`).
- **What remains human:** naming `android` and `ios` as required status checks in branch protection (`CONTRIBUTING.md` §5.3, `DEC-049`).
- **Activated checks, and the rows still awaiting their harness:** the workflow runs the shared `commonTest` suites on the JVM and both Apple targets, the build-logic regression suite and every policy task (`verifyModuleBoundaries`, `verifyDependencyPolicy`, `verifyRepositoryHygiene`, `verifyNoLiveHosts`, `verifyWorkflowGate`), the Android assemble and the Apple compiles. The snapshot, fixture-mode contract, accessibility, static-analysis and dependency-analysis rows stay recorded against their activation tasks in `TESTING.md` §14.2 (`DEC-071`) rather than being faked by an always-green job.
- **Merged before the gate existed, and the genuinely absent items:** a check that existed before `TASK-025` has no rollup of its own, so it is recorded as *integrated and locally verified*; everything from `TASK-024`/`TASK-025`/`TASK-028` onward runs under real required checks (`DEC-071`).
  - CI workflows (both platform workflows plus the fixture/replay contract job) — DEC-054;
  - the module-boundary enforcement check and its seeded violations — **merged** (TASK-017, PR #33; hardened by TASK-088, PR #43);
  - the module-boundary **topology completeness** rule and the durable build-logic regression suite — **merged** (`TASK-091`, `TASK-092`, PR #50 as `9cf6217`): `R16` fails when a required module is missing, `:feature:*` classification is restricted to the accepted five, and `./gradlew check` runs the 59-test suite;
  - the `VERSION` source — **merged** (TASK-018, PR #34; every artifact task consumes its validation since TASK-089, PR #44);
  - the repository-hygiene check that completes `.gitignore` is **merged in TASK-016** (PR #13, 2026-09-30): `verifyRepositoryHygiene` holds the ignore-rule and credential rules and runs in `check`;
  - the `tokens.json` parity test — DEC-022;
  - rendered PNG exports under `docs/figma/` — DEC-045;
  - `README.es.md` review as the only translation — DEC-047.

## 4. Next recommended actions, in priority order

Each action names the first concrete step and the document that owns it. A task is not started until its first failing test exists (DEC-053); the exceptions are documentation, build/CI configuration and tooling changes, and their use must be stated explicitly.

From 2026-10-02 the actions map onto the execution blocks of `BACKLOG.md` §2.6 (`DEC-063`). **B1 is closed** and **B2's nine rows are all `Done`**: the gate, the quality toolchain, the templates and both contract paths are merged (PRs #52, #62–#67, #69, #70, #72, #74, #76). An **audit of the merged block reproduced eight defects** in those deliverables — the workflow gate accepts configurations in which verification cannot run, the replay path can report an untested success, the JVM opt-in is unenforced, the macOS job covers four of nine modules, the documented gate is not substantiated, the observation path fails open in several ways, the exclusion lifecycle diverges from its decision, and the Swift scripts fail open — each registered as `GAP-016`…`GAP-023` and `TASK-098`…`TASK-105`; the human ruleset settings are `TASK-106`. Work continues block by block, and no task in a block depends on a later block.

1. **B1 closed.** All seven tasks and their corrections merged (PRs #25, #31–#36, #42–#45); the audit's open items are now `CONF-50`, `CONF-54` and the open `GAP-*` rows. Owner: `docs/DOCUMENTATION_AUDIT.md`.
2. **B2 is closed, remediation included.** Its nine original rows merged in PRs #52, #62–#70; its own audit then reproduced ten defects and registered them as `TASK-098`…`TASK-107` (`B2-R01`…`B2-R10`). All ten are now `Done` under D2, merged between `4953bde` (#88) and `a1de389` (#102): the workflow gate (`#88`), the documented gate (`#89`), the Swift toolchain (`#90`), the contract replay (`#91`), the exclusion register (`#92`), the target model (`#98`), the macOS coverage (`#99`), the live observations (`#100`), the repository settings (`#101`) and the document reconciliation (`#102`). The audit's open-gap count fell from 16 to 8, none of them a remediation finding. Owner: `docs/DOCUMENTATION_AUDIT.md` §6.2.1.
3. **B2 is closed, formally.** Its ten corrective rows are `Done` (PRs #88–#92, #98–#102) and the closure check that followed found and fixed three defects in the remediation's own documentation (PR #105, `LOG-0077`). The block's state, the traceability and the mechanical documentation guards are in `docs/DOCUMENTATION_AUDIT.md` §6.2.1–§6.2.2.
4. **B3 opens next** (`TASK-036`, `TASK-037`, `TASK-038`, `TASK-039`, `TASK-040`, `TASK-041`, `TASK-047`): the shared core and the data layer, which also carry the activation obligations `DEC-073`/`DEC-074` wrote into `TASK-037` and the interface-bound fakes `DEC-072` moved. Owner: `docs/BACKLOG.md` §4.
6. **Then the delivery blocks** in order: B4 (design system and Android shell), B5/B6 (Android features and validation), B7–B9 (iOS and closure). Each block's members and its intra-block order are in `docs/BACKLOG.md` §2.6.

## 5. Unresolved decisions and deferred items

Do not implement a deferred item. The authoritative lists are:

- `docs/DECISION_BOARD.md` §4 — decisions with status `Deferred`, with the condition that reopens each one.
- `docs/REQUIREMENTS.md` §1.3 — deferred scope items `DEF-001`…`DEF-004`, with their re-entry conditions. `docs/REQUIREMENTS.md` §14 records that nothing there is blocking.

- The decisions B1 needed are recorded (`DEC-064`…`DEC-068`, `LOG-0042`): `CONF-46`, `CONF-45`, `CONF-47`, `CONF-48` and `CONF-42` are resolved, and the `VERSION` acceptance is split for `TASK-018`/`TASK-051` (`DEC-067`). Still open and indexed as work rows in `docs/BACKLOG.md` §9: `GAP-010` (who owns dependency-artifact verification, and when); `GAP-011` (whether and when to rebuild the policy checks on Gradle's dependency model); and the OD-6 blocked item `CONF-50` (`CONF-51` is resolved as detekt target state by `DEC-075`), each with its own row.

Two consequences worth knowing before planning work: voice search is deferred and no microphone or speech permission may be added (`REQ-SEC-004`); Episodes ships as a placeholder (`DEC-005`), and Settings replaces Locations with three real settings, including a runtime REST/GraphQL switch (`DEC-055`, `DEC-056`).

## 6. Known risks

Product, contract and delivery risks are owned by `docs/REQUIREMENTS.md` §13 as `RISK-###`, with likelihood, impact, mitigation and owner. Do not restate or duplicate them here.

Two **operational** risks are specific to taking this repository over and are not covered there:

| Risk | Impact | Mitigation |
| --- | --- | --- |
| The Figma source file requires project access and returned HTTP 403 to an anonymous client on 2026-09-29. | `docs/UI_SPEC.md` cites Figma pages and node identifiers that a new contributor cannot open; the design becomes uncorroborable. | Obtain read access before design-dependent work; commit the rendered PNG exports under `docs/figma/` (DEC-045) so the specification stays checkable without access. |
| One toolchain artifact is pinned pre-release (Material 3 Expressive `1.5.0-alpha29`, `DESIGN.md` §3.5; the alpha-only `androidx.lifecycle` KMP and DataStore KMP of `CON-004` are **not** adopted). | An upgrade can break the build or change rendering, and the pinned versions age quickly. | Keep versions pinned and centralised in the version catalog, record the accepted alpha risk per `docs/adr/0008-alpha-dependencies.md`, and let the mandatory gate (DEC-054) catch breakage at upgrade time rather than at review time. |

One further operational note: `main` is the only integrated branch and it now carries a **branch ruleset** (`main protection`, active), configured by the owner on 2026-09-30: no deletion, no force-push, and a pull request required. Integration uses a **merge commit** and no other method, so the branch record survives (DEC-059); linear history is deliberately **not** required, because it would refuse merge commits. One deliberate omission remains: **no required status checks named in the ruleset**, although the workflow has existed since `TASK-025` (PR #52) — so a red run does not yet block a merge through GitHub; the exact list is in `CONTRIBUTING.md` §5.3. **No required approvals** are configured, because the repository has a single collaborator who cannot approve their own pull request.

**Reading the history, and recovering branch names.** Merges #1–#4 are real merge commits, so `git log --graph --all` shows those branches and their commits even though the refs are gone. Merges #6 and #7 were rebase merges and left **no** branch structure: `main`'s history is linear across them and their branch tips are unrecoverable from the graph, which is precisely what DEC-059 prevents from recurring. Every historical branch name is nevertheless recoverable, because a pull request keeps its head ref name permanently: `gh pr list --state all --json number,state,headRefName,baseRefName` lists all seven PRs with their branches (`docs/figma-designs`, `docs/module-and-ci-decisions`, `docs/settings-destination`, `docs/data-sources`, `build/gradle-kmp-skeleton`, `docs/ios-framework-export`, `docs/merge-commit-integration`). Repository settings and branch protection are human actions (DEC-049).

## 7. Validation status

**What has been verified in this repository, and how:**

- **Link checking** — relative links between documents were resolved by inspection as part of the documentation review; the inventory and any unresolved finding are in `docs/DOCUMENTATION_AUDIT.md`.
- **Identifier uniqueness** — `LOG-####` entries are sequential and unique; the requirements, decision, risk, contract and test identifier schemes are checked for uniqueness and cross-reference by the same review, which is the check class recorded in `docs/DOCUMENTATION_AUDIT.md` §6.
- **Consistency review** — statements that a document does not own were traced to their owner and de-duplicated; conflicts found are recorded as `CONF-###` in `docs/DOCUMENTATION_AUDIT.md` rather than silently resolved.
- **Remote contract verification** — live probes against the public API on 2026-09-29; the observed results and their date are in `docs/API_SPECS.md` §1.1.
- **Repository history** — read from the repository itself, not recalled.

**What has NOT been verified:** no **product** test has been executed; no benchmark or accessibility check has been run; the Android app and the iOS app have never been installed or launched (the skeleton's APK has no activity); no requirement has been demonstrated by an observing run. What **has** been executed is the build skeleton of LOG-0026, Android Lint and the build checks, and the repository-policy verification tasks: the dependency policy of LOG-0032…LOG-0035 and the repository/secret hygiene check of LOG-0036…LOG-0038. The build commands in §8 below that the skeleton now supports have been executed and are marked as such; the rest stay unexercised, and every product/application `AC-*` criterion in `docs/REQUIREMENTS.md` is currently unproven (the tooling criteria listed in §1.1 are implemented and evidenced).

## 8. Relevant commands

The interface as it stands today. The build skeleton exists (TASK-014), so the wrapper and the module names below are real; the feature, test and release commands are the intended interface and are unexercised. Module names follow DEC-052.

Commands marked **executed** were run on the branch named with the date and are recorded with their observed results in `PROJECT_LOG.md` (`LOG-0026`, `LOG-0066`…`LOG-0076`).

| Purpose | Command | State |
| --- | --- | --- |
| Build the Android debug app | `./gradlew :androidApp:assembleDebug` | **Executed 2026-09-30** — succeeded, with no `iosApp/` present and from a clean clone |
| Build every module, including the iOS klibs | `./gradlew assemble` · `./gradlew build` | **Executed 2026-09-30** — succeeded; no Android Lint error |
| List the module set | `./gradlew projects` | **Executed 2026-09-30** — exactly the 11 modules of ADR-0001 |
| Inspect the resolved module graph | `./gradlew :<module>:dependencies --configuration <sourceSet>Implementation` | **Executed 2026-09-30** — matches the module table |
| Inspect the APK manifest | `aapt2 dump badging androidApp/build/outputs/apk/debug/androidApp-debug.apk` | **Executed 2026-09-30** — package `io.github.davidru85.multiverse`, `minSdk` 26, `targetSdk` 37, no permission |
| Install on a connected device or emulator | `./gradlew :androidApp:installDebug` | Not run: the APK has no activity to launch (TASK-044) |
| Build the shared framework for the iOS simulator | `./gradlew :core:ios:linkDebugFrameworkIosSimulatorArm64` | Not run — `:core:ios` does not exist yet (`TASK-078`). `CONF-40` is resolved: one framework from `:core:ios` exports the five features plus the other core modules (`DEC-058`, [ADR-0012](adr/0012-ios-framework-export.md)) |
| Build the iOS app | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build` | Not run: `iosApp/` does not exist (TASK-051) |
| Shared and unit tests, plus the build-logic regression suite | `./gradlew allTests :build-logic:convention:test` | Executed 2026-10-02: the shared suites run on the JVM host-test target and both Apple targets (`:core:testing:testAndroidHostTest`, `:core:testing:iosSimulatorArm64Test`) and the build-logic regression suite runs in the included build. The earlier row documented `./gradlew test`, which selects only the Android unit-test tasks and reaches neither the shared KMP suites nor the build-logic suite (`TEST-UNIT-015`; `TASK-024`, `TASK-103`) |
| Android screenshot verification | `./gradlew :feature:discovery:verifyRoborazziDebug` | Not run: Roborazzi is not configured; it lands with the Android screenshots (`TASK-045`) |
| Record new Android screenshot baselines (review the diff before committing) | `./gradlew :feature:discovery:recordRoborazziDebug` | Not run: no baselines exist (`TASK-045`) |
| Formatting, static analysis, dependency checks | `./gradlew ktlintCheck lintDebug buildHealth` (detekt is **not** registered: `DEC-075` keeps it target state, so it is not part of any executed command) | Executed 2026-10-01: ktlint, Android Lint and `buildHealth` run and pass (`TASK-029`, PR #65) |
| Dependency-policy checks (exact pins, rationale, inventory) | `./gradlew verifyDependencyPolicy` | **Executed 2026-09-30** — passes; also runs inside `./gradlew check` and `./gradlew build` (TASK-015, LOG-0032) |
| Repository and secret hygiene check | `./gradlew verifyRepositoryHygiene` | **Executed 2026-10-01 on merged `main`** — passes with 0 findings over the working set, every reachable blob and every unique historical path; runs inside `./gradlew check` and `./gradlew build` (TASK-016, LOG-0036…LOG-0039) |
| iOS tests and snapshots | `xcodebuild test -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro'` | Not run: `iosApp/` does not exist (TASK-051) |
| Contract suite — fixture/replay mode, inside the pull-request gate | Expected shape `./gradlew :core:data:contractTest`; the exact task names for the fixture/replay and live modes are owned by `docs/TESTING.md` | Not run: no contract test exists (TASK-026) |
| Performance benchmarks (requires a device) | Task and module are defined in `docs/TECHNICAL_PLAN.md`; the budget and measurement method are in `docs/PERFORMANCE.md` | Not run: the harness module needs a decision first (CONF-41) |

Every row above that is not marked executed, and every row added since this table was written, remains unexercised and is marked accordingly; the skeleton's own verification is LOG-0026.

The complete required-check list for a pull request, and what each check blocks, are owned by `docs/TESTING.md` and `docs/DEFINITION.md`. As of DEC-054 every check in that list is blocking on both platforms, including the iOS suites; the red commit of a TDD cycle is expected to fail tests, because the gate evaluates the final state of the pull request.

## 9. Credentials and prerequisites

**No credentials are required, and none exist.** The API is public, unauthenticated and read-only; no API key, token, secret, keystore or environment variable is needed to build or run the application, and committing one is prohibited (`AGENTS.md` §4.2, `REQ-SEC-002`).

Two prerequisites involve an account or access decision, and neither is a secret:

| Prerequisite | Need | Status |
| --- | --- | --- |
| Figma file (`nFQdxd23Kk4rNI7G4iHDUr`) | Read access, so the design of record can be opened and the PNG exports can be produced | Not held by default — the file returned HTTP 403 to an anonymous client on 2026-09-29 |
| Package registry | None beyond network access: Kotlin, AndroidX, Ktor, Coil, Koin and the test tooling come from public repositories, and no private feed or license is involved | Satisfied by the public repositories; no account required |

Local toolchain prerequisites (JDK, Android SDK, Xcode, Kotlin supplied by the Gradle toolchain) are listed in `README.md` §6. A macOS host is required to run the iOS suites, which DEC-054 makes mandatory on every pull request.

## 10. Key documentation links

| Document | Why you need it |
| --- | --- |
| [`README.md`](../README.md) | Project overview, features, structure, setup, intended commands |
| [`AGENTS.md`](../AGENTS.md) | Precedence, roles, permissions, workflow, escalation, completion rules |
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | What the product must do, with acceptance criteria, scope, risks |
| [`DESIGN.md`](DESIGN.md) | Architecture, modules, state contracts, navigation |
| [`API_SPECS.md`](API_SPECS.md) | Remote contract, DTOs, errors, caching policy |
| [`UI_SPEC.md`](UI_SPEC.md) | Tokens, components, screens, motion, accessibility |
| [`DECISION_BOARD.md`](DECISION_BOARD.md) | Every decision, its status, its ADR, and what is deferred |
| [`TECHNICAL_PLAN.md`](TECHNICAL_PLAN.md) | Milestones, sequencing, quality gates |
| [`BACKLOG.md`](BACKLOG.md) | The canonical work index with task-level acceptance criteria |
| [`TESTING.md`](TESTING.md) | Test strategy, layers, tooling, required CI checks, traceability |
| [`DEFINITION.md`](DEFINITION.md) | Ready, Done, pull-request and release gates |
| [`DOCUMENTATION_AUDIT.md`](DOCUMENTATION_AUDIT.md) | Inventory, ownership, open gaps and conflicts |
| [`PROJECT_LOG.md`](PROJECT_LOG.md) | Why the project evolved as it did (`LOG-####`) |
