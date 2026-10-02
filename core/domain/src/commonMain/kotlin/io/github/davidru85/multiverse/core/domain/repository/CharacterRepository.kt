package io.github.davidru85.multiverse.core.domain.repository

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.result.DataResult

/**
 * The character seam (`IC-007`). Both methods return [DataResult] and never throw for an expected
 * remote failure; a `CancellationException` propagates unchanged. The invariants an implementation
 * honours are owned by `CONTRACTS.md` `IC-007`.
 */
public interface CharacterRepository {
    /** The requested [page] only, with [filter] applied unchanged, under [policy] (`DEC-086`). */
    public suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy = PageLoadPolicy.Default,
    ): DataResult<CharacterPage>

    /** One character; `enrich = false` issues no episode request. */
    public suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails>
}

/**
 * How a page load treats a cached entry (`DEC-086`). [ForceNetwork] reaches the network even when a
 * fresh entry exists — the manual-refresh policy — and is part of the request identity, so
 * [Default] work never satisfies it.
 */
public enum class PageLoadPolicy { Default, ForceNetwork }
