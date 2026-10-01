# DOCUMENTATION_AUDIT.md — Documentation System Audit

- **Status:** Active — audit performed 2026-09-29 on branch `docs/documentation-system`
- **Last verified:** 2026-10-01
- **Owner:** Documentation Maintainer (see `AGENTS.md`)
- **Authoritative for:** the documentation inventory, ownership map, open gaps, conflicts, and the reconciliation rule between documentation and implementation.
- **Inputs:** every document in the repository, `assessment.md`, and the live-API and toolchain verifications recorded below.

## 1. Scope and method

This audit covers the entire documentation set of the repository after the documentation-system rewrite. It answers four questions: what exists, who owns it, what is still wrong, and how drift is prevented from returning.

**Method.** Every pre-existing document was read in full; the repository tree, git history, tracked and untracked files were inspected; the live API was probed; library and platform versions were checked against primary sources. Verification evidence and dates are in §7.

**What could not be verified.** The Figma file (`nFQdxd23Kk4rNI7G4iHDUr`) returns HTTP 403 to anonymous clients, so node identifiers, frame sizes and component names referenced from `UI_SPEC.md` come from the repository's own documents and from the design briefs — they are consistent with each other but were not confirmed against the source file (`CON-005`, `GAP-004`).

## 2. Inventory

31 documents plus this audit. `Status` is the lifecycle state; `Lifecycle` distinguishes a normative document from an input or a working artifact.

| Document | Purpose | Owner | Status | Lifecycle | Update frequency |
| --- | --- | --- | --- | --- | --- |
| `assessment.md` | The assignment; overrides everything on conflict | — | Frozen | Authoritative source | Never |
| `AGENTS.md` | Operating rules for AI agents | Documentation Maintainer | Active | Normative | On process change |
| `README.md` | Entry point: setup, build, test, structure, status | Delivery Planner | Active | Normative | On milestone or command change |
| `README.es.md` | Spanish mirror of the README | Documentation Maintainer | Active | Normative | In lockstep with `README.md` |
| `docs/REQUIREMENTS.md` | Requirements, acceptance criteria, scope, constraints, risks | Requirements Analyst | Active | Normative | On requirement change |
| `docs/DESIGN.md` | Architecture, modules, state flow, navigation, DI | System Architect | Active | Normative | On design change |
| `docs/API_SPECS.md` | Remote contract, failure taxonomy, cache policy, contract ids | API Architect | Active | Normative | On API or protocol change |
| `docs/UI_SPEC.md` | Tokens, components, screens, motion, accessibility, icons | UI/UX Designer | Active | Normative | On design change |
| `docs/CONTRACTS.md` | Internal Kotlin seams and their invariants | System Architect | Active | Normative | On contract change |
| `docs/ERROR_FLOW.md` | Failure → state → copy chain | System Architect + QA | Active | Normative | On failure/state change |
| `docs/PERFORMANCE.md` | Budgets and measurement method | Implementation Engineer + QA | Active | Normative | On budget change |
| `docs/OBSERVABILITY.md` | Logging contract, redaction, debug diagnostics | Implementation Engineer | Active | Normative | On logging change |
| `docs/SECURITY.md` | Threat model, privacy, advisory register | Security Reviewer | Active | Normative | On security change |
| `docs/TESTING.md` | Test strategy, ID inventory, fixtures, traceability | QA & Validation | Active | Normative | On test-strategy change |
| `docs/DEFINITION.md` | Ready, Done, milestone, release and documentation gates | Delivery Planner + QA | Active | Normative | On gate change |
| `docs/GUIDELINES.md` | Code conventions and where they are enforced | Implementation Engineer | Active | Normative | On convention change |
| `docs/CONTRIBUTING.md` | Setup, TDD protocol, branching, PRs, review | Documentation Maintainer | Active | Normative | On process change |
| `docs/TECHNICAL_PLAN.md` | Milestones, sequencing, quality gates | Delivery Planner | Active | Normative | On plan change |
| `docs/BACKLOG.md` | Canonical work index (`TASK-###`) | Delivery Planner | Active | Working | Continuous |
| `docs/DECISION_BOARD.md` | Decision status index (`DEC-###`) | System Architect | Active | Normative | On decision change |
| `docs/adr/0000-adr-template.md` | ADR template | System Architect | Active | Template | Rarely |
| `docs/adr/0001`…`0011` | Decision rationale | System Architect | Active | Normative (immutable once accepted) | Never after acceptance |
| `docs/PROJECT_LOG.md` | Chronological record of why things changed | Documentation Maintainer | Active | Working | Per meaningful event |
| `docs/HANDOFF.md` | Current state and next actions | Delivery Planner | Active | Working | On handover |
| `docs/design/01-android-m3-expressive.md` | Historical Figma generation brief | UI/UX Designer | Superseded as a specification | Input | Never |
| `docs/design/02-ios-liquid-glass.md` | Historical Figma generation brief | UI/UX Designer | Superseded as a specification | Input | Never |
| `docs/figma/README.md` | Design-export location and procedure | UI/UX Designer | Active | Working | When exports land |
| `docs/templates/backlog-item.md` · `test-case.md` · `pull-request.md` · `bug-report.md` | Working templates | Documentation Maintainer | Active | Template | Rarely |
| `docs/DOCUMENTATION_AUDIT.md` | This file | Documentation Maintainer | Active | Working | Per milestone |

## 3. Ownership map — one authoritative source per topic

