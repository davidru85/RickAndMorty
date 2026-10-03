package io.github.davidru85.multiverse.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.feature.settings.domain.ClearFavorites
import io.github.davidru85.multiverse.feature.settings.domain.ObserveAppSettings
import io.github.davidru85.multiverse.feature.settings.domain.UpdateAppSettings
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsIntent
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsStateHolder
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The Android state holder of the Settings screen (`IC-023`, `DESIGN.md` §5, ADR-0006).
 *
 * It is a **thin adapter**: every rule lives in the shared [SettingsStateHolder], so this class
 * exposes its [state] and forwards intents to it, and computes no display value of its own
 * (`CONTRACTS.md` §7 R2). Its own concern is the one only a platform has — the `viewModelScope` the
 * observations and the confirmation's one clear run in.
 */
public class SettingsViewModel(
    observeAppSettings: ObserveAppSettings,
    updateAppSettings: UpdateAppSettings,
    clearFavorites: ClearFavorites,
    observeFavoriteIds: ObserveFavoriteIds,
) : ViewModel() {
    private val holder =
        SettingsStateHolder(
            observeAppSettings = observeAppSettings,
            updateAppSettings = updateAppSettings,
            clearFavorites = clearFavorites,
            observeFavoriteIds = observeFavoriteIds,
            scope = viewModelScope,
            dispatcher = Dispatchers.Main.immediate,
        )

    /** What the screen renders, owned by the shared holder and only projected here. */
    public val state: StateFlow<SettingsUiState> = holder.state

    init {
        holder.start()
    }

    /** The one write path into the state holder (`ERROR_FLOW.md` §3 invariant 4). */
    public fun onIntent(intent: SettingsIntent) {
        holder.onIntent(intent)
    }
}
