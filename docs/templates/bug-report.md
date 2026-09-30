# Bug report template

- **Status:** Active
- **Last verified:** 2026-09-29
- **Owner:** Documentation Maintainer (`../../AGENTS.md` §3.9)
- **Authoritative for:** the shape of a bug report as a GitHub Issue body. Not authoritative for: severities and their handling ([`BACKLOG.md`](../BACKLOG.md), [`DEFINITION.md`](../DEFINITION.md) own the gates and the milestone criteria), the failure→state→copy chain ([`ERROR_FLOW.md`](../ERROR_FLOW.md)), what may be logged ([`OBSERVABILITY.md`](../OBSERVABILITY.md)), the flaky-test quarantine process ([`TESTING.md`](../TESTING.md)). `DEC-051` names this file; no second bug-report template exists.
- **Inputs:** [`DECISION_BOARD.md`](../DECISION_BOARD.md) (DEC-044, DEC-051), [`REQUIREMENTS.md`](../REQUIREMENTS.md) (`REQ-SEC-005`), [`SECURITY.md`](../SECURITY.md), [`ERROR_FLOW.md`](../ERROR_FLOW.md), [`API_SPECS.md`](../API_SPECS.md), [`CONTRACTS.md`](../CONTRACTS.md), [`TESTING.md`](../TESTING.md), [`BACKLOG.md`](../BACKLOG.md), [`AGENTS.md`](../../AGENTS.md)

**Purpose.** One defect, one issue, reproducible by a reader who was not present when it was found. The issue body is this template; the report opens or links a `TASK-###` row (`DEC-044`).

> **Authoring note (how to fill this in).**
> 1. Replace **every** `<...>` placeholder. A field that does not apply carries `None` plus the reason. A report that still contains an angle-bracket placeholder is incomplete and MUST NOT be filed.
> 2. "Good" for this artifact: the reader reproduces the defect from `Steps to reproduce` alone, without asking a question; `Expected` and `Actual` are distinguishable statements rather than "it is broken"; `Evidence` contains redacted logs, not the raw text that caused the failure.
> 3. Redaction is mandatory and is part of the artifact, not a courtesy: never paste search text, response bodies, image bytes, personal data or stack traces from a release build into the issue. `REQ-SEC-005` and `DEC-039` forbid them in logs as well as in reports. Replace each such value with an explicit redaction marker and describe its shape instead.
> 4. Delete this authoring note when the issue is filed.
> 5. A security vulnerability is **not** reported here: use the private route in [`SECURITY.md`](../SECURITY.md), never a public issue or pull request (`AGENTS.md` §15).

## Fields

| Field | Value |
| --- | --- |
| Summary | `<one line: what is wrong, where it is observable>` |
| Reported by | `<role from AGENTS.md §3, or the reporter>` |
| Reported on | `<YYYY-MM-DD>` |
| Platform / version | `<Android \| iOS \| both> · app version from the single VERSION source · build type` |
| Device / OS | `<device or simulator model, OS version; the reference device from PERFORMANCE.md when the defect is a budget miss>` |
| Build | `<commit, tag, or CI run id that exhibits the defect>` |
| Environment | `<locale, network state (online / degraded / offline / captive), cache state, storage state, accessibility or motion settings that matter>` |
| Steps to reproduce | `<numbered, minimal steps starting from a known state — cold start, cleared cache or existing favourites as applicable>` |
| Expected | `<the behaviour the specification requires, cited: REQ-… / AC-… / API-… / ERROR_FLOW.md section / UI_SPEC.md section>` |
| Actual | `<the observed behaviour: the state, copy or log entry produced. Quote copy only from the owning document, and only when it is wrong.>` |
| Frequency | `<Always \| Intermittent (n of m attempts) \| Once>` |
| Impact | `<who is affected, and what they can no longer do>` |
| Severity / priority | `<P0 \| P1 \| P2 \| P3 (see below) · priority MoSCoW: Must \| Should \| Could>` |
| Suspected component / contract | `<:feature:* or :core:* module, or the platform surface; the contract that appears violated: IC-### and/or API-…>` |
| Evidence | `<redacted logs, screenshot, screen recording, failed test id, or None. State what was redacted.>` |
| Regression test proposal | `<the TEST-<FAMILY>-### id to add or extend, the level and source set, and the behaviour it must assert: failing before the fix and passing after TESTING.md §1 P5>` |
| Workaround | `<what a user can do meanwhile, or None>` |
| Related issue / task | `<TASK-###, an existing issue, a CONF-### conflict, a RISK-###, SEC-###, or None>` |

## Severity

Severity is the observed consequence; priority is the order it will be worked in. Assign both, and let the reviewer adjust. The milestone criteria in [`DEFINITION.md`](../DEFINITION.md) §4 refer to this scale when they require that no P0 or P1 defect is open.

- **P0 — blocking:** a crash, data loss, a security or privacy defect, or a Must-have journey broken with no workaround.
- **P1 — major:** a Must-have or Should-have behaviour wrong, unavailable or unacceptably slow, with a workaround; includes a missed budget in [`PERFORMANCE.md`](../PERFORMANCE.md).
- **P2 — minor:** wrong or missing copy, a visual deviation, or an accessibility defect, with a workaround.
- **P3 — cosmetic:** an inconsistency with no functional effect.

## Notes that keep the report useful

- If the defect is a failing **live** API contract test or an observed API change, classify it per [`TESTING.md`](../TESTING.md) §11.3 before filing — service outage, contract change, rate limiting or client defect are different reports, and only some of them open a task.
- If the defect was surfaced by a quarantined or flaky test, include the quarantine metadata required by [`TESTING.md`](../TESTING.md) §15 (owner, tracking item, removal deadline of at most 14 days) and do not cite the test as evidence while it is quarantined.
- Filing a bug does not authorise a fix outside the item's scope: the fix is a change like any other, with its own red/green evidence (`DEC-053`) and its own documentation update (`DEC-046`).
