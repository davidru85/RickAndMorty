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
    /// The screen's one scope and its state observation, ended with the holder (`DEC-143`).
    private let lifetime = ScreenLifetime()

    public init(
        observeFavoriteIds: ObserveFavoriteIds,
        resolveFavoriteCards: ResolveFavoriteCards
    ) {
        let scope = lifetime.scope
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
        // The shared state is observed, not polled (`DEC-143`): each new value arrives once, on the main
        // actor, and an idle screen wakes nothing.
        lifetime.observe(holder.state, as: FavoritesUiState.self) { [weak self] next in
            self?.state = next
        }
    }

    public func onIntent(_ intent: FavoritesIntent) {
        holder.onIntent(intent: intent)
    }
}
