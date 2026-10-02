# ADR-0013 — One domain logging contract and a debug-only `:core:diagnostics` module

- **Status:** Accepted
- **Date:** 2026-10-02
- **Last verified:** 2026-10-02
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) and Implementation Engineer (iOS); consulted Security Reviewer (what may be recorded, `OBSERVABILITY.md`).
- **Authoritative for:** where the shared logging contract is declared and implemented, and which module carries the debug-only diagnostic API so that release artifacts never link it. Not the permitted fields, the event catalogue or the redaction rules, which belong to [`../OBSERVABILITY.md`](../OBSERVABILITY.md) §2–§4; not the contract signatures, which belong to [`../CONTRACTS.md`](../CONTRACTS.md) `IC-024`; not the module set as a whole, which belongs to [ADR-0001](0001-module-boundaries.md).
- **Inputs:** `DEC-087`, `DEC-088`, `DEC-085`, `DEC-038`, `DEC-039`, `DEC-052`, `DEC-066` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `REQ-OBS-001`, `REQ-OBS-002`, `REQ-OBS-003`, `REQ-SEC-005` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`../OBSERVABILITY.md`](../OBSERVABILITY.md) §1, §2.1, §5; [`../DESIGN.md`](../DESIGN.md) §3; the B3 execution order of `DEC-082` (`TASK-047` in Phase 3.2, `TASK-041` in Phase 3.3); the owner's answers to the B3 decision packet, 2026-10-02.

## Owners

- **Decision owner:** System Architect — owns the placement and the review trigger below.
- **Implementation owners:** Implementation Engineer (Android) for the `:core:data` implementation, the `:core:diagnostics` module and the Android sink and debug wiring (`TASK-047`, `TASK-044`); Implementation Engineer (iOS) for the iOS sink and the Debug-only link (`TASK-051`).
- **Consulted:** Security Reviewer, because the diagnostic surface is part of what may be recorded and shown (`OBSERVABILITY.md` §5, `SECURITY.md` §7.4).

## Decision

The shared logging contract is declared **once, in `:core:domain`**, and the debug-only diagnostic API lives in a **new module `:core:diagnostics`** that release artifacts never link.

- The contract (`AppLogger`, `LogLevel`, the closed `LogEvent` catalogue and its field enums; `IC-024`) MUST be declared in `:core:domain`, in plain Kotlin with no dependency beyond those `DEC-066` permits. No second logging interface MAY exist in `:core:data`, `:core:presentation`, a feature module or an app shell.
- The validating, redacting implementation MUST live in `:core:data` and MUST write to an injected `LogSink`. The app shells supply the platform sink (Logcat on Android, `os.Logger` on iOS) and call no platform logging API for app diagnostics themselves.
- The debug-only diagnostic API MUST live in `:core:diagnostics`, a Kotlin Multiplatform module with the accepted Android and Apple targets that depends on `:core:domain` only. It is a read-only fold over validated log events, exposes no request trigger and no export, and is linked only by debug configurations: `debugImplementation` in `:androidApp`, and the Debug configuration of the iOS app.
- The ADR-0001 module-set amendment pointer is recorded with this decision. `TASK-047` creates `:core:diagnostics` together with the boundary-rule changes that admit it (`R11` for the debug-only shell edge, `R13`, `R16`). Until that change merges, the module is target state.

- **Board entries:** `DEC-087` and `DEC-088` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Context

`OBSERVABILITY.md` §1 placed the logger "in the shared core (`:core:data` for network/cache/pager events, `:core:presentation` for state-level events)", while §2.1 requires exactly one interface for both platforms. Read together, those two statements need either two interfaces or an edge between `:core:data` and `:core:presentation`, and ADR-0001 forbids that edge (`R2`, `R3`). The B3 execution order made the conflict concrete: `TASK-047` now runs in Phase 3.2, before `TASK-041` creates any `:core:presentation` source, so a contract that depends on presentation types could not compile in its own phase (`CONF-67`).

`REQ-OBS-002` and `AC-REQ-OBS-002-1` require the diagnostic surface to be **absent** from release builds, and `OBSERVABILITY.md` §5 rules out a runtime flag or a build-config boolean. The KMP library modules use the Android-KMP library plugin, which builds a single Android variant: a `:core:*` module has no debug or release source set, so it cannot keep diagnostic code out of a release artifact by itself (`CONF-68`). The separation therefore has to be a module that the release configuration never resolves.

## Decision drivers

