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
    private let scope: Kotlinx_coroutines_coreCoroutineScope
    private var observation: Task<Void, Never>?

    public init(
        id: Any,
        header: CharacterCardUi?,
        getDetails: GetCharacterDetails,
        toggleFavorite: ToggleFavorite,
        observeFavoriteIds: ObserveFavoriteIds,

        enrich: Bool
    ) {
        let scope = MultiverseBootstrap.shared.screenScope()
        self.scope = scope
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
        observation = Task { @MainActor [weak self, scope] in
            while !Task.isCancelled {
                guard let self else { break }
                // A `StateFlow` keeps its instance until the value changes, so a new reference is a
                // new state; republishing the same one would re-render the screen every frame.
                if let next = self.holder.state.value as? CharacterDetailUiState, next !== self.state {
                    self.state = next
                }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
            // The holder is gone: its shared scope and every collector in it end with it.
            MultiverseBootstrap.shared.cancelScope(scope: scope)
        }
    }

    public func onIntent(_ intent: CharacterDetailIntent) {
        holder.onIntent(intent: intent)
    }

    deinit {
        // Swift 6 does not allow touching a non-Sendable stored property from a nonisolated
        // `deinit`, so the observation carries the cancellation: ending it cancels the shared scope.
        observation?.cancel()
    }
}
