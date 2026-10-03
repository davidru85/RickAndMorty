package io.github.davidru85.multiverse.feature.settings.presentation

import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.feature.settings.domain.ClearFavorites
import io.github.davidru85.multiverse.feature.settings.domain.ObserveAppSettings
import io.github.davidru85.multiverse.feature.settings.domain.UpdateAppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The shared half of the Settings state holder (`IC-023`, `DESIGN.md` §4.1).
 *
 * STUB behind the declared surface, so the intent-rule cases (`TEST-UNIT-050`) are red until the
 * implementation commit lands. It holds no emission from `IC-021`, no favourite-derived flag and no
 * confirmation rule yet.
 */
public class SettingsStateHolder(
    private val observeAppSettings: ObserveAppSettings,
    private val updateAppSettings: UpdateAppSettings,
    private val clearFavorites: ClearFavorites,
    private val observeFavoriteIds: ObserveFavoriteIds,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(SettingsUiState())

    /** What the screen renders. */
    public val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    /** Starts the observations; the platform holder calls it once, from its own initialisation. */
    public fun start(): Job = Job()

    /** Dispatches [intent]; a view never reaches a repository or a use case directly. */
    public fun onIntent(intent: SettingsIntent) {
        // not yet implemented
    }
}
