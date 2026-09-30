# DOCUMENTATION_AUDIT.md — Documentation System Audit

- **Status:** Active — audit performed 2026-09-29 on branch `docs/documentation-system`
- **Last verified:** 2026-09-30
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

The repository contains the **Gradle/KMP build skeleton** (TASK-014, merged in PR #6 on 2026-09-30: wrapper, settings, convention plugins, the 11 modules of ADR-0001 and the five feature route declarations), the tracked `.gitignore` and the **pinned version catalog with its enforcement** (TASK-015, in review), and **no feature behaviour**: no repository, use case, screen, product test or CI (`GAP-001`). Outside the build configuration and the route declarations, every normative document still describes **target state** (DEC-046). The rule that keeps this honest:

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
| `CONF-29`, `CONF-30` | Shared ViewModels invalidated much of `DESIGN.md` once bridging was decided | `DESIGN.md` §2–§6 rewritten for platform-owned state holders and the feature-per-module layout |
| `CONF-31` | `API_SPECS.md` §7.1/§12 assumed OkHttp/Apollo | Rewritten for the Ktor stack and the app-level cache |
| `CONF-32` | Single pinned alpha with no policy | `GUIDELINES.md` alpha-dependency rule + ADR-0008 upgrade and fallback plan |
| `CONF-33` | 2026-09-30: the owner asked for a REST/GraphQL choice in Settings while `DECISION_BOARD.md` §3 and ADR-0004 rejected GraphQL as a shipped protocol (S2, blocked `API_SPECS.md` §2) | Escalated to the owner, who chose "both via Ktor, no Apollo": DEC-056 + ADR-0011; the §3 rejection narrowed to Apollo; ADR-0004 carries an amendment pointer |
| `CONF-34` | 2026-09-30: the owner removed Locations from the navigation while `DEC-005`, `REQ-FUNC-008` and ADR-0001 fixed it as a destination and module (S2, blocked `UI_SPEC.md` §6.4) | Owner decision recorded as DEC-055 + ADR-0010; `DEC-005` marked amended; ADR-0001 carries an amendment pointer |
| `CONF-35` | 2026-09-30: `README.md`/`README.es.md` §10 still stated "REST. GraphQL is documented as the alternative, not shipped" while `docs/API_SPECS.md` §2, `CONTRACTS.md` `IC-011`/`IC-021` and ADR-0011 ship both protocols, and the architecture diagrams showed a single REST adapter with no settings store (S3, blocked `README.md` §10) | Owner directive of 2026-09-30 to make the data layer's two remote data sources plus the settings data source explicit: README rows corrected to "both ship, REST default"; `DESIGN.md` §1/§2/§4.6/§6, `CONTRACTS.md` `IC-011` and `API_SPECS.md` §2 now state the three-data-source inventory and the per-request selection through `IC-021`; ADR-0005's `protocol` component updated to `rest`/`graphql` |
| `CONF-40` | 2026-09-30: the iOS framework had no producer that could reach the feature-owned state types; owner chose the umbrella export module and the decision is recorded as `DEC-058` / [ADR-0012](adr/0012-ios-framework-export.md), which amends the ADR-0001 module set and adds `TASK-078` |

### 6.2 Open gaps

| ID | Sev | Gap | Impact | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| `GAP-001` | S1 | No feature implementation: the Gradle/KMP build skeleton is merged (TASK-014, PR #6), the `.gitignore` is tracked and the version catalog is complete (TASK-015, in review), but there is no domain model, data layer, screen or product test | Every normative document describes target state; the assignment requires a deliverable | Implementation Engineer | Partially addressed: build skeleton and catalog by TASK-014/TASK-015; source code from M1; TASK-016's remaining acceptance is the secret scan |
| `GAP-002` | S1 | No CI exists, while the merge gate requires the full suite on both platforms (DEC-054) | The gate cannot be enforced until workflows and branch protection exist | Delivery Planner | Open — workflows tracked in `BACKLOG.md`; branch protection is a human repository setting |
| `GAP-003` | S2 | No design-token export (`tokens.json`) and no code-generation or parity test yet | The token parity requirement (`REQ-UX-002`) is specified but unenforced | Implementation Engineer | Open — tracked in `BACKLOG.md` |
| `GAP-004` | S2 | Figma file is inaccessible to anonymous clients and no PNG exports are committed | The visual specification cannot be checked by a reviewer without Figma access (`RISK-008`) | UI/UX Designer | Open — directory and procedure in `docs/figma/README.md`; exports tracked in `BACKLOG.md` |
| `GAP-005` | S2 | The reference device for performance budgets is not named | Budgets are numeric but not yet attributable to hardware (`REQ-NFR-003`) | Implementation Engineer + QA | Open — `PERFORMANCE.md` records the assumption |
| `GAP-006` | S2 | iOS snapshot baselines do not exist | The iOS half of the required gate is specified but has no baselines | QA & Validation | Open — lands with the iOS milestone (DEC-025) |
| `GAP-007` | S3 | Error states are specified in `UI_SPEC.md` §8 but are not drawn in Figma | Screenshot tests will baseline the implementation, not a design frame | UI/UX Designer | Open — recorded in `UI_SPEC.md` §8 and `PROJECT_LOG.md` |
| `GAP-008` | S3 | `README.md` shows no screenshots | A reviewer cannot see the product before running it | Documentation Maintainer | Open — depends on `GAP-004` and on the first runnable milestone |
| `GAP-009` | S3 | Issue tracker state does not exist yet for the `TASK-###` rows | `BACKLOG.md` is canonical but its issue links cannot resolve | Delivery Planner | Open — created with the first milestone |
| `GAP-011` | S3 | The dependency-policy checks P4, P5, I5 and I8, and P7's included-build half, infer inline versions, declarations and catalog declarations from **source text**. The **supported** shapes are: Kotlin-DSL version expressions of the seven forms P4 matches; `libs.<alias>` chains, including across lines; a settings-file `versionCatalogs { create("<name>") { from(files("<path>")) } }` on its own line; and a TOML value that is a plain quoted scalar or `version.ref`. The remaining limits are (L1) the deprecated positional notation (a configuration name applied to `"group", "name", "version"`); (L2) the generated `LibrariesForLibs` type used from build-logic through a classpath workaround; (L3) versionless-entry governance by group prefix, so the BOM's actual membership is proven only when the entry is first resolved; (L4) any Kotlin-DSL or TOML spelling outside the shapes above. | A deliberate or unusual spelling can place a version outside the catalog, or hide a declaration from the README inventory, without failing `check` | Implementation Engineer | Open. Recommend a backlog task after TASK-029: check every project's declared `ExternalModuleDependency` set and the settings-level plugin requests against the catalog, derive `Declared` from them, and retire the P4/I5 patterns |
| `GAP-010` | S2 | `SECURITY.md` §9.3 makes Gradle dependency-verification metadata or a lockfile the target state "when the build lands"; the build landed with TASK-014 and no task owns the adoption | A re-pointed or tampered artifact resolution would not be detected; the version catalog pins versions, not artifact bytes | Security Reviewer + Delivery Planner | Open — recommended as a dedicated task after CI exists (TASK-025), because platform-specific artifacts (`aapt2` per OS, Kotlin/Native per host) need checksums generated on every CI OS |

### 6.3 Open conflicts

| ID | Sev | Conflict | Blocked artifact | Owner | Recommendation | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `CONF-36` | S3 | `docs/REQUIREMENTS.md` l.191 cites `Task: TASK-014` for `REQ-FUNC-014`, while `BACKLOG.md` defines TASK-014 as the build skeleton and maps `REQ-FUNC-014` to TASK-028 (§8.1). Evidence: `REQUIREMENTS.md` l.191; `BACKLOG.md` §3 row TASK-014, §4 row TASK-028, §8.1. | `REQUIREMENTS.md` l.191 | Requirements Analyst | Replace the citation with TASK-028 | Open |
| `CONF-37` | S3 | `TECHNICAL_PLAN.md` §2.1 lists "the `iosApp` target and its Swift packages" among the M0 deliverables, while `BACKLOG.md` places them in TASK-051 (M2) and TASK-014 together with sequencing constraint S11 requires the Android build with the iOS app absent. Evidence: `TECHNICAL_PLAN.md` §2.1 Deliverables; `BACKLOG.md` §5 row TASK-051; `TECHNICAL_PLAN.md` §5 S11. | `TECHNICAL_PLAN.md` §2.1 | Delivery Planner | Move the phrase to M2 | Open |
| `CONF-38` | S3 | The `DESIGN.md` §3 edges diagram omitted `:feature:episodes → :core:presentation` (present in the ADR-0001 diagram for episodes and, before ADR-0010, for locations) and omitted `:feature:settings → :core:domain`, `:core:presentation`, which `DESIGN.md` §3.2 and `CONTRACTS.md` `IC-023` (`RemoteProtocol`) require. GUIDELINES.md §3.1 makes the ADR authoritative. | `DESIGN.md` §3 diagram | System Architect | — | **Resolved in TASK-014**: the build declares the edges of the module table, and the §3 diagram gained exactly those three edges |
| `CONF-39` | S3 | ADR-0001 says `:core:testing` "Depends on: test classpath only"; `DESIGN.md` §3.1 says it depends on `:core:domain` and `:core:data`. The build cannot express "test classpath only" as a module property: the dependencies are declared on the module. | ADR-0001 module table | System Architect | Confirm the interpretation ("consumed from test source sets only") and align the ADR wording | Open |
| `CONF-40` | S2 | `README.md` §8 and `HANDOFF.md` §8 built the iOS framework from `:core:presentation`, which may not depend on the features whose state types the iOS app consumes, and no umbrella export module existed. Per-module frameworks were rejected: the Kotlin documentation states that several frameworks in one Swift app are limited and that an umbrella framework is the supported arrangement. | iOS framework build path; TASK-051 | System Architect | — | **Resolved 2026-09-30** by `DEC-058` / [ADR-0012](adr/0012-ios-framework-export.md): one framework from a new `:core:ios` export module; recorded as `TASK-078` |
| `CONF-41` | S3 | `README.md` §5 says the build will add a `benchmark` module and `TESTING.md` §13.1 shows a root `contract-live/` directory, while ADR-0001 forbids modules beyond its list and `PERFORMANCE.md` `PERF-Q1` already requires a decision for the benchmark harness. TASK-014 created neither. | `README.md` §5; `TESTING.md` §13.1 | System Architect | Decide; `README.md` §5 is corrected in TASK-033 | Open |
| `CONF-42` | S3 | The `BACKLOG.md` TASK-014 row cites `AC-REQ-NFR-009-2` but not `TEST-UNIT-043`, the id `TESTING.md` §3.3 assigns to that criterion, and no task lists `TEST-UNIT-043`. The automated forms of `TEST-UNIT-012`, `TEST-UNIT-017` and `TEST-UNIT-019` belong to TASK-036, TASK-017 and TASK-025 respectively. | `BACKLOG.md` TASK-014 row; `TESTING.md` §16 | Delivery Planner | Assign `TEST-UNIT-043` (TASK-017 recommended) and annotate the TASK-014 row's expected tests | Open |
| `CONF-43` | S3 | **Recorded because TASK-014 selected the Android KMP library plugin.** `TESTING.md` §4.1, §6.3 and §13.1, `GUIDELINES.md` §3.2 and `docs/templates/test-case.md` name the Android test source sets `androidUnitTest` and `androidInstrumentedTest`, while `com.android.kotlin.multiplatform.library` names them `androidHostTest` and `androidDeviceTest`. TASK-014 enables no Android test source set. | `TESTING.md` §13.1; `GUIDELINES.md` §3.2 | QA & Validation Engineer | Update the names in TASK-024, when the first host test source set is enabled | Open |
| `CONF-45` | S3 | `AGENTS.md` §4.2 prohibits "a second solution for a concern that already has one", while `REQ-NFR-002`, `GUIDELINES.md` §3.6 item 2 and `SECURITY.md` §9.1 cap a concern at **two**. `TESTING.md` §4.2 documents two HTTP test doubles (`MockEngine` and `MockWebServer`), and `DESIGN.md` §3.5 lists both. | `AGENTS.md` §4.2; `README.md` §1 | Documentation Maintainer | Align `AGENTS.md` §4.2 to "a third solution, or a second one without a recorded rationale", and `README.md` §1 in TASK-033. The check enforces at most two, because `REQUIREMENTS.md` ranks above `AGENTS.md` for what to build | Open |
| `CONF-46` | S3 | `AGENTS.md` §1 and §4.3 keep agent writes limited to documentation unless an explicit, task-scoped authorization is given, while `CONTRIBUTING.md` §9 says "That restriction lifts when the build exists". The build has existed since TASK-014. | `AGENTS.md` §4.3 | Documentation Maintainer | The owner decides whether the documentation phase is over, and the losing file is aligned. TASK-015 followed `AGENTS.md`, through the owner's OD-1 authorization | Open |
| `CONF-47` | S2 | "`:core:domain` depends on nothing" appears in ADR-0001 l.62, `DESIGN.md` §3.4 rule 1, `AC-REQ-NFR-009-3`, `TESTING.md` §3.2 ("no dependency at all"), `DEFINITION.md` §3 D7 and the `CONTRACTS.md` overview diagram (l.31). Yet `IC-008` declares `fun observe(): Flow<Set<CharacterId>>` in `:core:domain`, and `ObserveFavoriteIds` (`IC-009`) returns `Flow` there (`CONTRACTS.md` §5), which requires `kotlinx-coroutines-core`. | TASK-036 and the `TEST-UNIT-012` assertion | System Architect | Amend the rule (an amendment pointer, as ADR-0010 and ADR-0012 did, or a superseding ADR) to "no project module and no platform, HTTP, UI or persistence library; the Kotlin standard library and `kotlinx-coroutines-core` (`Flow`) are permitted". The Requirements Analyst aligns `AC-REQ-NFR-009-3`, and QA aligns `TESTING.md` §3.2 and `DEFINITION.md` D7. TASK-015 pins coroutines anyway | Open |
| `CONF-48` | S3 | Test-id mapping. TASK-017 lists `TEST-UNIT-014` (pins and `VERSION`), although its acceptance is module boundaries. TASK-018 cites `AC-REQ-NFR-006-2` but lists `TEST-UNIT-018`, which asserts SDK levels, instead of `TEST-UNIT-014`, the id `TESTING.md` §3.3 assigns to it. `TESTING.md` §16 maps `REQ-NFR-006` to `TEST-UNIT-018`. TASK-029 lists `TEST-UNIT-013`, which TASK-015 now implements. | `BACKLOG.md` TASK-017/TASK-018/TASK-029 rows; `TESTING.md` §16 | Delivery Planner | Re-map TASK-017 → `TEST-UNIT-017`, `TEST-UNIT-043`; TASK-018 → `TEST-UNIT-014` (the `VERSION` half); TASK-029 → the `buildHealth` evidence. QA re-checks the §16 row. Links to `CONF-42` | Open |
| `CONF-50` | S2 | No first-party `material-color-utilities` artifact exists: `com.google.material:material-color-utilities` and `com.google.android.material:material-color-utilities` both answer `404` on Maven Central and Google Maven (2026-09-30), and the only published coordinates are third-party ports (`com.materialkolor:material-color-utilities`). `DESIGN.md` §4.4 and `UI_SPEC.md` §5.4 need `QuantizerCelebi`, `Score` and `TonalPalette`. | TASK-005 (`CharacterAccentResolver`) | System Architect + Implementation Engineer (Android) | Options: (a) vendor the Apache-2.0 source subset into `:feature:discovery`; (b) adopt a community port with its own rationale and ADR; (c) use the restricted copy inside another artifact — rejected, it is `@RestrictTo`. Recommendation: (a), which adds no dependency and keeps the colour policy project-owned | Open |
| `CONF-51` | S2 | The detekt Gradle plugin cannot be pinned under TASK-015's rules: the newest stable release, 1.23.8, is built against Kotlin 2.0.21, Gradle 8.12.1 and AGP 8.8.1, and the vendor documents no Kotlin 2.4.20 / Gradle 9.7.0 / AGP 9.3.1 support for it; every 2.x release on the Gradle Plugin Portal is an alpha (`2.0.0-alpha.0`…`2.0.0-alpha.6`, observed 2026-09-30), and ADR-0008 permits exactly one alpha. The entry is therefore unpinned and recorded in `DESIGN.md` §3.5's "Not pinned" register (TASK-015 OD-6). | TASK-029 (the `detekt` task) | QA & Validation Engineer | (a) TASK-029 pins 1.23.8 only after proving it runs on this build with Kotlin 2.4.20, Gradle 9.7.0 and AGP 9.3.1, including any Kotlin-version override of the `detekt` configuration, recorded with its rationale; (b) otherwise wait for a stable 2.x; (c) accepting a 2.x alpha requires an owner decision amending ADR-0008. Recommended: (a) evaluated inside TASK-029, falling back to (b) | Open |
| `CONF-49` | S3 | Current-state claims made stale by the merge of PR #6 (2026-09-30) and by `.gitignore` having been tracked since `078fe3f`: TASK-014 "in review", `.gitignore` absent, and no build. | `AGENTS.md` §1, `README.md` §14, `README.es.md` §14, `BACKLOG.md` §2.4, `HANDOFF.md` §1/§3/§8, `TESTING.md` preamble, `SECURITY.md` §9.3, `PROJECT_LOG.md` §1.3, `TECHNICAL_PLAN.md` §1 | Documentation Maintainer | — | **Resolved in TASK-015** (OD-5) |
| `CONF-44` | S3 | `README.md` §1 says "Eight explicit modules" (superseded DEC-019 wording) and §11 item 6 said three toolchain artifacts are pinned pre-release, while ADR-0001 defines 11 projects and ADR-0008 permits exactly one alpha. `README.es.md` mirrors both. | `README.md` §1 | Documentation Maintainer | §11 item 6 is corrected in TASK-015 (exactly one pre-release artifact is pinned, and no module declares it yet); §1 stays with TASK-033 | Partially resolved |

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

## 8. Audit completion and next actions

| Item | State |
| --- | --- |
| Documents created | 24 |
| Documents amended after the audit | 12 (the `TASK-014` and `TASK-015` changes) |
| Documents amended | 9 |
| Documents renamed | 2 (design briefs) |
| Documents rejected | 6 (listed in §4) |
| Open gaps | 11 (§6.2) |
| Open conflicts | 12 (§6.3: `CONF-36`, `CONF-37`, `CONF-39`, `CONF-41`…`CONF-43`, `CONF-45`…`CONF-48`, `CONF-50`, `CONF-51`; `CONF-38`, `CONF-40` and `CONF-49` are resolved, `CONF-44` is partially resolved) |
| Blocking gaps for the next milestone | `GAP-001`, `GAP-002` |

Next actions, in order: add the secret scan that completes `.gitignore` (`TASK-016`); add CI workflows and enable branch protection (`GAP-002`); produce the Figma exports (`GAP-004`); add the token export and parity test (`GAP-003`); name the reference device and record the first measurements (`GAP-005`). Each is tracked in `BACKLOG.md` and sequenced in `TECHNICAL_PLAN.md`.

## 9. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-09-29 | Audit created. 24 documents authored, 9 amended, 2 renamed, 6 rejected; all conflicts closed and 9 gaps recorded. | DEC-021, DEC-046, DEC-052, DEC-053, DEC-054 |
| 2026-09-29 | Post-audit reconciliation: `TEST-UNIT-014`…`016` collisions between `REQUIREMENTS.md` and `TESTING.md` resolved by allocating `TEST-UNIT-043`…`045` for `REQ-NFR-009`…`011` and `REQ-FUNC-014`; `API-CHAR-005` declared in the `API_SPECS.md` identifier index; the Paging 3 contradiction in `API_SPECS.md` §8 removed; `DESIGN.md` §6 diagram aligned to the `CONTRACTS.md` state names. | DEC-016, DEC-021, DEC-052 |
| 2026-09-30 | `CONF-33` and `CONF-34` recorded and resolved by owner decisions DEC-055 and DEC-056; inventory extended to ADR-0011. | DEC-055, DEC-056 |
| 2026-09-30 | `CONF-35` recorded and resolved: the READMEs' protocol row corrected and the three-data-source inventory stated in `DESIGN.md`, `CONTRACTS.md` `IC-011` and `API_SPECS.md` §2; ADR-0005 amended. | DEC-055, DEC-056 |
| 2026-09-30 | Reconciled with the TASK-014 build skeleton: §5 and §7 now describe the observed build state, `GAP-001` is split into build skeleton (addressed) and feature implementation (open), and `CONF-36`…`CONF-44` are recorded in §6.3. | TASK-014, DEC-057, `PROJECT_LOG.md` LOG-0026 |
| 2026-09-30 | Third review round of PR #10: `GAP-011` now names the supported syntax and the residual limits L1–L4, and §7 cites LOG-0035 as the latest dependency-policy evidence. No count in §8 changes: no gap or conflict was added or closed. | TASK-015, `PROJECT_LOG.md` LOG-0035 |
| 2026-09-30 | Second review round of PR #10: `GAP-011` records the source-text limits of the policy checks and recommends the Gradle-model redesign; §8's open-gap count is 11. | TASK-015, `PROJECT_LOG.md` LOG-0034 |
| 2026-09-30 | Review round of PR #10: `CONF-51` rewritten as an OD-6 blocked item (detekt unpinned), the §7 dependency-pin row corrected, and `LOG-0033` records the corrected policy rule set. `CONF-51` stays open, so §8's counts are unchanged. | TASK-015, `PROJECT_LOG.md` LOG-0033 |
| 2026-09-30 | Reconciled with TASK-015: §5 and `GAP-001` state the merged build and the completed catalog, `GAP-010` records the unowned dependency-verification metadata, `CONF-45`…`CONF-48`, `CONF-50` and `CONF-51` are recorded as open, `CONF-49` is resolved, and `CONF-44` becomes partially resolved. | TASK-015, DEC-060, DEC-061, `PROJECT_LOG.md` LOG-0032 |
| 2026-09-30 | `CONF-40` resolved by `DEC-058` / [ADR-0012](adr/0012-ios-framework-export.md) and moved to §6.1; recorded as `TASK-078`. | TASK-078, `PROJECT_LOG.md` LOG-0027 |
