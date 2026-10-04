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
    private let scope: Kotlinx_coroutines_coreCoroutineScope
    private var observation: Task<Void, Never>?

    public init(
        observeAppSettings: ObserveAppSettings,
        updateAppSettings: UpdateAppSettings,
        observeFavoriteIds: ObserveFavoriteIds,
        clearFavorites: ClearFavorites
    ) {
        let scope = MultiverseBootstrap.shared.screenScope()
        self.scope = scope
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
        observation = Task { @MainActor [weak self, scope] in
            while !Task.isCancelled {
                guard let self else { break }
                // A `StateFlow` keeps its instance until the value changes, so a new reference is a
                // new state; republishing the same one would re-render the screen every frame.
                if let next = self.holder.state.value as? SettingsUiState, next !== self.state { self.state = next }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
            // The holder is gone: its shared scope and every collector in it end with it.
            MultiverseBootstrap.shared.cancelScope(scope: scope)
        }
    }

    public func onIntent(_ intent: SettingsIntent) {
        holder.onIntent(intent: intent)
    }

    deinit {
        // Swift 6 does not allow touching a non-Sendable stored property from a nonisolated
        // `deinit`, so the observation carries the cancellation: ending it cancels the shared scope.
        observation?.cancel()
    }
}
