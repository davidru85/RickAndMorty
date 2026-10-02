package io.github.davidru85.multiverse.core.domain.usecase

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow

/**
 * The favourite id set, for every feature that marks or lists favourites (`IC-009`). It is
 * cross-feature — Discovery, Detail and Favorites read it — so it lives in `:core:domain`
 * (ADR-0001); it holds no state and adds nothing to the repository's stream.
 */
public class ObserveFavoriteIds(
    private val repository: FavoritesRepository,
) {
    public operator fun invoke(): Flow<Set<CharacterId>> = repository.observe()
}
