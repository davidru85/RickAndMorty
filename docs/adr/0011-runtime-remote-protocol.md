# ADR-0011 — Runtime-selectable remote protocol: REST or GraphQL through the one Ktor client

- **Status:** Accepted
- **Date:** 2026-09-30
- **Last verified:** 2026-09-30
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) for `:core:data`, Implementation Engineer (iOS) for the Settings binding; consulted API Architect (GraphQL operations, error mapping, contract tests) and QA & Validation Engineer (protocol parity)
- **Authoritative for:** that both protocols ship, how the active one is selected at runtime, and what "switching" does to in-flight work, the pager and the cache. Not the wire contract of either protocol (`API_SPECS.md` §4, §5, §6), not the HTTP client choice (ADR-0004), not the cache policy (ADR-0005) and not the Settings screen (`UI_SPEC.md` §6.5).
- **Inputs:** `DEC-056`, `DEC-011`, `DEC-018`, `DEC-055` in [`DECISION_BOARD.md`](../DECISION_BOARD.md) and its §3 row "GraphQL (Apollo Kotlin) as the shipped protocol"; `REQ-FUNC-034`, `REQ-FUNC-020`, `REQ-NFR-002`, `REQ-NFR-004`, `REQ-REL-001`, `REQ-SEC-001` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`API_SPECS.md`](../API_SPECS.md) §2, §5, §6.2, §7.2, §10.2; ADR-0004, ADR-0005, ADR-0009, ADR-0010; repository-owner directive of 2026-09-30 ("both via Ktor, no Apollo")

## Owners

- **Decision owner:** System Architect — owns the selection rule and the review trigger below.
- **Implementation owners:** Implementation Engineer (Android) for the two remote data sources and the selector in `:core:data`; Implementation Engineer (iOS) for binding the Settings control to `IC-021`.
- **Consulted:** API Architect for the checked-in GraphQL documents and their mapping; QA & Validation Engineer for the parity suite.

## Decision

Both remote protocols ship. The user chooses between them in Settings, REST is the default, and the choice persists (`REQ-FUNC-034`).

- `IC-011` (`CharacterRemoteDataSource`) MUST have exactly two implementations in `:core:data`:
  - `RestCharacterRemoteDataSource`, the existing REST adapter.
  - `GraphQlCharacterRemoteDataSource`, which posts the checked-in operations of `API_SPECS.md` §5.5 to `/graphql` through the **same Ktor `HttpClient`** and decodes them with kotlinx.serialization.
- Apollo Kotlin and any GraphQL code generator MUST NOT be added. ADR-0004's single-client rule stands.
- The repository reads the active protocol from `IC-021` and resolves the data source **per request**. No screen, use case or state holder knows which protocol served its data. Both implementations return the same domain types and the same `ApiFailure` taxonomy (`API_SPECS.md` §6.1, §6.2).
- A protocol change behaves like a filter change (ADR-0009 rule 4). The pager cancels in-flight loads, resets to page 1 and reloads through the newly selected protocol. A detail screen that is already loaded is not reloaded.
- Cache entries MUST stay isolated per protocol through the `protocol` component ADR-0005 already reserves:
  - REST: `protocol = rest`.
  - GraphQL: `protocol = graphql`, `method = POST`, `pathTemplate = /graphql`, and `canonicalQuery` = operation name plus the SHA-256 of the checked-in document plus the canonical variables (`API_SPECS.md` §7.2).
  - Switching protocol neither evicts nor reuses the other protocol's entries. The freshness policy is unchanged.
- A GraphQL request MUST go to the configured HTTPS host only (`REQ-SEC-001`). It carries no credentials, and its query document and variables are never logged in release builds (`REQ-SEC-005`).

- **Board entry:** `DEC-056` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). It amends the `DECISION_BOARD.md` §3 rejection of GraphQL as a shipped protocol. The Apollo part of that rejection stands.

## Context

On 2026-09-29 GraphQL was rejected as the shipped protocol for the MVP (`DECISION_BOARD.md` §3; ADR-0004 considered options). The rejection rested on three costs: Apollo's generated models, a second normalized cache, and the `POST` method defeating the standard HTTP cache. No screen needed a tailored payload. `API_SPECS.md` nevertheless specified GraphQL completely: its §5 operations, §6.2 error mapping, §7.2 cache identity and §10.2 contract tests. `TESTING.md` §4.3 already committed GraphQL fixtures and a protocol-parity contract test (`TEST-CONTRACT-005`).

On 2026-09-30 the repository owner asked for a Settings option that lets the user choose between the REST API and GraphQL. The owner chose to build it through the existing Ktor client, without Apollo.

Two later decisions remove most of the original cost:
- The project has used an application-level cache since `DEC-018`, not the HTTP engine's cache. That cache already reserves a `protocol` key component (ADR-0005), so `POST` no longer defeats caching.
- Hand-written operations posted through Ktor need no generated models and no normalized store.

