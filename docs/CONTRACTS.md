# CONTRACTS.md — Internal Kotlin Contract Baseline

- **Status:** Active — mixed, **accepted as the internal interface baseline** (`TASK-019`, `TECHNICAL_PLAN.md` §5 S1). B3 Phase 3.1 implements the `:core:domain` declarations (`IC-001`…`IC-004`, `IC-007`, `IC-008` and `IC-021` as interfaces, `IC-010`) and `IC-011`'s REST adapter; every other row is target state. The `Flow`-based seams are coherent with the `DEC-066` amendment of ADR-0001. See `DOCUMENTATION_AUDIT.md` §5 for the drift rule
- **Last verified:** 2026-10-06
- **Owner:** System Architect (see `AGENTS.md` §3)
- **Authoritative for:** the internal Kotlin contracts `IC-###` — the source-level declarations that cross a module boundary (repository, data-source, cache, storage and pager seams), the shared UI-state types both platforms consume, and the invariants, ownership, reference and compatibility rules attached to each of them.
- **Not authoritative for:** the remote-facing and domain model declarations (`API_SPECS.md` §3, §4.7, §5, §6), the failure → state → copy chain (`ERROR_FLOW.md`), module composition and dependency direction (`DESIGN.md` §3, `adr/0001-module-boundaries.md`), requirement ids and acceptance criteria (`REQUIREMENTS.md`), visual specification (`UI_SPEC.md`), test ids, layers and tooling (`TESTING.md`), user-visible copy strings (`UI_SPEC.md` §6.4, §8).
- **Inputs:** [`REQUIREMENTS.md`](REQUIREMENTS.md) · [`API_SPECS.md`](API_SPECS.md) §3, §4.7, §6, §7, §8 · [`DESIGN.md`](DESIGN.md) §1, §3, §4, §6 · [`ERROR_FLOW.md`](ERROR_FLOW.md) · [`UI_SPEC.md`](UI_SPEC.md) §6, §8 · [`TESTING.md`](TESTING.md) · [`DECISION_BOARD.md`](DECISION_BOARD.md) · [`adr/0001-module-boundaries.md`](adr/0001-module-boundaries.md) · [`adr/0003-ui-sharing-strategy.md`](adr/0003-ui-sharing-strategy.md)
- **Normative terms:** `MUST` mandatory · `SHOULD` strong recommendation · `MAY` optional.

> This file is the single canonical location for the declarations it owns. Another document that needs one of them `MUST` cite its `IC-###` id and `MUST NOT` restate its signature, defaults or invariants.

## Table of contents

