# DECISION_BOARD.md — Decision Index and Status Board

- **Status:** Active
- **Last verified:** 2026-10-01
- **Owner:** System Architect (see `AGENTS.md`)
- **Authoritative for:** the current *status* and *location* of every project decision. Rationale lives in the ADR files; chronology lives in `PROJECT_LOG.md`. This file MUST NOT restate rationale.

## 1. How to use this board

- Every decision has one `DEC-###` identifier, used by reference everywhere else (`REQUIREMENTS.md`, `TECHNICAL_PLAN.md`, `BACKLOG.md`, ADRs).
- Status values: `Proposed` · `Accepted` · `Deferred` · `Superseded` · `Rejected`.
- An `Accepted` decision that changes architecture MUST have an ADR. Decisions that only fix scope or process do not need one.
- A decision is never deleted. Superseding a decision adds a new row and marks the old one `Superseded` with a pointer.
- Urgency: `Blocking` (work stops without it) · `High` · `Normal`.
- Blocking impact names the artifact that cannot proceed until the decision is `Accepted`.

## 2. Board

| DEC | Title | Category | Status | Urgency | Blocking impact | ADR | Source |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DEC-001 | Delivery scope: staged dual-native (shared KMP core, Android first, iOS second) | Scope | Accepted | Blocking | All documents | [ADR-0002](adr/0002-platform-targets.md) | Interview A1 |
| DEC-002 | Assessment extras: all committed except voice search, which is deferred | Scope | Accepted | Blocking | `REQUIREMENTS.md` §5.3, `UI_SPEC.md` §6.2 | — | Interview A2 |
| DEC-003 | Depth concentrates on code and architecture quality | Process | Accepted | High | `TECHNICAL_PLAN.md` | — | Interview A3 |
| DEC-004 | Favorites in MVP on both platforms, stored locally | Scope | Accepted | High | `REQUIREMENTS.md` §5.1 | [ADR-0007](adr/0007-favorites-storage.md) | Interview A4 |
| DEC-005 | Episodes/Locations ship as placeholders | Scope | Accepted (amended by DEC-055) | Normal | `REQUIREMENTS.md` §5.3 | — | Interview A5 |
| DEC-006 | Baseline accessibility plus i18n-ready resources with English and Spanish shipped | Scope | Accepted | High | `REQUIREMENTS.md` §8 | — | Interview A6 |
| DEC-007 | "Review" means read-only browsing; no authored content | Scope | Accepted | Blocking | `REQUIREMENTS.md` §1.2 | — | Interview A7 |
| DEC-008 | iOS minimum deployment target 18.0, Liquid Glass via availability check with material fallback | Platform | Accepted | Blocking | iOS module, `UI_SPEC.md` §4.2 | [ADR-0003](adr/0003-ui-sharing-strategy.md) | Interview B1 |
| DEC-009 | Android `minSdk` 26 | Platform | Accepted | Blocking | Android module | [ADR-0002](adr/0002-platform-targets.md) | Interview B2 |
| DEC-010 | Material 3 Expressive pinned at `1.5.0-alpha29` with Compose BOM 2026.09.00 | Toolchain | Accepted | High | `:core:designsystem` | [ADR-0008](adr/0008-alpha-dependencies.md) | Interview B3 |
| DEC-011 | Ktor 3.6.0 + kotlinx.serialization as the single remote stack | Data | Accepted | Blocking | `:core:data`, `API_SPECS.md` §7.1/§12 | [ADR-0004](adr/0004-rest-client.md) | Interview B4 |
| DEC-012 | Explicit app freshness policy: 24 h fresh / 7 d stale-while-revalidate / 30 d offline | Data | Accepted | Blocking | `:core:data`, `API_SPECS.md` §7 | [ADR-0005](adr/0005-caching-strategy.md) | Interview B5 |
| DEC-013 | No SKIE and no shared ViewModels; shared domain/data/state contracts, platform-owned state holders | Architecture | Accepted | Blocking | `DESIGN.md` §4–§6, iOS module | [ADR-0003](adr/0003-ui-sharing-strategy.md) | Interview B6 |
| DEC-014 | Koin 4.2.2 runtime DSL, no compiler plugin | Architecture | Accepted | Normal | DI graph | [ADR-0006](adr/0006-presentation-state.md) | Interview B7 |
| DEC-015 | `:core:presentation` owns cross-feature UI-state primitives; each feature owns its own state classes; canonical copy keys live in `:core:presentation` | Architecture | Accepted (amended by DEC-052) | Blocking | `DESIGN.md` §3, `CONTRACTS.md` | [ADR-0006](adr/0006-presentation-state.md) | Interview C1 |
| DEC-016 | Paging by a shared custom pager in `:core:data` | Data | Accepted | High | `:core:data`, `REQ-FUNC-001` | [ADR-0009](adr/0009-pagination-strategy.md) | Interview C2 |
| DEC-017 | Favorites persisted with `expect/actual` stores (Android DataStore, iOS `UserDefaults`) | Data | Accepted | High | `:core:data` | [ADR-0007](adr/0007-favorites-storage.md) | Interview C3 |
| DEC-018 | App-level response cache with explicit keys and an injected clock | Data | Accepted | Blocking | `:core:data`, `API_SPECS.md` §7 | [ADR-0005](adr/0005-caching-strategy.md) | Interview C4 |
| DEC-019 | Module structure: keep the original eight-module shape | Architecture | Superseded | — | Superseded by DEC-052 | [ADR-0001](adr/0001-module-boundaries.md) | Interview C5 |
| DEC-052 | Feature-per-module: one module per user-facing capability, Clean Architecture layers inside each module, shared infrastructure in `:core:*` | Architecture | Accepted | Blocking | `DESIGN.md` §3, every module path and build command | [ADR-0001](adr/0001-module-boundaries.md) | Owner directive 2026-09-29 |
| DEC-053 | TDD protocol: red → commit → green → commit → refactor → commit → push, with phase commits preserved (no squash) | Process | Accepted | Blocking | `CONTRIBUTING.md`, `DEFINITION.md`, `TESTING.md` | — | Owner directive 2026-09-29 |
| DEC-054 | CI/CD: the entire test suite must run green on both platforms before a pull request can be approved or merged | Process | Accepted | Blocking | CI workflows, branch protection, `DEFINITION.md` §2 | — | Owner directive 2026-09-29 |
| DEC-055 | Settings replaces Locations as the fourth navigation destination (order Characters · Episodes · Favorites · Settings); Settings holds a sounds preference, the remote-protocol choice and "Delete favorites" with confirmation; `:feature:settings` replaces `:feature:locations`; preferences persist in `:core:data` | Scope | Accepted | High | `REQUIREMENTS.md` `REQ-FUNC-008`, `REQ-FUNC-033`…`REQ-FUNC-035`, `UI_SPEC.md` §6.5, module set | [ADR-0010](adr/0010-settings-destination.md) | Owner directive 2026-09-30 |
| DEC-056 | REST and GraphQL both ship, selectable at runtime in Settings (REST default), through the single Ktor client with hand-written GraphQL operations; no Apollo | Data | Accepted | High | `:core:data`, `API_SPECS.md` §2, §7.2, `TESTING.md` §4.3 | [ADR-0011](adr/0011-runtime-remote-protocol.md) | Owner directive 2026-09-30 |
| DEC-057 | Build logic: shared Gradle configuration in convention plugins in the included build `build-logic/`; module build scripts declare only their plugins and dependencies, and every project edge is declared in the consuming module's build script | Toolchain | Accepted | Normal | Every module build script | — | Owner directive 2026-09-30 |
| DEC-058 | iOS interop packaging: one Kotlin framework produced by a new `:core:ios` export module that exports the five `:feature:*` and the four other `:core:*` modules with `api`; `iosApp` links that one framework and no other | Architecture | Accepted | Blocking | `iosApp` linkage, `TASK-051`, the `api` declarations of every shared module | [ADR-0012](adr/0012-ios-framework-export.md) | Owner decision 2026-09-30 resolving CONF-40 |
| DEC-059 | Pull requests are integrated with a **merge commit** and no other method; rebase merges and squash merges are disabled, `required_linear_history` is removed from the `main` ruleset, and the branch is preserved with its individual commits reachable from the merge commit | Process | Accepted (supersedes the merge-method half of DEC-041) | High | GitHub merge settings, `CONTRIBUTING.md` §3.5, `TECHNICAL_PLAN.md` §8.2, `GUIDELINES.md` §7.5 | — | Owner directive 2026-09-30 |
| DEC-060 | Version-catalog scope: the catalog pins the full planned dependency inventory ahead of first use; every entry is justified in `DESIGN.md` §3.5 and mirrored with its declaration state in `README.md` §15; the Compose BOM is the only BOM | Toolchain | Accepted | Normal | `gradle/libs.versions.toml` and every dependency addition | — | Owner directive 2026-09-30 (TASK-015) |
| DEC-061 | Dependency-policy enforcement: Gradle verification tasks from the `multiverse.dependency.policy` plugin in `build-logic/`, wired into the root `check`, implement `TEST-UNIT-013`, `TEST-UNIT-014` and `TEST-UNIT-051` | Toolchain | Accepted | Normal | Root build, `build-logic/`, every dependency change | — | Owner directive 2026-09-30 (TASK-015) |
| DEC-062 | Repository and secret hygiene: the root `check` runs `verifyRepositoryHygiene` from the dependency-free `multiverse.repository.hygiene` plugin, implementing `TEST-UNIT-026`. It scans the commit-eligible working set (tracked plus untracked, non-ignored files) and every unique blob reachable from **all local refs after a full fetch**; a shallow checkout, a non-Git directory, a Git error or an incomplete object set fails closed rather than reporting zero findings. Findings are redacted — rule id, root-relative path, line and blob identity only, never the matched value | Toolchain | Accepted | High | Root build/`check`, `.gitignore`, TASK-025's checkout depth and CI wiring, `TEST-UNIT-026` | — | Owner directive 2026-09-30 (TASK-016) |
| DEC-063 | Block-based execution: from 2026-10-01 the remaining work is delivered in nine execution blocks (`B1`…`B9`), worked together inside a block and sequenced as blocks. A block is an execution grouping, not a delivery one: the `Milestone` column and the milestone exit criteria are unchanged, every block but B4 draws its members from a single milestone, each task still lands as its own pull request under its own `TASK-###` with its own TDD cycle (DEC-053) and its own required check set (DEC-054), and no task MAY depend on a task in a later block. `TASK-016` was the last task executed individually; the grouping is owned by `BACKLOG.md` §2.6 | Process | Accepted | High | `BACKLOG.md` §2.6 and §3–§6, `TECHNICAL_PLAN.md` §9, `HANDOFF.md` | — | Owner directive 2026-10-01 |
| DEC-020 | Native resource files per platform plus a canonical key list and a parity test | Process | Accepted | High | Localisation tasks | — | Interview C6 |
| DEC-021 | `ERROR_FLOW.md` is the canonical owner of the failure→state→copy chain | Documentation | Accepted | Blocking | `DESIGN.md` §7, `UI_SPEC.md` §8 | — | Interview D1 |
| DEC-022 | Hand-written design tokens checked against a committed `tokens.json` export | Process | Accepted | Normal | Design-system modules | — | Interview D2 |
| DEC-023 | Accessibility verified by automated checks plus a recorded manual checklist | Process | Accepted | High | `TESTING.md`, `DEFINITION.md` | — | Interview D3 |
| DEC-024 | Visual regression via Roborazzi (Android) and swift-snapshot-testing (iOS) with committed baselines | Process | Accepted | High | `TESTING.md` | — | Interview D4 |
| DEC-025 | iOS verified with snapshot tests and previews | Process | Accepted | Normal | iOS milestone | — | Interview D5 |
| DEC-026 | Coil 3.6.3 on Android; `URLCache` + `NSCache` on iOS | Data | Accepted | High | Image pipeline | — | Interview D6 |
| DEC-027 | Phone portrait only; tablet, foldable and landscape are non-goals | Scope | Accepted | Normal | `REQUIREMENTS.md` §1.2 | — | Interview D7 |
| DEC-028 | CI: Android gate on every change; iOS job on `main` and on demand | Process | Superseded | — | Superseded by DEC-054 | — | Interview E1 |
| DEC-029 | Test layers: unit, snapshot and contract suites all required on every pull request; the live-network contract check runs separately as a scheduled signal, while the PR gate runs the contract suite in fixture/replay mode | Process | Accepted (amended by DEC-054) | High | `TESTING.md` | — | Interview E2, owner directive 2026-09-29 |
| DEC-030 | Network tests use Ktor `MockEngine` with committed JSON fixtures in `commonTest` | Process | Accepted | High | `TESTING.md` | — | Interview E3 |
| DEC-031 | Targeted coverage of cache, pager, mappers and failure mapping; no global threshold | Process | Accepted | Normal | `TESTING.md` | — | Interview E4 |
| DEC-032 | ktlint, detekt, Android Lint, dependency-analysis; SwiftLint and swift-format | Toolchain | Accepted | High | `GUIDELINES.md`, CI | — | Interview E5 |
| DEC-033 | Numeric performance budgets with a named reference device and a measurement method | Process | Accepted | Blocking | `PERFORMANCE.md` | — | Interview E6 |
| DEC-034 | Android screenshots executed with Roborazzi on Robolectric | Toolchain | Accepted | Normal | Android test task | — | Interview E7 |
| DEC-035 | Security scope is an app-level threat model with no secrets, no PII and permission minimisation | Security | Accepted | Blocking | `SECURITY.md` | — | Interview F1 |
| DEC-036 | The security advisory register is a section of `SECURITY.md`, not a separate file | Documentation | Accepted | Normal | `SECURITY.md` | — | Interview F2 |
| DEC-037 | Dependabot/Renovate with GitHub Actions pinned by SHA and a dependency-analysis check | Security | Accepted | High | CI, dependency hygiene | — | Interview F3 |
| DEC-038 | Observability is a logging contract plus debug-only diagnostics; no analytics SDK | Security | Accepted | Blocking | `OBSERVABILITY.md`, `REQUIREMENTS.md` §11 | — | Interview F4 |
| DEC-039 | Strict log redaction; release builds log errors only | Security | Accepted | High | `OBSERVABILITY.md`, `SECURITY.md` | — | Interview F5 |
| DEC-040 | Delivery sequenced as one milestone per platform, each with its own definition of done | Process | Accepted | Blocking | `TECHNICAL_PLAN.md` | — | Interview G1 |
| DEC-041 | Trunk-based development; Conventional Commits; squash merge **replaced** by rebase merge that preserves the TDD phase commits (DEC-053) | Process | Accepted (amended twice: merge-method half superseded by DEC-059) | High | `CONTRIBUTING.md` | — | Interview G2, owner directive 2026-09-29 |
| DEC-042 | No `CHANGELOG.md`; `PROJECT_LOG.md` plus generated release notes | Documentation | Accepted | Normal | `PROJECT_LOG.md` | — | Interview G3 |
| DEC-043 | One shared `VERSION`; tags `vMAJOR.MINOR.PATCH`; GitHub Release publishes the APK | Process | Accepted | Blocking | Release workflow | — | Interview G4 |
| DEC-044 | `BACKLOG.md` is the canonical work index; GitHub Issues carry state and link by ID | Process | Accepted | Normal | `BACKLOG.md` | — | Interview G5 |
| DEC-045 | Public repository, public read-only Figma link, committed PNG exports under `docs/figma/` | Documentation | Accepted | Blocking | `README.md`, `UI_SPEC.md` §1 | — | Interview G6 |
| DEC-046 | Documentation describes target state, carries Status/Last verified headers, and is updated in the same change that ships a feature | Documentation | Accepted | Blocking | All documents | — | Interview G7 |
| DEC-047 | Documentation in English with `README.es.md` as the only translation | Documentation | Accepted | High | `README.es.md` | — | Interview H1 |
| DEC-048 | `CONTRIBUTING.md` documents the full team-shaped process | Process | Accepted | High | `CONTRIBUTING.md` | — | Interview H2 |
| DEC-049 | Agents may edit and open pull requests; merging, tagging, releases, repository settings and secrets are human-only | Process | Accepted | Blocking | `AGENTS.md` | — | Interview H3 |
| DEC-050 | Mermaid diagrams in the owning document; C4 context/container described in text | Documentation | Accepted | Normal | `DESIGN.md` §0 | — | Interview H4 |
| DEC-051 | Five reusable templates: ADR, backlog item, test case, pull request, bug report | Documentation | Accepted | Normal | `docs/templates/` | — | Interview H5 |

