# ADR-0009 — Pagination: a shared custom pager instead of Paging 3

- **Status:** Accepted
- **Date:** 2026-09-29
- **Last verified:** 2026-09-29
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementation owner Implementation Engineer (Android); consulted Implementation Engineer (iOS) for the Swift consumption of the pager state
- **Authoritative for:** the pager implementation, the page contract it follows, and the loading, prefetch, deduplication and cancellation rules it enforces. Not the list UI (`UI_SPEC.md` §6.2) and not the response cache (ADR-0005).
- **Inputs:** `DEC-016` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-012`/`DEC-018` (ADR-0005), `DEC-011` (ADR-0004); `REQ-FUNC-001`, `REQ-FUNC-003`, `REQ-FUNC-004`, `REQ-FUNC-010`, `REQ-FUNC-012`, `REQ-REL-002`, `REQ-REL-003`, `REQ-NFR-003` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`API_SPECS.md`](../API_SPECS.md) §4.3, §4.4, §6.3, §7.3, §8; [`DESIGN.md`](../DESIGN.md) §4.1, §9 (decision D5); live probe 2026-09-29: `GET /api/character?page=1` reports `count=826`, `pages=42`, 20 items per page, and `page=43` returns `404`

## Owners

- **Decision owner:** System Architect — accountable for the pager contract and its loading rules.
- **Implementation owners:** Implementation Engineer (Android) for the pager, its state and its tests in `:core:data` and `:feature:discovery`; Implementation Engineer (iOS) for consuming the same pager state from SwiftUI.
- **Consulted:** Implementation Engineer (iOS) for the absence of a platform paging library; QA & Validation for the pagination test set.

## Decision

The project ships **one shared custom pager in `:core:data`**, consumed by `:feature:discovery` on both platforms. Paging 3 MUST NOT be added.

**Page contract (server-controlled).** The page size is 20 and is chosen by the server; the client MUST NOT attempt to set it (`API_SPECS.md` §4.3). `info.next == null` is the authoritative end-of-pagination signal; `info.count` and `info.pages` are data that MUST be read from the response and MUST NOT be hardcoded or cached as constants. The active filter MUST be preserved on every subsequent page, and the request URL MUST be rebuilt against the configured host rather than followed blindly from `info.next` (`REQ-SEC-001`).

**Pager rules (normative):**

1. **Paging, not bulk loading.** The pager MUST fetch the first page alone and continue only while `info.next` is non-null; it MUST NOT pre-fetch the whole result set (`AC-REQ-FUNC-001-1`, `AC-REQ-FUNC-001-2`).
2. **Single prefetch.** At most one page MAY be requested in advance, triggered as the user approaches the end of the current page (the last item or the threshold the discovery grid defines). The prefetch MUST be suppressed when `nextPage == null`, when a load is already in flight for the same key, or when the previous append failed. Two or more pages MUST NOT be requested speculatively (`API_SPECS.md` §8).
3. **Deduplication.** Concurrent identical loads MUST be collapsed into one request and one shared result, keyed by the same canonical request identity used by the cache (ADR-0005, `REQ-REL-002`). This applies both to a prefetch racing a user-driven load and to two callers asking for the same page.
4. **Cancellation on identity change.** A changed search query or status filter MUST cancel the in-flight request, reset the page counter to 1, discard the collected items and start a fresh first-page load. The reset MUST be atomic with respect to rendering: the list MUST NOT show items collected under the previous identity after a new identity is active (`AC-REQ-FUNC-003-2`). The 300 ms debounce remains a presentation concern (`REQ-FUNC-003`) and MUST NOT be implemented inside the pager.
5. **State discipline.** `LoadState.Loading` MUST be used only when there is nothing to show. Once content is displayed, a subsequent page load MUST be represented as an append indicator (`isAppending`), never by replacing `Content` with `Loading`; a reset MUST set `Loading` and clear the items in the same state emission, so the UI never renders new content over old content (`DESIGN.md` §4.1).
6. **Failure behaviour.** A first-page failure MUST surface the mapped `ApiFailure` state (`DESIGN.md` §7). An append or prefetch failure MUST keep the already displayed items and expose the retry affordance rather than clearing the list. An out-of-range page reached through a valid paging walk MUST be treated as the end of pagination; a `404` on a filtered first page MUST map to the empty state, not to an error (`AC-REQ-FUNC-010-1`).
7. **Retry budget.** The pager MUST reuse the bounded retry policy of `API_SPECS.md` §6.3 (two automatic attempts, backoff with jitter) and MUST NOT retry schema, validation, decoding or cancellation outcomes (`REQ-REL-003`).
8. **Refresh.** A manual refresh MUST revalidate the first page through the network and replace the collection on success, keeping the previous items on failure (`REQ-FUNC-012`). A refresh MUST NOT be implemented as a silent page-1 append.
9. **Ordering.** Items MUST be concatenated in request order per identity, and duplicate character IDs arriving from the server MUST be dropped before the state is emitted, so a retried page cannot duplicate entries in the grid.

- **Board entry:** `DEC-016` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). Paging 3 is recorded in `DECISION_BOARD.md` §3 as rejected.

## Context

The list must load incrementally until `info.next` is null while keeping the first render fast (`REQ-FUNC-001`, `REQ-NFR-003`), and the same paging behaviour must exist on both platforms because the discovery screen is specified identically on both (`UI_SPEC.md` §6.2) and the shared core carries the behaviour (`REQ-PLAT-001`). The page contract is unusually simple: a fixed server-controlled page size of 20, an authoritative `next` link and an out-of-range `404`. Live probes on 2026-09-29 confirmed all three.

Paging 3 is the idiomatic Android answer and is already justified in `API_SPECS.md` §8 for a REST-only app. Its multiplatform line, however, is not the same artifact as the Android one, and the project's Android-first-then-iOS sequencing (DEC-040) makes a pager that exists only on Android a second implementation waiting to happen. `DESIGN.md` §9 records the pager as an open decision (D5) with a custom pager recommended.

## Decision drivers

- One paging implementation for both platforms — `REQ-PLAT-001`, DEC-040, `DESIGN.md` §4.1.
- Exactly one behaviour for reset, cancellation, dedup and prefetch, so the two clients cannot diverge — `REQ-FUNC-003`, `REQ-FUNC-004`, `REQ-REL-002`.
- Dependency restraint: a pager is a small amount of code for this contract — `REQ-NFR-002`, `assessment.md:7`, `API_SPECS.md` §8.
- The `next`-link contract and the server page size are stable enough to encode directly — `API_SPECS.md` §4.3, live probes 2026-09-29.
- The pager's risky logic must be directly testable in `commonTest` — `REQ-NFR-005`, `AC-REQ-NFR-005-1`.
- Fast first render and a bounded number of requests — `AC-REQ-FUNC-001-1`, `REQ-NFR-003`, `API_SPECS.md` §8.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Paging 3 (multiplatform artifacts) | `PagingSource` + `Pager` driving the list, with the standard `LoadState` model | Battle-tested paging, built-in dedup and retry helpers, and an Android-native integration; the multiplatform artifact set is separate from the Android line, its load-state model must be mapped onto the shared state contract anyway, and no equivalent consumer exists on iOS, so the iOS client would either consume a KMP paging abstraction or reimplement the same rules | Rejected: DEC-016; the contract is small enough to encode directly, and the dependency would not remove the need to keep the two clients behaviourally aligned |
| Per-platform paging | Paging 3 on Android and a hand-written pager in Swift on iOS | Each platform uses its idiomatic tooling; the paging rules (dedup, cancellation on identity change, prefetch suppression, `next` handling, empty-page mapping) are implemented twice and must be kept in step by hand, which is precisely the drift the shared core exists to prevent | Rejected: duplicates the riskiest behaviour on the two platforms (`REQ-NFR-005` targets exactly this logic) |
| Shared custom pager in `:core:data` (chosen) | A coroutine-based pager producing the shared list state, consuming the cached repository and the failure mapping | One implementation, fully testable in `commonTest` with a fake repository and fake clock, no new dependency beyond what the data layer already uses, and the rules above are directly expressible; costs project code that must be written and maintained, and gives up Paging 3's ecosystem (remote mediators, cached-invalidation helpers) which no screen needs | Chosen: satisfies `REQ-PLAT-001` and `REQ-NFR-002` with the smallest behavioural surface, and keeps paging verifiable in the shared test suite |

## Consequences

**Positive**

- One pager serves both clients, so `AC-REQ-FUNC-001-1` and `AC-REQ-FUNC-001-2` are proven once in `commonTest` rather than twice.
- The pager composes with the response cache and the dedup layer in the same module, so a page read is cache-aware by default (`AC-REQ-NFR-003-3`) and the prefetch does not bypass freshness.
- No new dependency enters the graph, which keeps the dependency inventory small (`REQ-NFR-002`).
- The state machine (reset, append, failure, refresh) is explicit and unit-testable with `TestDispatcher` and a fake clock from `:core:testing` (DEC-030).
- Retry, cancellation and dedup semantics are exactly those already fixed in `API_SPECS.md` §6.3 and §8, with no library default in the middle.

**Negative**

- The pager is project code: reset semantics, dedup, prefetch suppression and duplicate dropping MUST each be covered by tests, and a regression here is visible to the user as duplicated or stale rows.
- Paging 3's conveniences are unavailable: cached-invalidation signalling, remote mediators, and the `LoadState` model that Android developers expect. The mapping onto the shared state contract is written by hand.
- The prefetch threshold is a presentation-tuned constant; if it is misconfigured the grid either feels slow or over-fetches, and the value MUST be documented where the discovery grid is defined (`UI_SPEC.md` §6.2 says "prefetch the next page near the end").
- Because the pager keeps the accumulation in the shared layer, a very long walk (42 pages at the time of the probe) accumulates in memory; there is no windowing or eviction until the UI list is virtualised, which the grid already does at the rendering level.
- The out-of-range `404` behaves differently depending on how it was reached (a valid walk versus a user-entered page), so the pager MUST be the only component that walks pages; a feature MUST NOT construct page requests itself.

## Risks

- **`RISK-006`** (published totals quoted as constants go stale) — mitigated by rule 1 and by the ban on hardcoding: totals come from `info.count`/`info.pages` of the current response (`AC-REQ-FUNC-001-3`).
- **Local:** duplicate rows after a retried or duplicated request — mitigated by rule 3 (dedup) and rule 9 (duplicate dropping), both covered by tests.
- **Local:** a stale page arriving after an identity change (a race the cancellation does not cover) — mitigated by rule 4's atomic reset and by an ordering test that fails a late response.
- **Local:** prefetch storms on fast scrolling, exceeding the intended single-page lead — mitigated by rule 2's suppression conditions and by an assertion that at most one request is in flight per identity.
- **Local:** the append path showing `Loading` over existing content, which reads as a full refresh — mitigated by rule 5 and by the state-transition test.

## Validation criteria

- `TEST-UNIT-001` — first page renders alone; the walk continues while `info.next` is non-null and stops at null; the shown count comes from `info.count` (`AC-REQ-FUNC-001-1`, `AC-REQ-FUNC-001-2`, `AC-REQ-FUNC-001-3`).
- `TEST-UNIT-003` — a changed query or status cancels the in-flight load, resets to page 1 and emits only the newest result; a blank query sends no `name` parameter (`AC-REQ-FUNC-003-2`, `AC-REQ-FUNC-003-3`, `AC-REQ-FUNC-004-1`).
- `TEST-UNIT-006` — a user-initiated retry after a failure starts a fresh attempt budget and clears the error on success (`AC-REQ-FUNC-011-1`).
- `TEST-INT-001` — a cached page renders with zero network requests and the pager does not re-request it (`AC-REQ-NFR-003-3`).
- `TEST-PERF-002` — the populated grid scrolls within the frame-time budget, which is the observable proof that appending and prefetching do not stutter (`AC-REQ-NFR-003-2`).
- **Observable:** two simultaneous identical page requests produce one network call — observed by the dedup assertion in the pager tests (`AC-REQ-REL-002-1`).
- **Observable:** no `Loading` state is emitted while items are displayed — observed by asserting the emitted state sequence for append and prefetch (rule 5).
- **Observable:** an append failure keeps the displayed items and exposes retry, and a filtered first-page `404` renders the empty state — observed in the UI tests (`AC-REQ-FUNC-010-1`) and in the state-sequence test.
- **Verification protocol (DEC-053):** the pager rules land as red commits (the sequence tests observed to fail), then green commits, then an optional refactor commit, with history preserved (no squash; DEC-041 amended). Build or tooling changes MAY skip the red phase and MUST state the exception in the commit body. Because the pager is on the `REQ-NFR-005` risky list, its tests MUST NOT be deferred to a later change.

## Related requirements

- `REQ-FUNC-001` (`AC-REQ-FUNC-001-1`, `AC-REQ-FUNC-001-2`, `AC-REQ-FUNC-001-3`): incremental paging until `info.next` is null, count from `info.count`.
- `REQ-FUNC-003` (`AC-REQ-FUNC-003-1`, `AC-REQ-FUNC-003-2`): debounce at the presentation layer, cancellation and page reset in the pager.
- `REQ-FUNC-004` (`AC-REQ-FUNC-004-1`): a status selection resets to page 1 while preserving the query.
- `REQ-FUNC-010` (`AC-REQ-FUNC-010-1`): a filtered empty result is an empty state, not an error.
- `REQ-FUNC-012` (`AC-REQ-FUNC-012-1`, `AC-REQ-FUNC-012-2`): refresh revalidates and retains content on failure.
- `REQ-REL-002` (`AC-REQ-REL-002-1`): concurrent identical requests are deduplicated.
- `REQ-REL-003` (`AC-REQ-REL-003-1`): bounded retry, never applied to schema, validation, decoding or cancellation outcomes.
- `REQ-NFR-003` (`AC-REQ-NFR-003-2`, `AC-REQ-NFR-003-3`): scroll budget and zero-network cached renders.
- `REQ-NFR-005` (`AC-REQ-NFR-005-1`): the pager is on the explicitly tested risky list.

## Related implementation areas

- `:core:data` (pager, page walk, dedup, prefetch policy, accumulation), `:core:domain` (`CharacterPage`, `DataResult`), `:feature:discovery` (its `presentation` package under `commonMain`, its `ui` package under `androidMain`, and `iosApp/Features/Discovery`), `:core:testing` (fake repository, `TestDispatcher`, page fixtures).
- [`API_SPECS.md`](../API_SPECS.md) §4.3 (page contract), §6.3 (retry), §7.3 (cache read policy), §8 (prefetch and request-volume rules).
- [`DESIGN.md`](../DESIGN.md) §4.1 (`LoadState`, `isAppending`, the intent list) and §9 decision D5, which this ADR closes.
- [`UI_SPEC.md`](../UI_SPEC.md) §6.2 (grid, paging indicator, prefetch near the end) and §8 (paging state rendering).
- DEC-012 / DEC-018 (freshness bands the pager reads through), DEC-053 (TDD protocol), DEC-054 (the paging tests are part of the required pull-request suite).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none as a file. This ADR closes `DESIGN.md` §9 decision D5 in favour of the shared custom pager, and formalises the board's rejected "Paging 3" row.
- **Superseded by:** none as of 2026-09-29.
- **Related:** ADR-0005 (the cache whose entries the pager reads and writes), ADR-0004 (the client it drives), ADR-0006 (the state it emits), ADR-0001 (the module that owns it).