| Topic | Single owner | Explicitly delegated elsewhere |
| --- | --- | --- |
| Assignment intent | `assessment.md` | Interpretation of truncated lines recorded in `REQUIREMENTS.md` §4 |
| Requirements, acceptance criteria, scope, risks | `REQUIREMENTS.md` | Gate definitions in `DEFINITION.md`; test evidence in `TESTING.md` |
| Architecture, modules, dependency direction, navigation ownership | `DESIGN.md` | Interface signatures in `CONTRACTS.md`; ADR rationale in `adr/` |
| Remote contract, DTOs, cache and retry policy, contract ids | `API_SPECS.md` | Internal seams in `CONTRACTS.md` |
| Visual and interaction specification, copy | `UI_SPEC.md` | Copy keys owned by `:core:presentation`; state types by `CONTRACTS.md` |
| Internal Kotlin seams and invariants | `CONTRACTS.md` | Model/wire types stay in `API_SPECS.md` §3 |
| Failure → state → copy | `ERROR_FLOW.md` | Type contracts in `DESIGN.md` §7; visuals in `UI_SPEC.md` §8 |
| Performance budgets | `PERFORMANCE.md` | Requirement in `REQUIREMENTS.md` `REQ-NFR-003` |
| Logging and diagnostics | `OBSERVABILITY.md` | Privacy policy in `SECURITY.md` |
| Security, privacy, advisories | `SECURITY.md` | Reporting wording mirrored in `CONTRIBUTING.md` §10 |
| Test strategy and test ids | `TESTING.md` | Gate definition in `DEFINITION.md`; budgets in `PERFORMANCE.md` |
| Gates (Ready/Done/release/docs) | `DEFINITION.md` | Criteria in `REQUIREMENTS.md` |
| Code conventions | `GUIDELINES.md` | Process in `CONTRIBUTING.md`; agent rules in `AGENTS.md` |
| Contribution process | `CONTRIBUTING.md` | Conventions in `GUIDELINES.md`; gates in `DEFINITION.md` |
| Milestones and sequencing | `TECHNICAL_PLAN.md` | Task detail in `BACKLOG.md` |
| Work items | `BACKLOG.md` | Issue state in GitHub |
| Decision status | `DECISION_BOARD.md` | Rationale in `adr/`; chronology in `PROJECT_LOG.md` |
| Chronology | `PROJECT_LOG.md` | Release notes generated from commits |
| Setup and commands | `README.md` | Process in `CONTRIBUTING.md` |
| Agent operating rules | `AGENTS.md` | Conventions/process/gates in the files above |

## 4. Documents deliberately not created

| Proposed | Verdict | Reason |
| --- | --- | --- |
| `ARCHITECTURE.md` | Rejected | `DESIGN.md` owns architecture end to end and gained the missing system overview (§0). A second file would restate it. |
| `SPECIFICATION.md` | Rejected | Behaviour is already owned by requirements (what), `UI_SPEC.md` (screens) and `API_SPECS.md` (remote). A third behavioural authority would conflict. |
| `ANALYTICS.md` | Replaced | There is no analytics SDK (`REQ-OBS-003`). `OBSERVABILITY.md` owns logging and diagnostics instead. The `EVT-###` namespace does not exist. |
| `SECURITY_ADVISORY_REGISTER.md` | Rejected for now | The register is a section of `SECURITY.md` (DEC-036) and becomes its own file when the first real finding exists. |
| `CHANGELOG.md` | Rejected | `PROJECT_LOG.md` plus release notes generated from Conventional Commits (DEC-042). |
| `DECISION_BOARD.md` as an ADR replacement | Rejected | The board indexes status; ADRs hold immutable rationale. Both exist, with disjoint roles. |
| Glossary document | Merged | Terminology rules live in `GUIDELINES.md` §7; canonical user-visible copy lives in `UI_SPEC.md` §6.2 with keys in `:core:presentation`. A separate glossary had no second user. |

## 5. Reconciliation rule (documentation vs implementation)

