package io.github.davidru85.multiverse.feature.favorites.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters

/**
 * The shared half of the Favorites state holder (`IC-020`, `DESIGN.md` §4.1): the mapping from the
 * observed favourite id set plus the summaries resolved for it to [FavoritesUiState], so neither the
 * Android `FavoritesViewModel` nor the iOS `ObservableObject` computes a display value of its own
 * (`CONTRACTS.md` §7 R2).
 *
 * **The order.** `IC-020` fixes determinism and stability across a re-observation but not a concrete
 * order (`CONTRACTS.md` §10 assumption A3). This reducer sorts by the **canonical id string**
 * (`CharacterId.value`), which is the identity every layer holds (`IC-001`) and the only key that does
 * not depend on the store's emission order. The sort is lexicographic on the string, not numeric —
 * `"10"` precedes `"2"` — which is deterministic, repeatable and independent of which character was
 * marked first.
 *
 * **The three inputs, and why each is separate.** [ids] is `null` until the store's first emission
 * arrives: that is the one meaning of "nothing is known about the stored set yet", so `Loading` appears
 * exactly while it holds. [summaries] holds the characters resolved for the current ids through the
 * cached path (`CONF-79`, `DESIGN.md` §4.5). [failure] is the one failure of the newest read, or `null`.
 */
public object FavoritesReducer {
    /**
     * The state for one store emission and the resolution of it.
     *
     * A [failure] wins over everything else: the section has no content to keep alongside it, because
     * a favourite with no resolvable summary has no card to render.
     */
    public fun render(
        ids: Set<CharacterId>?,
        summaries: Map<CharacterId, CharacterSummary>,
        failure: ApiFailure?,
        formatters: PresentationFormatters,
    ): FavoritesUiState {
        if (failure != null) {
            return FavoritesUiState(items = emptyList(), loadState = LoadState.Error(failure))
        }
        if (ids == null) {
            return FavoritesUiState()
        }
        if (ids.isEmpty()) {
            // The designed empty state shows while the stored set is empty, and only then
            // (`AC-REQ-FUNC-006-3`); an empty resolution of a non-empty set is not this condition.
            return FavoritesUiState(items = emptyList(), loadState = LoadState.Empty)
        }
        val items =
            ids
                .sortedBy { it.value }
                .mapNotNull { id -> summaries[id]?.let { summary -> CharacterCardUi.from(summary, formatters) } }
        // `Content` iff at least one favourite is displayable: a set whose cards have not landed yet
        // is still loading, and a set none of whose ids the read could resolve is not an empty set.
        if (items.isEmpty()) {
            return FavoritesUiState()
        }
        return FavoritesUiState(items = items, loadState = LoadState.Content)
    }
}
