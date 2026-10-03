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
 * **The precedence**, which `IC-020` states in words and `IC-018` — its sibling over the same
 * `LoadState` primitive — pins as an evaluation order. Applied in order:
 *
 * 1. no store emission yet → `Loading`, the one state that is not a claim about the stored set;
 * 2. a non-empty stored set with at least one displayable card → `Content`, **even when the newest
 *    read also failed**: the cards a user can see outrank the failure, which is what
 *    `AC-REQ-FUNC-006-1` means by reflecting the stored state, and what stops a partial read from
 *    blanking a section that has content;
 * 3. a failed read with nothing displayable → `Error`, the only way `Error` is reachable;
 * 4. an empty stored set → `Empty`, the designed empty state, shown only then
 *    (`AC-REQ-FUNC-006-3`);
 * 5. otherwise (a non-empty set whose cards have not landed, or none of whose ids the read could
 *    resolve) → `Loading`, because nothing is displayable yet.
 *
 * **The three inputs, and why each is separate.** [ids] is `null` until the store's first emission
 * arrives, so a `Loading` that means "nothing is known about the set" is distinguishable from one that
 * means "the cards are still coming". [summaries] holds the characters resolved for those ids through
 * the cached path (`CONF-79`, `DESIGN.md` §4.5). [failure] is the one failure of the newest read, or
 * `null`.
 */
public object FavoritesReducer {
    /** The state for one store emission and the resolution of it. */
    public fun render(
        ids: Set<CharacterId>?,
        summaries: Map<CharacterId, CharacterSummary>,
        failure: ApiFailure?,
        formatters: PresentationFormatters,
    ): FavoritesUiState {
        if (ids == null) return FavoritesUiState()
        val items =
            ids
                .sortedBy { it.value }
                .mapNotNull { id -> summaries[id]?.let { summary -> CharacterCardUi.from(summary, formatters) } }
        if (items.isNotEmpty()) return FavoritesUiState(items = items, loadState = LoadState.Content)
        if (failure != null) return FavoritesUiState(loadState = LoadState.Error(failure))
        if (ids.isEmpty()) return FavoritesUiState(loadState = LoadState.Empty)
        return FavoritesUiState()
    }
}
