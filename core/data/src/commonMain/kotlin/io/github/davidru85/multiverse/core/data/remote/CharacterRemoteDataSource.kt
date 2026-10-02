package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.result.DataResult

/**
 * The remote seam (`IC-011`): one implementation per protocol, returning domain types only, so no
 * DTO or wire envelope crosses it. An expected remote failure is a `DataResult.Failure` and only a
 * `CancellationException` propagates (`DEC-090`). The invariants are owned by `CONTRACTS.md`
 * `IC-011`.
 */
public interface CharacterRemoteDataSource {
    /** One page of the character list for [filter]. */
    public suspend fun characterPage(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage>

    /** One character with its episode id list; enrichment is orchestrated above this seam. */
    public suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails>

    /** Episode summaries for [ids], in bounded batches, reconciled by id. */
    public suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>>
}