- One interface for both platforms, with permitted fields only — `REQ-OBS-001`, `AC-REQ-OBS-001-1`, `OBSERVABILITY.md` §2.1.
- No data↔presentation edge — ADR-0001, `R2`/`R3` of `verifyModuleBoundaries`.
- `:core:domain` stays platform-free — `DEC-066`; a logging contract is plain Kotlin and needs nothing beyond the standard library.
- The diagnostic surface is physically absent from release — `REQ-OBS-002`, `AC-REQ-OBS-002-1`, `TEST-UNIT-033`.
- Phase 3.2 compiles with Phase 3.1 alone — `DEC-082`, `DEC-085`.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Contract in `:core:domain`, implementation in `:core:data`, injected sinks | One interface every layer can reach; one implementation; the shells supply the sink | Domain gains an observability vocabulary; the implementation's validation stays in data, next to the events it validates | Chosen: satisfies one interface without a forbidden edge |
| The split of `OBSERVABILITY.md` §1 (data and presentation each own a logger) | Network events in `:core:data`, state events in `:core:presentation` | Two loggers, or a data↔presentation edge; and Phase 3.2 could not compile without Phase 3.3 | Rejected: violates "one interface" or ADR-0001 |
| Contract in `:core:data` only | One interface, but presentation and feature state holders log through data | `LOG-021` would need a `:core:presentation` → `:core:data` edge | Rejected: `R3` |
| Diagnostics in `:core:diagnostics`, linked by debug configurations only | A module the release graph never resolves | One more module and three rule changes in `TASK-047`; the iOS Debug-only link is a B7 obligation | Chosen: the only option that is physical exclusion |
| Diagnostics inside `:core:data`, reached only by debug wiring | No new module | The code ships in every release artifact; "unreachable" is not "absent" | Rejected: fails `AC-REQ-OBS-002-1` as `OBSERVABILITY.md` §5 reads it |
| Diagnostics in `:androidApp`'s debug source set (B4) | No new module | Moves the API out of B3 and contradicts the `DEC-085` staging | Rejected: owner staging |

## Consequences

**Positive**

- There is exactly one logging interface, and every layer that may log reaches it without a new edge.
- Phase 3.2 (`TASK-047`) compiles and is testable with Phase 3.1 alone.
- Release artifacts cannot contain the diagnostic API, because no release configuration declares the module.

**Negative**

- `OBSERVABILITY.md` §1 and §2.1 are amended in the same change as `DEC-087` (`DEC-046`).
- The module set grows by one; `TASK-047` owes the module and the `R11`/`R13`/`R16` changes with their tests, and `TASK-051` owes the iOS Debug-only link.

## Risks

- No `RISK-###` row of `REQUIREMENTS.md` §13 bears on this decision directly.
- **Local:** the diagnostic surface could expose a prohibited field — mitigated: the module folds only events that already passed the `:core:data` validator, so it cannot show a field the logger dropped (`OBSERVABILITY.md` §5 "Redaction applies in full").
- **Local:** a release configuration could gain `:core:diagnostics` by mistake — mitigated by `TEST-UNIT-033` (release exclusion) and the boundary rule that admits the edge only from a debug configuration.

## Validation criteria

- `TEST-UNIT-032` proves one contract with permitted fields only.
- `TEST-UNIT-033` proves the diagnostic API is absent from a release artifact's resolved graph.
- `TEST-UNIT-029` proves a search value never reaches a sink through the production logger boundary.
- **Observable:** `:core:domain` still declares only the `DEC-066` libraries → `verifyModuleBoundaries` (`R14`).

## Related requirements

- `REQ-OBS-001` (`AC-REQ-OBS-001-1`): one shared contract with permitted fields.
- `REQ-OBS-002` (`AC-REQ-OBS-002-1`): a debug-only diagnostic surface absent from release.
- `REQ-OBS-003` (`AC-REQ-OBS-003-1`): no analytics artifact.
- `REQ-SEC-005` (`AC-REQ-SEC-005-1`): no query string reaches a sink.

## Related implementation areas

- `:core:domain` (contract), `:core:data` (implementation), `:core:diagnostics` (debug API, created by `TASK-047`), `:androidApp` and `iosApp` (sinks and debug wiring).
- [`../OBSERVABILITY.md`](../OBSERVABILITY.md) §1–§5 and [`../CONTRACTS.md`](../CONTRACTS.md) `IC-024` for the specification this decision feeds.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none as a file; it amends the placement sentence of `OBSERVABILITY.md` §1.
- **Superseded by:** none as of 2026-10-02.
- **Related:** [ADR-0001](0001-module-boundaries.md) (module set, amended by pointer on 2026-10-02), [ADR-0003](0003-ui-sharing-strategy.md) (platform-owned hosts).
