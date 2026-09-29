# DOCUMENTATION_AUDIT.md — Documentation System Audit

- **Status:** Active — audit performed 2026-09-29 on branch `docs/documentation-system`
- **Last verified:** 2026-09-29
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
| `docs/adr/0001`…`0009` | Decision rationale | System Architect | Active | Normative (immutable once accepted) | Never after acceptance |
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

The repository currently contains **no implementation**: no Gradle build, no source code, no CI, no `.gitignore` (`GAP-001`). Every normative document therefore describes **target state** (DEC-046). The rule that keeps this honest:

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

### 6.2 Open gaps

| ID | Sev | Gap | Impact | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| `GAP-001` | S1 | No implementation: no Gradle build, no source, no version catalog, no `.gitignore` | Every normative document describes target state; the assignment requires a deliverable | Implementation Engineer | Open — first milestone work, tracked in `BACKLOG.md` |
| `GAP-002` | S1 | No CI exists, while the merge gate requires the full suite on both platforms (DEC-054) | The gate cannot be enforced until workflows and branch protection exist | Delivery Planner | Open — workflows tracked in `BACKLOG.md`; branch protection is a human repository setting |
| `GAP-003` | S2 | No design-token export (`tokens.json`) and no code-generation or parity test yet | The token parity requirement (`REQ-UX-002`) is specified but unenforced | Implementation Engineer | Open — tracked in `BACKLOG.md` |
| `GAP-004` | S2 | Figma file is inaccessible to anonymous clients and no PNG exports are committed | The visual specification cannot be checked by a reviewer without Figma access (`RISK-008`) | UI/UX Designer | Open — directory and procedure in `docs/figma/README.md`; exports tracked in `BACKLOG.md` |
| `GAP-005` | S2 | The reference device for performance budgets is not named | Budgets are numeric but not yet attributable to hardware (`REQ-NFR-003`) | Implementation Engineer + QA | Open — `PERFORMANCE.md` records the assumption |
| `GAP-006` | S2 | iOS snapshot baselines do not exist | The iOS half of the required gate is specified but has no baselines | QA & Validation | Open — lands with the iOS milestone (DEC-025) |
| `GAP-007` | S3 | Error states are specified in `UI_SPEC.md` §8 but are not drawn in Figma | Screenshot tests will baseline the implementation, not a design frame | UI/UX Designer | Open — recorded in `UI_SPEC.md` §8 and `PROJECT_LOG.md` |
| `GAP-008` | S3 | `README.md` shows no screenshots | A reviewer cannot see the product before running it | Documentation Maintainer | Open — depends on `GAP-004` and on the first runnable milestone |
| `GAP-009` | S3 | Issue tracker state does not exist yet for the `TASK-###` rows | `BACKLOG.md` is canonical but its issue links cannot resolve | Delivery Planner | Open — created with the first milestone |

### 6.3 Open conflicts

None. Every conflict identified in this audit is either resolved (§6.1) or recorded as an accepted assumption (`CON-003`, `CON-004`, `PERFORMANCE.md`) or as a deferred decision (`DECISION_BOARD.md` §4). A new conflict is raised as `CONF-###` here and escalated rather than resolved silently.

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
| Builds, linters and tests | Not executed | Nothing to run: the repository has no build (`GAP-001`). No claim in this documentation set rests on an executed build, test or app run |

## 8. Audit completion and next actions

| Item | State |
| --- | --- |
| Documents created | 24 |
| Documents amended | 9 |
| Documents renamed | 2 (design briefs) |
| Documents rejected | 6 (listed in §4) |
| Open gaps | 9 (§6.2) |
| Open conflicts | 0 |
| Blocking gaps for the next milestone | `GAP-001`, `GAP-002` |

Next actions, in order: create the Gradle build and version catalog (`GAP-001`); add CI workflows and enable branch protection (`GAP-002`); produce the Figma exports (`GAP-004`); add the token export and parity test (`GAP-003`); name the reference device and record the first measurements (`GAP-005`). Each is tracked in `BACKLOG.md` and sequenced in `TECHNICAL_PLAN.md`.

## 9. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-09-29 | Audit created. 24 documents authored, 9 amended, 2 renamed, 6 rejected; all conflicts closed and 9 gaps recorded. | DEC-021, DEC-046, DEC-052, DEC-053, DEC-054 |
| 2026-09-29 | Post-audit reconciliation: `TEST-UNIT-014`…`016` collisions between `REQUIREMENTS.md` and `TESTING.md` resolved by allocating `TEST-UNIT-043`…`045` for `REQ-NFR-009`…`011` and `REQ-FUNC-014`; `API-CHAR-005` declared in the `API_SPECS.md` identifier index; the Paging 3 contradiction in `API_SPECS.md` §8 removed; `DESIGN.md` §6 diagram aligned to the `CONTRACTS.md` state names. | DEC-016, DEC-021, DEC-052 |
