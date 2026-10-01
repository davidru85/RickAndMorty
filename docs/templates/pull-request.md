# Pull request description template

- **Status:** Active
- **Last verified:** 2026-10-01
- **Owner:** Documentation Maintainer (`../../AGENTS.md` §3.9)
- **Authoritative for:** the shape of a pull request description. Not authoritative for: branching, commit-message forms, the TDD phase protocol and the merge policy ([`CONTRIBUTING.md`](../CONTRIBUTING.md) owns them, DEC-053), the gate and its required checks ([`DEFINITION.md`](../DEFINITION.md)), the test strategy ([`TESTING.md`](../TESTING.md)), the agent permission boundary (`DEC-049`). `DEC-051` names this file; no second pull-request template exists.
- **Inputs:** [`DECISION_BOARD.md`](../DECISION_BOARD.md) (DEC-046, DEC-049, DEC-051, DEC-053, DEC-054), [`DEFINITION.md`](../DEFINITION.md) §3/§7, [`CONTRIBUTING.md`](../CONTRIBUTING.md) §3/§5, [`TESTING.md`](../TESTING.md) (§13.2, §14, §15, §16), [`REQUIREMENTS.md`](../REQUIREMENTS.md), [`SECURITY.md`](../SECURITY.md), [`AGENTS.md`](../../AGENTS.md)

**Purpose.** One pull request description per change, written while the evidence is in front of you. The review process, the branch naming and the commit forms live in [`CONTRIBUTING.md`](../CONTRIBUTING.md); this file owns only the shape of the description.

> **Authoring note (how to fill this in).**
> 1. Replace **every** `<...>` placeholder. A field that does not apply carries `None` plus the reason. A description that still contains an angle-bracket placeholder is incomplete and MUST NOT be submitted for review.
> 2. "Good" for this artifact: evidence, not intent. Every check claims a command and an observed result; "should work", "tested locally" without output, and a claim without a command are defects ([`AGENTS.md`](../../AGENTS.md) §4.3, §11).
> 3. Name identifiers, do not restate them: `REQ-…`, `AC-…`, `DEC-…`, `IC-…`, `API-…`, `TEST-…`, `TASK-…`. The normative sentences are owned by the files that define them.
> 4. Delete this authoring note and the guidance under each heading before submitting. The reviewer wants the artifact, not the instructions.
> 5. The gate evaluates the **final state** of the pull request, not each commit: with the TDD protocol (`DEC-053`) the `test:` commit is expected to fail tests by design, and that is not a violation. A failing, skipped or absent required check on the final state does block approval (`DEC-054`).

## Title and identity

| Field | Value |
| --- | --- |
| Title | `<type>(<scope>): <imperative summary>` ([`CONTRIBUTING.md`](../CONTRIBUTING.md)) |
| Branch | `feat\|fix\|docs\|test\|build\|chore/<short-slug>` ([`CONTRIBUTING.md`](../CONTRIBUTING.md) §2); every change is prepared on its own typed branch from the current `main` |
| Base | `main` |
| Task | `TASK-<###>` |
| Closes | `<GitHub Issue link, or None>` |
| Platform impact | `<Android \| iOS \| shared \| all platforms>` (determines which required checks apply, [`CONTRIBUTING.md`](../CONTRIBUTING.md) §5.2) |

## Summary

`<What changed and why, in three to five sentences. Name the behaviour a reviewer can observe before and after. Do not summarise the diff file by file.>`

## Requirement, decision and contract ids

| Kind | Ids |
| --- | --- |
| Requirements / acceptance criteria | `<REQ-… → AC-…>` |
| Decisions | `<DEC-###, ADR-####>` |
| Internal contracts | `<IC-###>` |
| Remote contract | `<API-…>` |
| Tests | `<TEST-<FAMILY>-###>` |

## Type of change

`<One of the allowed Conventional Commits types — test · feat · fix · refactor · docs · build · ci · perf · chore · revert ([`CONTRIBUTING.md`](../CONTRIBUTING.md) §3.3) — and whether it is breaking. A pull request that mixes several types SHOULD be split (§5.1).>`

## What was verified, and how

Record the command and the observed result. Do not write "works" without both.

| # | Check | Command | Observed result |
| --- | --- | --- | --- |
| 1 | `<e.g. shared unit and contract tests in fixture mode>` | `<exact command>` | `<pass/fail, counts, or the failure text>` |
| 2 | `<e.g. Android unit and integration tests>` | `<exact command>` | `<…>` |
| 3 | `<e.g. Compose semantics and Roborazzi snapshot verification>` | `<exact command>` | `<…>` |
| 4 | `<e.g. iOS unit, snapshot and state-holder tests>` | `<exact command>` | `<…>` |
| 5 | `<e.g. static analysis and formatting>` | `<exact command>` | `<…>` |
| 6 | `<e.g. dependency analysis and exact pinning>` | `<exact command>` | `<…>` |
| 7 | `<manual or device check, if any>` | `<what was done>` | `<what was observed>` |

- **Required checks:** the full set the gate requires applies to every pull request, on both platforms (`DEC-054` as amended by `DEC-071`: each row is mandatory from the change that introduces its harness). The gate definition and what each check blocks are owned by [`DEFINITION.md`](../DEFINITION.md) and [`TESTING.md`](../TESTING.md): link the CI run rather than copying the list.
- **Not verified:** `<what was deliberately not run, and why. Never leave this blank when a check was skipped.>`

## TDD evidence

