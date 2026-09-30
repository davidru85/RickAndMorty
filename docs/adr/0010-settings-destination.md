# ADR-0010 — Settings replaces Locations as the fourth destination, backed by a local preferences store

- **Status:** Accepted
- **Date:** 2026-09-30
- **Last verified:** 2026-09-30
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) and Implementation Engineer (iOS); consulted UI/UX Designer (navigation and Settings screen design), Requirements Analyst (scope traceability) and Security Reviewer (what the preferences store may hold)
- **Authoritative for:** which feature module fills the fourth navigation slot, where the app preferences are stored and exposed, and how "delete favorites" reaches the favorites store. Not the behaviour and acceptance criteria (`REQ-FUNC-008`, `REQ-FUNC-033`…`REQ-FUNC-035` in [`../REQUIREMENTS.md`](../REQUIREMENTS.md)), not the screen design ([`../UI_SPEC.md`](../UI_SPEC.md) §6.5), not the interface signatures ([`../CONTRACTS.md`](../CONTRACTS.md) `IC-008`, `IC-013`, `IC-021`…`IC-023`) and not the REST/GraphQL switch mechanics, which belong to [ADR-0011](0011-runtime-remote-protocol.md). The rest of the module set stays with [ADR-0001](0001-module-boundaries.md).
- **Inputs:** `DEC-055`, `DEC-056`, `DEC-005`, `DEC-017` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-052` (ADR-0001), `DEC-004`/`DEC-017` (ADR-0007); `REQ-FUNC-006`, `REQ-FUNC-008`, `REQ-FUNC-032`…`REQ-FUNC-036`, `REQ-NFR-002`, `REQ-NFR-009`, `REQ-SEC-003`, `REQ-SEC-004` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`DESIGN.md`](../DESIGN.md) §3, §4.2; [`UI_SPEC.md`](../UI_SPEC.md) §1, §6.5; repository-owner directives of 2026-09-30 (navigation redesign; the three settings; "toggle only" for sounds; confirmation before deleting favorites); Figma file `nFQdxd23Kk4rNI7G4iHDUr` checked 2026-09-30 (Settings screens `101:568` and `102:322`, confirmation frames `122:1293` and `123:529`)

## Owners

- **Decision owner:** System Architect — owns the module change, the store placement and the review trigger below.
- **Implementation owners:** Implementation Engineer (Android) for `:feature:settings`, the preferences store in `:core:data` and the `:androidApp` graph; Implementation Engineer (iOS) for `iosApp/Features/Settings` and the `UserDefaults` actual.
- **Consulted:** UI/UX Designer for the Settings screen and dialogs; Security Reviewer for the store contents rule.

## Decision

The fourth top-level destination is **Settings**, and the destination order is Characters · Episodes · Favorites · Settings. Locations is removed from the navigation.

- `:feature:settings` MUST replace `:feature:locations` in the module set of ADR-0001, and `iosApp/Features/Settings` MUST replace `iosApp/Features/Locations`. It follows every ADR-0001 dependency rule.
- App preferences (the sounds flag and the remote protocol) MUST be exposed through one repository interface in `:core:domain` (`IC-021`) and persisted by one `expect/actual` store in `:core:data` (`IC-022`), built on the same platform stores favorites already use: DataStore on Android, `UserDefaults` on iOS (`DEC-017`). No new storage dependency is added.
- "Delete favorites" MUST go through `FavoritesRepository` (`IC-008`), which gains a clear operation. `:feature:settings` MUST NOT depend on `:feature:favorites`, and it MUST NOT reach the favorites store directly.
- The sounds preference is stored and shown, and nothing else: no sound asset, audio API or audio dependency is added until a decision defines the sound set (`REQ-FUNC-036`, `DEF-005`).
- No module for Locations exists in the MVP. If real Locations screens are admitted later (`REQ-FUNC-032`, `DEF-003`), that decision also chooses how Locations is reached, because it no longer has a navigation slot.

- **Board entry:** `DEC-055` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). It amends `DEC-005` (Locations no longer ships as a placeholder) and the module list of `DEC-052`.

## Context

Until 2026-09-30 the four destinations were Characters, Episodes, Locations and Favorites. Episodes and Locations were coming-soon placeholders (`DEC-005`), and each was its own feature module (`DEC-052`, ADR-0001). On 2026-09-30 the repository owner redesigned the navigation in Figma, removing Locations and adding Settings as the last item on both platforms. The same day the owner added three settings: a sounds on/off switch, a choice between the REST API and GraphQL, and a "Delete favorites" button. The owner then decided three details:
- The sounds switch stores a preference only; which sounds exist is decided later.
- GraphQL goes through the existing Ktor client (ADR-0011).
- Deleting favorites asks for confirmation first.

The repository is documentation-only (`AGENTS.md` §1). This ADR changes the target architecture and the documents that describe it, not code.

Three facts constrain the design:
- Favorites live in `:core:data` behind `IC-008`/`IC-013`, and `:feature:settings` may not depend on `:feature:favorites` (ADR-0001).
- The remote-protocol preference must be read inside `:core:data`, where the remote data source is chosen (ADR-0011). A store owned by `:feature:settings` would force `:core:data` to depend on a feature, which ADR-0001 forbids.
- The favorites store already provides a verified `expect/actual` persistence path on both platforms (ADR-0007).

## Decision drivers

- Owner directives of 2026-09-30 — `DEC-055`.
- Four one-tap destinations — `REQ-FUNC-008`.
- One module per user-facing capability; no feature-to-feature edge; `:core:*` never depends on a feature — `REQ-NFR-009`, ADR-0001.
- One solution per concern; no second storage library — `REQ-NFR-002`, `DEC-017`.
- Nothing personal stored, no new permission — `REQ-SEC-003`, `REQ-SEC-004`.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Preferences in `:core:domain` + `:core:data`, favorites cleared through `IC-008` | `IC-021`/`IC-022` beside the favorites seams; `ClearFavorites` use case in `:feature:settings` | Respects every dependency rule; reuses the platform stores; one new interface pair to test | Chosen |
| Preferences store inside `:feature:settings` | Settings owns DataStore/`UserDefaults` keys | Keeps the store next to its screen, but `:core:data` could not read the protocol without depending on a feature | Rejected: violates ADR-0001 |
| Delete favorites by calling the favorites feature | `:feature:settings` → `:feature:favorites` | Short path, but it is a feature-to-feature edge | Rejected: violates `AC-REQ-NFR-009-1` |
| Keep `:feature:locations` and add `:feature:settings` | Six feature modules, one unreachable | Keeps a future Locations home, but ships a module no route reaches | Rejected: speculative structure (`AGENTS.md` §5 step 3) |

## Consequences

**Positive**

- The module set matches the navigation exactly: every feature module has a destination, and every destination has a module.
- One persistence mechanism serves favorites and preferences on each platform, so the `expect/actual` contract suite pattern of `TESTING.md` §6.2 covers both.
- The protocol preference is available to `:core:data` without a new dependency edge.

**Negative**

- `IC-008` and `IC-013` change (a clear operation), so the `:core:testing` fakes and their contract suite change in the same change (`CONTRACTS.md` §8).
- The sounds switch has no audible effect until `DEF-005` is resolved. A reviewer may read that as unfinished; the requirement states it explicitly (`REQ-FUNC-033`).
- Every document that lists modules, destinations or placeholders changes in the same change (DEC-046).

## Risks

- **`RISK-009`** Reviewer perceives scope as exceeding the assignment — aggravated: three settings go beyond `assessment.md`. Mitigated by keeping each behind a Should-have requirement with its own acceptance criteria. The data-source switch also gives the interview a concrete protocol trade-off to discuss (`assessment.md` l.12).
- **Local:** clearing favorites while another screen observes them — `IC-008` emits the empty set to every collector, so Favorites and the Detail heart update without a manual refresh (`AC-REQ-FUNC-035-2`).
- **Local:** the preferences store grows into a dumping ground — `IC-022` admits only the keys named in `IC-021`; adding one is a contract change (`CONTRACTS.md` §8).

## Validation criteria

- `TEST-UI-007` traverses the four destinations in the order Characters · Episodes · Favorites · Settings (`AC-REQ-FUNC-008-1`, `AC-REQ-FUNC-008-2`).
- `TEST-UNIT-046` proves the preferences store contract on both `actual`s: defaults, persistence across restart, and no keys beyond `IC-021` (`AC-REQ-FUNC-033-2`).
- `TEST-UNIT-047` proves the clear operation: one emission of the empty set to every collector, and nothing else in the store touched (`AC-REQ-FUNC-035-2`).
- `TEST-UI-017` proves the Settings screen and the confirmation flow on both platforms (`AC-REQ-FUNC-033-1`, `AC-REQ-FUNC-035-1`, `AC-REQ-FUNC-035-3`).
- **Observable:** no module or Swift package named `locations` exists, and `:feature:settings` has no edge to another feature → the Gradle settings file, the `iosApp/Features/` listing and the dependency-analysis report (`DEC-032`).

## Related requirements

- `REQ-FUNC-008` (`AC-REQ-FUNC-008-1`, `AC-REQ-FUNC-008-2`): the destination set and order.
- `REQ-FUNC-033`, `REQ-FUNC-034`, `REQ-FUNC-035`: the three settings this module owns.
- `REQ-FUNC-006`: the favorites store that "Delete favorites" clears.
- `REQ-NFR-009` (`AC-REQ-NFR-009-1`, `AC-REQ-NFR-009-2`): one module per capability, no feature-to-feature edge.
- `REQ-FUNC-032`, `REQ-FUNC-036`: Locations screens and the sound set stay deferred.

## Related implementation areas

- `:feature:settings`, `:core:domain` (`IC-021`), `:core:data` (`IC-022`, `IC-013`), `:androidApp`, `iosApp/Features/Settings`.
- [`DESIGN.md`](../DESIGN.md) §3 and §4.2; [`UI_SPEC.md`](../UI_SPEC.md) §1, §4.1, §4.2, §6.5, §7.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none. Amends the module set of ADR-0001 (the `:feature:locations` and `iosApp/Features/Locations` rows only); ADR-0001 is otherwise unchanged and still Accepted.
- **Superseded by:** none as of 2026-09-30.
- **Related:** ADR-0001 (module boundaries), ADR-0006 (presentation-state ownership), ADR-0007 (favorites storage, whose store mechanism is reused), ADR-0011 (runtime REST/GraphQL selection, which reads the protocol preference).
