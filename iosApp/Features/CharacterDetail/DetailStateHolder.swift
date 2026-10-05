import Foundation
import MultiverseExplorer
import SwiftUI

/// The iOS Detail state holder (`IC-019`, `DESIGN.md` §4.1, `TASK-053`).
///
/// The peer of the Android `CharacterDetailViewModel`: it starts the **shared**
/// `CharacterDetailStateHolder` once, republishes its state and forwards intents. The header
/// retention, the inline retry, the favourite toggle and the enrichment rules all live in the shared
/// holder, so neither platform re-implements them (`CONTRACTS.md` R2).
@MainActor
public final class DetailStateHolder: ObservableObject {
    @Published public private(set) var state: CharacterDetailUiState

    private let holder: CharacterDetailStateHolder
    /// The screen's one scope and its state observation, ended with the holder (`DEC-143`).
    private let lifetime = ScreenLifetime()

    public init(
        id: Any,
        header: CharacterCardUi?,
        getDetails: GetCharacterDetails,
        toggleFavorite: ToggleFavorite,
        observeFavoriteIds: ObserveFavoriteIds,

        enrich: Bool
    ) {
        let scope = lifetime.scope
        let holder = CharacterDetailStateHolder(
            id: id,
            header: header,
            getDetails: getDetails,
            toggleFavorite: toggleFavorite,
            observeFavoriteIds: observeFavoriteIds,
            scope: scope,
            dispatcher: MultiverseBootstrap.shared.mainDispatcher(),
            formatters: DefaultPresentationFormatters.shared,
            enrich: enrich
        )
        self.holder = holder
        guard let initial = holder.state.value as? CharacterDetailUiState else {
            preconditionFailure("the shared detail holder published no initial state")
        }
        self.state = initial
        // Once, as the Android `CharacterDetailViewModel` does: without it nothing loads and the stored
        // favourite set is never observed, so a tap on a stored favourite would remove it.
        holder.start()
        // The shared state is observed, not polled (`DEC-143`): each new value arrives once, on the main
        // actor, and an idle screen wakes nothing.
        lifetime.observe(holder.state, as: CharacterDetailUiState.self) { [weak self] next in
            self?.state = next
        }
    }

    public func onIntent(_ intent: CharacterDetailIntent) {
        holder.onIntent(intent: intent)
    }
}