The remaining cost is a second DTO set with its mappers, and a second contract suite. Both are already specified.

## Decision drivers

- Owner directive of 2026-09-30 — `DEC-056`, `REQ-FUNC-034`.
- One HTTP client, one serializer, no duplicated concern — `REQ-NFR-002`, ADR-0004.
- Cache isolation across protocols — `REQ-REL-001`, ADR-0005.
- The same domain result regardless of protocol — `REQ-NFR-004`, `TEST-CONTRACT-005`.
- Same-host, HTTPS-only networking — `REQ-SEC-001`.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Two `IC-011` implementations over the one Ktor client, selected per request from `IC-021` | Hand-written GraphQL documents plus serializable envelopes in `:core:data` | No new dependency; reuses the app-level cache and the error taxonomy; costs a second DTO/mapper set and its contract tests | Chosen |
| Apollo Kotlin for the GraphQL path | Generated operations plus the normalized cache | Stronger typing, but a second networking library and a second cache mechanism | Rejected: `REQ-NFR-002`, ADR-0004 |
| Protocol fixed per build | Build flavour chooses REST or GraphQL | Simplest runtime, but the user cannot choose, which is what the requirement asks for | Rejected: does not meet `REQ-FUNC-034` |
| Mixed per screen | List on REST, detail on GraphQL | Tailored payloads, but mixes caches, mappers and failure paths per screen (`API_SPECS.md` §2) | Rejected: explicitly warned against |

## Consequences

**Positive**

- The protocol trade-off becomes observable in the running app, which gives the interview a concrete performance and caching discussion (`assessment.md` l.12).
- The existing parity contract test becomes a shipped-behaviour test rather than a hypothetical.

**Negative**

- `:core:data` carries two remote adapters, two DTO sets and two mapper sets, each with fixtures and contract tests (`API_SPECS.md` §10.1, §10.2).
- `API_SPECS.md` §2 and §14, `TESTING.md` §4.3 and ADR-0004's status line change in the same change (DEC-046).
- A cold switch to the other protocol misses the cache once per page and filter, because entries are not shared.

## Risks

- **`RISK-004`** The unversioned API changes shape — aggravated, because two surfaces can now drift. Mitigated by the scheduled observation probes (`TEST-CONTRACT-006`) covering both protocols and by the parity suite in the gate.
- **Local:** protocol-specific empty-result semantics (REST `404` versus GraphQL `200` with empty `results`) diverge in the UI — both map to the same empty domain result before the state holder sees them (`API_SPECS.md` §6.1, §6.2), and the parity suite asserts it.
- **Local:** a switch during an in-flight load renders mixed pages — prevented by treating the switch as an identity change (ADR-0009 rule 4), proved by `TEST-UNIT-048`.

## Validation criteria

- `TEST-CONTRACT-005` proves REST and GraphQL fixtures map to equivalent domain models, including the empty-filter case (`AC-REQ-FUNC-034-3`).
- `TEST-UNIT-048` proves that a protocol change cancels in-flight loads, resets the pager to page 1 and reloads through the selected source without rendering items from the previous protocol (`AC-REQ-FUNC-034-2`).
- `TEST-UNIT-049` proves cache keys differ between protocols for the same filter and page, and that a switch neither evicts nor reads the other protocol's entries (`AC-REQ-FUNC-034-4`).
- **Observable:** no Apollo or GraphQL-codegen artifact appears in the resolved dependency graph → the dependency-analysis report (`AC-REQ-NFR-002-1`).

## Related requirements

- `REQ-FUNC-034` (`AC-REQ-FUNC-034-1`…`AC-REQ-FUNC-034-4`): the user-facing switch.
- `REQ-FUNC-020`, `REQ-REL-001`: response caching and cache isolation.
- `REQ-NFR-002`: one solution per concern.
- `REQ-NFR-004`: resilience against API drift.
- `REQ-SEC-001`, `REQ-SEC-005`: host restriction and logging limits.

## Related implementation areas

- `:core:data` (`RestCharacterRemoteDataSource`, `GraphQlCharacterRemoteDataSource`, the per-request selector, GraphQL envelopes and mappers), `:core:domain` (`IC-021`), `:feature:settings`.
- [`API_SPECS.md`](../API_SPECS.md) §2, §5, §6.2, §7.2, §10.2; [`CONTRACTS.md`](../CONTRACTS.md) `IC-011`, `IC-012`, `IC-021`; [`TESTING.md`](../TESTING.md) §4.3, §11.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none. Amends ADR-0004's considered-options outcome for "GraphQL as the shipped protocol": GraphQL now ships through Ktor, and the Apollo rejection stands.
- **Superseded by:** none as of 2026-09-30.
- **Related:** ADR-0004 (single HTTP client), ADR-0005 (cache identity with a `protocol` component), ADR-0009 (pager identity changes), ADR-0010 (the Settings destination and the preferences store).
