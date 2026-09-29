# DECISION_BOARD.md — Decision Index and Status Board

- **Status:** Active
- **Last verified:** 2026-09-29
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
| DEC-005 | Episodes/Locations ship as placeholders | Scope | Accepted | Normal | `REQUIREMENTS.md` §5.3 | — | Interview A5 |
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
| DEC-041 | Trunk-based development; Conventional Commits; squash merge **replaced** by rebase merge that preserves the TDD phase commits (DEC-053) | Process | Accepted (amended) | High | `CONTRIBUTING.md` | — | Interview G2, owner directive 2026-09-29 |
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
| Retrofit/OkHttp as the shipped REST client | Superseded by DEC-011 | JVM-only; cannot serve the shared data layer. | `API_SPECS.md` §14 |
| GraphQL (Apollo Kotlin) as the shipped protocol | Rejected for MVP | `POST` breaks the standard HTTP cache, needs a normalized cache, and no screen requires it. REST stays documented as the alternative. | `API_SPECS.md` §2 |
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
| DEC-005 | Real Episodes/Locations screens | After the iOS milestone | `REQUIREMENTS.md` `REQ-FUNC-031/032` |
| DEC-013 | Kotlin Swift export as the single interop path | Swift export becomes Stable | DEF-004 |
| DEC-025 | iOS snapshot baseline breadth | iOS milestone starts | `TESTING.md` |
| DEC-029 | Whether live contract tests move into the merge gate | The API publishes a versioned or stable contract | `API_SPECS.md` §1 |

## 5. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-09-29 | Board created; DEC-001…DEC-051 recorded from the documentation decision interview; superseded and rejected alternatives filed in §3. | This audit |
