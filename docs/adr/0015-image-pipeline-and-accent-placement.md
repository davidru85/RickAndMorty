# ADR-0015 — The portrait seam lives in the design system, the loader and accent policy split around it

- **Status:** Accepted
- **Date:** 2026-10-03
- **Last verified:** 2026-10-03
- **Owner:** System Architect (see [`../../AGENTS.md`](../../AGENTS.md))
- **Owners:** decision owner System Architect; implementers Implementation Engineer (Android) for the seam, the vendored colour subset and the shell loader, Implementation Engineer (iOS) for the later platform half; consulted UI/UX Designer (image behaviour) and Security Reviewer (the allow-listed image host).
- **Authoritative for:** which module owns the portrait rendering states, which module owns image loading, where the accent is computed from, and how the two halves meet. Not the portrait's visual specification, which belongs to [`../UI_SPEC.md`](../UI_SPEC.md) §5; not the loader configuration values, which belong to [`../API_SPECS.md`](../API_SPECS.md) §14; not the module set, which belongs to [ADR-0001](0001-module-boundaries.md).
- **Inputs:** `DEC-097` in [`DECISION_BOARD.md`](../DECISION_BOARD.md); `REQ-FUNC-005`, `REQ-FUNC-021`, `REQ-SEC-001` in [`REQUIREMENTS.md`](../REQUIREMENTS.md); [`UI_SPEC.md`](../UI_SPEC.md) §5.1–§5.4; [`DESIGN.md`](../DESIGN.md) §0, §3.4, §4.4; [`CONTRACTS.md`](../CONTRACTS.md) §3, `IC-016`; [`SECURITY.md`](../SECURITY.md) §5; `CONF-50` in [`DOCUMENTATION_AUDIT.md`](../DOCUMENTATION_AUDIT.md); the live Figma variable export of 2026-10-03 for the brand colours.

## Owners

- **Decision owner:** System Architect — owns the split and the review trigger below.
- **Implementation owners:** Implementation Engineer (Android) for `TASK-043` (the seam and the rendering states), `TASK-044` (the loader and the seam implementation), `TASK-005` (the vendored accent policy) and `TASK-021` (the cache); Implementation Engineer (iOS) for the later platform half.
- **Consulted:** UI/UX Designer, because the seam must still deliver §5's behaviour; Security Reviewer, because the loader must honour the host allow-list before any transport call.

## Decision

The portrait's **behaviour** belongs to `:core:designsystem`; the **transport** belongs to the composition root.

- `:core:designsystem` MUST own the three portrait rendering states (placeholder with shimmer, loaded with a crossfade, error with the portal mark), the crossfade and animation timings of `UI_SPEC.md` §5.1/§5.3, and the accent **policy** — the quantize/score/palette computation over pixels, with the chroma clamp, the tone-30 container and the Portal Green fallback of `UI_SPEC.md` §5.4.
- It MUST expose a Compose-only **image seam**: a small interface that takes the image URL, a requested pixel size and a caller-supplied `CoroutineDispatcher`, and returns a painter or a state result. It MUST NOT name a Coil, Ktor or platform type.
- The accent computation MUST be backed by a **vendored subset of `material-color-utilities`** (`QuantizerCelebi`, `Score`, `TonalPalette`), copied with its Apache-2.0 headers and a `NOTICE`, because no first-party artifact exists on Maven Central or Google Maven (`CONF-50`, verified 2026-09-30). The policy MUST memoise one result per URL and MUST run off the main thread on the supplied dispatcher.
- `:androidApp` MUST own Coil (`coil-compose`, `coil-network-ktor3`), the `ImageLoader`, the memory and disk cache, and the seam implementation: it requests the software bitmap the extraction needs, keys the cache by the image URL verbatim, and fetches through a Ktor client built with `rickAndMortyDefaults()` so an image URL that fails the host allow-list issues **zero** transport calls and renders the error state.
- A `:feature:*` module MUST NOT declare an image loader or compute an accent; it renders the design-system portrait and passes the URL.

- **Board entry:** `DEC-097` — Accepted, in [`DECISION_BOARD.md`](../DECISION_BOARD.md). It amends [`../DESIGN.md`](../DESIGN.md) §0 and §4.4, the `CONTRACTS.md` §3 map and `TESTING.md` §7 by pointer, and resolves `CONF-50` and closes `TASK-083`.

## Context

Four facts collided at the start of B4 (observed 2026-10-03 on `main` at `47e284f`):

1. `TASK-043`'s acceptance demands image behaviour, but boundary rule `R15` admits Compose-only externals into `:core:designsystem` and `R4` forbids it any project dependency, so Coil cannot live there.
2. `DESIGN.md` §0 placed "image pipeline, accent extraction" in `:androidApp`, while §4.4 and the `CONTRACTS.md` §3 map placed `CharacterAccentResolver` in `:feature:discovery`.
3. Favorites renders "the same cards as Discovery" (`UI_SPEC.md` §6.4), so a resolver owned by `:feature:discovery` cannot serve it without a `:feature:*` → `:feature:*` edge, which `R7` forbids.
4. `SECURITY.md` §5 requires every server-supplied URL, image URLs included, to pass the host allow-list, and `CONF-50`/`TASK-083` had already established that no first-party colour library exists to depend on.

