package io.github.davidru85.multiverse.feature.discovery.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.result.DataResult

/**
 * One page of the character list (`IC-009`, `docs/CONTRACTS.md` §6 use-case table): the `IC-007`
 * repository call for a single page, and nothing else.
 *
 * Paging state, page accumulation and prefetch are not a use case's business — they belong to
 * `IC-014`, which the state holder observes directly. The class is stateless, reads no clock and no
 * platform API, and returns the [DataResult] it received unchanged: a `CancellationException`
 * propagates, because failure handling and state decisions belong to `IC-018` (`TASK-001`).
 */
public class GetCharacterPage(
    private val repository: CharacterRepository,
) {
    /** The page [page] of [filter], under the repository's default freshness policy. */
    public suspend operator fun invoke(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage> = repository.page(filter, page)
}
