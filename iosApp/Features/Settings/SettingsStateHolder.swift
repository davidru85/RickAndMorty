import Foundation
import MultiverseExplorer
import SwiftUI

/// The iOS Settings state holder (`IC-023`, `DESIGN.md` §4.1, `TASK-053`).
///
/// The peer of the Android `SettingsViewModel`: the Sounds flag, the protocol preference, the
/// `canDeleteFavorites` derivation and the confirmation lifecycle all live in the shared
/// `SettingsStateHolder`, so the two Settings screens cannot diverge (`CONTRACTS.md` R2).
@MainActor
public final class SettingsStateHolder: ObservableObject {
    @Published public private(set) var state: SettingsUiState

    private let holder: MultiverseExplorer.SettingsStateHolder
    /// The screen's one scope and its state observation, ended with the holder (`DEC-143`).
    private let lifetime = ScreenLifetime()

    public init(
        observeAppSettings: ObserveAppSettings,
        updateAppSettings: UpdateAppSettings,
        observeFavoriteIds: ObserveFavoriteIds,
        clearFavorites: ClearFavorites
    ) {
        let scope = lifetime.scope
        let holder = MultiverseExplorer.SettingsStateHolder(
            observeAppSettings: observeAppSettings,
            updateAppSettings: updateAppSettings,
            clearFavorites: clearFavorites,
            observeFavoriteIds: observeFavoriteIds,
            scope: scope,
            dispatcher: MultiverseBootstrap.shared.mainDispatcher()
        )
        self.holder = holder
        guard let initial = holder.state.value as? SettingsUiState else {
            preconditionFailure("the shared settings holder published no initial state")
        }
        self.state = initial
        holder.start()
        // The shared state is observed, not polled (`DEC-143`): each new value arrives once, on the main
        // actor, and an idle screen wakes nothing.
        lifetime.observe(holder.state, as: SettingsUiState.self) { [weak self] next in
            self?.state = next
        }
    }

    public func onIntent(_ intent: SettingsIntent) {
        holder.onIntent(intent: intent)
    }
}
