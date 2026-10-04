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
        observation = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if let next = self.holder.state.value as? SettingsUiState { self.state = next }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }

    public func onIntent(_ intent: SettingsIntent) {
        holder.onIntent(intent: intent)
    }

    deinit {
        // Swift 6 does not allow touching a non-Sendable stored property from a nonisolated
        // `deinit`, so the observation is what carries the cancellation here; the scope's own
        // completion is left to the shared holder's supervisor job.
        observation?.cancel()
    }
}
