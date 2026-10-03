package io.github.davidru85.multiverse.feature.settings.domain

import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository

/**
 * Removes every stored favourite (`IC-009`, `CONTRACTS.md` §6 use-case table; `REQ-FUNC-035`).
 *
 * The state holder invokes it **only after the user confirms** (`IC-023`, `AC-REQ-FUNC-035-1`): it is
 * the `IC-008` clear and nothing else, so `Favorites` drops to its empty state and no Detail screen
 * keeps a stale flag without a manual refresh (`AC-REQ-FUNC-035-2`). A failed write is logged and
 * contained by the repository (`IC-008`, ADR-0007) rather than thrown here.
 */
public class ClearFavorites(
    private val repository: FavoritesRepository,
) {
    /** Empties the stored favourite set in one write. */
    public suspend operator fun invoke(): Unit = repository.clear()
}
