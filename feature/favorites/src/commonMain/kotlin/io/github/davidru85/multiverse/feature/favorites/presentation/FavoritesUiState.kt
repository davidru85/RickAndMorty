package io.github.davidru85.multiverse.feature.favorites.presentation

import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.LoadState

/**
 * What the Favorites section renders (`IC-020`, `CONTRACTS.md` §6).
 *
 * Both platforms construct this one type — the Android `FavoritesViewModel` and the iOS
 * `ObservableObject` — so the two sections cannot diverge.
 *
 * [items] reuses `IC-016` unchanged, so a favourite is displayed by the same card contract
 * Discovery's grid uses (`UI_SPEC.md` §6.4). [loadState] follows `ERROR_FLOW.md`':
 *
 * - `Empty` while the **stored set** is empty: the designed empty state shows only in that condition
 *   (`REQ-FUNC-006`, `AC-REQ-FUNC-006-3`);
 * - `Loading` only while the first store emission is awaited, and while a non-empty set's cards are
 *   still being resolved, because nothing is displayable yet (`CONF-79`);
 * - `Error` only when a read failed, carrying that one failure;
 * - `Content` iff at least one favourite is displayable.
 *
 * The item order is deterministic and stable across a re-observation; [FavoritesReducer] documents
 * the concrete order (`CONTRACTS.md` §10 assumption A3).
 */
public data class FavoritesUiState(
    public val items: List<CharacterCardUi> = emptyList(),
    public val loadState: LoadState = LoadState.Loading,
)

/**
 * The Favorites intents (`IC-020`), the only write path into the state holder: a view never calls a
 * repository or a use case directly (`ERROR_FLOW.md` §3 invariant 4).
 *
 * It declares exactly one, because the section has no query, no filter and no refresh: `Retry`
 * re-attempts the read that failed with a fresh attempt (`REQ-FUNC-011`).
 */
public sealed interface FavoritesIntent {
    /** The user asked for a retry after a failed read; a fresh attempt budget is granted. */
    public data object Retry : FavoritesIntent
}
