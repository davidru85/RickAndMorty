# Test case template

- **Status:** Active
- **Last verified:** 2026-10-01
- **Owner:** Documentation Maintainer (`../../AGENTS.md` §3.9)
- **Authoritative for:** the shape of one test case description (`TEST-<FAMILY>-###`) as recorded for contract tests and manual checklist entries. Not authoritative for: the test strategy, the test-id families, the source-set layout, the fixture inventory and the flaky-test policy ([`TESTING.md`](../TESTING.md) owns them), the gates ([`DEFINITION.md`](../DEFINITION.md)). `DEC-051` names this file; no second test-case template exists.
- **Inputs:** [`DECISION_BOARD.md`](../DECISION_BOARD.md) (DEC-051), [`TESTING.md`](../TESTING.md) §1, §4.3, §13, §15, [`REQUIREMENTS.md`](../REQUIREMENTS.md), [`DEFINITION.md`](../DEFINITION.md) §3, [`CONTRIBUTING.md`](../CONTRIBUTING.md) §3, [`AGENTS.md`](../../AGENTS.md)

**Purpose.** Describe one test case so a reviewer can see what it proves without reading the code. [`TESTING.md`](../TESTING.md) §13 requires this shape when a case needs an author-visible description.

> **Authoring note (how to fill this in).**
> 1. Replace **every** `<...>` placeholder. A field that does not apply carries `None` plus the reason. A case whose description still contains an angle-bracket placeholder is incomplete and MUST NOT be cited as evidence.
> 2. "Good" for this artifact: one case per id, one asserted behaviour per case, a `Then` that states an observable outcome rather than an internal call, and a `Would fail without the change` line a reader could reproduce. Assert boundaries, state transitions, precedence and error outcomes, not implementation constants.
> 3. A test that asserts incidental implementation detail — wording, log phrasing, an internal call order, a default no requirement names — MUST be **deleted** rather than re-pinned when the behaviour changes legitimately ([`TESTING.md`](../TESTING.md) §1 P4, [`AGENTS.md`](../../AGENTS.md) §9). Do not record such a case here.
> 4. The case is written before the implementation; the observed failing run is the red evidence recorded in `Red / green evidence` (`DEC-053`), and the observed passing run is the green evidence.
> 5. Delete this authoring note when the case is recorded. It is not part of the artifact.

## Fields

| Field | Value |
| --- | --- |
| ID | `TEST-<UNIT \| CONTRACT \| INT \| UI \| A11Y \| PERF>-<###>` (permanent; never reused) |
| Title | `given_<precondition>_when_<action>_then_<outcome>` |
| Level | `<unit \| contract \| integration \| ui \| a11y \| perf>` |
| Requirements | `<REQ-…, REQ-…>` |
| Acceptance criteria | `<AC-…, AC-…>` |
| Family / source set | `<e.g. commonTest, androidHostTest, androidDeviceTest, iosTest, contract-live>` (`DEC-069`) ([`TESTING.md`](../TESTING.md) §13.1) |
| Platform(s) | `<common \| Android \| iOS \| both>` |
| Given | `<the precondition and the state the system is in>` |
| When | `<the single action, input or event under test>` |
| Then | `<the observable outcome: state, rendered result or mapped failure>` |
| Would fail without the change | `<what the run produces before the implementation: the assertion, failure class or rendered difference>` |
| Fixtures / data used | `<fixture file names from TESTING.md §4.3, or the fake seam, or None>` |
| Determinism | `<injected TestDispatcher and fake clock; the virtual time advanced; what is deliberately not real (network, storage, device clock)>` |
| Evidence | `<automated \| snapshot \| measurement \| manual>` |
| Blocking | `<yes \| no>` (a quarantined case is not evidence: [`TESTING.md`](../TESTING.md) §15) |
| Red / green evidence | `<red commit message and observed failure> / <green commit message and observed pass>` |
| Owner | `<role from AGENTS.md §3>` |
| Status | `<Planned \| Active \| Quarantined \| Retired>` |

## Notes that keep the case honest

- Determinism is a property of the case, not an aspiration: no real network I/O, no wall-clock dependence, no real image decoding, no real device clock ([`TESTING.md`](../TESTING.md) §1 P6, §5).
- `Retired` means the id is permanently reserved and never reused; a case deleted with its behaviour takes its id out of circulation ([`TESTING.md`](../TESTING.md) §1 P8).
- `Blocking` is also the field that distinguishes the two `perf` kinds: the deterministic budget assertion runs inside the required set (`Blocking: yes`), while a measurement that reports into the scheduled job does not (`Blocking: no`). Do not add a second field for the distinction ([`TESTING.md`](../TESTING.md) §10).
- `Quarantined` requires the metadata in [`TESTING.md`](../TESTING.md) §15 (owner, tracking item, removal deadline of at most 14 days) and MUST NOT be cited as evidence for any acceptance criterion.
- The naming convention, including the id as the first token of the test name, is owned by [`TESTING.md`](../TESTING.md) §13.2; do not restate it in the recorded case.