A green suite alone does not satisfy this section: it must show the observed red failure before the implementation and the observed passing run after it (`AC-REQ-NFR-010-2`). A failing red commit is expected — the gate evaluates the final state of the pull request (`AC-REQ-NFR-011-3`). The twelve-step protocol is owned by [`CONTRIBUTING.md`](../CONTRIBUTING.md) §3.1 and the gate by [`DEFINITION.md`](../DEFINITION.md) §3 D3/D4; neither is restated here.

| Phase | Commit SHA | Exact command | Observed result | CI run |
| --- | --- | --- | --- | --- |
| **Red** | `<sha>` | `<the command that was run>` | `<the failing assertion, verbatim>` | `<run link, or None>` |
| **Green** | `<sha>` | `<the command that was run>` | `<the passing test ids>` | `<run link, or None>` |
| **Refactor** | `<sha, or "no change">` | `<the command that was run, or "—">` | `<"no change" stated explicitly, or the still-green ids>` | `<run link, or None>` |

## Exception

Fill this only when the change is pure documentation, build/CI configuration or tooling — the three classes [`DEFINITION.md`](../DEFINITION.md) §3 D4 allows. Otherwise write `None` and complete the table above.

| Field | Value |
| --- | --- |
| Class | `<pure documentation \| build/CI configuration \| tooling \| None>` |
| Justification | `<why the red phase does not apply>` |
| Compensating verification | `<the seed matrix an introduced tool owes: what was seeded, and the observed failure>` |


## Tests added or updated

`<The new or changed TEST-<FAMILY>-### ids and the behaviour each asserts, or: "No test added because <reason>", with the reason the change cannot be covered — a documented exception, not a convenience. A test that would not fail without the change is not evidence (`DEFINITION.md` §3 D3).>`

- **Deleted tests:** `<the ids deleted with the behaviour they pinned, or None. An orphan test for removed behaviour is deleted in the same change (TESTING.md §1 P8).>`

## Documentation updated (DEC-046)

| Document | Change |
| --- | --- |
| `<docs/<FILE>.md>` | `<section and what changed>` |
| [`BACKLOG.md`](../BACKLOG.md) | `<row and status>` |
| [`PROJECT_LOG.md`](../PROJECT_LOG.md) | `<entry, when the change is decision-relevant>` |
| `<None>` | — |

`<If a document that describes the changed behaviour is not updated in this change, say which one and why the change does not affect it.>`

## Screenshots, for UI changes

- **Surfaces:** `<screens or components touched>`
- **Android:** `<before / after images, or the committed Roborazzi baseline diff>`
- **iOS:** `<before / after images, or the committed snapshot baseline diff, including the glass and iOS 18 fallback pair when the surface differs>`
- **Design reference:** `<the Figma node in UI_SPEC.md §1.1, and the committed export under docs/figma/ when one exists; CON-005 otherwise>`
- **Not a UI change:** `<state "not applicable" when no rendered surface changed.>`

## Dependency changes

| Dependency | Change | Version | Justification |
| --- | --- | --- | --- |
| `<name>` | `<added / removed / upgraded>` | `<exact version, no range>` | `<the DESIGN.md section or ADR that records the rationale; REQ-NFR-002, REQ-NFR-006>` |

`<No dependency change: state it. A third solution for a concern, or a second one without a recorded rationale, is prohibited (`DEC-065`, `AGENTS.md` §4.2).>`

## Security and privacy considerations

- **Trust boundaries and network:** `<what host, protocol or relation handling changed; REQ-SEC-001>`
- **Persisted data:** `<what is stored, and whether the inventory in SECURITY.md §3 changed; REQ-SEC-003>`
- **Logging and redaction:** `<whether any log line, field or debug surface changed, and that no search text, response body, image byte, personal datum or stack trace is logged; REQ-SEC-005, DEC-039>`
- **Permissions and SDKs:** `<confirmation that no permission, analytics or tracking dependency was added; REQ-SEC-004, REQ-OBS-003>`
- **Secrets:** `<confirmation that no secret, token, keystore or credential is committed; REQ-SEC-002>`
- **Not applicable:** `<say so only when the change touches no boundary, storage, log line, permission or dependency.>`

## Review checklist

- **Done gate:** every item of the Definition of Done in [`DEFINITION.md`](../DEFINITION.md) §3 holds for this change, with the evidence named there. Do not restate the items here; confirm each one and report the unmet ones instead of silently narrowing them.
- **Traceability:** the requirement links to the task and to the test that covers it, or the coverage loss is recorded ([`TESTING.md`](../TESTING.md) §16).
- **No placeholders:** no `TODO`, no stub, no no-op, no angle-bracket placeholder, no commented-out specification in the change.
- **Links:** every link in every document touched by this change resolves.
- **Waivers:** no gate is waived, or the waiver in [`DEFINITION.md`](../DEFINITION.md) is recorded and unexpired.

`<Tick the items that hold and name the ones that do not, with the blocking reason.>`

## AI-agent disclosure (DEC-049)

- **Agent-authored:** `<yes / no>`
- **Documents touched:** `<every file under docs/, README.md, README.es.md or AGENTS.md this change edits>`
- **Checks actually run by the agent:** `<the commands from the verification table that were executed, with the observed result; do not list a check that was only intended>`
- **Checks delegated to a human or to CI:** `<what the agent did not run, and who runs it>`
- **Human-only actions not performed:** merging, tagging, releasing, repository settings, branch protection and secrets (`DEC-049`, [`AGENTS.md`](../../AGENTS.md) §4.3).
