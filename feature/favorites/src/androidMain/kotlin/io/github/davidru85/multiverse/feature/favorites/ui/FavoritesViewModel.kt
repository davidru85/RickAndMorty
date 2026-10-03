package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.favorites.domain.ResolveFavoriteCards
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesIntent
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesStateHolder
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The Android state holder of the Favorites section (`IC-020`, `DESIGN.md` §5, ADR-0006).
 *
 * It is a **thin adapter**: every rule lives in the shared `FavoritesStateHolder`, so this class
 * exposes its [state] and forwards intents to it, and computes no display value of its own
 * (`CONTRACTS.md` §7 R2). Its only platform concern is the `viewModelScope` the observation and the
 * resolution run in.
 *
 * The graph resolves it with `koinViewModel()` from `favoritesModule` plus `coreModule`; the module
 * binds the two collaborators it takes (`DEC-091`, `DESIGN.md` §5).
 */
public class FavoritesViewModel(
    observeFavoriteIds: ObserveFavoriteIds,
    resolveFavoriteCards: ResolveFavoriteCards,
    formatters: PresentationFormatters,
) : ViewModel() {
    private val holder =
        FavoritesStateHolder(
            observeFavoriteIds = observeFavoriteIds,
            resolveFavoriteCards = resolveFavoriteCards,
            scope = viewModelScope,
            dispatcher = Dispatchers.Main.immediate,
            formatters = formatters,
        )

    /** What the section renders, owned by the shared holder and only projected here. */
    public val state: StateFlow<FavoritesUiState> = holder.state

    init {
        holder.start()
    }

    /** The one write path into the state holder (`ERROR_FLOW.md` §3 invariant 4). */
    public fun onIntent(intent: FavoritesIntent) {
        holder.onIntent(intent)
    }
}
