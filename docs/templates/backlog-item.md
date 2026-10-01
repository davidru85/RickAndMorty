# Backlog item template

- **Status:** Active
- **Last verified:** 2026-10-01
- **Owner:** Documentation Maintainer (`../../AGENTS.md` §3.9)
- **Authoritative for:** the shape of one backlog item (`TASK-###`) as recorded in [`BACKLOG.md`](../BACKLOG.md) and in its GitHub Issue. Not authoritative for: backlog content, the column set and the size definitions (`BACKLOG.md` owns them), the readiness and done gates ([`DEFINITION.md`](../DEFINITION.md)), test design and identifiers ([`TESTING.md`](../TESTING.md)). `DEC-051` names this file; no second backlog template exists.
- **Inputs:** [`DECISION_BOARD.md`](../DECISION_BOARD.md) (DEC-044, DEC-051), [`BACKLOG.md`](../BACKLOG.md), [`DEFINITION.md`](../DEFINITION.md), [`REQUIREMENTS.md`](../REQUIREMENTS.md), [`TESTING.md`](../TESTING.md), [`CONTRIBUTING.md`](../CONTRIBUTING.md), [`AGENTS.md`](../../AGENTS.md)

**Purpose.** Copy this shape for one unit of work. `BACKLOG.md` is the canonical work index; the GitHub Issue carries state and links back by `TASK-###` (`DEC-044`).

> **Authoring note (how to fill this in).**
> 1. Replace **every** `<...>` placeholder, including the ones inside the tables. A field that does not apply carries `None` plus the reason — never an empty cell, never a leftover angle bracket. An item that still contains an angle-bracket placeholder is incomplete and MUST NOT be started.
> 2. Keep the twelve fields in the order below. They are the columns of [`BACKLOG.md`](../BACKLOG.md); do not add, reorder or rename one here.
> 3. "Good" for this artifact: one line per field, an `AC-…` or a single observable outcome in `Acceptance`, and an `Expected tests` entry that names a real `TEST-*` family. It does not restate a requirement sentence, an `AC-…` definition or a design decision — it cites the identifier, which is what [`BACKLOG.md`](../BACKLOG.md) and [`REQUIREMENTS.md`](../REQUIREMENTS.md) §15 trace.
> 4. Delete this authoring note and the size reminder when pasting the item into `BACKLOG.md` or into the issue body. Neither is part of the artifact.
> 5. A behaviour change is not started until its first failing test exists (`DEC-053`, [`CONTRIBUTING.md`](../CONTRIBUTING.md)); `Expected tests` names the family and ids that increment will carry.
> 6. Never reuse a retired `TASK-###`. `TASK-001`…`TASK-013` and `TASK-020`…`TASK-023` are cited by [`REQUIREMENTS.md`](../REQUIREMENTS.md) §5 against specific requirements, so those ids and their titles are fixed ([`BACKLOG.md`](../BACKLOG.md) §2.5).
> 7. When the item is not a behaviour change, name its TDD exception class — pure documentation, build/CI configuration or tooling — in the row, in the cell [`BACKLOG.md`](../BACKLOG.md) uses for it. Those three are the only allowed classes (`DEC-053`, [`DEFINITION.md`](../DEFINITION.md) §3).

## Fields

| Field | Value |
| --- | --- |
| ID | `TASK-<###>` |
| Title | `<imperative, sentence case, one line, no trailing period>` |
| Type | `<Product \| Tech \| Doc \| Test \| Risk>` |
| Block | `<B1 \| … \| B9, or — for a task outside the block plan>` (`DEC-063`, [`BACKLOG.md`](../BACKLOG.md) §2.6) |
| Milestone | `<M0 \| M1 \| M2 \| M3>` |
| Value | `<why the task exists: the review, requirement or decision it serves>` |
| Requirements | `<REQ-… ids the task delivers, or the governing DEC-### when no requirement applies>` |
| Dependencies | `<prerequisite TASK-### ids, or None>` |
| Priority | `<Must \| Should \| Could>` |
| Size | `<S \| M \| L>` (definition below) |
| Acceptance | `<AC-… ids from REQUIREMENTS.md, or one observable outcome when no AC-… exists>` |
| Expected tests | `<TEST-… ids from TESTING.md §16, or the family plus the AC-… each planned test maps to; a dash plus the reason when the evidence is a gate artefact rather than a test>` |
| Status | `<Proposed \| Ready \| In progress \| In review \| Done>` |

## Size reminder (mirrors `BACKLOG.md`; that file is authoritative)

- **S** — one module, a small number of files, no new contract; completable and reviewable in a single review cycle.
- **M** — several files across one or two modules; adds tests or wiring, but introduces no new cross-module contract.
- **L** — spans multiple modules or both platforms, or introduces a new contract, build capability or CI pipeline; needs its own sequencing and cannot land in one review cycle.

If [`BACKLOG.md`](../BACKLOG.md) changes a size definition, this reminder follows it rather than the other way round.

## Recording the item

- **Index row:** the twelve-field row in [`BACKLOG.md`](../BACKLOG.md), grouped by milestone.
- **Issue:** title is the item's `ID`, an em dash and its `Title` — for example `TASK-029 — Add ktlint, Android Lint and dependency-analysis to the build`; labels from the type vocabulary in [`CONTRIBUTING.md`](../CONTRIBUTING.md) §4 (`type:product`, `type:tech`, `type:test`, `type:doc`, `type:risk`) plus the milestone; body = the fields above. The `Status` column mirrors the issue state and is never the only record of it (`DEC-044`).
- **Deferred work:** an item whose requirement is `Could have` or `Deferred` is recorded as such and not started ([`DECISION_BOARD.md`](../DECISION_BOARD.md) §4, [`REQUIREMENTS.md`](../REQUIREMENTS.md) §1.3).

## Before starting

Readiness is owned by [`DEFINITION.md`](../DEFINITION.md) §2. This template does not restate the checklist: confirm every mandatory item there (R1–R11) holds — requirement and `AC-…` exist, dependencies are Ready, size is assigned, impacted documents and test intent are known, no blocking decision is open, the work is not `Deferred`, and the design or contract input for the work type is present — before moving the item to `Ready`.