## 3. Superseded and rejected

| Item | Status | Reason | Reference |
| --- | --- | --- | --- |
| Original eight-module layout (`:shared:*`, `:android:designsystem`, `:android:feature:characters`) | Superseded by DEC-052 | Owner directive: feature-per-module with Clean Architecture inside each feature module. | DEC-052, `DESIGN.md` §3 |
| Android-only PR gate with iOS deferred to `main` | Superseded by DEC-054 | Owner directive: all tests, both platforms, required before approval. | DEC-054 |
| Squash merge | Superseded by DEC-041 (amended) | Squashing would destroy the red/green/refactor commit sequence mandated by DEC-053. | DEC-041, DEC-053 |
| Rebase merge as the integration method | Superseded by DEC-059 | The owner requires the record of the previous branch, which a rebase merge discards by rewriting SHAs: linear history was traded for auditability. Squash merge stays superseded by both decisions, because it does not preserve the individual commits. | DEC-059, DEC-041, DEC-053 |
| Retrofit/OkHttp as the shipped REST client | Superseded by DEC-011 | JVM-only; cannot serve the shared data layer. | `API_SPECS.md` §14 |
| GraphQL (Apollo Kotlin) as the shipped protocol | Rejected for MVP; GraphQL-through-Ktor admitted by DEC-056 (Apollo still rejected) | `POST` breaks the standard HTTP cache, needs a normalized cache, and no screen requires it. REST stays documented as the alternative. | `API_SPECS.md` §2 |
| Paging 3 | Rejected | No iOS equivalent; the page contract is simple enough for a shared pager. | DEC-016 |
| Shared ViewModels with SKIE | Rejected | Alpha AndroidX artifact plus a third-party compiler plugin on the critical path. | DEC-013 |
| Kotlin Swift export | Deferred | Officially Alpha at the time of the decision. | DEC-013, DEF-004 |
| Multiplatform DataStore for favorites | Rejected | Alpha artifact; `expect/actual` stores keep the data layer stable. | DEC-017 |
| SQLDelight | Rejected | A database for one set of IDs. | DEC-017 |
| Compose Multiplatform UI | Rejected | Discards the two native design languages and `docs/design/*`. | DEC-001 |
| Global coverage threshold | Rejected | Rewards trivial tests; conflicts with the test-quality rules. | DEC-031 |
| Analytics SDK | Rejected | No assessment value, extra dependency, privacy surface. | DEC-038 |
| `SPECIFICATION.md` as a separate document | Rejected | Would duplicate requirements and `UI_SPEC.md` behaviour. | `DOCUMENTATION_AUDIT.md` §4 |
| `ARCHITECTURE.md` as a separate document | Rejected | `DESIGN.md` already owns architecture end to end. | `DOCUMENTATION_AUDIT.md` §4 |
| `SECURITY_ADVISORY_REGISTER.md` as a separate document | Rejected for now | Promote from `SECURITY.md` §7 when the first real finding exists. | DEC-036 |
| `CHANGELOG.md` | Rejected | Duplicates `PROJECT_LOG.md` and generated release notes. | DEC-042 |
| PlantUML/C4 tooling | Rejected | GitHub renders Mermaid natively; no build tooling needed. | DEC-050 |

