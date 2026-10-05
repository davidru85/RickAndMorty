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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The shared half of the Settings state holder (`IC-023`, `DESIGN.md` §4.1): every rule a platform
 * holder must not re-derive lives here, so the Android `SettingsViewModel` and the iOS
 * `ObservableObject` are thin adapters over one implementation (`CONTRACTS.md` §7 R1–R5).
 *
 * It owns three things, and nothing else:
 *
 * - the **`IC-021` projection**. `ObserveAppSettings` is the one source of `soundsEnabled` and
 *   `remoteProtocol`; the holder keeps no competing copy, so a change written by any path — this
 *   screen or another consumer of the repository — is mirrored by the next emission;
 * - the **favorite-derived flag**. `canDeleteFavorites` is true exactly while the `ObserveFavoriteIds`
 *   set is non-empty (`AC-REQ-FUNC-035-3`), which is also what makes the action disabled on a fresh
 *   install;
 * - the **confirmation**. It opens only while favorites exist (`AC-REQ-FUNC-035-1`), `Delete` runs the
 *   one clear and closes it, and `Cancel` — like a tap outside or a system dismissal, which the
 *   platform reports as `DeleteFavoritesDismissed` — closes it and invokes nothing.
 *
 * [scope] belongs to the owner and is closed by it (`GUIDELINES.md` §2.7), so closing the holder
 * cancels the observations with it. The feature issues no remote request anywhere.
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

    /** What the screen renders, always up to date. */
    public val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    /**
     * Starts the two observations; a platform holder invokes it once, from its own initialisation.
     *
     * Nothing else is started here: the screen renders the contract defaults until `IC-021` answers,
     * which is the fresh-install state the specification draws (`AC-REQ-FUNC-033-2`).
     */
    public fun start(): Job =
        scope.launch(dispatcher) {
            launch {
                observeAppSettings().collect { settings ->
                    mutableState.update {
                        it.copy(
                            soundsEnabled = settings.soundsEnabled,
                            remoteProtocol = settings.remoteProtocol,
                        )
                    }
                }
            }
            launch {
                observeFavoriteIds().collect { ids ->
                    mutableState.update { it.copy(canDeleteFavorites = ids.isNotEmpty()) }
                }
            }
        }

    /** Dispatches [intent]; a view never reaches a repository or a use case directly. */
    public fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SoundsToggled ->
                scope.launch(dispatcher) {
                    updateAppSettings { settings -> settings.copy(soundsEnabled = intent.enabled) }
                }

            // The protocol already in force is a no-op: the write is skipped here so the repository's
            // equal-value rule is not the only thing standing between a tap and a store write
            // (`IC-023`).
            is SettingsIntent.RemoteProtocolSelected -> {
                if (mutableState.value.remoteProtocol != intent.protocol) {
                    scope.launch(dispatcher) {
                        updateAppSettings { settings -> settings.copy(remoteProtocol = intent.protocol) }
                    }
                }
            }

            // Only a live confirmation opens, and only with favorites present (AC-REQ-FUNC-035-3).
            SettingsIntent.DeleteFavoritesRequested -> {
                if (mutableState.value.canDeleteFavorites) mutableState.update { it.copy(isConfirmingDelete = true) }
            }

            // The confirmation guard is what makes "exactly once" hold: it is consumed here, before
            // the clear is launched, so a second dispatch — a double tap that arrives before the
            // clear has run — finds nothing open and is a no-op.
            SettingsIntent.DeleteFavoritesConfirmed -> {
                if (mutableState.value.isConfirmingDelete) {
                    mutableState.update { it.copy(isConfirmingDelete = false) }
                    scope.launch(dispatcher) { clearFavorites() }
                }
            }

            SettingsIntent.DeleteFavoritesDismissed ->
                mutableState.update { it.copy(isConfirmingDelete = false) }
        }
    }
}
