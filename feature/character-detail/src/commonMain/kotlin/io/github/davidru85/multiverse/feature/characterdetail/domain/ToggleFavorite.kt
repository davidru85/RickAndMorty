package io.github.davidru85.multiverse.feature.characterdetail.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository

/**
 * The favourite toggle the Detail screen dispatches (`IC-019`, `CONTRACTS.md` §6 use-case table;
 * `REQ-FUNC-006`).
 *
 * It is the repository call and nothing else: the optimistic flag and the reconciliation with
 * `ObserveFavoriteIds` are the state holder's, because only it knows what is on screen. A failed
 * write is logged and contained by the repository (`IC-008`, ADR-0007) rather than thrown here.
 */
public class ToggleFavorite(
    private val repository: FavoritesRepository,
) {
    /** Flips the stored favourite state of [id]. */
    public suspend operator fun invoke(id: CharacterId): Unit = repository.toggle(id)
}
