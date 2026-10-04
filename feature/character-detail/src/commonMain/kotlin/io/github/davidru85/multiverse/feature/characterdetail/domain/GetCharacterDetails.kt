package io.github.davidru85.multiverse.feature.characterdetail.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.result.DataResult

/**
 * The detail of one character (`IC-019`'s state holder's loader, `CONTRACTS.md` §6 use-case table).
 *
 * [enrich] is what asks the repository for the episode summaries the `First seen in` row and the
 * dimension enrichment need; the repository orchestrates the bounded batch and reconciles it by id
 * (`IC-007`, `REQ-FUNC-023`), which is why this use case issues **one** call and never one per
 * episode. It is stateless, reads no clock and no platform API, and returns the [DataResult] it
 * received unchanged: a `CancellationException` propagates, because failure handling and state
 * decisions belong to `IC-019` (`IC-009`).
 */
public class GetCharacterDetails(
    private val repository: CharacterRepository,
) {
    /** The detail of [id], with the episode enrichment only when [enrich] is true. */
    public suspend operator fun invoke(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> = repository.details(id, enrich)
}