1. [Purpose and the one-owner rule](#1-purpose-and-the-one-owner-rule)
2. [Identifier scheme and how contracts are referenced](#2-identifier-scheme-and-how-contracts-are-referenced)
3. [Ownership map: one canonical location per type](#3-ownership-map-one-canonical-location-per-type)
4. [Referenced contracts owned by API_SPECS](#4-referenced-contracts-owned-by-api_specsmd)
5. [Data and domain seam contracts (owned here)](#5-data-and-domain-seam-contracts-owned-here)
6. [Presentation contracts (owned here)](#6-presentation-contracts-owned-here)
7. [Consumption by platform state holders](#7-consumption-by-platform-state-holders)
8. [Change, versioning and compatibility rules](#8-change-versioning-and-compatibility-rules)
9. [Contract verification strategy](#9-contract-verification-strategy)
10. [Assumptions and drift register](#10-assumptions-and-drift-register)
11. [Change log](#11-change-log)

Module names, source-set layout and dependency direction follow `adr/0001-module-boundaries.md` (`DEC-052`, which supersedes `DEC-019`). This file states which module a declaration lives in; it does not restate the dependency rules.

```mermaid
flowchart LR
    subgraph Domain[":core:domain — stdlib + kotlinx-coroutines-core only (DEC-066)"]
        ID[IC-001..005<br/>API_SPECS declarations]
        REPO[IC-007 CharacterRepository<br/>IC-008 FavoritesRepository<br/>IC-009 use cases<br/>IC-010 filters<br/>IC-014 CharacterPager<br/>IC-021 AppSettingsRepository<br/>IC-024 AppLogger contract<br/>failure = IC-003 Failure]
    end
    subgraph Data[":core:data — depends on :core:domain"]
        REMOTE[IC-011 CharacterRemoteDataSource]
        CACHE[IC-012 CacheStorage]
        LOCAL[IC-013 FavoritesLocalDataSource]
        PAGER[IC-014 pager implementation]
        PREFS[IC-022 AppSettingsLocalDataSource]
    end
    subgraph Pres[":core:presentation — depends on :core:domain"]
        LOAD[IC-015 LoadState]
        CARD[IC-016 CharacterCardUi]
        FMT[IC-017 CopyKey + PresentationFormatters]
    end
    subgraph Feature["feature presentation packages"]
        LIST[IC-018 CharacterListUiState + Intent]
        DETAIL[IC-019 CharacterDetailUiState + Intent]
        FAV[IC-020 FavoritesUiState + Intent]
        SET[IC-023 SettingsUiState + Intent]
    end
    Data -. implements .-> REPO
    Pres --> Domain
    Feature --> Pres
    Feature --> Domain
    Android[Android ViewModel] --> LIST
    Android --> DETAIL
    Android --> FAV
    Android --> SET
    iOS[iOS ObservableObject] --> LIST
    iOS --> DETAIL
    iOS --> FAV
    iOS --> SET
```

## 1. Purpose and the one-owner rule

### 1.1 Why this file exists

Filtering, paging, enrichment, favourites and display formatting must behave identically on Android and iOS while each platform owns its own UI, navigation, image pipeline and state holder (`DEC-001`, `DEC-013`, `REQ-PLAT-001`). That is only verifiable if the source-level boundary between shared logic and platform code has exactly one normative description. This file is it.

### 1.2 The one-owner rule

| Rule | Statement |
| --- | --- |
| O1 | Every type, interface or enum that crosses a module boundary has exactly **one** canonical declaration, named in §3. |
| O2 | A document that is not the owner references the declaration by `IC-###` id (or by `REQ-*`/`API-*`/`DEC-###` id) and `MUST NOT` repeat its signature, defaults or invariants. |
| O3 | When a signature or an invariant changes, the owning document changes in the same pull request as the code (`DEC-046`, `DEFINITION.md` D7). For the declarations this file owns, the owner is this file. |
| O4 | Ownership never moves silently: relocating a declaration to another module or another document is a contract change (§8). |

`DESIGN.md` §4.1 owns the **data flow** of the shared presentation state; it does not own the type declarations. This file owns them. A change to a UI-state type is therefore made **in this file first**, then consumed by the feature module, and `DESIGN.md` §4.1 is edited only if the flow or the module picture changed.

## 2. Identifier scheme and how contracts are referenced

### 2.1 The `IC-###` namespace

- `IC-###` is the identifier for one internal contract. It is allocated in this file only, in ascending order, and never reused: a contract that stops existing has its id retired with it. A retired id is kept in place, marked `Withdrawn` with the reason and the convention that replaced it, and the surrounding ids are never renumbered (`IC-006` is the first precedent).
- An id names a *contract*, not a file: two closely coupled declarations that always change together (for example a state class and its intent type) `MAY` share one id when they are listed in one table row, and their members are then addressed as `IC-018` (state + intents).
- A contract that is canonically declared in another document is still listed here with an id (§4). The id is the stable reference; the owning document is named in the row.
- The ids are referenced by other families the same way `REQ-*` and `DEC-###` are: permanently, and never renumbered (`DEC-046`).

### 2.2 Reference forms

| Form | Meaning | Example |
| --- | --- | --- |
| `IC-018` | The contract, cited from another document or from code review | "see `IC-018`" |
| `IC-018 (CharacterListUiState)` | The contract with its primary type name, for readability in prose | — |
| `IC-###` inside a pull request | `DEFINITION.md` D7 requires the same-change update of this file | — |

A document `MUST NOT` write "the state class in `DESIGN.md` §4.1" or "the pager in `:core:data`" when an `IC-###` id exists; it cites the id. Requirement-level traceability (requirement → contract → task → test) is maintained in `DOCUMENTATION_AUDIT.md` §6, with the contract half taken from this file.

### 2.3 What a contract row contains

Every contract in §4–§6 states: the `IC-###` id, the declaration (or the pointer to the owning document), the module and source set, the invariants other code may rely on, and the traceability identifiers (`REQ-*`, `AC-*`, `API-*`, `DEC-###`). An invariant is always a testable statement about behaviour, not a restatement of the signature.

## 3. Ownership map: one canonical location per type

```text
Kotlin type                              Canonical document        Module and source set
---------------------------------------  ------------------------  ------------------------------------------
CharacterId, LocationId, EpisodeId       API_SPECS.md §3           :core:domain  commonMain
CharacterSummary, CharacterDetails       API_SPECS.md §3           :core:domain  commonMain
CharacterPage, LocationSummary           API_SPECS.md §3           :core:domain  commonMain
EpisodeSummary, CharacterStatus          API_SPECS.md §3           :core:domain  commonMain
CharacterGender                          API_SPECS.md §3           :core:domain  commonMain
DataResult<T> (sealed), DataSource,        API_SPECS.md §3           :core:domain  commonMain
  ApiWarning
ApiFailure (+ every variant)             API_SPECS.md §6           :core:domain  commonMain
REST DTOs, GraphQL envelopes             API_SPECS.md §4.7, §5.1   :core:data    commonMain (never outside)
CharacterRepository, PageLoadPolicy      CONTRACTS.md IC-007       :core:domain  commonMain
FavoritesRepository                      CONTRACTS.md IC-008       :core:domain  commonMain
AppSettingsRepository, AppSettings,      CONTRACTS.md IC-021       :core:domain  commonMain
  RemoteProtocol
Use cases (cross-feature and feature)    CONTRACTS.md IC-009       :core:domain, or the feature's domain package
CharacterFilter, StatusFilter            CONTRACTS.md IC-010       :core:domain  commonMain
CharacterRemoteDataSource                CONTRACTS.md IC-011       :core:data    commonMain
CacheStorage, CacheKey, CacheEntry       CONTRACTS.md IC-012       :core:data    commonMain
FavoritesLocalDataSource                 CONTRACTS.md IC-013       :core:data    commonMain
CharacterPager, PagerState               CONTRACTS.md IC-014       :core:domain  commonMain (implemented in :core:data)
AppSettingsLocalDataSource               CONTRACTS.md IC-022       :core:data    commonMain
DetailHandoff                            CONTRACTS.md IC-025       :core:presentation  commonMain
SplashGate                               CONTRACTS.md IC-026       :core:presentation  commonMain (splash package)
StateObserver                            CONTRACTS.md IC-027       :core:presentation  commonMain (observation package)
PresentationBindings                     CONTRACTS.md IC-028       :core:presentation  commonMain
LoadState                                CONTRACTS.md IC-015       :core:presentation  commonMain
CharacterCardUi                          CONTRACTS.md IC-016       :core:presentation  commonMain
CopyKey, CopyKeys, DisplayText,          CONTRACTS.md IC-017       :core:presentation  commonMain
  PresentationFormatters
CharacterListUiState, CharacterListIntent CONTRACTS.md IC-018      :feature:discovery  presentation package
CharacterDetailUiState, InfoRowUi,       CONTRACTS.md IC-019       :feature:character-detail  presentation package
  InfoRowKind, CharacterDetailIntent
FavoritesUiState, FavoritesIntent         CONTRACTS.md IC-020      :feature:favorites  presentation package
SettingsUiState, SettingsIntent           CONTRACTS.md IC-023      :feature:settings  presentation package
CharacterAccentResolver                   DESIGN.md §4.4            :feature:discovery  androidMain UI
AppLogger, LogLevel, LogEvent (+ field   CONTRACTS.md IC-024       :core:domain  commonMain
  enums)
LogSink, LogRecord, LogField             CONTRACTS.md IC-024       :core:domain  commonMain (DEC-093)
ValidatingAppLogger (IC-024 impl.)       CONTRACTS.md IC-024       :core:data    commonMain
Debug diagnostic API (DiagnosticsRecorder) ADR-0013, DEC-088       :core:diagnostics  commonMain
```

### 3.1 The boundary rule between this file and `API_SPECS.md`

| Owner | Owns | Reason |
| --- | --- | --- |
| `API_SPECS.md` §3, §4.7, §5, §6 | Wire-facing and domain model declarations: DTOs, response envelopes, domain models, identity types, `DataResult`, `DataSource`, `ApiFailure`, and the mapping and retry policy | These types are derived from the remote contract and change when the API changes; `API_SPECS.md` §14 already requires the document and its contract tests to move together (`CON-001`, `RISK-004`) |
| This file | Module seam interfaces, the shared presentation-state types, and their invariants | These are internal composition decisions that do not depend on the API shape; they change with the architecture |

Consequence, stated once and applied throughout: this file lists the `API_SPECS.md` declarations as **reference-only entries** (`IC-001`…`IC-005`) carrying the usage invariants that follow from the layering rules, and declares no wire type of its own. No `API_SPECS.md` declaration is duplicated here, and no signature declared here is restated in `API_SPECS.md`.

### 3.2 Types deliberately absent from the contract surface

| Absent | Where it may appear | Rule |
| --- | --- | --- |
| REST DTOs (`RestCharacterDto`, `RestPageDto<T>`, …) and GraphQL envelopes (`GraphQlResponse<T>`, `GraphQlError`) | `:core:data` internals and its test source sets | `MUST NOT` appear in any `IC-###` signature, in a UI-state type, or in a public signature outside `:core:data` (`REQ-NFR-001`, `AC-REQ-NFR-001-2`) |
| Platform and UI types (`Bitmap`, `Color`, `Modifier`, `UIImage`, `View`, lifecycle artifacts) | Platform source sets only | `MUST NOT` appear in any shared contract (`AC-REQ-PLAT-001-1`); `CharacterAccentResolver` is the single documented exception and is Android UI code, not a shared contract (`DESIGN.md` §4.4) |
| Exception types originating in a platform or in Ktor | `:core:data` internals | `MUST NOT` cross a repository or data-source seam: `:core:data` maps them to an `ApiFailure` and returns `DataResult.Failure` (`IC-003`); a `CancellationException` is the only exception that propagates (`ERROR_FLOW.md` §2, §6) |

Test doubles are not contracts: the fakes named in §9 live in `:core:testing`, are owned by `TESTING.md` §6, and `MUST` honour the `IC-###` they stand in for rather than defining competing semantics.

## 4. Referenced contracts owned by `API_SPECS.md`

Each entry below is a pointer plus the invariants that apply to *consumers*. The declaration lives in `API_SPECS.md`; this file `MUST NOT` grow a second copy of it.

### IC-001 — Identity types

- **Declared in:** `API_SPECS.md` §3 (`CharacterId`, `LocationId`, `EpisodeId`).
- **Module:** `:core:domain`.
- **Invariants**
  - An id is the canonical server string in every layer: REST integer ids are converted with `toString()` exactly once, in the mapper (`API_SPECS.md` §3).
  - An id `MUST NOT` be derived from an array index or from a position in a batch response, on any code path.
  - Value-class identity is the only identity: two ids are equal iff their strings are equal; no case folding, trimming or numeric coercion is applied.
- **Traceability:** `API_SPECS.md` §3, `REQ-NFR-001`, `AC-REQ-NFR-001-1`.

### IC-002 — Domain models

- **Declared in:** `API_SPECS.md` §3 (`CharacterSummary`, `CharacterDetails`, `CharacterPage`, `LocationSummary`, `EpisodeSummary`, `CharacterStatus`, `CharacterGender`).
- **Module:** `:core:domain`.
- **Invariants**
  - `CharacterDetails.episodeSummaries == null` means enrichment was not requested, and an empty list means enrichment completed with no appearances (`API_SPECS.md` §3). Consumers `MUST NOT` treat `null` as "no episodes".
  - An unknown `status` or `gender` string is preserved in the model as `Unsupported(raw)` (or the gender equivalent) and never drops the character or throws (`REQ-NFR-004`, `AC-REQ-NFR-004-2`).
  - An empty `type` is absent in the domain (`null`), and a `LocationSummary` with an empty reference URL is valid and has no id (`API_SPECS.md` §4.7).
  - A missing or unparsable `createdAt` maps to `null` and `MUST NOT` prevent the rest of the character from rendering.
  - `CharacterPage.page` is the requested page number; `pageCount`, `totalCount` and `nextPage` remain `null` when the server has not established them. Consumers `MUST NOT` substitute `0` for a null count (`REQ-FUNC-001`, `AC-REQ-FUNC-001-3`).
- **Traceability:** `API_SPECS.md` §3, §4.7, §5.3, `REQ-FUNC-001`, `REQ-FUNC-002`, `REQ-FUNC-023`, `REQ-NFR-004`, `AC-REQ-NFR-004-2`.

### IC-003 — Result envelope and cache provenance

- **Declared in:** `API_SPECS.md` §3 (sealed `DataResult<out T>` with `Success`/`Failure`, `DataSource`, `ApiWarning`). That section owns the declaration; this entry owns the consumer invariants below.
- **Module:** `:core:domain`.
- **Invariants**
  - `DataResult.Success.value` exists only for a payload that was fully decoded and passed domain mapping; a partially decoded or `errors`-accompanied payload is never a silent partial value — it is either a `Success` carrying non-empty `warnings`, when every field the screen needs is valid, or a `Failure` (`API_SPECS.md` §6.2).
  - Failure is a value, not an exception: a suspending seam declared in §5 returns `DataResult.Failure(failure, source, warnings)` for any expected remote failure. It `MUST NOT` throw, `MUST NOT` return a sentinel and `MUST NOT` return a partially populated model to signal failure.
  - `CancellationException` is always rethrown. It is never converted into a `DataResult.Failure`, never wrapped, and never mapped to `ApiFailure` (`ERROR_FLOW.md` §6, `AC-REQ-FUNC-022-2`).
  - `Failure.source` is the source that produced the failure and is never a cache hit: a failure cannot originate from served cache content (`API_SPECS.md` §7.3).
  - `Failure` carries no value: there is no partial payload alongside a failure, and a caller `MUST NOT` reconstruct one.
  - `isStale` exists on `Success` only. `Success.isStale == true` implies `source != NETWORK`: stale means content served from a cache (`API_SPECS.md` §7.3). The converse does not hold — a cache hit inside the fresh window is not stale.
  - `warnings` is available on both outcomes and is non-empty only for a response that was usable but incomplete; it carries `ApiWarning` entries (`API_SPECS.md` §3). A warning `MUST NOT` be rendered as a failure, and a response carrying warnings `MUST NOT` be written to any cache (`API_SPECS.md` §7.2, `REQ-FUNC-020`).
  - `source` is decided by the data layer only. A UI-state consumer reads it; it never computes it.
  - A consumer handles the sealed hierarchy exhaustively; a `when` over `DataResult` `MUST NOT` fall through to a default branch in shared code.
- **Traceability:** `API_SPECS.md` §3, §6.2, §7.1–§7.3, `REQ-FUNC-020`, `REQ-FUNC-022`, `REQ-REL-004`, `DEC-012`, `DEC-018`.

### IC-004 — Failure taxonomy

- **Declared in:** `API_SPECS.md` §6 (`ApiFailure` and its variants).
- **Module:** `:core:domain`.
- **Invariants**
  - Failure classification is total: every failure that reaches a consumer is one `ApiFailure` variant, produced by the mapping rules in `API_SPECS.md` §6.1–§6.2 and the taxonomy in `ERROR_FLOW.md` §2.
  - `CancellationException` is never a failure value: it propagates unchanged (`ERROR_FLOW.md` §6, `AC-REQ-FUNC-022-2`).
  - `ApiFailure.GraphQl` is never produced by the REST adapter (`DEC-011`, `ERROR_FLOW.md` §2).
  - The failure → state → copy chain is owned by `ERROR_FLOW.md` (DEC-021) and `MUST NOT` be restated in a contract: contracts carry the failure, they do not decide the copy.
- **Traceability:** `API_SPECS.md` §6, `ERROR_FLOW.md` §2, §4, `REQ-FUNC-022`, `AC-REQ-FUNC-022-1`, `AC-REQ-FUNC-022-2`.

### IC-005 — Wire types

- **Declared in:** `API_SPECS.md` §4.7 (REST DTOs), §5.1 (GraphQL envelope), §5.5 (operations).
- **Module:** `:core:data`, `commonMain` — and nowhere else.
- **Invariants**
  - A wire type `MUST NOT` appear in a signature declared in §5 or §6 of this file, in a UI-state type, or in any public signature outside `:core:data` (`REQ-NFR-001`, `AC-REQ-NFR-001-2`).
  - A wire type `MUST NOT` be cached as a domain value: the cache stores the decoded-and-validated shape the app consumes, and mappers run before anything is written (`API_SPECS.md` §7.1).
  - JSON field names and nullability follow `API_SPECS.md`; a DTO `MUST NOT` be reshaped by a contract change here.
- **Traceability:** `API_SPECS.md` §4.7, §5.1, §5.5, `REQ-NFR-001`, `AC-REQ-NFR-001-2`, `DEC-030`.

## 5. Data and domain seam contracts (owned here)

### IC-006 — Withdrawn: failure signalling across a seam

- **Status:** Withdrawn, 2026-09-29. The id is retired and is not reallocated; the `IC-###` sequence continues at `IC-007`, so no existing id is renumbered.
- **Invariants:** none — the id no longer names a live contract. The invariants that survive it are the `Carried forward` items below, which are now stated by `IC-003`.
- **Former contract:** `class ApiException(val failure: ApiFailure) : Exception()`, thrown by a data-layer seam to signal a mapped remote failure.
- **Withdrawn because:** failure is now a value, not thrown control flow. The convention was replaced by the sealed `DataResult` declared in `API_SPECS.md` §3 and used by `IC-003`, `IC-007` and `IC-011`:
  - an error is representable in the type system and cannot be silently swallowed by an empty `catch`;
  - no Kotlin exception crosses the Kotlin → Swift interop boundary, where a thrown exception is awkward to observe from an `ObservableObject` — and `DEC-013` makes Swift a first-class consumer of these seams;
  - exhaustive `when` handling of the sealed hierarchy is enforced at compile time;
  - `source` and `warnings` remain available on both outcomes, which a thrown exception could not carry for a successful-but-warned payload.
- **Carried forward:** a `CancellationException` is still rethrown and is never converted into `DataResult.Failure`; the failure is still mapped inside `:core:data` before it reaches a seam (`ERROR_FLOW.md` §2, §3 invariant 1); and a caller that displays a failure still resolves copy through `ERROR_FLOW.md` rather than building user-visible text from the `ApiFailure` fields.
- **Traceability:** `REQ-FUNC-022`, `AC-REQ-FUNC-022-1`, `ERROR_FLOW.md` §2, §3, `adr/0003-ui-sharing-strategy.md`.

### IC-007 — `CharacterRepository`

- **Declaration** (`:core:domain`, `commonMain`):

```kotlin
interface CharacterRepository {
    suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy = PageLoadPolicy.Default,
    ): DataResult<CharacterPage>
    suspend fun details(id: CharacterId, enrich: Boolean): DataResult<CharacterDetails>
}

enum class PageLoadPolicy { Default, ForceNetwork }
```

- **Page-load policy (`DEC-086`).** `Default` lets the implementation serve the page under the cache read policy of `API_SPECS.md` §7.3. `ForceNetwork` is the manual-refresh policy: the implementation `MUST` issue a network request for the page even when a fresh cached entry exists. The policy is part of the request identity the implementation coalesces on, so in-flight or cached `Default` work `MUST NOT` satisfy a `ForceNetwork` call, and the bypass `MUST` be observable in a test (a recorded network call for a fresh entry). `PageLoadPolicy` is platform-free and carries no cache vocabulary of its own.
- **Invariants**
  - Both methods return `DataResult` and never throw for an expected remote failure: success is `DataResult.Success` carrying a fully mapped value, failure is `DataResult.Failure` carrying the `ApiFailure` (`IC-003`). A `null` value has no meaning here and `MUST NOT` be used to signal a miss.
  - `page` returns the requested page only, and `MUST NOT` fetch a later page as part of the same call (`REQ-FUNC-001`, `AC-REQ-FUNC-001-1`).
  - A filter that matches nothing is a **success** carrying an empty page, not a `Failure` (`REQ-FUNC-010`, `AC-REQ-FUNC-010-1`, `ERROR_FLOW.md` §5).
  - `page` applies the caller's filter unchanged: all active filters are sent on every page (`REQ-FUNC-001`).
  - `details(id, enrich = false)` `MUST NOT` issue an episode request; `details(id, enrich = true)` performs at most one bounded episode batch call, never one request per episode (`REQ-FUNC-023`, `AC-REQ-FUNC-023-1`).
  - A detail for an unknown id returns `DataResult.Failure` carrying `ApiFailure.NotFound`; the repository `MUST NOT` return an empty shell model (`API_SPECS.md` §4.4).
  - Concurrent identical calls are deduplicated in the implementation, so two simultaneous identical loads issue one remote request (`REQ-REL-002`, `AC-REQ-REL-002-1`). Identity includes the protocol, the operation, the page or id, the normalized filter, the enrichment mode and the `PageLoadPolicy`.
  - `page(…, policy = ForceNetwork)` reaches the network even when a fresh entry exists (`AC-REQ-FUNC-012-1`, `DEC-086`). When a background revalidation of the same entry is already on the network, a `ForceNetwork` load joins it and returns its result, so the two cost one request (`REQ-REL-002`, `DEC-130`).
  - A background revalidation fetches what a first load fetches: an enriched detail is revalidated with its episodes and stored only when complete, so no request's answer is discarded (`DEC-130`).
  - Only successfully decoded, domain-valid payloads are promoted to a cache; errors, empty bodies and partial responses are never written (`REQ-FUNC-020`, `AC-REQ-FUNC-020-3`).
  - A `CancellationException` from an underlying suspending call propagates unchanged and is never converted into a `Failure` (`IC-003`).
  - The repository `MUST NOT` expose, accept or depend on a DTO type or a platform type.
- **Implementation (`TASK-038`):** `RemoteCharacterRepository` in `:core:data`, over `IC-011`, with one bounded retry policy (`DEC-084`) and request coalescing whose scope ownership is fixed: the shared work runs in a supervisor child of the injected owner scope, so closing the owner cancels it; a cancelled waiter stops waiting while any other waiter keeps the work alive; the work is cancelled when its last waiter leaves; and an entry is removed then and never joined once finished, so a later identical call runs again — coalescing is not caching. There is no response cache yet (`TASK-020`), so `Default` and `ForceNetwork` both reach the network today; the policy is already part of the identity.
- **Traceability:** `REQ-FUNC-001`, `REQ-FUNC-002`, `REQ-FUNC-010`, `REQ-FUNC-012`, `REQ-FUNC-020`, `REQ-FUNC-023`, `REQ-REL-002`, `DEC-012`, `DEC-018`, `DEC-086`.

### IC-008 — `FavoritesRepository`

- **Declaration** (`:core:domain`, `commonMain`):

```kotlin
interface FavoritesRepository {
    fun observe(): Flow<Set<CharacterId>>
    suspend fun toggle(id: CharacterId)
    suspend fun clear()
}
```

- **Invariants**
  - `observe()` is a hot, conflated, never-completing stream: it emits the current set to every new collector as its first value, emits again on every change, and never completes on its own (`REQ-FUNC-006`, `AC-REQ-FUNC-006-2`).
  - Emissions are ordered and distinct: a set that is unchanged is not re-emitted.
  - `toggle(id)` applies exactly one flip per call and writes it before returning; concurrent calls are serialised by the implementation so an update cannot be lost.
  - `clear()` empties the whole set in one write and returns after it is persisted; `observe()` then emits the empty set exactly once, and a `clear()` on an empty set emits nothing. It is serialised with `toggle` like any other write (`REQ-FUNC-035`, `AC-REQ-FUNC-035-2`).
  - Stored state survives process restart, because persistence is owned by `IC-013` — the repository holds no authoritative in-memory copy (`REQ-FUNC-006`).
  - The set is local to the device: the repository `MUST NOT` perform any network request (`NG-003`, `REQ-SEC-003`).
  - No call on this interface blocks the caller's thread: `observe()` performs no I/O on the subscribing thread and `toggle()` suspends rather than blocking the UI thread.
  - A write the store cannot complete is not thrown: it is logged as `LOG-019` and the last consistent set stays, while a `CancellationException` propagates unchanged (ADR-0007, `SECURITY.md` §6.3). A written toggle is logged as `LOG-018`, which carries no id (`IC-024`).
- **Implementation (`TASK-040`):** `LocalFavoritesRepository` in `:core:data` (package `…core.data.favorites`) over `IC-013`. A toggle reads the persisted set and writes the one flip it implies; toggles and clears share one lock. It holds no copy of the set.
- **Traceability:** `REQ-FUNC-006`, `REQ-FUNC-035`, `DEC-004`, `DEC-017`, `DEC-055`, [`adr/0007-favorites-storage.md`](adr/0007-favorites-storage.md), [`adr/0010-settings-destination.md`](adr/0010-settings-destination.md).

### IC-009 — Use cases

- **Declarations** (one class per row; `invoke` is the only public API):

| Use case | Module and package | Signature | Used by |
| --- | --- | --- | --- |
| `GetCharacterPage` | `:feature:discovery`, `domain` package | `suspend operator fun invoke(filter: CharacterFilter, page: Int): DataResult<CharacterPage>` | `IC-018` state holder, for a one-shot page fetch |
| `GetCharacterDetails` | `:feature:character-detail`, `domain` package | `suspend operator fun invoke(id: CharacterId, enrich: Boolean): DataResult<CharacterDetails>` | `IC-019` state holder |
| `ObserveFavoriteIds` | `:core:domain` (cross-feature) | `operator fun invoke(): Flow<Set<CharacterId>>` | `IC-018`, `IC-019`, `IC-020` state holders |
| `ToggleFavorite` | `:feature:character-detail`, `domain` package | `suspend operator fun invoke(id: CharacterId)` | `IC-019` state holder |
| `ClearFavorites` | `:feature:settings`, `domain` package | `suspend operator fun invoke()` | `IC-023` state holder, only after the user confirms |
| `ObserveAppSettings` | `:feature:settings`, `domain` package | `operator fun invoke(): Flow<AppSettings>` | `IC-023` state holder |
| `UpdateAppSettings` | `:feature:settings`, `domain` package | `suspend operator fun invoke(change: (AppSettings) -> AppSettings)` | `IC-023` state holder |

- **Placement rule:** a use case lives in the `domain` package of the feature that uses it, except a use case consumed by more than one feature, which lives in `:core:domain` (`adr/0001-module-boundaries.md`). `ObserveFavoriteIds` is cross-feature for exactly that reason, and is implemented in `:core:domain`'s `usecase` package by `TASK-040` (`DEC-090`); `GetCharacterPage`, `GetCharacterDetails`, `ToggleFavorite`, `ClearFavorites`, `ObserveAppSettings` and `UpdateAppSettings` are feature-local. `:core:data` reads the protocol preference through `IC-021` directly, not through a use case, because it is infrastructure, not a feature.
- **Invariants**
  - A use case is stateless and holds no cache of its own: two invocations with the same arguments are independent.
  - A use case `MUST NOT` catch `CancellationException`, and `MUST NOT` convert a `CancellationException` into a `DataResult.Failure`. Failure handling and state decisions belong to the state holder (`IC-018`, `IC-019`); a use case returns the `DataResult` it received unchanged.
  - A use case `MUST NOT` read the clock, connectivity or a platform API directly; those arrive through `:core:data` seams.
  - `GetCharacterPage` is the repository call of `IC-007` for one page: paging state, page accumulation and prefetch are not a use-case responsibility. They belong to `IC-014`, which a state holder observes directly through its injected pager.
- **Traceability:** `REQ-FUNC-001`, `REQ-FUNC-002`, `REQ-FUNC-006`, `REQ-FUNC-023`, `adr/0001-module-boundaries.md`.

### IC-010 — `CharacterFilter` and `StatusFilter`

- **Declarations** (`:core:domain`, `commonMain`):

```kotlin
data class CharacterFilter(
    val query: String = "",
    val status: StatusFilter = StatusFilter.All,
)

enum class StatusFilter { All, Alive, Dead, Unknown }
```

- **Why `:core:domain`:** the filter is an input parameter of `IC-007` and therefore part of the domain seam, not a screen-local value. It is immutable, carries no platform type and is the same instance type on both platforms.
- **Invariants**
  - Exactly four status options exist and the default is `All` (`REQ-FUNC-004`, `AC-REQ-FUNC-004-2` refers to the filters the UI offers).
  - `StatusFilter.All` means "send no `status` parameter"; the serialization of that rule happens in `:core:data` and is not a UI decision (`API_SPECS.md` §4.4).
  - A blank `query` means "send no `name` parameter" (`AC-REQ-FUNC-003-3`). The filter retains the raw user text; trimming and encoding are performed by the mapper.
  - `CharacterFilter` is a value: equal filters produce equal cache keys and must resolve to the same cached entry, and different filters must never share one (`REQ-REL-001`, `AC-REQ-REL-001-1`).
  - Species, type and gender are not part of the filter and `MUST NOT` be added without a contract change (§8) and a requirement (`REQ-FUNC-004`, `AC-REQ-FUNC-004-2`).
- **Traceability:** `REQ-FUNC-003`, `REQ-FUNC-004`, `REQ-REL-001`.

### IC-011 — `CharacterRemoteDataSource`

- **Declaration** (`:core:data`, `commonMain`):

```kotlin
interface CharacterRemoteDataSource {
    /** One page of the character list for the given filter. */
    suspend fun characterPage(filter: CharacterFilter, page: Int): DataResult<CharacterPage>
    /** One character with its episode id list; enrichment is orchestrated above this seam. */
    suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails>
    /** Episode summaries for the given ids, in one bounded batch. */
    suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>>
}
```

- **Return type (`DEC-090`, `CONF-64`).** The methods return the sealed `DataResult` of `IC-003`: the baseline declared raw values while the invariants below already required `DataResult.Failure`, and `API_SPECS.md` §3 makes failure a value. Every outcome of this seam has `source = NETWORK`; the seam carries no cache provenance.

- **Semantics:** the seam returns **domain** types. DTO decoding and mapping happen inside the implementation so `IC-005` cannot leak through it. The remote data sources of `:core:data` are exactly three, each with one responsibility:
  - `RestCharacterRemoteDataSource` — `IC-011`, the default protocol, `GET` against `/api/...`.
  - `GraphQlCharacterRemoteDataSource` — `IC-011`, posts the checked-in operations of `API_SPECS.md` §5.5 to `/graphql` through the same Ktor client.
  - `AppSettingsLocalDataSource` — `IC-022`, the DataStore/`UserDefaults` seam that holds the settings the user configures on the Settings screen, including the choice that selects between the two adapters above. It is not an `IC-011` implementation and returns `AppSettings`, not character data.
  The repository reads `IC-021` and resolves the adapter **per request** from the current `remoteProtocol`; the same preference selects one protocol for every screen (DEC-056, [`adr/0011-runtime-remote-protocol.md`](adr/0011-runtime-remote-protocol.md)).
- **Invariants**
  - No DTO or wire envelope crosses this seam (`AC-REQ-NFR-001-2`).
  - An expected remote failure is returned as `DataResult.Failure` (`IC-003`); transport and decoding exceptions never escape, and only a `CancellationException` propagates.
  - `episodes(emptyList())` returns an empty list without any network request; a single-id call uses the single-resource operation and a multi-id call the batch operation, per the routing rule in `API_SPECS.md` §4.2. An implementation `MUST NOT` define one polymorphic batch decoder.
  - A batch chunk is bounded, and the final singleton chunk is routed through the single-resource operation (`API_SPECS.md` §4.4).
  - A batch response that omits a requested id is reconciled by id; the omission is reported as a warning rather than as a failure (`API_SPECS.md` §6.1). A returned resource is matched to a request by its own id, never by its position.
  - Requests are issued only to the configured HTTPS host; a relation or pagination URL naming another host or another scheme is rejected — the response maps to `DataResult.Failure(ApiFailure.InvalidRequest)` and nothing is followed (`REQ-SEC-001`, `AC-REQ-SEC-001-1`). Transport-wide enforcement, redirects included, is `TASK-038`'s.
  - A list `404` on page 1 of a filtered request (`name` or `status` sent) is an empty page; every other list `404` is `ApiFailure.NotFound`, and deciding that a `404` reached through a valid paging sequence ends pagination is the pager's job (`IC-014`, `ERROR_FLOW.md` §5.2).
  - The seam carries no cache policy: caching is decided above it (`IC-012`).
  - Both implementations return equal domain values for the same logical request, and map failures to the same `ApiFailure` variants (`API_SPECS.md` §6.1, §6.2). An empty filtered result is an empty page on both, whatever the wire shape (`AC-REQ-FUNC-034-3`).
- **Traceability:** `REQ-FUNC-001`, `REQ-FUNC-023`, `REQ-FUNC-034`, `REQ-NFR-001`, `REQ-SEC-001`, `API_SPECS.md` §4.2, §5.5, §6.1, §6.2, `DEC-011`, `DEC-055`, `DEC-056`.

### IC-012 — `CacheStorage`

- **Declarations** (`:core:data`, `commonMain`):

```kotlin
@JvmInline
value class CacheKey(val value: String)

class CacheEntry(
    val payload: ByteArray,
    val storedAt: Instant,
    val validator: String?,
)

interface CacheStorage {
    suspend fun get(key: CacheKey): CacheEntry?
    suspend fun put(key: CacheKey, entry: CacheEntry)
    suspend fun evict(key: CacheKey)
    suspend fun evictAll()
}
```

- **Semantics:** `CacheStorage` is the persistence seam only — bytes plus the metadata needed to revalidate. Freshness, staleness and write admission are policy owned by the response cache above the seam, using the injected clock (`DEC-012`, `DEC-018`, `API_SPECS.md` §7).
- **Invariants**
  - `get` returns `null` for a miss and never throws: an unreadable or corrupt entry is a miss plus a warning, never a user-visible failure (`REQ-FUNC-020`).
  - A `put` failure is contained: the read path degrades to a cache miss rather than propagating an exception to a screen.
  - `CacheKey.value` is the complete normalized request identity — protocol, resource, page and every filter value — so two filter combinations, or the same request over REST and over GraphQL, can never share an entry (`REQ-REL-001`, `AC-REQ-REL-001-1`, `AC-REQ-FUNC-034-4`; key shape in ADR-0005 and ADR-0011).
  - Only successfully decoded, domain-valid payloads are written; errors, empty bodies and partial responses are never admitted (`REQ-FUNC-020`, `AC-REQ-FUNC-020-3`, `RISK-005`).
  - Image bytes are never stored here: image caching is independent of response caching (`REQ-FUNC-021`, `AC-REQ-FUNC-021-2`).
  - No method reads the wall clock; an entry's age is computed by the caller from `storedAt` and the injected clock, so a device clock change cannot alter freshness evaluation (`REQ-REL-004`, `AC-REQ-REL-004-1`).
  - `evictAll` clears response payloads only and `MUST NOT` touch the favourites store (`IC-013`) or the preferences store (`IC-022`). A protocol switch `MUST NOT` call it.
- **Implementation (`TASK-020`):** the policy above the seam is `ResponseCache` in `:core:data` (`commonMain`), over a `CacheStorage` the composition root supplies. `CacheKeyBuilder` is the one builder of a key: the complete normalized request identity, `protocol|method|pathTemplate|canonicalQuery`, with the query's parameters in a fixed alphabetical order, blank values omitted and the case the wire contract fixes preserved. The freshness bands are `CachePolicy` — 24 h fresh, 7 d stale-while-revalidate, 30 d offline fallback — injectable, and an age is computed only from the injected clock. A negative age (a device clock moved backwards) classifies as fresh rather than expired.
  - The read policy is `API_SPECS.md` §7.3: a fresh entry answers without a network call; a stale entry is served at once with `isStale = true` and revalidated behind the caller; an offline-fallback entry is kept as the answer to a failed request; an expired entry is evicted. `PageLoadPolicy.ForceNetwork` bypasses every band (`DEC-086`).
  - The write guard is the never-cache rule: only a complete decoded success with no warnings is written. A failed enrichment produces a warning and is refused, so a partial detail can never hide the rows it omits (`ERROR_FLOW.md` §7).
  - A store that cannot read, a store that cannot write and an undecodable record each degrade to a miss; none reaches a screen as a failure.
- **Traceability:** `REQ-FUNC-020`, `REQ-FUNC-021`, `REQ-REL-001`, `REQ-REL-004`, `DEC-012`, `DEC-018`.

### IC-013 — `FavoritesLocalDataSource`

- **Declaration** (`:core:data`, `commonMain`, package `…core.data.favorites`; one implementation per platform, the `expect/actual` split of `DEC-017` realised as one class per platform source set behind this interface):

```kotlin
interface FavoritesLocalDataSource {
    fun observe(): Flow<Set<CharacterId>>
    suspend fun add(id: CharacterId)
    suspend fun remove(id: CharacterId)
    suspend fun clear()
}
```

- **Semantics:** the storage seam for the favourite id set. `expect/actual` implementations are DataStore on Android and `UserDefaults` on iOS (`DEC-017`, [`adr/0007-favorites-storage.md`](adr/0007-favorites-storage.md)). `IC-008` is the only consumer.
- **Invariants**
  - `add` and `remove` are idempotent set operations: adding a present id and removing an absent id are no-ops that still leave the observable set unchanged.
  - `clear()` removes every id in one persisted write; clearing an empty store is a no-op that emits nothing. It removes only the favourite ids; the preferences of `IC-022` share the platform store technology but not its keys, and are untouched.
  - `observe()` emits the persisted set to a new collector without blocking the subscriber, then every subsequent change; the stream is conflated and never completes.
  - Persistence survives process restart, and no id leaks across a simulated reinstall or clear of the store (`REQ-FUNC-006`, `AC-REQ-FUNC-006-2`).
  - Only canonical id strings are persisted (`IC-001`); no index, no ordinal and no name is used as a key.
  - The store holds nothing else: no profile, no token and no personal data (`REQ-SEC-003`).
  - Both `actual` implementations `MUST` satisfy this one contract; semantics `MUST NOT` differ per platform.
  - A stored value that cannot be read — a corrupted DataStore file, a `UserDefaults` value that is not a list of strings — degrades to its readable part, or to the empty set, plus `LOG-019`; it never crashes the app (`SECURITY.md` §6.3).
- **Implementation (`TASK-040`):** Android — `DataStoreFavoritesLocalDataSource` over a Preferences DataStore built by `preferencesDataStore(file, scope, logger)`, which replaces an undecodable file and logs `LOG-019`; the ids are the string set under the key `favorite_ids`. Apple — `UserDefaultsFavoritesLocalDataSource` over an `NSUserDefaults`, the ids as a sorted string array under the key `multiverse.favorites.ids`, read once when the store is built and kept observable in process; writes from another process are not observed (ADR-0007). Both are measured by the one `TEST-INT-003` suite; the composition roots supply the file, the suite and the scope (`TASK-044`, `TASK-051`).
- **Traceability:** `REQ-FUNC-006`, `REQ-SEC-003`, `DEC-017`, `TESTING.md` §6.2.

### IC-014 — `CharacterPager`

- **Declarations** (`:core:domain`, `commonMain`; implemented in `:core:data`). Relocated from `:core:data` on 2026-10-02 by `DEC-091` ([ADR-0014](adr/0014-api-impl-boundary.md)) before any implementation existed: the discovery state holder consumes the pager, and a feature's production code depends on the API module only:

```kotlin
interface CharacterPager {
    val state: Flow<PagerState>
    suspend fun setFilter(filter: CharacterFilter)
    suspend fun next()
    suspend fun refresh()
    suspend fun retry()
}

data class PagerState(
    val filter: CharacterFilter,
    val items: List<CharacterSummary>,
    val totalCount: Int?,
    val isAppending: Boolean,
    val isEndReached: Boolean,
    val isStale: Boolean,
    val failure: ApiFailure?,
    val isLoading: Boolean = false,   // DEC-130
)
```

- **Semantics:** the shared paging engine (`DEC-016`). It owns page accumulation, prefetch eligibility, cancellation of a superseded load and the end-of-pagination flag. It reports a failure through `PagerState.failure` rather than throwing, because a failed `next()` must not destroy the content already displayed (`REQ-FUNC-012`, `AC-REQ-FUNC-012-2`).
- **Layering note:** `PagerState` does not reuse `IC-015`: the contract lives in `:core:domain`, which depends on no project module (`DEC-066`), and its implementation in `:core:data`, which depends only on `:core:domain`, so a presentation primitive cannot appear in a pager signature. The feature presentation package maps `PagerState` to `IC-018` (see `IC-018` invariants), and the composition root supplies the `:core:data` implementation (`DEC-091`).
- **Invariants**
  - `state` is hot with replay of the current value: a new collector receives the current `PagerState` as its first emission and never triggers a load by collecting.
  - `setFilter` resets to page 1 and cancels any in-flight page load; the items of the previous filter are not carried into the new filter's accumulation (`REQ-FUNC-003`, `REQ-FUNC-004`, `AC-REQ-FUNC-003-2`).
  - A change of the active protocol resets exactly as `setFilter` does, keeping the filter. Items, total, end flag, failure and staleness clear, and the load in flight is cancelled, so nothing the previous protocol loaded stays on screen while page 1 of the new one loads (`AC-REQ-FUNC-034-2`, `DEC-130`).
  - `isLoading` is `true` from a reset (`setFilter` or a protocol switch) until the first page of the new identity is published, whatever its outcome, so a consumer never reads an empty reset as an empty result (`DEC-130`).
  - A **stale** first page, published from a `Default` load, is followed by one silent network load of page 1, which joins the repository's revalidation of that entry. On success it is published like any load — replacing the page and clearing `isStale` — unless the identity changed or a page was appended meanwhile; on failure nothing changes, and no `failure` is reported (`ERROR_FLOW.md` §9, `DEC-130`).
  - `next()` while `isEndReached == true` performs no request; `isEndReached` is set when the server's end-of-pagination signal is observed (`REQ-FUNC-001`, `AC-REQ-FUNC-001-2`, `API_SPECS.md` §4.3).
  - `next()` while a page load is in flight is coalesced: it `MUST NOT` start a second concurrent page request.
  - `next()` while `failure != null` performs no request: a failed load suppresses further speculative loads, so repeated scroll triggers cannot become a request storm while the service is failing (`DEC-092`). `retry()`, `refresh()` and `setFilter` are the ways out.
  - `retry()` re-attempts the load that failed — page 1 when no content is displayed, otherwise the failed append; a failed `refresh()` is re-attempted as a refresh, with `ForceNetwork` — with a fresh attempt budget, and never discards loaded pages (`ERROR_FLOW.md` §10 rule 4); it is a no-op when `failure == null` (`DEC-092`).
  - A `NotFound` for a page after the first, reached through `next()`, is the end of pagination: `isEndReached` becomes `true` and no failure is reported (`ERROR_FLOW.md` §5.2). A `NotFound` for page 1 is a failure.
  - `isAppending` is `true` only while a page is being appended to existing content; it is always `false` outside an append (so a first page load and a `refresh()` do not set it).
  - `refresh()` loads page 1 through `IC-007` with `PageLoadPolicy.ForceNetwork` (`DEC-086`), so it performs a network request even when the served entry is fresh, and a failed `refresh()` leaves `items` untouched (`REQ-FUNC-012`, `AC-REQ-FUNC-012-1`, `AC-REQ-FUNC-012-2`). It `MUST NOT` rely on the absence of a cache to reach the network.
  - `items` is never emptied by `next()`, `refresh()` or a failure: `setFilter` is the only transition that empties it, and the following successful load replaces rather than appends. A consumer can therefore never observe an empty list that is merely a reload (`ERROR_FLOW.md` §3 invariant 2).
  - `items` preserves server order with no duplicates, and page *n* is appended only after pages `1..n-1` are present.
  - `totalCount` is `null` until the server establishes it and `MUST NOT` be replaced with `0` (`AC-REQ-FUNC-001-3`).
  - `isStale` is `true` only while `items` come from a cache source (`IC-003`): it reports the provenance of the results the items came from, and a failed `refresh()` neither sets nor clears it (`CONF-71`).
  - `failure` is the `ApiFailure` from the last `DataResult.Failure` (`IC-003`) that did not clear content, and is reset to `null` by the next successful load; a failure carried in `failure` `MUST NOT` be rethrown to a collector of `state`.
  - Prefetch is bounded to the next page and `MUST NOT` fetch the whole catalogue up front (`API_SPECS.md` §8, `REQ-NFR-003`).
  - The pager owns no scope of its own: its loads run in the scope its owner supplies (the state holder's), so closing the owner cancels every load (`GUIDELINES.md` §2.7).
  - `PagerState` carries no `LoadState`: the presentation layer derives it from `items`, `failure` and `isAppending` under the mapping fixed in `IC-018`. The pager therefore never decides which screen state is rendered.
- **Implementation (`TASK-039`):** `RepositoryCharacterPager` in `:core:data`, over `IC-007`, whose coalescing and retry it reuses (ADR-0009 rule 7). Its suspending methods return when the load they started or joined ends. State transitions happen under one lock, and every load carries the generation it started in: `setFilter` and `refresh()` start a new generation, so a superseded load that still returns cannot publish. The next page comes from the server's metadata only; a known total is kept when a later result states none (`ERROR_FLOW.md` §5.3). `next()` before any load loads page 1 of the initial filter. Each load opens a correlation scope and a published load is logged (`IC-024`).
- **Traceability:** `REQ-FUNC-001`, `REQ-FUNC-003`, `REQ-FUNC-004`, `REQ-FUNC-012`, `DEC-016`, `API_SPECS.md` §8.

### IC-021 — `AppSettingsRepository`, `AppSettings` and `RemoteProtocol`

- **Declarations** (`:core:domain`, `commonMain`):

```kotlin
data class AppSettings(
    val soundsEnabled: Boolean = false,
    val remoteProtocol: RemoteProtocol = RemoteProtocol.Rest,
)

enum class RemoteProtocol { Rest, GraphQl }

interface AppSettingsRepository {
    fun observe(): Flow<AppSettings>
    suspend fun update(change: (AppSettings) -> AppSettings)
}
```

- **Consumed by:** the `:feature:settings` use cases (`IC-009`); for `remoteProtocol` only, the repository implementation in `:core:data` that selects the `IC-011` implementation; and, for `soundsEnabled` only, the selection sound of each shell (`REQ-FUNC-036`, `DEC-162`) — `:androidApp` observes `observe()` itself, and `iosApp` through `MultiverseBootstrap.observeSoundsEnabled` in `:core:ios`.
- **Invariants**
  - The defaults above are the fresh-install values: Sounds off, REST (`AC-REQ-FUNC-033-2`, `AC-REQ-FUNC-034-1`).
  - `observe()` is hot, conflated and never completes. It emits the current value to every new collector first, then each distinct change.
  - `update` applies the function to the latest persisted value and writes the result atomically; concurrent updates are serialised so none is lost. An update that produces an equal value writes and emits nothing.
  - `AppSettings` holds only these two fields. Adding a field is a contract change (§8) and needs a requirement; nothing personal may be added (`REQ-SEC-003`).
  - `soundsEnabled` gates the selection sound, and nothing else: while it is `false` no sound plays, and a change applies from the next selection without a restart (`AC-REQ-FUNC-036-3`). `MultiverseBootstrap.observeSoundsEnabled(onEach)` delivers the stored value and then each distinct change on the main queue, until the observation it returns is closed.
  - A `remoteProtocol` change is visible to the next remote request. It does not by itself evict the response cache (`IC-012`).
- **Traceability:** `REQ-FUNC-033`, `REQ-FUNC-034`, `REQ-FUNC-036`, `REQ-SEC-003`, `DEC-055`, `DEC-056`, `DEC-162`, [`adr/0010-settings-destination.md`](adr/0010-settings-destination.md), [`adr/0011-runtime-remote-protocol.md`](adr/0011-runtime-remote-protocol.md).

### IC-022 — `AppSettingsLocalDataSource`

- **Declaration** (`:core:data`, `commonMain`, implemented per target by `expect/actual`):

```kotlin
interface AppSettingsLocalDataSource {
    fun observe(): Flow<AppSettings>
    suspend fun write(settings: AppSettings)
}
```

- **Semantics:** the storage seam for `IC-021`, using the same platform store technology as `IC-013`: DataStore on Android, `UserDefaults` on iOS (`DEC-017`). `IC-021`'s implementation is the only consumer.
- **Invariants**
  - A missing or unreadable key reads as the `IC-021` default for that field, never as an error.
  - `RemoteProtocol` is persisted by a stable string (`"rest"`, `"graphql"`), never by ordinal. An unknown stored string reads as `Rest`.
  - Persistence survives process restart, and both `actual`s satisfy this one contract with identical semantics.
  - The store holds exactly the two `AppSettings` fields and shares no key with `IC-013`.
- **Traceability:** `REQ-FUNC-033`, `REQ-FUNC-034`, `DEC-017`, `DEC-055`.

### IC-024 — `AppLogger`, `LogLevel`, `LogEvent`, `LogSink`

- **Declarations** (`DEC-087`, [ADR-0013](adr/0013-observability-placement.md)), all in `:core:domain`, `commonMain`, package `…core.domain.logging`. The sink half moved there from `:core:data` by `DEC-093` before it was implemented, so the debug-only `:core:diagnostics` module can implement `LogSink` while depending on `:core:domain` only (`DEC-088`); the validating `AppLogger` implementation stays in `:core:data`:

```kotlin
// :core:domain
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface AppLogger {
    /** Whether an event at [level] can reach a sink in this build. */
    fun isEnabled(level: LogLevel): Boolean
    /** Records [event] at its catalogue level. Never throws, never blocks. */
    fun log(event: LogEvent)
}

/** Builds the event only when its level is enabled (`OBSERVABILITY.md` §7 rule 3). */
inline fun AppLogger.log(level: LogLevel, event: () -> LogEvent) {
    if (isEnabled(level)) log(event())
}

/** The closed catalogue: one implementation per `OBSERVABILITY.md` §3 row that has an emitter in the build. */
sealed interface LogEvent {
    val catalogueId: String     // "LOG-001" … "LOG-022", fixed per implementation
    val level: LogLevel         // fixed per row by the catalogue

    // Shown compactly: each is `data class X(val …) : LogEvent`, with its row's id and level.
    data class RequestStarted(operation: LogOperation, pathTemplate: PathTemplate, page: Int?,
        filterNames: Set<FilterName>, protocol: RemoteProtocol, correlationId: String?)          // LOG-001 DEBUG
    data class RequestCompleted(operation: LogOperation, pathTemplate: PathTemplate, page: Int?,
        statusFamily: StatusFamily, durationMs: Long, correlationId: String?, outcome: LogOutcome) // LOG-002 INFO
    data class RequestFailed(operation: LogOperation, pathTemplate: PathTemplate, page: Int?,
        statusFamily: StatusFamily?, errorClass: ErrorClass, durationMs: Long,
        correlationId: String?)                                                                  // LOG-003 ERROR, outcome=FAILURE
    data class ForeignHostRejected(operation: LogOperation, screen: LogScreen?,
        correlationId: String?)                                                                  // LOG-004 ERROR, errorClass=INVALID_REQUEST
    data class PageLoaded(page: Int, outcome: LogOutcome, durationMs: Long,
        cacheSource: DataSource, correlationId: String?)                                         // LOG-010 DEBUG, operation=CHARACTER_LIST
    data class PaginationExhausted(page: Int)                                                    // LOG-011 DEBUG, CHARACTER_LIST, SUCCESS
    data class RequestDeduplicated(operation: LogOperation, page: Int?,
        filterNames: Set<FilterName>, correlationId: String?)                                    // LOG-012 DEBUG
    data class RetryScheduled(operation: LogOperation, errorClass: ErrorClass,
        statusFamily: StatusFamily?, retryAfterSeconds: Long?, correlationId: String?)           // LOG-013 WARN
    data class RequestCancelled(operation: LogOperation, correlationId: String?)                 // LOG-014 DEBUG, outcome=CANCELLED
    data class UnknownValuePreserved(operation: LogOperation, pathTemplate: PathTemplate)        // LOG-022 DEBUG, outcome=SUCCESS
    data class FavoritesToggled(outcome: LogOutcome)                                             // LOG-018 INFO, component=FAVORITES_STORE
    data class FavoritesStoreDegraded(screen: LogScreen?)                                        // LOG-019 ERROR, FAVORITES_STORE, errorClass=UNKNOWN
}

// The closed value sets of OBSERVABILITY.md §2.2 that these events carry; the protocol and the cache
// source reuse RemoteProtocol (IC-021) and DataSource (IC-003), which already are those sets.
enum class LogOperation { CHARACTER_LIST, CHARACTER_DETAIL, EPISODE_BATCH }
enum class PathTemplate(val template: String) { CHARACTER("/character"), CHARACTER_BY_ID("/character/{id}"), EPISODES_BY_IDS("/episode/{ids}") }
enum class FilterName(val wireName: String) { NAME("name"), STATUS("status") }
enum class StatusFamily(val wireName: String) { SUCCESSFUL("2XX"), CLIENT_ERROR("4XX"), SERVER_ERROR("5XX"), NO_RESPONSE("NO_RESPONSE") }
enum class LogOutcome { SUCCESS, EMPTY, FAILURE, CANCELLED }
enum class ErrorClass { OFFLINE, TIMEOUT, NOT_FOUND, INVALID_REQUEST, RATE_LIMITED, SERVER, MALFORMED_RESPONSE, EMPTY_BODY, UNKNOWN }
enum class LogScreen { SPLASH, DISCOVERY, CHARACTER_DETAIL, FAVORITES, EPISODES, SETTINGS }
enum class LogComponent { RESPONSE_CACHE, IMAGE_CACHE, FAVORITES_STORE, PAGER }

// :core:domain as well, since `DEC-093`: the sink half is what platform sinks and `:core:diagnostics` implement
fun interface LogSink {
    fun write(record: LogRecord)
}

data class LogRecord(
    val level: LogLevel,
    val catalogueId: String,
    val fields: Map<LogField, String>,   // validated permitted fields only, in their §2.2 form
)

// Every §2.2 field, by its wire name: PROTOCOL("protocol"), OPERATION("operation"), PATH_TEMPLATE("pathTemplate"),
// PAGE, FILTER_NAMES, STATUS_FAMILY, CACHE_SOURCE, IS_STALE, DURATION_MS, CORRELATION_ID, OUTCOME, ERROR_CLASS,
// SCREEN, COMPONENT, RETRY_AFTER_SECONDS, APP_VERSION, PLATFORM, BUILD_TYPE, CAUSE — each with its camelCase name.
enum class LogField(val wireName: String) { /* … */ }

// :core:data — the implementation; the factories are the only way to build one
class ValidatingAppLogger private constructor(…) : AppLogger {
    companion object {
        fun forRelease(sink: LogSink): ValidatingAppLogger   // ERROR only, fixed
        fun forDebug(sink: LogSink): ValidatingAppLogger     // every level
    }
}
```

- **Semantics:** one contract for both platforms (`REQ-OBS-001`). Each `LogEvent` implementation is a `data class` whose properties are exactly the fields its catalogue row lists, typed by closed enums, never by a free-form `String` a feature fills in; the correlation id is the one string-typed field, and it is validated. A row whose emitter does not exist in the build yet — the cache events `LOG-005`…`LOG-009`, the image events `LOG-015`…`LOG-017`, app start `LOG-020` and the screen event `LOG-021` — gains its implementation, and the value set it needs, with that emitter. The `:core:data` implementation renders each event as its row's fields in the `OBSERVABILITY.md` §2.2 form (a path as its template, filter *names* sorted and comma-separated, a status family as `2XX`/`4XX`/`5XX`/`NO_RESPONSE`, a constant the row fixes from the row), omits a field the event leaves `null`, validates every value, drops a value that fails and counts it, and writes a `LogRecord` to the injected `LogSink`; a field with no emitter in the build has no valid value yet. The app shells supply the platform sinks. The debug-only diagnostic API reads the same validated records from `:core:diagnostics` (`DEC-088`).
- **Correlation id:** 16 lowercase hex characters from a random source, generated on the client per request scope and carried in the coroutine context: a pager load opens one, a repository call outside a scope opens one, the single flight's shared work runs in the scope of the call that started it, and the adapter's events and the retry policy's read it. It is never persisted and never derived from input (`OBSERVABILITY.md` §4.1 rule 5).
- **Emitters (B3 Phase 3.2):** the REST adapter logs `LOG-001` for every request it sends and exactly one of `LOG-002`, `LOG-003`, `LOG-004` or `LOG-014` when it ends, plus `LOG-022` when a mapped response preserved an unknown enum value; input rejected before a request exists is not logged. The retry policy logs `LOG-013`, the single flight logs `LOG-012` with the id of the request the duplicate joined, and the pager logs `LOG-010` (and `LOG-011` when pagination ends) for a load it publishes — never for a superseded one. Since B3 Phase 3.3 the favourites repository logs `LOG-018` for a written toggle and `LOG-019` for a write the store could not complete, and each platform store logs `LOG-019` for a value it could not read (`TASK-040`).
- **Invariants**
  - There is exactly one logging interface. No other module declares a second one, and platform code calls no platform logging API for app diagnostics (`OBSERVABILITY.md` §2.1).
  - `log` never throws into its caller and never blocks it; a failing sink loses the record rather than queueing it (`OBSERVABILITY.md` §7).
  - A disabled level builds no event: callers use the inline `log(level) { … }` form.
  - Release builds emit `ERROR` events only, with no runtime override: the threshold is fixed when the logger is built, and the build variant's source set picks the factory (`DEC-039`, `OBSERVABILITY.md` §4.1 rule 7).
  - No event carries search text, a filter value, a URL with parameters, an id-bearing path, a cache key, a response body or a stack trace (`REQ-SEC-005`, `OBSERVABILITY.md` §2.3). `TEST-UNIT-029` proves it on the real request path.
  - The contract adds no dependency to `:core:domain` beyond those `DEC-066` permits.
- **Status:** implemented by `TASK-047` in B3 Phase 3.2 (`TEST-UNIT-029`, `TEST-UNIT-032`, `TEST-UNIT-033`); the platform sinks and the variant wiring are target state until the shells exist (`TASK-044`, `TASK-051`).
- **Traceability:** `REQ-OBS-001`, `REQ-OBS-002`, `REQ-SEC-005`, `DEC-038`, `DEC-039`, `DEC-087`, `DEC-088`, `DEC-093`, `OBSERVABILITY.md` §2–§5.

## 6. Presentation contracts (owned here)

### IC-015 — `LoadState`

- **Declaration** (`:core:presentation`, `commonMain`):

```kotlin
sealed interface LoadState {
    data object Loading : LoadState
    data object Content : LoadState
    data object Empty : LoadState
    data class Error(val failure: ApiFailure) : LoadState
}
```

- **Invariants**
  - `Error` always carries exactly one `ApiFailure`; the variant `MUST NOT` be constructed with a fabricated or default failure.
  - `Empty` means "the newest attempt completed successfully and matched nothing", including the filtered-REST-`404` case; it `MUST NOT` be used for the initial load or for a failure (`REQ-FUNC-010`, `AC-REQ-FUNC-010-1`).
  - `Loading` `MUST NOT` replace `Content` without an explicit reset (a new filter, a new query or a first load). Stale content stays `Content` with a stale flag (`ERROR_FLOW.md` §3 invariant 2).
  - Failure selection is owned by `ERROR_FLOW.md` (DEC-021): a state object carries `LoadState`; it does not derive copy from it.
  - Adding a variant to this hierarchy is a breaking change for the Swift consumer (§8).
- **Status:** implemented by `TASK-041` in B3 Phase 3.3.
- **Traceability:** `REQ-UX-009`, `DEC-021`, `ERROR_FLOW.md` §3, `UI_SPEC.md` §8.

### IC-016 — `CharacterCardUi`

- **Declaration** (`:core:presentation`, `commonMain`):

```kotlin
data class CharacterCardUi(
    val id: CharacterId,
    val name: String,
    val species: DisplayText,        // IC-017: data, or the unknown key
    val status: CharacterStatus,     // drives the badge's colour and semantics
    val statusLabel: CopyKey,        // the badge's text, from IC-017 statusKey
    val imageUrl: String,
) {
    companion object {
        fun from(summary: CharacterSummary, formatters: PresentationFormatters): CharacterCardUi
    }
}
```

- **Why `:core:presentation`:** the card is rendered by more than one feature (discovery grid, favourites list, the detail header) and is the payload of the shared navigation hand-off (`DESIGN.md` §4.2), so it is a cross-feature presentation primitive, not a discovery-local model.
- **Invariants**
  - `imageUrl` is the API image URL verbatim; it is simultaneously the image cache key, so it `MUST NOT` be rewritten, resized or decorated by a state mapper (`REQ-FUNC-005`, `UI_SPEC.md` §5.1, `API_SPECS.md` §7.4).
  - `species` is the API `species` value as `DisplayText.Data`, or the unknown key as `DisplayText.Copy` when the value is absent, blank or `"unknown"` — normalised through `IC-017`'s `valueText`, so no raw `"unknown"` and no English literal reaches a platform (`REQ-FUNC-002`, `UI_SPEC.md` §6.2); `type` is never substituted for it. Amended 2026-10-03 from `String` (`CONF-74`).
  - `statusLabel` is `statusKey(status)`, so a card component renders its text without calling a formatter; `status` stays for colour and semantics.
  - The four displayed items (photo, name, status, species) are complete in this type: a platform component `MUST NOT` require a fetch or a second model to render a card.
  - The type is immutable and contains no platform and no wire type; a design-system component consumes its primitives only (`DESIGN.md` §3).
  - Adding a required field is a contract change (§8) because both platforms construct and read this type.
- **Status:** implemented by `TASK-041` in B3 Phase 3.3, with `from` as the one mapping from `CharacterSummary`.
- **Traceability:** `REQ-FUNC-002`, `REQ-FUNC-005`, `UI_SPEC.md` §6.2, `DESIGN.md` §4.2.

### IC-017 — `CopyKey` and `PresentationFormatters`

- **Declarations** (`:core:presentation`, `commonMain`):

```kotlin
@JvmInline
value class CopyKey(val value: String)

/** A string whose wording depends on a count; never resolved as a plain string (DEC-132). */
@JvmInline
value class PluralKey(val value: String)

/** The one canonical key list: every key a shared contract binds, each once. */
object CopyKeys {
    val ERROR_TITLE: CopyKey            // … every key of ERROR_FLOW.md §4.1 …
    val STATUS_ALIVE: CopyKey           // "status_alive"
    val STATUS_DEAD: CopyKey            // "status_dead"
    val VALUE_UNKNOWN: CopyKey          // "value_unknown", the one "Unknown"
    val GENDER_FEMALE: CopyKey          // "gender_female"
    val GENDER_MALE: CopyKey            // "gender_male"
    val GENDER_GENDERLESS: CopyKey      // "gender_genderless"
    val APP_NAME: CopyKey               // "app_name", the launcher label
    // … the B4 surface keys: the splash, the four navigation labels, the two placeholder screens,
    // each registered by TASK-013 with the resources that carry it (DEC-101) …
    val DETAIL_APPEARS_IN_EPISODES: PluralKey  // "detail_appears_in_episodes", DEC-132
    val all: Set<CopyKey>
    val plurals: Set<PluralKey>         // apart from `all` (DEC-132)
}

sealed interface DisplayText {
    data class Data(val value: String) : DisplayText    // data-derived, shown unchanged
    data class Copy(val key: CopyKey) : DisplayText     // resolved by the platform
}

interface PresentationFormatters {
    fun statusKey(status: CharacterStatus): CopyKey
    fun genderKey(gender: CharacterGender): CopyKey
    fun unknownKey(): CopyKey
    fun valueText(raw: String?): DisplayText
    fun dimensionText(origin: LocationSummary, enrichRequested: Boolean): String?
    fun firstSeenText(summaries: List<EpisodeSummary>?): String?
    fun rateLimitCountdown(retryAfterSeconds: Long?): Long?            // TASK-022, GAP-027, DEC-123
    fun failureMessage(failure: ApiFailure): FailureMessage            // TASK-022
    fun failureTitle(): CopyKey                                        // TASK-022
    fun retryAction(): CopyKey                                         // TASK-022
    fun isAutomaticallyRetryable(failure: ApiFailure): Boolean          // TASK-022
    fun recovery(failure: ApiFailure): Recovery                        // TASK-112, DEC-131
    fun inlineFailureMessage(failure: ApiFailure): FailureMessage      // TASK-112, DEC-131
}

/** How a failed Detail load is recovered, with the key of its one affordance (DEC-131). */
enum class Recovery(val actionKey: CopyKey) { Retry(CopyKeys.ACTION_RETRY), Back(CopyKeys.ACTION_BACK) }

/** The copy of one failure: the key, plus the typed values its wording substitutes (GAP-027, DEC-123). */
data class FailureMessage(val key: CopyKey, val arguments: List<MessageArgument> = emptyList())

sealed interface MessageArgument {
    data class Number(val value: Long) : MessageArgument     // substituted by %d / %1$ld
    data class Text(val value: String) : MessageArgument     // substituted by %s / %1$@
}

fun FailureMessage.formatArguments(): Array<Any>             // Long or String per argument, in order

object DefaultPresentationFormatters : PresentationFormatters
```

- **Semantics:** `CopyKey` names a user-visible string. The canonical key list is `CopyKeys` (`DEC-015`, `DEC-020`): it registers the keys the failure chain binds (`ERROR_FLOW.md` §4.1) and the keys the formatters return, allocated here — `status_alive`, `status_dead` and `value_unknown`, whose approved English copy is `UI_SPEC.md` §6.2's "Alive", "Dead" and "Unknown". A feature registers its own keys with the resources that carry them (`TASK-013`, `TASK-060`). The English and Spanish strings are authored in each platform's resource files (`strings.xml`, `Localizable.strings`; `GUIDELINES.md` §7.2), never in Kotlin, and `TEST-UNIT-036` holds every key present on both platforms with identical values per locale. The formatters are pure functions over `IC-002` types.
- **Invariants**
  - Every user-visible literal is a `CopyKey`; a formatter `MUST NOT` embed English copy. Only data-derived text (proper nouns, episode codes, numeric values) is returned as `String`.
  - The Android copy set lives in `:core:designsystem` (`core/designsystem/src/main/res/values{,-es}/strings.xml`) and a component resolves a key **by its name** through that module's compile-checked resolver table; `:core:designsystem` therefore names no presentation type and keeps its Compose-only build (`DEC-100`). The iOS set lives in the Apple resources (`TASK-060`).
  - The formatters are pure and platform-free: no clock, no network, no `Locale`-dependent formatting beyond what the platform resource layer applies, and no platform type in a signature.
  - `unknownKey()` is the single source of the "Unknown" presentation: a raw API value that is absent, blank or `"unknown"` is rendered through it and `MUST NOT` be displayed raw (`REQ-FUNC-002`, `AC-REQ-FUNC-002-2`).
  - `statusKey(CharacterStatus.Unsupported(raw))` returns `unknownKey()`; an unrecognised status never renders as an internal value (`REQ-NFR-004`, `AC-REQ-NFR-004-2`).
  - A plural key is a `PluralKey`, registered in `CopyKeys.plurals` and never in `all`. Android carries it as `<plurals>` in the one copy set, resolved by `CopyResolver.plural(key, count)`; Apple carries it in `Localizable.stringsdict` as exactly one `NSStringPluralRuleType` variable, resolved by `LocalizedCopy.plural(for:count:)`. Both platforms carry the `one` and `other` forms in `en` and `es`, and `TEST-UNIT-082` holds every form identical per locale (`DEC-132`).
  - `genderKey` returns `gender_female`, `gender_male` or `gender_genderless` for the three supported genders, and `unknownKey()` for `Unknown` and `Unsupported(raw)` (`REQ-FUNC-002`, `AC-REQ-FUNC-002-2`, `DEC-131`). The approved copy is "Female", "Male", "Genderless"; Spanish "Femenino", "Masculino", "Sin género" (`DEC-128`).
  - A formatter returns `null` to mean "hide this row/tile" and `MUST NOT` return an empty or placeholder string (`UI_SPEC.md` §6.3).
  - `dimensionText` derives the value from `origin` and returns `null` when the origin carries neither a dimension nor a parenthesised designation; it `MUST NOT` invent a value (`UI_SPEC.md` §6.3).
  - `firstSeenText(null)` returns `null`; the "first seen in" row is therefore absent exactly when enrichment was not requested (`AC-REQ-FUNC-023-2`). An enrichment that found no episode is `null` as well; otherwise the value is the first episode's "name · code" (`UI_SPEC.md` §6.3).
  - `valueText(raw)` is `DisplayText.Copy(unknownKey())` for an absent, blank or `"unknown"` value (any case) and `DisplayText.Data(raw)`, unchanged, otherwise.
  - `dimensionText` prefers the enriched `origin.dimension` when enrichment was requested, then the designation in parentheses at the end of `origin.name` ("Earth (C-137)" → "C-137"); an unknown value is not a dimension.
  - Every `CopyKey` value produced by these formatters `MUST` exist in both the Android resource file and the iOS resource file; the parity test fails on a missing or extra key (`REQ-UX-008`, `AC-REQ-UX-008-1`, `DEC-020`).
  - `failureMessage(failure)` maps every `ApiFailure` of `ERROR_FLOW.md` §4 to its own key, so a failure class can never render another class's copy (`REQ-FUNC-022`, `AC-REQ-FUNC-022-1`). The rate-limit message carries its countdown as an **argument** rather than interpolated text, because the wording and its placeholder live in the platform resource file (`GAP-027`).
  - Every argument is **typed** (`DEC-123`): `MessageArgument.Number` for a number, `MessageArgument.Text` for data-derived text, in the order of the key's positional specifiers. A platform substitutes each with the specifier its type needs through its own resource formatter (`stringResource(id, *formatArguments())` on Android, `String(format:locale:arguments:)` with an `Int64` or `NSString` on iOS) and `MUST NOT` parse a string or inspect the template to choose a type; `String.format` over a resolved resource is not a substitution path.
  - A key with a placeholder is never paired with fewer arguments than it substitutes: `RateLimited` maps to `error_message_rate_limited` with exactly one `Number` when `rateLimitCountdown` yields a value, and to `error_message_rate_limited_no_countdown`, which has no placeholder, otherwise (`DEC-123`).
  - `rateLimitCountdown(retryAfterSeconds)` is the one formatter for the number in `error_message_rate_limited`: the advised seconds, or `null` when the advice is absent or negative. It `MUST NOT` invent a value, and it is the only place the number is derived, so both platforms render the same countdown (`ERROR_FLOW.md` §4.1, `GAP-027`).
  - `isAutomaticallyRetryable(failure)` states the recovery table's answer rather than a per-surface choice: `Offline`, `Timeout` and `Server` are retried within the bounded budget, and TLS (`Unknown`), another `4xx` (`InvalidRequest`), `NotFound`, `RateLimited`, `GraphQl`, `MalformedResponse` and `EmptyBody` are not retried automatically (`ERROR_FLOW.md` §10, `API_SPECS.md` §6.3, `DEC-084`). A user-initiated retry stays available for every class.
  - `failureTitle()` and `retryAction()` return the shared full-surface title and the retry affordance, so the two keys are named once instead of per surface.
  - `recovery(failure)` is `Back` for `NotFound` — a detail `404` is terminal for the identifier (`API-ERR-016`) — and `Retry` for every other failure; a Detail surface `MUST NOT` offer Retry for a `NotFound` (`ERROR_FLOW.md` §4, §10, `DEC-131`). `inlineFailureMessage(failure)` is the message of the inline error beside a retained header: `failureMessage(failure)` when the recovery is `Back`, `detail_error_inline` otherwise.
- **Traceability:** `REQ-FUNC-002`, `REQ-FUNC-013`, `REQ-FUNC-022`, `REQ-FUNC-023`, `REQ-UX-008`, `DEC-015`, `DEC-020`.

### IC-025 — `DetailHandoff`

- **Declaration** (`:core:presentation`, `commonMain`):

```kotlin
class DetailHandoff {
    fun publish(card: CharacterCardUi)
    fun consume(id: CharacterId): CharacterCardUi?
    fun clear()
}
```

- **Semantics:** the card-to-detail hand-off (`DESIGN.md` §4.2). The detail screen must render the card the user tapped **before the network responds**, so the hero animates from the card's bounds and the known fields are on screen immediately (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`). Neither feature may name the other (ADR-0001), so the payload travels through this port: Discovery publishes one `CharacterCardUi`, Detail consumes the one matching the id it was routed with, and the composition root owns the single instance.
- **Invariants**
  - It holds at most one card, and never a list, a page or any network state: a screen reads it once on entry.
  - `consume` returns the held card only when its id equals the requested one; a deep link or a process restart consumes nothing, which is why the detail screen renders from its own state in that case.
  - It is not a cache: the card a consumer receives is the one that was published, and `clear` drops it rather than retaining it.
  - `:androidApp` links `:core:presentation` in production for this declaration (`R11`) and for `IC-026`; beyond those two and the shared copy keys the composition root uses nothing from it.
- **Traceability:** `REQ-FUNC-002`, `REQ-FUNC-009`, `DESIGN.md` §4.2, `DEC-013`.

### IC-026 — `SplashGate`

- **Declaration** (`:core:presentation`, `commonMain`, `splash` package):

```kotlin
class SplashGate(
    repository: CharacterRepository,
    dispatcher: CoroutineDispatcher,
    val minimum: Duration = MINIMUM,   // 1.2 s
    val maximum: Duration = MAXIMUM,   // 3 s
) {
    suspend fun awaitReady(): DataResult<*>?
}
```

- **Semantics:** the splash readiness gate of `DEC-098`, shared by both shells since `DEC-136`. The Android shell awaits it in its splash overlay, and the iOS root view awaits it through `MultiverseBootstrap.awaitSplashReady(onReady)`, so the two splashes end by one policy (`REQ-FUNC-007`, `UI_SPEC.md` §6.1).
- **Invariants**
  - `awaitReady` returns no sooner than `minimum` and no later than `maximum`, measured on the injected dispatcher's clock; no wall clock is read.
  - It completes on the first page's **outcome** — a success or a failure — and returns it; it returns `null` when the ceiling expired first. A failure never holds the splash (`AC-REQ-FUNC-007-2`).
  - Its one request is `CharacterRepository.page(CharacterFilter(), 1)` under the default policy, the page the first screen loads, so the splash warms that entry instead of costing a request of its own.
  - A shell shows the splash once per launch: the Android "ready" flag lives in saved state, so a configuration change does not replay it.
- **Traceability:** `REQ-FUNC-007`, `DEC-098`, `DEC-136`, `TEST-UI-006`, `TEST-UNIT-084`, `TEST-UI-026`.

### IC-027 — `StateObserver`

- **Declaration** (`:core:presentation`, `commonMain`, `observation` package):

```kotlin
class StateObserver<T>(
    flow: StateFlow<T>,
    dispatcher: CoroutineDispatcher,
    onEach: (T) -> Unit,
) {
    fun close()
}
```

- **Semantics:** the hand-written bridge through which an iOS state holder hears a shared `StateFlow` (`DEC-143`, within `DEC-013`): it collects [flow] on [dispatcher] — the main queue on iOS — and calls `onEach` with each value until `close`. It replaces reading the flow on a timer.
- **Invariants**
  - The current value is delivered first, then each new value once; a value equal to the last is not delivered, because a `StateFlow` does not emit it.
  - Nothing is delivered after `close`; `close` is idempotent.
  - On iOS each holder owns its observers and its one scope through `ScreenLifetime`, which closes both when the holder goes (`IC-014`).
- **Traceability:** `REQ-NFR-003`, `DEC-143`, `TEST-UNIT-094`, `TEST-UNIT-095`.

### IC-028 — `PresentationBindings`

- **Declaration** (`:core:presentation`, `commonMain`):

```kotlin
object PresentationBindings {
    const val DEFAULT_DISPATCHER: String   // "multiverse.dispatcher.default"
    const val MAIN_DISPATCHER: String      // "multiverse.dispatcher.main"
}
```

- **Semantics:** the names under which the composition roots bind the app-wide presentation dependencies once (`DEC-145`): `DEFAULT_DISPATCHER` (`Dispatchers.Default`) for shared state-holder work and `MAIN_DISPATCHER` (the platform main thread) for a platform holder's publication. `PresentationFormatters` is bound once, unqualified, beside them.
- **Invariants**
  - Only `:androidApp`'s shell and `IosGraph` bind these; no feature module declares an app-wide dispatcher or the formatters.
  - A feature resolves a dispatcher by its name, so loading or reordering another feature's module never changes what it receives.
- **Traceability:** `DEC-145`, `TEST-UNIT-101`.

### IC-018 — `CharacterListUiState` and `CharacterListIntent`

- **Declarations** (`:feature:discovery`, `presentation` package, `commonMain`):

```kotlin
data class CharacterListUiState(
    val filter: CharacterFilter = CharacterFilter(),
    val items: List<CharacterCardUi> = emptyList(),
    val totalCount: Int? = null,
    val loadState: LoadState = LoadState.Loading,
    val isAppending: Boolean = false,
    val isStale: Boolean = false,
    val contentFailure: ApiFailure? = null,   // DEC-124
    val isRefreshing: Boolean = false,        // DEC-124
)

sealed interface CharacterListIntent {
    data class QueryChanged(val query: String) : CharacterListIntent
    data class StatusSelected(val status: StatusFilter) : CharacterListIntent
    data object ClearFilters : CharacterListIntent      // DEC-129
    data object LoadNextPage : CharacterListIntent
    data object Refresh : CharacterListIntent
    data object Retry : CharacterListIntent
}
```

- **Consumed by:** the Android ViewModel in `:feature:discovery` (its Android UI source set) and the iOS `ObservableObject` in `iosApp/Features/Discovery` — the same classes, unchanged (`DEC-013`, `DEC-015`).
- **Invariants**
  - `items`, `totalCount`, `isAppending`, `isStale` and `contentFailure` are projections of the observed `PagerState` (`IC-014`); the state holder `MUST NOT` introduce an additional source of truth for any of them. `isRefreshing`, like the `Loading` session flag, is the holder's own fact about a load it started.
  - `contentFailure` is `PagerState.failure` while content is displayable — `ERROR_FLOW.md` §4's "keep content, non-blocking error" — and `null` otherwise: it is non-null only with `loadState == Content`, and `null` while a load is re-attempting it (`isAppending` or `isRefreshing`), so the failure is never offered for retry while its retry runs. Both platforms render it with its `IC-017.failureMessage` and a Retry (`UI_SPEC.md` §8, `DEC-124`).
  - `isRefreshing` is `true` from a `Refresh` (or a stale `Retry`) until that refresh ends, superseded or not.
  - `filter` is the filter the current `items` were loaded with; a state whose `filter` has changed but whose `items` still belong to the previous filter `MUST NOT` be emitted.
  - `items` maps one-to-one and in order from `PagerState.items`; a state holder `MUST NOT` reorder, filter or de-duplicate the list.
  - `loadState` is derived from `PagerState` (`IC-014`) plus the state holder's per-filter session flags, using this precedence, evaluated in order and pinned by a test: (1) `Loading` while no load has completed for the current filter; (2) `Error(failure)` when the newest attempt failed and no content is displayable; (3) `Empty` when a load completed for the current filter with no failure and zero items; (4) `Content` otherwise. `TESTING.md` §1 P3 requires this precedence to be asserted.
  - `isAppending` is `true` only with `loadState == Content`; it is `false` in every other combination.
  - `isStale == true` implies `loadState == Content` and a cache source for the displayed items (`IC-003`); stale content is displayed, never replaced by an error, while it exists.
  - `totalCount` is `null` until the server establishes it and is never `0` as a placeholder (`AC-REQ-FUNC-001-3`).
  - `QueryChanged` and `StatusSelected` reset paging to page 1; `StatusSelected` preserves the active query and `QueryChanged` preserves the active status (`REQ-FUNC-003`, `REQ-FUNC-004`, `AC-REQ-FUNC-004-1`).
  - `ClearFilters` resets the query **and** the status to their defaults in one page-1 request, and cancels a query still settling, so the result is the unfiltered first page (`AC-REQ-FUNC-010-2`, `DEC-129`). A platform's search field follows a query the state changed and does not send it back as `QueryChanged`.
  - `Retry` starts a fresh attempt budget and clears the error on success: with a failure it re-attempts the failed load through `IC-014.retry()` — the failed append as that page, a failed refresh as a refresh — and with no failure but `isStale` content it revalidates page 1 through `IC-014.refresh()` (`ForceNetwork`), which is the stale banner's action (`ERROR_FLOW.md` §9); with neither it does nothing. `Refresh` revalidates over the network even when the cache is fresh and keeps the previous items if it fails (`REQ-FUNC-011`, `REQ-FUNC-012`, `AC-REQ-FUNC-011-1`, `DEC-124`).
  - No intent awaits a load inside the holder's intent loop: `LoadNextPage`, `Refresh` and `Retry` start their pager call in a child of the holder's scope, so a `QueryChanged` or `StatusSelected` that arrives during a load is handled at once and supersedes the load through `IC-014`'s generation guard (`AC-REQ-FUNC-003-2`, `AC-REQ-FUNC-004-1`, `DEC-124`).
  - An intent `MUST` be the only write path: a view `MUST NOT` call a repository or use case directly (`ERROR_FLOW.md` §3 invariant 4).
  - Voice search adds no intent: a dictated query arrives as `QueryChanged` and receives the same debounce and cancellation (`DEC-002`, `UI_SPEC.md` §6.2).
- **Traceability:** `REQ-FUNC-001`, `REQ-FUNC-003`, `REQ-FUNC-004`, `REQ-FUNC-010`, `REQ-FUNC-011`, `REQ-FUNC-012`, `REQ-UX-009`, `DEC-013`, `DEC-015`, `DEC-016`.

### IC-019 — `CharacterDetailUiState`, `InfoRowUi` and `CharacterDetailIntent`

- **Declarations** (`:feature:character-detail`, `presentation` package, `commonMain`):

```kotlin
data class CharacterDetailUiState(
    val header: CharacterCardUi? = null,
    val gender: CopyKey? = null,
    val episodeCount: Int? = null,
    val dimension: String? = null,
    val info: List<InfoRowUi> = emptyList(),
    val isFavorite: Boolean = false,
    val loadState: LoadState = LoadState.Loading,
)

enum class InfoRowKind { Origin, LastKnownLocation, FirstSeenIn }

data class InfoRowUi(
    val kind: InfoRowKind,
    val copyKey: CopyKey,
    val value: DisplayText,
)

sealed interface CharacterDetailIntent {
    data object ToggleFavorite : CharacterDetailIntent
    data object Retry : CharacterDetailIntent
}
```

- **Consumed by:** the Android ViewModel in `:feature:character-detail` (its Android UI source set) and the iOS `ObservableObject` in `iosApp/Features/CharacterDetail` (`DEC-013`).
- **Invariants**
  - `header` is populated from the list-provided `CharacterCardUi` before any detail response and `MUST` be rendered first, so the shared-element/zoom transition has a source (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`, `DESIGN.md` §4.2).
  - `loadState == Error` `MUST NOT` clear a non-null `header`: a detail failure with list data keeps the known fields and offers the inline error with `IC-017.inlineFailureMessage` and `IC-017.recovery` (`AC-REQ-FUNC-002-3`). With a `null` header there is nothing known to keep, so both platforms render the full-surface error state — `failureTitle()`, `failureMessage(failure)` and the recovery's affordance — never an empty hero (`AC-REQ-UX-009-1`, `DEC-131`).
  - `gender` is `IC-017.genderKey(CharacterDetails.gender)` once a detail has answered, and `null` before that and on a failure, because the list-provided card carries no gender. Android renders the subtitle "Species · Gender · Origin", iOS "Species · Gender" (`REQ-FUNC-002`, `UI_SPEC.md` §6.3, `DEC-131`).
  - `episodeCount` is derived from `CharacterDetails.episodeIds.size` and `MUST NOT` be computed from `episodeSummaries`, so it renders even when enrichment is absent (`REQ-FUNC-023`, `AC-REQ-FUNC-023-2`).
  - `InfoRowKind.FirstSeenIn` appears in `info` only when enrichment was requested and `episodeSummaries` is non-null; the row is absent rather than empty (`AC-REQ-FUNC-023-2`).
  - `dimension == null` means "hide the dimension tile"; it is produced by `IC-017.dimensionText` and `MUST NOT` be replaced by a placeholder (`UI_SPEC.md` §6.3).
  - `copyKey` on a row is a `CopyKey`; the row `MUST NOT` carry an English label (`REQ-FUNC-013`, `REQ-UX-008`).
  - `isFavorite` reflects the stored set and updates immediately on `ToggleFavorite`, before the write completes, then reconciles with `ObserveFavoriteIds` emissions; the control therefore never shows a stale toggled state (`REQ-FUNC-006`, `AC-REQ-FUNC-006-1`).
  - `info` order is fixed as `Origin`, `LastKnownLocation`, `FirstSeenIn`. Once a detail has answered, the `Origin` and `LastKnownLocation` rows are always present, and their `value` is `IC-017.valueText` of the location name: an absent, blank or `unknown` name is `DisplayText.Copy(value_unknown)`, never a dropped row (`AC-REQ-FUNC-002-2`, `DEC-131`). Only `FirstSeenIn` is filtered by availability; its `value` is `DisplayText.Data`.
  - Only `ToggleFavorite` and `Retry` are intents; `Retry` starts a fresh attempt budget (`REQ-FUNC-011`).
- **Traceability:** `REQ-FUNC-002`, `REQ-FUNC-006`, `REQ-FUNC-011`, `REQ-FUNC-023`, `REQ-UX-009`, `DEC-013`, `DEC-015`.

### IC-020 — `FavoritesUiState` and `FavoritesIntent`

- **Declarations** (`:feature:favorites`, `presentation` package, `commonMain`):

```kotlin
data class FavoritesUiState(
    val items: List<CharacterCardUi> = emptyList(),
    val loadState: LoadState = LoadState.Loading,
)

sealed interface FavoritesIntent {
    data object Retry : FavoritesIntent
}
```

- **Consumed by:** the Android ViewModel in `:feature:favorites` and the iOS `ObservableObject` in `iosApp/Features/Favorites` (`DEC-013`).
- **Invariants**
  - `items` reuses `IC-016` unchanged, and the same card rendering path as discovery, so a favourite is displayed by the same component contract (`UI_SPEC.md` §6.4).
  - `loadState == Empty` while the stored set is empty; the designed empty state is shown only in that condition (`REQ-FUNC-006`, `AC-REQ-FUNC-006-3`, `UI_SPEC.md` §6.4).
  - `loadState == Content` iff at least one favourite is displayable; there is no network load in this feature, so `Loading` appears only while the first store emission is awaited and `Error` only when the store read failed.
  - The items' order `MUST` be deterministic and stable across re-observation; the concrete order is not fixed by any requirement (§10 assumption).
  - The feature issues no **detail** request and holds no detail data: it renders cards from `IC-016` and resolves a card to its detail screen through the shared navigation hand-off (`IC-025`). It resolves each favourite **id** through `IC-007`'s cached single-id read, because the store holds ids only (`IC-008`, `IC-013`) and this state's `items` are cards; the earlier wording forbade the request its own declared state type requires (`CONF-79`, `DESIGN.md` §4.5).
- **Traceability:** `REQ-FUNC-006`, `REQ-FUNC-008`, `DEC-004`, `UI_SPEC.md` §6.4.

### IC-023 — `SettingsUiState` and `SettingsIntent`

- **Declarations** (`:feature:settings`, `presentation` package, `commonMain`):

```kotlin
data class SettingsUiState(
    val soundsEnabled: Boolean = false,
    val remoteProtocol: RemoteProtocol = RemoteProtocol.Rest,
    val canDeleteFavorites: Boolean = false,
    val isConfirmingDelete: Boolean = false,
)

sealed interface SettingsIntent {
    data class SoundsToggled(val enabled: Boolean) : SettingsIntent
    data class RemoteProtocolSelected(val protocol: RemoteProtocol) : SettingsIntent
    data object DeleteFavoritesRequested : SettingsIntent
    data object DeleteFavoritesConfirmed : SettingsIntent
    data object DeleteFavoritesDismissed : SettingsIntent
}
```

- **Consumed by:** the Android ViewModel in `:feature:settings` and the iOS `ObservableObject` in `iosApp/Features/Settings` (`DEC-013`).
- **Invariants**
  - `soundsEnabled` and `remoteProtocol` mirror `IC-021`'s latest emission; the state holder keeps no competing copy.
  - `canDeleteFavorites` is true iff the `ObserveFavoriteIds` set is non-empty (`AC-REQ-FUNC-035-3`).
  - `DeleteFavoritesRequested` sets `isConfirmingDelete` only when `canDeleteFavorites` is true. `DeleteFavoritesConfirmed` invokes `ClearFavorites` exactly once and then clears `isConfirmingDelete`. `DeleteFavoritesDismissed` clears it and invokes nothing (`AC-REQ-FUNC-035-1`).
  - `RemoteProtocolSelected` with the current protocol writes nothing.
  - The feature issues no remote request.
- **Traceability:** `REQ-FUNC-033`, `REQ-FUNC-034`, `REQ-FUNC-035`, `DEC-055`, `UI_SPEC.md` §6.5.

## 7. Consumption by platform state holders

`DEC-013` removed shared ViewModels; `DEC-015` kept the shared state classes. The consequence is a precise, two-sided obligation.

| Concern | Android | iOS |
| --- | --- | --- |
| Observation | `viewModelScope` collection of the shared `StateFlow` | `StateObserver` (`IC-027`) on the main queue, owned with the screen's one scope by `ScreenLifetime`; no polling (`DEC-143`) |
| State holder | A ViewModel per feature screen in the feature module's Android UI source set (for example `:feature:discovery`, `androidMain/<package>/discovery/ui/`) | An `ObservableObject` (or `@Observable` type) per feature screen in the matching Swift package (for example `iosApp/Features/Discovery`) |
| State types | The `IC-###` classes from the Kotlin framework, unchanged | The same classes, reached through the generated Kotlin framework |
| Intents | `onIntent(CharacterListIntent)` on the shared type | The same intent values dispatched from SwiftUI |
| Derived display values | Provided by `IC-017` formatters and by the state objects | Identical — the same shared code |
| Ownership of state content | Shared Kotlin only | Shared Kotlin only |

### 7.1 Rules

- **R1** A platform state holder `MUST` hold, expose and forward the shared state object; it `MUST NOT` re-declare, copy or shadow any field of an `IC-###` state type with a locally derived value.
- **R2** A platform state holder `MUST NOT` compute a display string, a status label, a dimension or an episode count; those come from `IC-017` or from the state object, so the two platforms cannot diverge (`REQ-UX-008`).
- **R3** A platform state holder owns only platform concerns: lifecycle, observation mechanics, navigation, and the image pipeline.
- **R4** Both platforms consume the same type names and the same `IC-###` ids; a name that exists only in Swift or only in Compose `MUST NOT` be introduced for a shared value.
- **R5** Where a state change is behavioural (a filter applied, a page appended, a favourite toggled), the rule is implemented in shared Kotlin, and the platform state holder only dispatches the intent. Divergence between the two platforms is a defect in the shared contract, not a platform choice.
- **R6** If a UI-state type changes, this file is edited first; then the Kotlin feature module; then the Android ViewModel and the iOS `ObservableObject` if the consumed surface changed; then any test that pins the old shape. `DESIGN.md` §4.1 is edited only when the *flow* or the module picture changed, not when a field changes.

Swift reaches these types without SKIE: the framework exposes them as Objective-C-compatible classes, and the state holder reads properties and dispatches intents (`adr/0003-ui-sharing-strategy.md`). The framework itself is packaging, not a contract: exactly one is produced by the `:core:ios` export module, it exports the five `:feature:*` modules, `:core:domain` and `:core:presentation` through `api` and links `:core:data` as an unexported `implementation` (`DEC-091`, amending ADR-0012), and `iosApp/` links no second Kotlin framework ([ADR-0012](adr/0012-ios-framework-export.md), `DEC-058`) — the Swift-visible surface is therefore exactly the `IC-###` types of this file, and adding a type to it is a contract change (`§8.2`). The module and the framework exist since `TASK-078`. The hand-written bridge is project code and is covered by an iOS test (see §9.4).

## 8. Change, versioning and compatibility rules

### 8.1 How a contract change is made

| Step | Requirement |
| --- | --- |
| 1 | The change is described against this file: the `IC-###` row, the new signature or invariant, and the reason. |
| 2 | A decision id is recorded when the change is a decision rather than a detail: a new seam, a relocated declaration, a changed module boundary, or a new shared type. The `DEC-###` row is added in `DECISION_BOARD.md` and, when the change is architectural, an ADR is added under `docs/adr/` (`DECISION_BOARD.md` §1: an accepted decision that changes architecture requires an ADR). |
| 3 | A failing test precedes the implementation for the changed behaviour, per the TDD protocol (`DEC-053`, `DEFINITION.md` D3). The red test is written against the new contract; a pure documentation change is one of the three recognised exceptions and states so. |
| 4 | This file, the code, the fakes in `:core:testing`, the platform call sites and the documents that cite the id change in the same pull request (`DEC-046`, `DEFINITION.md` D7, D8). |
| 5 | The pull request requires the full mandatory check set on both platforms, green on its final state (`DEC-054`); the red commit from step 3 is expected to fail and is not a violation. |

Relocating a declaration to a different module, or moving ownership between documents, is a contract change under this section even when the signature is unchanged: it changes what a consumer may depend on.

### 8.2 What counts as breaking for the Swift consumer

The iOS app consumes these types through the generated Kotlin framework without SKIE, so the binding is less expressive than Kotlin's: sealed hierarchies and enums surface as classes with `is`/`as` checks, nullability becomes an annotation, and defaults do not exist. The following are therefore **breaking** and require a same-change Swift update plus a test:

| # | Change | Why it breaks Swift |
| --- | --- | --- |
| B1 | Renaming or removing a type, a property, a function or an enum case | The Swift binding name changes or disappears; the build fails or a silent default is used |
| B2 | Changing a parameter's type, order or count; changing a return type | Call-site signature changes |
| B3 | Adding a variant to a sealed hierarchy consumed by Swift (`IC-015`, `IC-018`, `IC-019`, `IC-020` intents) | Swift code that branches with `is` checks silently falls into its `else` path, so the new state renders as the wrong UI without a compile error |
| B4 | Adding, removing or renaming an `enum class` case (`StatusFilter`, `InfoRowKind`) | Enum case sets are part of the surface on both sides |
| B5 | Tightening nullability (`T?` → `T`), or relaxing it (`T` → `T?`) | The nullability annotation consumed by Swift changes; the Kotlin side may compile while the Swift side does not, or a force-unwrap becomes wrong |
| B6 | Changing a default value or an invariant's precedence | No compile error anywhere; the behaviour of both platforms changes silently |
| B7 | Changing an ordering guarantee stated as an invariant (row order, item order) | Swift code that relies on the order renders differently |

### 8.3 What is not breaking

- Adding an `IC-###` row that references a declaration owned elsewhere.
- Adding a member to an interface whose only implementations live in `:core:data`, provided the fakes in `:core:testing` are updated in the same change.
- Adding an optional parameter with a default value, when the change does not alter an invariant.
- Adding a `MAY` clause, a clarification or a traceability reference.
- Adding a new optional state field **only** when both platform state holders ignore it; if either reads it, treat it as B1.

### 8.4 Rules that hold for every change

- **C1** A DTO, a wire envelope, a platform type or an exception originating outside `:core:data` `MUST NOT` appear in a contract signature (§3.2).
- **C2** A contract change `MUST NOT` be introduced by a caller: a feature `MUST NOT` widen a shared type, and a platform `MUST NOT` add a member to an `IC-###` declaration.
- **C3** A contract `MUST NOT` be forked: no `expect/actual` may give two different meanings to one contract. `IC-013` has two `actual` implementations of one meaning, verified by one shared contract suite (`TESTING.md` §6.2).
- **C4** Removing a contract is a clean cutover: every consumer migrates, and the declaration, its fakes and its tests are deleted in the same change. No compatibility shim, alias or re-export is kept.
- **C5** Versioning is by document revision, not by semantic version: this file has no `MAJOR.MINOR` number. Compatibility is decided by the tables above; the app versions in `VERSION` describe the shipped apps (`DEC-043`).

## 9. Contract verification strategy

Test ids and layers are owned by `TESTING.md`; this section states which mechanism verifies which contract and never allocates an id. Every `IC-###` row `MUST` have at least one test in the family that owns its implementation (`TESTING.md` §3.1).

### 9.1 Verified in `commonTest` with fakes

| Contract | Mechanism | Notes |
| --- | --- | --- |
| `IC-003` (with `IC-007`, `IC-011`) | Sealed-outcome tests over fakes: `Success` carries a fully mapped value and may carry warnings; `Failure` carries the `ApiFailure` and no value; `Failure.source` is never a cache hit; a `CancellationException` propagates and produces no `Failure`; the hierarchy is handled exhaustively | No network, no platform type |
| `IC-007` | Fake remote source + fake cache storage; assertions on empty-result success, enrichment bounds, deduplication, `Failure` propagation and cancellation pass-through. `FakeCharacterRepository` (`TASK-036`) is the behavioural double of this seam for its consumers, and `FakeRemoteSource` (`TASK-037`) is the double of `IC-011` below it (`DEC-090`) | `TEST-UNIT-001`, `TEST-UNIT-005`, `TEST-UNIT-011`, `TEST-UNIT-021`, `TEST-UNIT-053` |
| `IC-008` | `FakeFavoritesStore` (in-memory, `Flow`-backed) reused across a simulated restart | `TEST-UNIT-004` |
| `IC-009` | Fake repository; assertions that a use case does not swallow failure or cancellation | `TEST-UNIT-003`, `TEST-UNIT-011` |
| `IC-010` | Pure value tests: equality, cache-key distinction per filter combination, blank-query rule | `TEST-UNIT-020` |
| `IC-012` | `FakeCacheStorage` with an injectable clock and a failure mode: miss is not an exception, write failure degrades, keys do not collide, `evictAll` spares favourites | `TEST-UNIT-009`, `TEST-UNIT-020`, `TEST-UNIT-023` |
| `IC-014` | Virtual time and a fake repository: reset, coalesced `next()`, end-of-pagination, `refresh()` semantics, no clearing on failure, prefetch bound | `TEST-UNIT-016`, `TEST-UNIT-006`, `TEST-UNIT-007` |
| `IC-015`–`IC-017` | Pure tests: the `LoadState` variant construction rules, formatter outputs including `Unsupported` status and null-means-hide, `CopyKey` parity against both resource files | `TEST-UNIT-002`, `TEST-UNIT-008`, `TEST-UNIT-036` |
| `IC-018`–`IC-020` | Mapping tests over recorded `PagerState`/store emissions; the precedence and intent rules asserted without a platform test runner | `TEST-UNIT-003`, `TEST-UNIT-005`, `TEST-UNIT-006`, `TEST-UNIT-007` |
| `IC-008` `clear()`, `IC-013` `clear()` | Fake store with two collectors: one empty-set emission each, no emission on an empty store, preferences untouched | `TEST-UNIT-047` |
| `IC-021`, `IC-022` | `FakeAppSettingsStore`: defaults, atomic `update`, no emission on an equal value, unknown protocol string reads as `Rest` | `TEST-UNIT-046` |
| `IC-011` selection, `IC-012` isolation, `IC-014` reset | Fake settings flow switching protocol during an in-flight load; distinct keys per protocol; no `evictAll` on switch | `TEST-UNIT-048`, `TEST-UNIT-049` |
| `IC-023` | Intent-rule tests over fake repositories: confirmation gating, single clear, no-op on unchanged protocol | `TEST-UNIT-050` |

Fakes live in `:core:testing` and `MUST` honour the contract rather than merely echo configuration (a fake that returns what it was given is not evidence — `TESTING.md` §1 P2). Where a contract's behaviour cannot be exercised through a fake, it is a `TEST-INT-###` case instead (§9.3).

### 9.2 Verified with Ktor `MockEngine` and fixtures

| Contract | What `MockEngine` proves | Notes |
| --- | --- | --- |
| `IC-011` (both implementations) | Each adapter's observable behaviour against committed fixtures, and REST-versus-GraphQL parity to equal domain values (`TEST-CONTRACT-005`): status and query parameter encoding, `All` sending no `status`, blank query sending no `name`, singleton-versus-batch routing, chunk bounds, batch reconciliation by id, and `ApiFailure` classification for empty, malformed, `4xx`, `429` and `5xx` bodies | `TEST-CONTRACT-001`, `TEST-CONTRACT-003`, `TEST-UNIT-010`; fixtures and their inventory are owned by `TESTING.md` §4.3 (`DEC-030`) |
| `IC-007` (through the adapter) | That a filtered `404` becomes an empty page rather than a failure, and that a partial response is never cached | `TEST-CONTRACT-001`, `TEST-UNIT-009` |

`MockEngine` is used because the contract under test is the mapping and failure behaviour, not the socket. The same ids run in fixture/replay mode inside the required check set and in live mode in the separate scheduled job, as reconciled by `DEC-054` and `DEFINITION.md` D2.

### 9.3 Verified by a real integration

| Contract | Mechanism | Notes |
| --- | --- | --- |
| `IC-012` | Real HTTP disk cache behind the OkHttp engine with `MockWebServer`, plus the `404` → `no-store` rewrite | `TEST-INT-001` |
| `IC-013` | One shared contract suite executed against both `actual` implementations, including restart persistence, concurrency of two writes, empty and large sets | `TEST-INT-003`, `TEST-INT-004` |
| `IC-012` versus the image pipeline (outside the contract surface) | Rendering an image leaves the response cache untouched and stores no image bytes | `TEST-INT-002`, `AC-REQ-FUNC-021-2` |

### 9.4 Verified parity between the Kotlin contract and its Swift consumption

The risk this file must close is drift between a Kotlin state contract and the Swift state holder that consumes it, because `DEC-013` makes the bridge hand-written (`adr/0003-ui-sharing-strategy.md`).

| Check | Mechanism |
| --- | --- |
| Type and member visibility | The iOS feature package calls every member of the `IC-###` types it consumes; a renamed or removed member fails the iOS build in the required check set (`DEC-054`) |
| Nullability and enum-case handling | iOS unit tests in `iosApp/Tests` construct each shared state value through the Kotlin framework and assert the Swift-observed values; the sealed-hierarchy and enum cases of `IC-015`, `IC-018`, `IC-019`, `IC-020` are each constructed once, so adding a case without a Swift branch is a visible test change rather than a silent `else` |
| Behavioural parity | The same shared state values drive the Android ViewModel test and the iOS state-holder test, so one set of fixtures proves both platforms render the same state (`TEST-INT-003`, `TEST-INT-004` for storage; the feature state-holder cases for the screens) |
| Rendering parity | One snapshot per `LoadState` on each platform against the same fixture data; the states themselves are specified in `ERROR_FLOW.md` and `UI_SPEC.md` §8, not here (`TEST-UI-016`) |
| Bridging regressions | An iOS test that mutates shared state and asserts the Swift-observed value changes, so a broken observation path cannot pass |

`TESTING.md` allocates `TEST-UNIT-042` to the Kotlin-to-Swift parity check, wired to `REQ-PLAT-001` and `REQ-NFR-005`. The checks above are the parity evidence for that case; a contract change under §8.2 `MUST` name the iOS test that covers it.

### 9.5 What this file does not verify

Failure copy, rendered visuals and retry affordances are verified where they are owned: `ERROR_FLOW.md` (chain and copy keys), `UI_SPEC.md` §8 (visual states), `TESTING.md` (ids, layers, tooling). Coverage policy has no global threshold; the risk-bearing components are named in `TESTING.md` §12 (`DEC-031`).

## 10. Assumptions and drift register

### 10.1 Stated assumptions

| # | Assumption | Why it is stated rather than resolved |
| --- | --- | --- |
| A1 | **Resolved 2026-09-29:** failure signalling is a sealed `DataResult` (`Success`/`Failure`), not a thrown exception — `IC-006` is withdrawn. `API_SPECS.md` §3 owns the declaration; `IC-003` owns the consumer invariants | Recorded here because it reverses the convention first drafted for this file. Reasons: errors stay representable in the type system, no Kotlin exception crosses the Kotlin → Swift interop boundary (`DEC-013`), `when` handling is exhaustive, and `source`/`warnings` remain available on both outcomes (`IC-006`) |
| A2 | `IC-012` stores bytes plus a validator, and the freshness policy lives above the seam | `API_SPECS.md` §7 owns the policy but does not describe the storage shape; this keeps the policy out of the storage contract |
| A3 | `IC-020` pins determinism only; the favourites ordering is not fixed by any requirement | `UI_SPEC.md` §6.4 specifies the empty and populated cases but not an order. The concrete order is recorded when `:feature:favorites` is implemented |
| A4 | `IC-017` returns `CopyKey` for user-visible text and `String` for data-derived text | `ERROR_FLOW.md` §3 invariant 3 requires copy to be resolved from localisable resources; a formatter therefore must not embed English |
| A5 | `PagerState` is pager-local rather than reusing `IC-015` | Neither `:core:domain`, where the contract lives since `DEC-091`, nor `:core:data`, where it is implemented, may depend on `:core:presentation` (`adr/0001-module-boundaries.md`); mapping to `LoadState` happens in `IC-018` |
| A6 | The repository is the only seam that deduplicates concurrent identical requests | `REQ-REL-002` requires deduplication; this file fixes where it is observable (`IC-007`), not how it is implemented |
| A7 | The Kotlin→Swift surface is the `api` export graph of `:core:ios` — the five features, `:core:domain` and `:core:presentation`, never `:core:data` (`DEC-091`) — not a per-module framework set (ADR-0012, `DEC-058`) | The export module is M1 work (`TASK-078`) and the mechanism's behaviour with `@Serializable data object` routes and `sealed interface` intents is confirmed by that task's spike, not by this file; the surface is declared here so a contract change can be judged against it (`§8.2`) |

### 10.2 Drift found in other documents

| # | Document and section | Drift | Suggested action for that document's owner |
| --- | --- | --- | --- |
| D1 | `DESIGN.md` §3.2 | Named the discovery state and intent types `DiscoveryUiState`/`DiscoveryIntent`, while `DESIGN.md` §4.1 and `ERROR_FLOW.md` §1.2 name `CharacterListUiState`/`CharacterListIntent` | **Resolved 2026-09-29:** `CharacterListUiState`/`CharacterListIntent` (`IC-018`) is the single name repo-wide; `DESIGN.md` §3.2, §4.1 and §6 are aligned to it |
| D2 | `DESIGN.md` §4.1 | Its ownership note correctly delegates the signatures here, but the section still contains the full type block, so the same declarations exist twice | **Resolved 2026-09-29:** `DESIGN.md` §4.1 is now an ownership note plus a contract → type → consumer table, and it cites `IC-015`, `IC-016`, `IC-018`, `IC-019` |
| D3 | `ERROR_FLOW.md` §1.2 | Delegated `LoadState`, the state class names and the intent names to `DESIGN.md` §4.1, which no longer owns them | **Resolved 2026-10-01:** `ERROR_FLOW.md` §1.1 and §1.2 now cite this file's `IC-015`, `IC-018`, `IC-019` (and `IC-023` for Settings) directly; the hop through `DESIGN.md` §4.1 is gone |
| D4 | `TESTING.md` §1 P3 | Cited the `LoadState` precedence as living in `DESIGN.md` §4.1; the precedence is now an invariant of `IC-018` | **Resolved 2026-10-01:** `TESTING.md` §1 P3 already cites `IC-018` in `CONTRACTS.md`; no remaining hop |
| D5 | `DESIGN.md` §8 | The architectural testing-hooks section still names the module paths that `DEC-052` superseded | **Resolved 2026-09-29:** `DESIGN.md` §8 names `:core:*`/`:feature:*` and points the state types at this file |
| D6 | `DESIGN.md` §3.2 | Its discovery source-set sketch named a `WatchCharacterPage` use case that no document defined | **Resolved 2026-09-29:** the name is removed from `DESIGN.md`; this file declares no such use case — the pager state is observed through `IC-014` and mapped into `IC-018` |

Rows marked **Resolved** were corrected in the owning document; the remaining open rows are coordination items for that document's owner. This file edits only what it owns.

## 11. Change log

| Date | Change | Decision |
| --- | --- | --- |
| 2026-10-06 | `IC-021` gains its `soundsEnabled` consumer (`TASK-139`): the selection sound of each shell, which the iOS side observes through `MultiverseBootstrap.observeSoundsEnabled`; the invariant that no consumer produces sound is replaced by the gate's rule. | `DEC-162` |
| 2026-10-05 | `IC-027` and `IC-028` added (`TASK-115`): the state observer the iOS holders use instead of polling, and the names the composition roots bind the app-wide presentation dependencies under. | `DEC-143`, `DEC-145` |
| 2026-10-05 | `IC-026` added (`TASK-112`): the splash gate moves from `:androidApp` to `:core:presentation` and serves both shells; iOS awaits it through `MultiverseBootstrap.awaitSplashReady`. | `DEC-136` |
| 2026-10-05 | `IC-017` (`TASK-112`): `PluralKey`, `CopyKeys.plurals` and the first plural key `detail_appears_in_episodes`; Android `<plurals>` through `CopyResolver.plural`, Apple `Localizable.stringsdict` through `LocalizedCopy.plural`. Additive for the Swift consumer. | `DEC-132` |
| 2026-10-05 | `IC-017`/`IC-019` (`TASK-112`): `Recovery`, `recovery(failure)` and `inlineFailureMessage(failure)`; a not-found Detail is recovered by Back, and a failure without a header renders the full-surface error. Additive for the Swift consumer. | `DEC-131` |
| 2026-10-05 | `IC-017`/`IC-019` (`TASK-112`): `genderKey(CharacterGender)` and the keys `gender_female`, `gender_male`, `gender_genderless`; `CharacterDetailUiState.gender: CopyKey?`. Breaking for the Swift consumer (§8.2), whose initialiser gains the parameter; the iOS app and its tests change in the same commit. | `DEC-131` |
| 2026-10-05 | `IC-019` (`TASK-112`): `InfoRowUi.value` becomes `DisplayText`, and an unknown origin or location keeps its row with `value_unknown`; only `FirstSeenIn` stays availability-filtered. Breaking for the Swift consumer (§8.2), which resolves the value through `CharacterPresentation.text` in the same commit. | `DEC-131` |
| 2026-10-05 | `IC-007`/`IC-014` (`TASK-112`): a `ForceNetwork` load joins a revalidation in flight; an enriched detail is revalidated with its episodes; a stale first page is followed by a silent network load that clears the stale state on success. | `DEC-130` |
| 2026-10-05 | `IC-014` (`TASK-112`): `PagerState.isLoading` added, and a protocol switch resets like `setFilter`. The Swift initialiser of `PagerState` gains the parameter; no Swift code constructs one. | `DEC-130` |
| 2026-10-05 | `IC-018` (`TASK-112`): `CharacterListIntent.ClearFilters` clears both filter dimensions in one request. Additive for the Swift consumer. | `DEC-129` |
| 2026-10-05 | `IC-018` (`TASK-111`): `CharacterListUiState` gains `contentFailure` (the failure carried beside displayable content) and `isRefreshing`; `Retry` revalidates stale content when there is no failure; no intent awaits a load inside the intent loop. Breaking for the Swift consumer (§8.2), whose initialiser gains the two parameters; the iOS app and its tests change in the same commit. | `DEC-124` |
| 2026-10-05 | `IC-017` (`TASK-111`): `FailureMessage.arguments` becomes `List<MessageArgument>` (`Number`/`Text`) with `formatArguments()`, `rateLimitCountdown` returns `Long?`, and a rate limit without usable advice maps to the new placeholder-free key `error_message_rate_limited_no_countdown`. Breaking for the Swift consumer (§8.2), which changes in the same commit: Android crashed formatting `%d` with a `String`, and iOS rendered a pointer value or a raw `%1$ld`. | `DEC-123` |
| 2026-10-03 | B3 Phase 3.3 (`TASK-041`): `IC-015`…`IC-017` are implemented. `IC-016`'s `species` becomes `DisplayText` and gains `statusLabel` and the `from` mapping, because a `String` could not carry "Unknown" through `IC-017`'s key without an English literal (`CONF-74`); `IC-017` gains the `CopyKeys` registry, `DisplayText`, `valueText` and `DefaultPresentationFormatters`, and states where the strings live. No consumer existed, so nothing breaks (§8). | `TASK-041`, `DEC-015`, `DEC-020` |
| 2026-10-03 | B3 Phase 3.3 (`TASK-040`): `IC-013` states its two platform implementations, their keys and the read-failure degradation. | `TASK-040`, `DEC-017` |
| 2026-10-02 | B3 Phase 3.3 (`TASK-040`): `IC-008` states its implementation and its failure semantics (a write the store cannot complete is logged, not thrown); `IC-009`'s `ObserveFavoriteIds` is implemented; `IC-013` names its package and how the `expect/actual` split is realised; `IC-024` gains `LOG-018`/`LOG-019` and the `LogComponent` set with their emitter. | `TASK-040`, `DEC-017`, `DEC-090` |
| 2026-10-02 | B3 Phase 3.2: `IC-024` states its implemented members — the ten `LogEvent` classes with emitters, their value sets, `LogField`, the `ValidatingAppLogger` factories, the correlation id and the emitters — and its status; `IC-007` and `IC-014` record their implementations (`TASK-038`, `TASK-039`) and `IC-014.retry()` re-attempts a failed refresh as a refresh; §2's map places `LogSink`/`LogRecord` in `:core:domain`, as `DEC-093` decided, and the diagnostic API as implemented. | `TASK-038`, `TASK-039`, `TASK-047`, `DEC-092`, `DEC-093` |
| 2026-10-02 | B3 Phase 3.2 readiness: `IC-014` gains `retry()` and the rule that a failure suppresses `next()` until a retry, refresh or new filter (`DEC-092`), states the paging-`404` end and the owner-supplied scope, and pins `isStale` to result provenance (`CONF-71`); `IC-024`'s `LogSink`/`LogRecord` move to `:core:domain` (`DEC-093`). | `DEC-092`, `DEC-093` |
| 2026-10-02 | `IC-014` (`CharacterPager`, `PagerState`) relocated from `:core:data` to `:core:domain` before implementation, and §7 states the narrowed `:core:ios` export list; the signatures and invariants are unchanged (`DEC-091`, ADR-0014). | `DEC-091` |
| 2026-10-02 | B3 Phase 3.1: `IC-007` gains the defaulted `PageLoadPolicy` parameter and the policy-in-identity invariant (`DEC-086`, `CONF-66`); `IC-011` returns `DataResult` as its invariants already required, and states the foreign-host and list-`404` rules (`DEC-090`, `CONF-64`); `IC-014.refresh()` uses `ForceNetwork`; `IC-024` declares the single logging contract in `:core:domain` with its `:core:data` sink (`DEC-087`, ADR-0013); the fakes are mapped to `IC-007`/`IC-011` (`CONF-69`). | `DEC-086`, `DEC-087`, `DEC-088`, `DEC-090` |
| 2026-10-01 | Accepted as the `IC-###` baseline by `TASK-019`: `IC-001`…`IC-023` audited for identifier uniqueness across the repository, module ownership, absence of platform and wire types in shared signatures, DTO containment, and agreement with `DESIGN.md` §3, `API_SPECS.md` §7, `ERROR_FLOW.md` §2 and the `DEC-066` `:core:domain` rule. §7.1 states the `:core:ios` `api`-export surface (`ADR-0012`); assumption A7 added; drift rows D3 and D4 resolved. No signature changed. | `TASK-019`, `DEC-066`, `DEC-058`, ADR-0012 |
| 2026-09-29 | Document created on the `docs/documentation-system` branch: `IC-###` scheme and reference rules, the one-owner map, reference-only entries for the `API_SPECS.md` declarations, the data/domain seam contracts (`CharacterRepository`, `FavoritesRepository`, use cases, filters, `CharacterRemoteDataSource`, `CacheStorage`, `FavoritesLocalDataSource`, `CharacterPager`), the presentation contracts (`LoadState`, `CharacterCardUi`, formatters and copy keys, the list/detail/favorites state and intent types), platform consumption rules, change and Swift-compatibility rules, the verification strategy and the assumptions/drift register. Module names follow the feature-per-module layout of `DEC-052`. | `DEC-013`, `DEC-015`, `DEC-016`, `DEC-017`, `DEC-018`, `DEC-021`, `DEC-052`, `DEC-053`, `DEC-054` |
| 2026-09-29 | Failure signalling changed from a thrown `ApiException` to a sealed `DataResult` (`Success`/`Failure`); `IC-006` withdrawn and its id retired as a gap rather than reallocated, so `IC-007`…`IC-020` keep their numbers. `IC-003` rewritten around the sealed envelope; `IC-007`, `IC-009`, `IC-011` and `IC-014` reworded from throwing/returning to `DataResult` outcomes; §3, §9.1 and §10.1 updated. `API_SPECS.md` §3 owns the declaration. | `DEC-013`, `ADR-0003` |
| 2026-09-30 | `IC-008` and `IC-013` gained `clear()`; `IC-009` gained `ClearFavorites`, `ObserveAppSettings` and `UpdateAppSettings`; `IC-011` now has a REST and a GraphQL implementation selected per request; `IC-012` keys include the protocol; `IC-021` (`AppSettingsRepository`), `IC-022` (`AppSettingsLocalDataSource`) and `IC-023` (`SettingsUiState`/`SettingsIntent`) added; verification rows added. | `DEC-055`, `DEC-056` |
| 2026-09-29 | Drift register updated against the realigned documents: D1, D2, D5 and D6 marked resolved; D3 and D4 narrowed to the remaining indirect citation hop through `DESIGN.md` §4.1. | `DEC-046`, `DEC-052` |