The repository contains the **Gradle/KMP build skeleton** (TASK-014, merged in PR #6 on 2026-09-30: wrapper, settings, convention plugins, the 11 modules of ADR-0001 and the five feature route declarations), the tracked `.gitignore` and the **repository-hygiene check** (TASK-016, merged in PR #13 on 2026-09-30), the **pinned version catalog with its enforcement** (TASK-015, merged in PR #10 on 2026-09-30) and the **accepted internal contract baseline** (TASK-019, PR #31), and **no feature behaviour**: no repository, use case, screen, product test or CI (`GAP-001`, `GAP-002`). Outside the build configuration and the route declarations, every normative document still describes **target state** (DEC-046). The rule that keeps this honest:

1. Until a feature is implemented, a document describes intended behaviour and says so in its `Status` header.
2. The change that implements or alters a behaviour **must update the owning document in the same pull request** (DEC-046). A document that contradicts shipped code is a defect in the document.
3. `HANDOFF.md` §7 and §8 record what has actually been observed; nothing else may claim execution.
4. This audit is re-run at each milestone; the open gaps in §6 are the input to that re-run.

## 6. Open gaps and conflicts

Severity: **S1** blocks planning · **S2** blocks a document · **S3** consistency.

### 6.1 Resolved during this audit

| ID | Item | Resolution |
| --- | --- | --- |
| `CONF-01` | `AGENTS.md` described an Android-only pipeline while the project is KMP with two native clients | `AGENTS.md` rewritten around the real platform set and the precedence chain |
| `CONF-02` | `AGENTS.md` demanded deliverables that already existed and had no permission or completion rules | Rewritten: roles with inputs/outputs, permitted and prohibited actions, escalation, completion |
| `CONF-03` | `API_SPECS.md` recommended Retrofit/OkHttp + Apollo while `DESIGN.md` mandated Ktor | DEC-011; `API_SPECS.md` §7.1 and §14 rewritten, ADR-0004 records the rationale |
| `CONF-04` | Priorities contradicted `assessment.md`: extras were promoted, and invented scope ranked equally | `REQUIREMENTS.md` rebuilt against the assessment with an explicit traceability table; voice search deferred |
| `CONF-05` | No identifiers, acceptance criteria or numeric non-functional requirements anywhere | `REQUIREMENTS.md` rewritten; 28 requirements, 66 acceptance criteria |
| `CONF-06` | Design briefs asked for species/gender filters and dynamic colour, both overruled by `UI_SPEC.md` | Briefs marked non-normative with the divergences listed inline; `UI_SPEC.md` remains normative |
| `CONF-07` | Two competing open-decision lists (`API_SPECS.md` §12 vs `DESIGN.md` §9) | `API_SPECS.md` §14 is now a resolved-decision table; `DESIGN.md` §9 lists resolutions and links the board |
| `CONF-08` | `API_SPECS.md` scope claimed an Android-only app | Scope corrected to the two-platform client |
| `CONF-09` | State and domain types declared in two files | `CONTRACTS.md` owns seams and invariants; `DESIGN.md` §4.1 reduced to flow plus a contract table |
| `CONF-10` | `826` hardcoded in normative documents while `API_SPECS.md` forbids hardcoding totals | Replaced with runtime-sourced text plus a dated observation note; `AC-REQ-FUNC-001-3` forbids constants |
| `CONF-11` | REST freshness deferred to a 90-day server directive while GraphQL had 24 h/7 d/30 d | DEC-012; one policy for both protocols, `API_SPECS.md` §7.1/§7.3 |
| `CONF-13` | Claim that the Gateway exposes rate-limit headers | Corrected after the live probe found none; `API_SPECS.md` §9 |
| `CONF-19` | "Review every character" was ambiguous between browsing and writing reviews | DEC-007: read-only; `NG-001` |
| `CONF-21` | Four names for one concept (Discovery / CharacterList / Characters / list view) | Route names in `DESIGN.md` §4.2, screen name "Discovery", requirement text uses "character list"; state type names unified to `CharacterListUiState`/`CharacterListIntent` |
| `CONF-22` | Design-brief filenames contained spaces, `·` and `—` | Renamed to `01-android-m3-expressive.md` and `02-ios-liquid-glass.md`; links updated |
| `CONF-26` | Three toolchain components were alpha-only, unrecorded as accepted risk | DEC-010 + ADR-0008; `CON-004` narrowed to the single shipped alpha; the other two explicitly not adopted |
| `CONF-27` | Interop strategy (SKIE vs Swift export) was open and load-bearing | DEC-013 + ADR-0003: no SKIE, platform-owned state holders |
| `CONF-42` | 2026-10-01: `TEST-UNIT-043` had no owner and the feature packages it names do not exist. Resolved by `DEC-068`: `TASK-017` owns it as a **staged** enforcement — destination ownership and the absence of an app-wide `NavHost` reference are asserted now, and package presence is asserted only once a feature module declares production source outside its route declaration; the check is never satisfied by empty stub source. `TESTING.md` §3.3 and §17 state the staged scope |
| `CONF-45` | 2026-10-01: the one-vs-two-solution wording. Resolved by `DEC-065`; `AGENTS.md` §4.2 is aligned by `TASK-032` and `README.md` §1 by `TASK-033`, and `TEST-UNIT-013` rule R5 already enforces the cap of two |
| `CONF-46` | 2026-10-01: whether the documentation-phase write limit still applies. Resolved by `DEC-064`: the limit is task-scoped, not phase-scoped; `AGENTS.md` §1/§4.1/§4.3 keep it and name the authorization shape, and `docs/CONTRIBUTING.md` §9 now records that the restriction never lifts on its own |
| `CONF-47` | 2026-10-01: `:core:domain` "depends on nothing" versus the `Flow` signatures of `IC-008`/`IC-009`. Resolved by `DEC-066`: an amendment pointer on ADR-0001 permits the Kotlin standard library and `kotlinx-coroutines-core` and forbids every project, platform, HTTP, UI and persistence dependency; `DESIGN.md` §3.1/§3.4, `REQUIREMENTS.md` `AC-REQ-NFR-009-3`, `TESTING.md` §3.2, `DEFINITION.md` §3 D7 and the `CONTRACTS.md` overview diagram state the amended rule |
| `CONF-48` | 2026-10-01: the mis-booked test ids. Resolved by `DEC-068`: `TASK-017` cites `TEST-UNIT-017` and `TEST-UNIT-043`, `TASK-018` cites the `VERSION` half of `TEST-UNIT-014`, `TASK-029` cites the `buildHealth` evidence, and `TESTING.md` §16's `REQ-NFR-006` row is corrected; `CONF-42` is closed with it |
| `CONF-36` | 2026-10-01: `REQUIREMENTS.md` cited `Task: TASK-014` for `REQ-FUNC-014` while `BACKLOG.md` §8.1 maps it to `TASK-028` | **Resolved in `TASK-019`**: the citation now reads `TASK-028`, the task the canonical coverage index assigns. |
| `CONF-37` | 2026-10-01: `TECHNICAL_PLAN.md` §2.1 listed the real `iosApp` target and its Swift packages as M0 deliverables, contradicted by `BACKLOG.md` (`TASK-051`, M2) and S11 | **Resolved in `TASK-019`** under precedence: §2.1 now defers the iOS app target, its Swift packages and the framework export to M2 (`TASK-051`, `TASK-078`); only the shared `iosTest` source sets, which exist without `iosApp/`, remain in the M0 evidence. |
| `CONF-38` | 2026-09-30: the `DESIGN.md` §3 edges diagram omitted three edges the module table requires | **Resolved in `TASK-014`**: the build declares the edges of the module table and the §3 diagram gained exactly those edges. |
| `CONF-39` | 2026-10-01: ADR-0001 says `:core:testing` “Depends on: test classpath only” while `DESIGN.md` §3.1 lists `:core:domain` and `:core:data` | **Resolved by `DEC-069`**: the module declares the two dependencies and **consumers** may reference it only from test source sets — the rule `DESIGN.md` §3.4 rule 9 and `TEST-UNIT-017` already enforce; the ADR wording is aligned by pointer in the `TASK-017` change that owns the executable rule. No module edge changes. |
| `CONF-40` | 2026-09-30: the iOS framework had no producer able to reach the feature-owned state types | **Resolved by `DEC-058`** / [ADR-0012](adr/0012-ios-framework-export.md): one umbrella framework from the new `:core:ios` export module, recorded as `TASK-078`, and the README §8 / HANDOFF §8 build paths now name `:core:ios` rather than `:core:presentation`. |
| `CONF-41` | 2026-10-01: `README.md` §5 promised a `benchmark` module and `TESTING.md` §13.1 a root `contract-live/` directory, while ADR-0001 forbids modules beyond its list and `PERF-Q1` reserves the harness decision | **Resolved by `DEC-070`** under precedence and executed in `TASK-033`: `PERF-Q1` stays unresolved and unclaimed, the `README.md` §5/§9 wording no longer promises a `benchmark` module, `contract-live` is stated as a source set/directory rather than a Gradle project, and no benchmark module is created or scheduled before the `PERF-Q1` decision. |
| `CONF-43` | 2026-10-01: Android test source-set names (`androidUnitTest`/`androidInstrumentedTest`) differ from the plugin's (`androidHostTest`/`androidDeviceTest`) | **Resolved by `DEC-069`**: `TASK-024` owns the correction, which lands when the first host/device test source set is enabled; `TESTING.md` §4.1/§6.3/§13.1, `GUIDELINES.md` §3.2 and `docs/templates/test-case.md` are corrected in that same change. Not closed by prose alone. |
| `CONF-44` | 2026-10-01: `README.md` §1 still said “Eight explicit modules” and §11 item 6 was stale about pre-release artifacts | **Resolved**: §11 item 6 was corrected in `TASK-015` and §1 no longer says “Eight explicit modules”; `TASK-033` carries the correction. |
| `CONF-49` | 2026-09-30: current-state claims made stale by PR #6 and by `.gitignore` having been tracked since `078fe3f` | **Resolved in `TASK-015`** (OD-5): the affected current-state statements were realigned then, and `TASK-032` realigned `AGENTS.md` §1 again after TASK-016 merged. |
| `CONF-53` | 2026-10-01 (S1): `DEFINITION.md` D2/D14, `TESTING.md` §14, `CONTRIBUTING.md` §5 and `DEC-054` required the complete check set on both runners before a change is `Done`, while no CI existed, the B1 pull requests had empty status rollups and several suites cannot exist before M1/M2. **Resolved by `DEC-071`** (owner decision, 2026-10-01): staged activation — the list never shrinks, a suite becomes mandatory and non-empty in the same change that introduces its harness, the final milestone and release gates still require the complete set, and a pre-CI change is *integrated and locally verified*, never *Done under D2*. `DEFINITION.md` D2, `TESTING.md` §14.2 and `CONTRIBUTING.md` §5.3 state it |
| `CONF-29`, `CONF-30` | Shared ViewModels invalidated much of `DESIGN.md` once bridging was decided | `DESIGN.md` §2–§6 rewritten for platform-owned state holders and the feature-per-module layout |
| `CONF-31` | `API_SPECS.md` §7.1/§12 assumed OkHttp/Apollo | Rewritten for the Ktor stack and the app-level cache |
| `CONF-32` | Single pinned alpha with no policy | `GUIDELINES.md` alpha-dependency rule + ADR-0008 upgrade and fallback plan |
| `CONF-33` | 2026-09-30: the owner asked for a REST/GraphQL choice in Settings while `DECISION_BOARD.md` §3 and ADR-0004 rejected GraphQL as a shipped protocol (S2, blocked `API_SPECS.md` §2) | Escalated to the owner, who chose "both via Ktor, no Apollo": DEC-056 + ADR-0011; the §3 rejection narrowed to Apollo; ADR-0004 carries an amendment pointer |
| `CONF-34` | 2026-09-30: the owner removed Locations from the navigation while `DEC-005`, `REQ-FUNC-008` and ADR-0001 fixed it as a destination and module (S2, blocked `UI_SPEC.md` §6.4) | Owner decision recorded as DEC-055 + ADR-0010; `DEC-005` marked amended; ADR-0001 carries an amendment pointer |
| `CONF-35` | 2026-09-30: `README.md`/`README.es.md` §10 still stated "REST. GraphQL is documented as the alternative, not shipped" while `docs/API_SPECS.md` §2, `CONTRACTS.md` `IC-011`/`IC-021` and ADR-0011 ship both protocols, and the architecture diagrams showed a single REST adapter with no settings store (S3, blocked `README.md` §10) | Owner directive of 2026-09-30 to make the data layer's two remote data sources plus the settings data source explicit: README rows corrected to "both ship, REST default"; `DESIGN.md` §1/§2/§4.6/§6, `CONTRACTS.md` `IC-011` and `API_SPECS.md` §2 now state the three-data-source inventory and the per-request selection through `IC-021`; ADR-0005's `protocol` component updated to `rest`/`graphql` |
| `CONF-52` | 2026-10-01: the owner-supplied block order differed from `BACKLOG.md` §2.6 and, verbatim, introduced three later-block dependencies while omitting `TASK-021` | Owner confirmed the dependency-safe reconciliation: `TASK-031` and its dependant `TASK-073` are in B2; `TASK-020` precedes `TASK-012` and `TASK-075` in B5; `TASK-021` follows `TASK-005` in B4. The source plan, block table and task rows now agree, and `DEC-063` remains unchanged |

### 6.2 Open gaps

| ID | Sev | Gap | Impact | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| `GAP-001` | S1 | No feature implementation: the Gradle/KMP build skeleton is merged (TASK-014, PR #6), the `.gitignore` is tracked and completed and the hygiene check is merged (TASK-016, PR #13 on 2026-09-30), the version catalog is complete (TASK-015, merged in PR #10), and the internal contract baseline is accepted (TASK-019, PR #31), but there is no domain model, data layer, screen or product test | Every normative document describes target state; the assignment requires a deliverable | Implementation Engineer | Partially addressed: build skeleton, catalog and the automated repository-hygiene scan exist; feature source code from M1 |
| `GAP-002` | S1 | No CI exists, while the merge gate requires the full suite on both platforms (DEC-054) | The gate cannot be enforced until workflows and branch protection exist | Delivery Planner | Open — workflows tracked in `BACKLOG.md`; branch protection is a human repository setting |
| `GAP-003` | S2 | No design-token export (`tokens.json`) and no code-generation or parity test yet | The token parity requirement (`REQ-UX-002`) is specified but unenforced | Implementation Engineer | Open — tracked in `BACKLOG.md` |
| `GAP-004` | S2 | Figma file is inaccessible to anonymous clients and no PNG exports are committed | The visual specification cannot be checked by a reviewer without Figma access (`RISK-008`) | UI/UX Designer | Open — directory and procedure in `docs/figma/README.md`; exports tracked in `BACKLOG.md` |
| `GAP-005` | S2 | The reference device for performance budgets is not named | Budgets are numeric but not yet attributable to hardware (`REQ-NFR-003`) | Implementation Engineer + QA | Open — `PERFORMANCE.md` records the assumption |
| `GAP-006` | S2 | iOS snapshot baselines do not exist | The iOS half of the required gate is specified but has no baselines | QA & Validation | Open — lands with the iOS milestone (DEC-025) |
| `GAP-007` | S3 | Error states are specified in `UI_SPEC.md` §8 but are not drawn in Figma | Screenshot tests will baseline the implementation, not a design frame | UI/UX Designer | Open — recorded in `UI_SPEC.md` §8 and `PROJECT_LOG.md` |
| `GAP-008` | S3 | `README.md` shows no screenshots | A reviewer cannot see the product before running it | Documentation Maintainer | Open — depends on `GAP-004` and on the first runnable milestone |
| `GAP-009` | S3 | Issue tracker state was to be created with the first milestone | `BACKLOG.md` is canonical and its `TASK-###` rows now resolve: issues exist for the merged tasks (#5, #9, #12), the seven B1 tasks (#17…#23) and the four decision rows (#26…#29) | Delivery Planner | **Resolved 2026-10-01** — remaining rows open their issue when the task starts (`CONTRIBUTING.md` §4) |
| `GAP-011` | S3 | The dependency-policy checks P4, P5, I5 and I8, and P7's included-build half, infer inline versions, declarations and catalog declarations from **source text**. The **supported** shapes are: Kotlin-DSL version expressions of the seven forms P4 matches; `libs.<alias>` chains, including across lines; a settings-file `versionCatalogs { create("<name>") { from(files("<path>")) } }` on its own line; and a TOML value that is a plain quoted scalar or `version.ref`. The remaining limits are (L1) the deprecated positional notation (a configuration name applied to `"group", "name", "version"`); (L2) the generated `LibrariesForLibs` type used from build-logic through a classpath workaround; (L3) versionless-entry governance by group prefix, so the BOM's actual membership is proven only when the entry is first resolved; (L4) any Kotlin-DSL or TOML spelling outside the shapes above. | A deliberate or unusual spelling can place a version outside the catalog, or hide a declaration from the README inventory, without failing `check` | Implementation Engineer | Open. Recommend a backlog task after TASK-029: check every project's declared `ExternalModuleDependency` set and the settings-level plugin requests against the catalog, derive `Declared` from them, and retire the P4/I5 patterns |
| `GAP-010` | S2 | `SECURITY.md` §9.3 makes Gradle dependency-verification metadata or a lockfile the target state "when the build lands"; the build landed with TASK-014 and no task owns the adoption | A re-pointed or tampered artifact resolution would not be detected; the version catalog pins versions, not artifact bytes | Security Reviewer + Delivery Planner | Open — recommended as a dedicated task after CI exists (TASK-025), because platform-specific artifacts (`aapt2` per OS, Kotlin/Native per host) need checksums generated on every CI OS |
| `GAP-012` | S2 | A post-merge review reproduced four false negatives in `verifyModuleBoundaries`: an effective feature-to-feature edge declared in a custom configuration inherited by `commonMainImplementation` passed; a direct Ktor dependency in `:core:designsystem` passed; a file under `/navigation/` with no destination passed S1; and `domain`/`presentation` files with wrong Kotlin packages passed S3 | The check certified states the architecture forbids; `AC-REQ-NFR-009-1`/`-2` were claimed on a check that failed open | Implementation Engineer | Open — `TASK-088` corrects it; the reproduced seeds are recorded in `LOG-0049` |
| `GAP-013` | S2 | A post-merge review reproduced an artifact bypass: with `VERSION` set to `invalid-version`, `verifyDependencyPins` failed as designed but `:androidApp:assembleDebug` produced an APK whose badging reported `versionName='invalid-version'` | The single-source guarantee of `AC-REQ-NFR-006-2` did not protect the documented artifact path | Implementation Engineer | Open — `TASK-089` wires the Android artifact paths to the canonical validation |

### 6.3 Open conflicts

| ID | Sev | Conflict | Blocked artifact | Owner | Recommendation | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `CONF-50` | S2 | No first-party `material-color-utilities` artifact exists: `com.google.material:material-color-utilities` and `com.google.android.material:material-color-utilities` both answer `404` on Maven Central and Google Maven (2026-09-30), and the only published coordinates are third-party ports (`com.materialkolor:material-color-utilities`). `DESIGN.md` §4.4 and `UI_SPEC.md` §5.4 need `QuantizerCelebi`, `Score` and `TonalPalette`. | TASK-005 (`CharacterAccentResolver`) | System Architect + Implementation Engineer (Android) | Options: (a) vendor the Apache-2.0 source subset into `:feature:discovery`; (b) adopt a community port with its own rationale and ADR; (c) use the restricted copy inside another artifact — rejected, it is `@RestrictTo`. Recommendation: (a), which adds no dependency and keeps the colour policy project-owned | Open |
| `CONF-51` | S2 | The detekt Gradle plugin cannot be pinned under TASK-015's rules: the newest stable release, 1.23.8, is built against Kotlin 2.0.21, Gradle 8.12.1 and AGP 8.8.1, and the vendor documents no Kotlin 2.4.20 / Gradle 9.7.0 / AGP 9.3.1 support for it; every 2.x release on the Gradle Plugin Portal is an alpha (`2.0.0-alpha.0`…`2.0.0-alpha.6`, observed 2026-09-30), and ADR-0008 permits exactly one alpha. The entry is therefore unpinned and recorded in `DESIGN.md` §3.5's "Not pinned" register (TASK-015 OD-6). | TASK-029 (the `detekt` task) | QA & Validation Engineer | (a) TASK-029 pins 1.23.8 only after proving it runs on this build with Kotlin 2.4.20, Gradle 9.7.0 and AGP 9.3.1, including any Kotlin-version override of the `detekt` configuration, recorded with its rationale; (b) otherwise wait for a stable 2.x; (c) accepting a 2.x alpha requires an owner decision amending ADR-0008. Recommended: (a) evaluated inside TASK-029, falling back to (b) | Open |

## 7. Verification performed

| Check | Method | Result |
| --- | --- | --- |
| Internal Markdown links | Scripted resolution of every relative link against the filesystem, over all Markdown files | 0 broken links (4 earlier failures were `DOCUMENTATION_AUDIT.md` itself, written by this audit) |
| Requirement identifier uniqueness and coverage | Scripted extraction of `REQ-*`, `AC-*`, `DEC-*`, `TEST-*`, `TASK-*`, `IC-*`, `API-*` across all documents | 28 requirements, 66 acceptance criteria, 54 decisions, 80 test ids, 73 task ids, 20 contracts, 18 API contract ids; no undefined references, and no test id claimed by two requirements |
| Acceptance criteria coverage | Every requirement checked for at least one `AC-<REQ>-n` | All 28 requirements have acceptance criteria |
| Task coverage | Every requirement id referenced from `BACKLOG.md` | Present for all Must/Should requirements |
| Test coverage | Every requirement id referenced from `TESTING.md` traceability | Present for all Must/Should requirements |
| ADR references | Every `adr/NNNN-…md` link resolved | All resolve to an existing file |
| Cross-document contradiction scan | Full read of every document plus scripted identifier checks | No open conflicts (§6.3) |
| Live API behaviour | Direct HTTPS probes on 2026-09-29 | `count=826`, `pages=42`, 20 per page; `Cache-Control: public, max-age=7776000, immutable` with `ETag`; filtered-empty and detail `404` are also marked cacheable; `page=43` → 404; batch `1,99999` → 200 with existing resources only; one id → object, two ids → array; GraphQL `character(id:"99999")` → `{"data":{"character":null}}` at HTTP 200; GraphQL `info.next` is an integer; GraphQL empty filter → all `info` fields null; unknown field → 400 `GRAPHQL_VALIDATION_FAILED`; no rate-limit headers observed |
| Toolchain versions | Primary sources: Maven Central and Google Maven metadata; vendor release notes and API documentation | Kotlin 2.4.20; Ktor 3.6.0; Coil 3.6.3; Koin 4.2.2; Navigation Compose 2.10.2; SplashScreen 1.2.0; Material3 1.4.0 stable / 1.5.0-alpha29 Expressive; Compose BOM 2026.09.00; Android API 37; `androidx.lifecycle` KMP 2.12.0-alpha04 (no stable); DataStore KMP 1.3.0-alpha11 (no stable); Liquid Glass available from iOS 26.0; KMP Swift export Alpha; SKIE 0.10.15 |
| Repository state | `git ls-files`, `git log`, `git status --ignored`, tree inspection | 9 tracked Markdown files before this work; 31 after; 14 commits, single branch `main` before the audit branch; no source, build, CI or ignore files |
| Dependency-pin verification | HTTP existence probes against Maven Central, Google Maven and the Gradle Plugin Portal, dated 2026-09-30, for every entry `TASK-015` pins; plus `./gradlew verifyDependencyPolicy` on branch `build/version-catalog-inventory` | Every decided pin answers `200`; the `TASK-015` policy tasks pass with the S1–S63 seed matrix (`LOG-0032`…`LOG-0035`; `LOG-0035` is the latest evidence). No first-party `material-color-utilities` artifact exists (`CONF-50`), and detekt is **not pinned** because no supported stable line exists (`CONF-51`) |
| Builds, linters and tests | Executed 2026-09-30 on branch `build/gradle-kmp-skeleton` | The Gradle/KMP skeleton builds: `./gradlew --version`, `./gradlew projects`, `./gradlew help --warning-mode=all`, `./gradlew assemble`, `./gradlew build`, `:androidApp:assembleDebug` (no `iosApp/` present and from a clean clone) and the per-module dependency reports all succeeded; no deprecation warning originates in this repository's scripts. No test exists to run and no app is launchable (no activity). Evidence and the full command list: `PROJECT_LOG.md` LOG-0026 |
| Repository and secret hygiene | Executed 2026-09-30 on branch `build/repository-hygiene`; corrected on review twice and merged in PR #13 (`LOG-0037`, `LOG-0038`, `LOG-0039`): `./gradlew verifyRepositoryHygiene` and the S1–S14, R1–R15 and T1–T14 seed matrices in disposable clones | Corrected head `963da9c` passes with 95 commit-eligible paths, 704 reachable objects, 326 unique blobs scanned and verified, 102 unique historical paths classified and 0 findings, and the task re-executes on a second run and under a reused configuration cache; `check` and `build` include it; all 38 ignore sentinels are ignored and the shared Xcode/project files are not; each `HYG-01`…`HYG-07` and `SEC-026-01`…`SEC-026-12` rule failed its own seed (S1–S14, plus HYG-05 and the review regressions) and the clean state passed again after each seed was removed, with no seed value printed. Evidence: `PROJECT_LOG.md` LOG-0036, LOG-0037 and LOG-0038, merged per LOG-0039 |

## 8. Audit completion and next actions

| Item | State |
| --- | --- |
| Documents created | 24 |
| Documents amended after the audit | 12 (the `TASK-014` and `TASK-015` changes) + the `TASK-016`…`TASK-019`, `TASK-032`, `TASK-034` reconciliations |
| Documents amended | 9 |
| Documents renamed | 2 (design briefs) |
| Documents rejected | 6 (listed in §4) |
| Open gaps | 12 (§6.2; `GAP-009` is resolved) |
| Open conflicts | 2 (§6.3: `CONF-50`, `CONF-51`; every other conflict — `CONF-36`…`CONF-49` and `CONF-52`…`CONF-53` — is resolved in §6.1) |
| Blocking gaps for the next milestone | `GAP-001`, `GAP-002` |

Next actions, in order: the post-merge correction programme — `TASK-087` (the accepted `DEC-071` decision, this change), `TASK-088` (boundary hardening), `TASK-089` (`VERSION` artifact wiring) and `TASK-090` (the B1 documentation reconciliation) — then B2, whose first obligations are `TASK-024` (the shared fakes and fixtures) and `TASK-025` (the workflows and both runners, with branch protection as the human step). Each is tracked in `BACKLOG.md` and sequenced in `TECHNICAL_PLAN.md`.

## 9. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-10-01 | Post-merge review: `CONF-53` recorded and resolved by `DEC-071` (staged activation of the required set); `GAP-012` (four reproduced boundary false negatives) and `GAP-013` (the reproduced `VERSION` artifact bypass) recorded with their correction tasks. Counts: 12 open gaps, 2 open conflicts. | `DEC-071`, `TASK-087`…`TASK-090`, `LOG-0049` |
| 2026-10-01 | B1 documentation reconciliation: `CONF-36`, `CONF-37`, `CONF-38`, `CONF-39`, `CONF-40`, `CONF-41`, `CONF-43`, `CONF-44` and `CONF-49` are recorded as resolved (`DEC-069`, `DEC-070`, and the `TASK-019` corrections); the audit now carries 2 open conflicts (`CONF-50`, `CONF-51`). | `TASK-019`, `DEC-069`, `DEC-070` |
| 2026-10-01 | `GAP-009` resolved: the tracker carries the merged tasks, the seven B1 tasks and the four decision rows, so the `TASK-###` references resolve. §8 now counts 10 open gaps and 2 open conflicts. | `TASK-019`, `DEC-044` |
| 2026-10-01 | `CONF-42`, `CONF-45`…`CONF-48` recorded as resolved: `DEC-066` amends the `:core:domain` rule (Kotlin stdlib + `kotlinx-coroutines-core`), `DEC-064` makes the agent write boundary task-scoped, `DEC-065` fixes the two-solution cap, `DEC-067` splits the `VERSION` acceptance, and `DEC-068` re-maps the test ids and stages `TEST-UNIT-043` under `TASK-017`. §6.3 now carries 7 open conflicts, and the next-actions line names the B1 block. | `DEC-064`…`DEC-068`, `docs/BACKLOG.md` §9 |
| 2026-09-29 | Audit created. 24 documents authored, 9 amended, 2 renamed, 6 rejected; all conflicts closed and 9 gaps recorded. | DEC-021, DEC-046, DEC-052, DEC-053, DEC-054 |
| 2026-09-29 | Post-audit reconciliation: `TEST-UNIT-014`…`016` collisions between `REQUIREMENTS.md` and `TESTING.md` resolved by allocating `TEST-UNIT-043`…`045` for `REQ-NFR-009`…`011` and `REQ-FUNC-014`; `API-CHAR-005` declared in the `API_SPECS.md` identifier index; the Paging 3 contradiction in `API_SPECS.md` §8 removed; `DESIGN.md` §6 diagram aligned to the `CONTRACTS.md` state names. | DEC-016, DEC-021, DEC-052 |
| 2026-09-30 | `CONF-33` and `CONF-34` recorded and resolved by owner decisions DEC-055 and DEC-056; inventory extended to ADR-0011. | DEC-055, DEC-056 |
| 2026-09-30 | `CONF-35` recorded and resolved: the READMEs' protocol row corrected and the three-data-source inventory stated in `DESIGN.md`, `CONTRACTS.md` `IC-011` and `API_SPECS.md` §2; ADR-0005 amended. | DEC-055, DEC-056 |
| 2026-09-30 | Reconciled with the TASK-014 build skeleton: §5 and §7 now describe the observed build state, `GAP-001` is split into build skeleton (addressed) and feature implementation (open), and `CONF-36`…`CONF-44` are recorded in §6.3. | TASK-014, DEC-057, `PROJECT_LOG.md` LOG-0026 |
| 2026-09-30 | `TASK-015` marked `Done`: PR #10 merged, so §5, `GAP-001` and the README status row no longer describe it as in review. | TASK-015, `PROJECT_LOG.md` LOG-0035 |
| 2026-09-30 | Third review round of PR #10: `GAP-011` now names the supported syntax and the residual limits L1–L4, and §7 cites LOG-0035 as the latest dependency-policy evidence. No count in §8 changes: no gap or conflict was added or closed. | TASK-015, `PROJECT_LOG.md` LOG-0035 |
| 2026-09-30 | Second review round of PR #10: `GAP-011` records the source-text limits of the policy checks and recommends the Gradle-model redesign; §8's open-gap count is 11. | TASK-015, `PROJECT_LOG.md` LOG-0034 |
| 2026-09-30 | Review round of PR #10: `CONF-51` rewritten as an OD-6 blocked item (detekt unpinned), the §7 dependency-pin row corrected, and `LOG-0033` records the corrected policy rule set. `CONF-51` stays open, so §8's counts are unchanged. | TASK-015, `PROJECT_LOG.md` LOG-0033 |
| 2026-09-30 | Reconciled with TASK-015: §5 and `GAP-001` state the merged build and the completed catalog, `GAP-010` records the unowned dependency-verification metadata, `CONF-45`…`CONF-48`, `CONF-50` and `CONF-51` are recorded as open, `CONF-49` is resolved, and `CONF-44` becomes partially resolved. | TASK-015, DEC-060, DEC-061, `PROJECT_LOG.md` LOG-0032 |
| 2026-09-30 | `CONF-40` resolved by `DEC-058` / [ADR-0012](adr/0012-ios-framework-export.md) and moved to §6.1; recorded as `TASK-078`. | TASK-078, `PROJECT_LOG.md` LOG-0027 |
| 2026-09-30 | Reconciled with TASK-016: `GAP-001` no longer names a pending `.gitignore` scan, §7 gained the repository-hygiene verification row, and the next-actions line now starts with reviewing the in-review TASK-016 change. No count in §8 changes: the new decision `DEC-062` is not a gap or a conflict. | TASK-016, `DEC-062`, `PROJECT_LOG.md` LOG-0036 |
| 2026-09-30 | Reconciled with the TASK-016 review corrections (`LOG-0037`): §7's repository-hygiene row now cites the corrected head and both matrices. No gap or conflict was added or closed, so §8's counts are unchanged. | TASK-016, `DEC-062`, `PROJECT_LOG.md` LOG-0037 |
| 2026-10-01 | TASK-016 merged: PR #13 merged into `main` on 2026-09-30 and issue #12 closed, so the next-actions line no longer asks for the hygiene change to be reviewed and §7's row is the merged state. No gap or conflict was added or closed, so §8's counts are unchanged. | TASK-016, `DEC-062`, `PROJECT_LOG.md` LOG-0039 |
| 2026-10-01 | `CONF-52` recorded: the owner-supplied block order and the canonical `DEC-063` grouping differ in four placements, while the source order introduces three later-block dependency edges and omits live `TASK-021`. No block assignment changed pending the owner's resolution. | DEC-063, `BACKLOG.md` §2.6 |
| 2026-10-01 | `CONF-52` resolved by owner direction: `TASK-031` and `TASK-073` are together in B2, `TASK-020` precedes its B5 dependants, and `TASK-021` remains in B4. The source plan and canonical documentation are aligned without changing `DEC-063`. | DEC-063, `BACKLOG.md` §2.6, `PROJECT_LOG.md` LOG-0041 |