## 4. Deferred decisions

| DEC | Question | Revisit when | Reference |
| --- | --- | --- | --- |
| DEC-002 | Voice search implementation and its permission flow | A platform speech requirement appears | `REQUIREMENTS.md` `REQ-FUNC-030` |
| DEC-005 | Real Episodes/Locations screens | After the iOS milestone; Locations also needs an entry point, since it has no navigation destination (DEC-055) | `REQUIREMENTS.md` `REQ-FUNC-031/032` |
| DEC-055 | Which sound effects exist and when they play (the Sounds preference is stored but plays nothing) | A sound set is specified and traced to an accepted decision | `REQUIREMENTS.md` `REQ-FUNC-036` (`DEF-005`) |
| DEC-013 | Kotlin Swift export as the single interop path | Swift export becomes Stable | DEF-004 |
| DEC-025 | iOS snapshot baseline breadth | iOS milestone starts | `TESTING.md` |
| DEC-029 | Whether live contract tests move into the merge gate | The API publishes a versioned or stable contract | `API_SPECS.md` §1 |

## 5. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-09-29 | Board created; DEC-001…DEC-051 recorded from the documentation decision interview; superseded and rejected alternatives filed in §3. | This audit |
| 2026-09-30 | DEC-055 added (Settings replaces Locations in the navigation and holds three settings); DEC-056 added (runtime REST/GraphQL selection through Ktor); DEC-005 marked amended; the §3 GraphQL rejection narrowed to Apollo; the sound set filed as deferred. | [ADR-0010](adr/0010-settings-destination.md), `PROJECT_LOG.md` LOG entry of 2026-09-30 |
| 2026-09-30 | DEC-057 added (shared Gradle configuration in `build-logic/` convention plugins; project edges in the consuming module's build script). No ADR: a tooling decision, not architecture (§1). | `PROJECT_LOG.md` LOG-0026, TASK-014 |
| 2026-09-30 | DEC-058 added (one iOS umbrella framework from `:core:ios`); `CONF-40` resolved. | [ADR-0012](adr/0012-ios-framework-export.md), `PROJECT_LOG.md` LOG-0027 |
| 2026-09-30 | DEC-060 and DEC-061 added (the catalog holds the full planned inventory ahead of use; the dependency policy is enforced by Gradle verification tasks wired into the root `check`). No ADR: both are tooling decisions (§1). | `PROJECT_LOG.md` LOG-0032 |
| 2026-09-30 | DEC-059 added: pull requests integrate with a merge commit only; rebase and squash merges disabled and `required_linear_history` removed. Supersedes the merge-method half of DEC-041. | `PROJECT_LOG.md` LOG-0031 |
| 2026-09-30 | DEC-062 added (repository and secret hygiene enforced by the dependency-free `verifyRepositoryHygiene` Gradle task wired into the root `check`; working-set plus all-refs history boundary, fail-closed on shallow/incomplete Git state, redacted findings). No ADR: a tooling decision, not architecture (§1). | `PROJECT_LOG.md` LOG-0036, TASK-016 |
| 2026-09-30 | DEC-062 implementation corrected on review (the scan now covers every reachable blob and every historical path occurrence and fails closed on incomplete Git reads). The decision text is unchanged, so no row was superseded and no new decision was allocated. | `PROJECT_LOG.md` LOG-0037, TASK-016 |
| 2026-09-30 | DEC-062 implementation corrected again after the second review: refs that peel directly to a tree are now enumerated, every `-z` stream must end with the record terminator, content is streamed without a whole-object ceiling, and the reported count is labelled as unique historical paths. The decision text is unchanged; no row was superseded and no decision was allocated. | `PROJECT_LOG.md` LOG-0038, TASK-016 |
| 2026-10-01 | DEC-062's implementation was merged to `main` in PR #13 on 2026-09-30; the decision is `Accepted` and its status is unchanged. | `PROJECT_LOG.md` LOG-0039, TASK-016 |
| 2026-10-01 | DEC-063 added: block-based execution of the remaining work (nine blocks `B1`…`B9`, milestone-scoped, no dependency on a later block). No ADR: a process decision, not architecture (§1). | `PROJECT_LOG.md` LOG-0040 |
| 2026-10-01 | DEC-063's grouping was reconciled with the owner-supplied source plan: `TASK-031` and `TASK-073` are in B2, `TASK-020` precedes its B5 dependants, and `TASK-021` remains in B4. The decision text and status are unchanged; `CONF-52` is resolved. | `PROJECT_LOG.md` LOG-0041 |
