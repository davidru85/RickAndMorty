import Foundation
import MultiverseExplorer
import SwiftUI

/// The iOS Favorites state holder (`IC-020`, `DESIGN.md` §4.1, `TASK-053`).
///
/// The peer of the Android `FavoritesViewModel`: the stored-set observation, the card resolution, the
/// empty-versus-loading precedence (`CONF-79`) and the deterministic item order all live in the
/// shared `FavoritesStateHolder`, so neither platform re-derives them (`CONTRACTS.md` R2).
@MainActor
public final class FavoritesStateHolder: ObservableObject {
    @Published public private(set) var state: FavoritesUiState

    private let holder: MultiverseExplorer.FavoritesStateHolder
    private let scope: Kotlinx_coroutines_coreCoroutineScope
    private var observation: Task<Void, Never>?

    public init(
        observeFavoriteIds: ObserveFavoriteIds,
        resolveFavoriteCards: ResolveFavoriteCards
    ) {
        let scope = MultiverseBootstrap.shared.screenScope()
        self.scope = scope
        let holder = MultiverseExplorer.FavoritesStateHolder(
            observeFavoriteIds: observeFavoriteIds,
            resolveFavoriteCards: resolveFavoriteCards,
            scope: scope,
            dispatcher: MultiverseBootstrap.shared.mainDispatcher(),
            formatters: DefaultPresentationFormatters.shared
        )
        self.holder = holder
        guard let initial = holder.state.value as? FavoritesUiState else {
            preconditionFailure("the shared favorites holder published no initial state")
        }
        self.state = initial
        holder.start()
        observation = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if let next = self.holder.state.value as? FavoritesUiState { self.state = next }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }

    public func onIntent(_ intent: FavoritesIntent) {
        holder.onIntent(intent: intent)
    }

    deinit {
        // Swift 6 does not allow touching a non-Sendable stored property from a nonisolated
        // `deinit`, so the observation is what carries the cancellation here; the scope's own
        // completion is left to the shared holder's supervisor job.
        observation?.cancel()
    }
}
