# ADR-<NNNN> — <Decision title, sentence case, no trailing period>

- **Status:** `<Proposed | Accepted | Superseded by ADR-NNNN | Rejected>`
- **Date:** `<YYYY-MM-DD>` · the date the decision was taken (ISO 8601).
- **Last verified:** `<YYYY-MM-DD>` · the date the facts in this ADR were last checked.
- **Owner:** `<one role from AGENTS.md>` — accountable for keeping this ADR valid (see [`../../AGENTS.md`](../../AGENTS.md)).
- **Owners:** `<decision owner role>`; implementers `<Implementation Engineer (Android) | Implementation Engineer (iOS)>`; consulted `<roles>`.
- **Authoritative for:** `<the one thing this ADR decides. Not <the neighbouring concern owned elsewhere>, which belongs to ../<FILE>.md §<n>.>`
- **Inputs:** `<DEC-###>` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `<REQ-FUNC-### | REQ-NFR-### | REQ-PLAT-### | REQ-UX-### | REQ-REL-### | REQ-SEC-### | REQ-OBS-###>` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); `<../API_SPECS.md | ../DESIGN.md | ../UI_SPEC.md> §<n>`; `<dated upstream release or live probe, e.g. "<artifact> <version> checked <YYYY-MM-DD>">`

> **Authoring note.** This template is the shape of every ADR in `docs/adr/` (DEC-051). Before the ADR is reviewed:
> 1. Replace **every** `<...>` placeholder. An ADR that still contains a placeholder MUST NOT be merged. There is no catch-all placeholder: if a section does not apply to the decision, say `None.` and give the reason.
> 2. Record **why**, not **what**. Do not restate normative content owned by `REQUIREMENTS.md` (what, how well), `API_SPECS.md` (remote contract), `DESIGN.md` (architecture) or `UI_SPEC.md` (visual/behavioural). Reference the identifier (`DEC-###`, `REQ-FUNC-###`, `AC-REQ-…-n`, `API-…-###`, a section number) instead.
> 3. One decision per ADR. A decision is here only if it has architectural consequences; module-level or file-level choices belong in review or in `CONTRIBUTING.md`.
> 4. `Status` MUST be one of the four values above. A superseded ADR keeps its body unedited and gains a pointer in its last section plus a `Superseded` row in `DECISION_BOARD.md`.
> 5. Cite verified facts with the date they were verified. Anything unverified MUST be labelled an assumption; never present a guess as a fact.
> 6. Validation criteria MUST be checkable: name a `TEST-UNIT|CONTRACT|INT|UI|A11Y|PERF-###` identifier from `TESTING.md`, or state an observable behaviour together with the way it is observed.
> 7. Delete this note when the placeholders are replaced.

## Owners

- **Decision owner:** `<role>` — owns the decision and its review trigger.
- **Implementation owners:** `<roles>` — own the code the decision governs.
- **Consulted:** `<roles>` — reviewed the decision before it was accepted.

## Decision

`<The accepted decision, present tense, active voice, using MUST / SHOULD / MAY for the parts later work must obey. One short paragraph plus at most a short list of the constraints it fixes.>`

- **Board entry:** `<DEC-###>` — `<Accepted | Proposed>`, in [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Context

`<The situation that forced a decision: verified repository facts, platform/toolchain facts, the requirement or contract that constrains it, and which alternatives were realistically available. Distinguish current state from target state.>`

## Decision drivers

- `<driver>` — `<REQ-… | DEC-### | assessment.md line | verified fact>`.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| `<option>` | `<one line: what it would look like in the repository>` | `<what it buys; what it costs>` | `<Chosen | Rejected: the reason, referencing a driver>` |

## Consequences

**Positive**

- `<consequence that is now true of the codebase or process>`.

**Negative**

- `<cost accepted knowingly, including documentation that must be rewritten in the same change (DEC-046)>`.

## Risks

- **`<RISK-###>`** `<existing project risk, if the ADR bears on one>` — `<how this decision mitigates or aggravates it>`.
- **Local:** `<risk that exists only because of this decision>` — `<mitigation>`.

## Validation criteria

- `<TEST-<LAYER>-<NNN>>` `<what it proves>`.
- **Observable:** `<behaviour>` → `<how it is observed: a test, a build report, a rendered screen, a dependency report>`.
- **Observable:** `<behaviour>` → `<observation method>`.

## Related requirements

- `<REQ-…>` (`<AC-…>`): `<one-line relevance>`.
- `<REQ-…>`: `<one-line relevance>`.

## Related implementation areas

- `<module or path>`, `<module or path>`.
- `<DESIGN.md | API_SPECS.md | UI_SPEC.md> §<n>` for the specification this decision feeds.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** `<ADR-NNNN (topic) | none>`.
- **Superseded by:** `<ADR-NNNN (topic) | none as of <YYYY-MM-DD>>`.
- **Related:** `<ADR-NNNN (topic)>, <ADR-NNNN (topic)>`.
