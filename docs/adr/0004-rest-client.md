# ADR-0004 — REST client: Ktor 3.6.0 with kotlinx.serialization everywhere

- **Status:** Accepted (GraphQL outcome amended by [ADR-0011](0011-runtime-remote-protocol.md))
- **Date:** 2026-09-29
- **Last verified:** 2026-09-29
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementation owner Implementation Engineer (Android); consulted Implementation Engineer (iOS) for the Darwin engine configuration
- **Authoritative for:** which HTTP client and which serialization stack the project ships, and how error and mapping behaviour is kept in one place. Not the wire contract (`API_SPECS.md`), not the cache policy (ADR-0005) and not the pager (ADR-0009).
- **Inputs:** `DEC-011` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `DEC-018` (ADR-0005), `DEC-030` (ADR-0004's test seam), `DEC-052` (ADR-0001); `REQ-NFR-002`, `REQ-NFR-004`, `CON-004`, `CON-001` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`API_SPECS.md`](../API_SPECS.md) §2, §4, §6, §7.1, §12; [`DESIGN.md`](../DESIGN.md) §2, §9 (decision D1); verified Ktor 3.6.0 release, Kotlin 2.4.20 and kotlinx.serialization as of 2026-09-29

> **Amended 2026-09-30 by [ADR-0011](0011-runtime-remote-protocol.md) (`DEC-056`):** GraphQL now ships as a user-selectable remote protocol, sent through this ADR's single Ktor client with hand-written operations. The rejection of Apollo Kotlin and of any second HTTP client below still stands; only the "GraphQL as the shipped protocol" outcome is amended.

## Owners

- **Decision owner:** System Architect — accountable for the single-stack rule and for the rewrite of the affected document sections.
- **Implementation owners:** Implementation Engineer (Android) for the client, engines and mapping in `:core:data`; Implementation Engineer (iOS) for the Darwin engine and TLS configuration consumed by the same module.
- **Consulted:** API Architect for the error contract in `API_SPECS.md` §6 and for the wording in §7.1 and §12.

## Decision

The project ships exactly one remote stack: **Ktor 3.6.0** with **kotlinx.serialization**, behind the `CharacterRemoteDataSource` interface in `:core:data`.

- Ktor MUST be the only HTTP client in the project. Retrofit, OkHttp-as-a-client (the engine aside), Apollo and any community Rick and Morty SDK MUST NOT be added.
- The engine is platform-specific: the OkHttp engine on Android and the Darwin engine on iOS. `:core:data` MUST NOT reference an engine type outside its platform source set.
- Serialization MUST be kotlinx.serialization for both protocols, so DTOs and their decoding failures follow one path.
- One error path: transport, HTTP-status and decoding outcomes MUST map to `ApiFailure` (`:core:domain`) in `:core:data`, and no other layer MAY construct or translate failures.
- One mapping path: DTO → domain mapping MUST live in `:core:data` mappers, and DTOs MUST NOT appear in a public signature outside that module (`AC-REQ-NFR-001-2`).
- The client MUST be constructed with the fixed HTTPS base URL and MUST reject relations or pagination URLs pointing at another host (`REQ-SEC-001`).
- `API_SPECS.md` §7.1 and §12 MUST be rewritten in the same change to describe Ktor, its engines and its cache interceptor rather than Retrofit/OkHttp and the retired open question, per DEC-046.

- **Board entry:** `DEC-011` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Context

The character list, search, filter and detail operations are plain `GET` requests to a public, unversioned REST API (`API_SPECS.md` §1, §2), and the same code must run on Android and on iOS because the data layer is shared (`REQ-PLAT-001`, DEC-052/ADR-0001). `API_SPECS.md` was written before the platform split was fixed and recommends Retrofit/OkHttp (§7.1, §12), which are JVM-only artifacts and therefore cannot serve a shared module; `DESIGN.md` §9 (decision D1) records the conflict. GraphQL was already rejected as the shipped protocol for the MVP (`DECISION_BOARD.md` §3, `API_SPECS.md` §2).

The error contract (`API_SPECS.md` §6) is protocol-aware but transport-agnostic: every condition maps to one `ApiFailure` family, with retry bounded by `API_SPECS.md` §6.3 and cancellation never surfaced. That contract is much easier to keep if exactly one stack implements it.

## Decision drivers

- One multiplatform stack for one shared data layer — `REQ-PLAT-001`, `assessment.md:7`, `API_SPECS.md` §12.
- One error and mapping path, so failure behaviour is uniform across features — `REQ-FUNC-022`, `REQ-NFR-004`, `API_SPECS.md` §6, ADR-0001.
- Dependency restraint: no second solution for the same concern, no community SDK — `REQ-NFR-002`, `assessment.md:7` ("use them wisely, each third party library added is a dependency").
- The cache interceptor and the dedup layer must sit on the same client that performs the request — `API_SPECS.md` §7.1, §8; ADR-0005.
- Deterministic tests without a network: the client must be constructible with a mock engine — `REQ-NFR-005`, DEC-030.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Ktor 3.6.0 + kotlinx.serialization (chosen) | One client in `:core:data` with an OkHttp engine on Android and a Darwin engine on iOS | Multiplatform, one code path for transport, retry, error mapping and cache interception, and a mock engine for tests; costs a less familiar API than Retrofit for Android-only reviewers, and engine-specific configuration (TLS, cache) must be written per platform | Chosen: the only option that serves both platforms from one shared module without a second stack |
| Retrofit + OkHttp on Android | Retrofit interfaces in `:core:data`, JVM-only | Best-known Android idiom and the configuration described in the current `API_SPECS.md` §7.1; cannot run in `commonMain`, so either the data layer stops being shared or a second implementation is written for iOS | Rejected: `REQ-PLAT-001`; and a second implementation would duplicate error mapping and cache interception (`REQ-NFR-002`) |
| Retrofit + shared Ktor | Shared interface with a Ktor implementation for iOS and a Retrofit implementation for Android | Keeps the familiar Retrofit surface on Android and shares the contract; ships two clients, two serializers, two cache configurations and two failure-mapping paths, and every repository test doubles | Rejected: two solutions for one concern (`REQ-NFR-002`), and the failure-mapping behaviour could no longer be guaranteed uniform (`REQ-FUNC-022`) |
| Apollo Kotlin / GraphQL as the shipped protocol | Generated operations against `/graphql` | One request per screen payload and a normalized cache; the app contract uses `POST`, which breaks the standard HTTP cache, requires generated models plus a normalized store, and no MVP screen needs a tailored nested payload | Rejected for the MVP (`DECISION_BOARD.md` §3, `API_SPECS.md` §2): more moving parts and a second cache mechanism for no screen-level benefit. GraphQL stays documented as the protocol alternative |

## Consequences

**Positive**

- One transport implementation serves both platform clients, so `REQ-PLAT-001` holds at the networking layer.
- `ApiFailure` mapping and retry live in one module, so a change to the failure taxonomy is one edit (`REQ-FUNC-022`, `API_SPECS.md` §6).
- The same client instance carries the `no-store` interceptor for filtered `404` responses and the request deduplication required by ADR-0005 and `API_SPECS.md` §7.1.
- Tests construct the client with `MockEngine` and committed fixtures in `commonTest`, so decoder, mapper and retry behaviour are testable without a network (DEC-030, `REQ-NFR-005`).
- The dependency inventory shrinks: one HTTP client, one serializer, one coroutine stack.

**Negative**

- Android reviewers expecting Retrofit find Ktor instead; the repository MUST therefore keep the rationale and the affected `API_SPECS.md` sections current (DEC-046).
- Engine-specific concerns do not disappear: the OkHttp engine's disk cache and the Darwin engine's `URLCache` behave differently, so cache verification is per platform even though the code is shared (ADR-0005).
- `API_SPECS.md` §7.1 and §12 currently describe Retrofit/OkHttp as the shipped client and name an open decision that this ADR closes; both sections MUST be rewritten in the same change, and until that lands the ADR is the authoritative statement.
- Ktor's retry and timeout configuration must be validated against `API_SPECS.md` §6.3 by tests rather than assumed, because the library defaults differ from the app policy.

## Risks

- **`RISK-004`** (the unversioned API changes shape and silently breaks mapping) — mitigated by committed fixtures and by the contract suite, which now runs in fixture/replay mode inside the pull-request gate (DEC-054).
- **Local:** engine behaviour drift between Android and iOS (redirect handling, TLS, cache semantics) producing platform-specific failures — mitigated by the shared failure-mapping tests plus per-platform integration tests, and by forbidding engine types outside the platform source sets.
- **Local:** a feature importing the Ktor client directly, bypassing the repository and the cache — mitigated by the dependency rules of ADR-0001 (feature modules depend on `:core:data`, not on the client) and by the dependency-analysis check (DEC-032).
- **Local:** `API_SPECS.md` continuing to describe Retrofit while the code ships Ktor — mitigated by the documentation rule DEC-046 and by the rewrite of §7.1/§12 required above.

## Validation criteria

- `TEST-CONTRACT-001` — page decoding against committed fixtures through the Ktor client (rest/fixture-replay mode).
- `TEST-UNIT-010` — failure mapping covers offline, timeout, `429`, `5xx`, malformed, not-found and invalid-request conditions, and never surfaces `CancellationException`.
- **Observable:** no Retrofit, Apollo or community-SDK artifact appears in the resolved dependency graph — observed in the dependency-analysis report and in `README.md`'s dependency inventory (`AC-REQ-NFR-002-1`).
- **Observable:** the shared client compiles and its tests run for both the Android and the Apple targets with only platform engine implementations differing — observed in the both-platform CI run.
- **Observable:** a request to a host other than the configured API host fails — observed by a test on the client configuration (`AC-REQ-SEC-001-1`).
- **Verification protocol (DEC-053):** the client, its interceptor and its mapping land as a red commit (a test observed to fail against the fixtures), then a green commit, then an optional refactor commit, with history preserved (no squash; DEC-041 amended). Toolchain-only changes MAY skip the red phase and MUST state the exception in the commit body.

## Related requirements

- `REQ-PLAT-001` (`AC-REQ-PLAT-001-1`): one shared data layer used by both clients.
- `REQ-NFR-002` (`AC-REQ-NFR-002-2`): one named rationale per dependency; no second stack.
- `REQ-NFR-004` (`AC-REQ-NFR-004-1`): malformed or partial responses degrade to a designed failure instead of a crash.
- `REQ-NFR-005` (`AC-REQ-NFR-005-1`): the risky modules, including failure mapping, have direct behavioural tests.
- `REQ-FUNC-022` (`AC-REQ-FUNC-022-1`, `AC-REQ-FUNC-022-2`): every failure maps to a domain failure and a designed state; cancellation is never surfaced.
- `REQ-SEC-001` (`AC-REQ-SEC-001-1`): HTTPS only, single host, foreign-host URLs rejected.
- `REQ-SEC-005`: logs never carry search text, bodies or stack traces.
- `CON-001`: the API is public, unversioned and read-only, which is why the contract suite is split from the merge gate's live network (DEC-054).

## Related implementation areas

- `:core:data` (client configuration, engines, DTOs, mappers, interceptors), `:core:domain` (`ApiFailure`, repository interfaces), `:core:testing` (mock-engine helpers and fixtures).
- [`API_SPECS.md`](../API_SPECS.md) §2, §4, §6 (contract this client implements); §7.1 and §12 MUST be rewritten to Ktor.
- [`DESIGN.md`](../DESIGN.md) §2 (networking impact) and §9 decision D1, which this ADR closes.
- DEC-030 (`MockEngine` + committed fixtures), DEC-054 (fixture/replay contract suite blocking on pull requests), DEC-032 (dependency-analysis check).
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none as a file. This ADR supersedes the Retrofit/OkHttp recommendation in `API_SPECS.md` §7.1/§12 and `DESIGN.md` §9 D1, and the board's superseded row "Retrofit/OkHttp as the shipped REST client".
- **Superseded by:** none as of 2026-09-29.
- **Related:** ADR-0005 (the cache built on this client), ADR-0009 (the pager that drives it), ADR-0001 (the module that owns it), ADR-0008 (the alpha policy, which does not cover Ktor — 3.6.0 is stable).