The design system is the only module every surface that shows a portrait already depends on, so it is the only place the behaviour can be defined once; the allow-listed client, by contrast, only exists where the composition root builds it.

## Decision drivers

- `REQ-FUNC-005` — portraits are the product's primary element on list and detail, so their behaviour must have exactly one owner.
- `REQ-FUNC-021` — image bytes never travel the JSON path, so the image transport is separate from the data stack.
- `REQ-SEC-001` and [`../SECURITY.md`](../SECURITY.md) §5 — a foreign-host image URL must issue no transport call.
- `DEC-097`. `R4`/`R7`/`R15` — the boundary rules that made the two obvious placements impossible.
- `CONF-50` (`TASK-083`) — no first-party quantizer artifact exists; probed again and confirmed unavailable.

## Considered options

| Option | Shape | Trade-offs | Outcome |
| --- | --- | --- | --- |
| Design-system seam, shell loader, vendored colour subset | Rendering states, seam and accent policy in `:core:designsystem`; Coil and the allow-listed fetcher in `:androidApp` | One owner per concern; features cannot drift; the vendored subset is reviewed code with its license; `R4`/`R15` stay intact | Chosen |
| Coil in each feature's `androidMain` and the resolver in `:feature:discovery` | Every feature loads its own images and Discovery owns the resolver | Would duplicate the resolver for Favorites, need a `:feature:*` → `:feature:*` edge that `R7` rejects, and contradict ADR-0008's single `:androidApp` image loader | Rejected: violates `R7` and the recorded single-loader rationale |
| Adopt a community port (`com.materialkolor`) | A pinned third-party dependency instead of vendored code | A new pinned artifact to justify and keep current; the needed surface is three small classes | Rejected: `REQ-NFR-002`'s justification bar is not met by a community port when a license-compatible subset can be vendored |

## Consequences

**Positive**

- The three portrait states and the accent policy have one owner that every screen already depends on, so Discovery, Detail and Favorites cannot diverge.
- The design system stays Compose-only (`R15`) and project-dependency-free (`R4`), so no boundary rule had to be weakened for it.
- The allow-list is enforced in the one place that performs the transport, so an image URL cannot bypass it.

**Negative**

- `:core:designsystem` carries a vendored copy of three `material-color-utilities` classes, which must be kept with its license and revisited if a first-party artifact ever ships.
- `DESIGN.md` §0, §4.4, the `CONTRACTS.md` §3 map and `TESTING.md` §7 are rewritten in the same change (`DEC-046`).
- The seam is a Compose-typed interface, so the iOS half (`TASK-052`, B7) needs its own implementation of the same shape rather than reusing it.

## Risks

- **`RISK-008`** (Figma access) — unrelated to this decision; recorded for completeness.
- **Local:** the vendored subset diverges from upstream — mitigated by importing only the classes the policy uses and recording the upstream revision and license in the file header.
- **Local:** the seam's dispatcher parameter is dropped by a caller, putting extraction on the main thread — mitigated by `TEST-UI-004`'s dispatcher assertion and the default in the production implementation.

## Validation criteria

- `TEST-UI-004` — the placeholder renders first, content appears after the 200 ms crossfade, failure renders the portal mark at 40 % alpha, and the requested size passed to the seam is ≤ 300 px.
- `TEST-INT-002` — a second render of the same URL issues no transport call; a foreign-host or cleartext image URL issues none and renders the error state.
- `TEST-UNIT-035` — the palette policy returns tone 30 with the chroma clamp, and falls back to Portal Green on unquantizable input.
- **Observable:** the accent computation runs off the main thread → the recorded dispatcher in the seam call is not `Main`.
- **Observable:** `:core:designsystem` declares no Coil type → `R15` stays green and the module's compiled surface contains no `coil` reference.

## Related requirements

- `REQ-FUNC-005` (`AC-REQ-FUNC-005-1`…`3`): portrait placeholder, crossfade and error behaviour.
- `REQ-FUNC-021` (`AC-REQ-FUNC-021-1`/`2`): one network fetch per URL and image bytes outside the response cache.
- `REQ-SEC-001` (`AC-REQ-SEC-001-1`): the image host passes the same allow-list as the API host.

## Related implementation areas

- `:core:designsystem` (rendering states, seam, accent policy), `:androidApp` (Coil, `ImageLoader`, seam implementation).
- [`../UI_SPEC.md`](../UI_SPEC.md) §5.1–§5.4 for the visual specification the seam delivers.
- [`../DESIGN.md`](../DESIGN.md) §3.4/§4.4 and [`../CONTRACTS.md`](../CONTRACTS.md) §3 for the architecture and the `IC-016` mapping.
- Decision status index: [`DECISION_BOARD.md`](../DECISION_BOARD.md).

## Superseded and superseding ADRs

- **Supersedes:** none.
- **Superseded by:** none as of 2026-10-03.
- **Related:** [ADR-0008](0008-alpha-dependencies.md) (the single pinned image-loader rationale this decision keeps), [ADR-0001](0001-module-boundaries.md) (the module rules `R4`/`R7`/`R15` this decision works within).
