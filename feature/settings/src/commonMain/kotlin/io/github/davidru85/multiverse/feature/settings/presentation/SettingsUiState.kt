package io.github.davidru85.multiverse.feature.settings.presentation

import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol

/**
 * What the Settings screen renders (`IC-023`, `UI_SPEC.md` §6.5). Both platforms construct this one
 * type — the Android `SettingsViewModel` and the iOS `ObservableObject` — so the two screens cannot
 * diverge (`CONTRACTS.md` §7 R1).
 *
 * [soundsEnabled] and [remoteProtocol] mirror the latest `IC-021` emission; the state holder keeps no
 * competing copy of either. [canDeleteFavorites] is true exactly while the `ObserveFavoriteIds` set
 * is non-empty (`AC-REQ-FUNC-035-3`), and [isConfirmingDelete] is true only while the confirmation the
 * user opened is still open (`AC-REQ-FUNC-035-1`).
 */
public data class SettingsUiState(
    public val soundsEnabled: Boolean = false,
    public val remoteProtocol: RemoteProtocol = RemoteProtocol.Rest,
    public val canDeleteFavorites: Boolean = false,
    public val isConfirmingDelete: Boolean = false,
)

/**
 * The Settings intents (`IC-023`), the only write path into the state holder: a view never reaches a
 * repository or a use case directly (`ERROR_FLOW.md` §3 invariant 4).
 */
public sealed interface SettingsIntent {
    /** The user moved the Sounds control to [enabled]. */
    public data class SoundsToggled(
        public val enabled: Boolean,
    ) : SettingsIntent

    /** The user picked [protocol] in the data-source picker. */
    public data class RemoteProtocolSelected(
        public val protocol: RemoteProtocol,
    ) : SettingsIntent

    /** The user activated "Delete favorites"; the confirmation opens only when favourites exist. */
    public data object DeleteFavoritesRequested : SettingsIntent

    /** The user pressed "Delete" in the confirmation. */
    public data object DeleteFavoritesConfirmed : SettingsIntent

    /** The user cancelled or dismissed the confirmation. */
    public data object DeleteFavoritesDismissed : SettingsIntent
}
