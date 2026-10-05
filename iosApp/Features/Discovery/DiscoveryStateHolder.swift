import Foundation
import MultiverseExplorer
import SwiftUI

/// The iOS Discovery state holder (`DESIGN.md` §4.1, `IC-018`, `TASK-053`).
///
/// It is the exact peer of the Android `DiscoveryViewModel`: a thin adapter over the **shared**
/// `DiscoveryReducer`, which owns every rule — the 300 ms debounce, the cancellation of a superseded
/// request, the page-1 reset and the `IC-018` precedence. Nothing here re-derives a display value or
/// a page-1 reset, because a second implementation is what `CONTRACTS.md` R2 and `REQ-PLAT-001`
/// forbid; the state the view reads is the shared `CharacterListUiState`.
///
/// There is no `StateFlow`-to-Swift bridge (`DEC-013`): the holder owns the coroutine scope the
/// reducer runs in, and it republishes each emission so SwiftUI can read it. The scope comes from
/// `MultiverseBootstrap`, because Kotlin/Native exports `CoroutineScope` as a protocol and Swift
/// cannot construct one.
@MainActor
public final class DiscoveryStateHolder: ObservableObject {
    /// What the screen renders; the view holds no state of its own.
    @Published public private(set) var state: CharacterListUiState

    private let reducer: DiscoveryReducer
    private let scope: Kotlinx_coroutines_coreCoroutineScope
    private var observation: Task<Void, Never>?

    /// Builds the holder around the pager and the initial filter the shell resolved for this screen.
    public init(
        pager: CharacterPager,
        initialFilter: CharacterFilter
    ) {
        let scope = MultiverseBootstrap.shared.screenScope()
        self.scope = scope
        let reducer = DiscoveryReducer(
            pager: pager,
            scope: scope,
            dispatcher: MultiverseBootstrap.shared.mainDispatcher(),
            formatters: DefaultPresentationFormatters.shared,
            debounce: DiscoveryReducer.companion.DEFAULT_DEBOUNCE
        )
        self.reducer = reducer
        // The shared reducer publishes its initial state eagerly, so the value is present before the
        // first suspension. The observation below takes over from here.
        guard let initial = reducer.state.value as? CharacterListUiState else {
            preconditionFailure("the shared reducer published no initial state")
        }
        self.state = initial
        // The first load is started exactly as the Android holder's `init` starts it, through the
        // shared reducer, so a page-1 reset or a debounce rule cannot differ between platforms. Without
        // it the screen stays on its initial `Loading` state (`DiscoveryStateHolderTests`, `GAP-031`).
        _ = reducer.start(initialFilter: initialFilter)
        // `reducer.state` is a `StateFlow`, which Kotlin/Native exports without `AsyncSequence`
        // conformance, so it is read by polling rather than collected.
        observation = Task { [weak self, scope] in
            while !Task.isCancelled {
                guard let self else { break }
                // A `StateFlow` keeps its instance until the value changes, so a new reference is a
                // new state; republishing the same one would re-render the screen every frame.
                if let next = self.reducer.state.value as? CharacterListUiState, next !== self.state {
                    self.state = next
                }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
            // The holder is gone: the reducer's scope and its intent loop end with it.
            MultiverseBootstrap.shared.cancelScope(scope: scope)
        }
    }

    /// The only write path: every view interaction arrives here as a `CharacterListIntent`.
    public func onIntent(_ intent: CharacterListIntent) {
        reducer.onIntent(intent: intent)
    }

    /// The manual refresh `.refreshable` runs (`REQ-FUNC-012`, `DEC-134`): it starts the shared
    /// reducer's refresh and returns when that refresh ends — the moment `isRefreshing` clears — so
    /// the system spinner holds exactly as long as the network revalidation, with no state polled.
    public func refresh() async {
        let job = reducer.refresh()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            _ = job.invokeOnCompletion { _ in continuation.resume() }
        }
    }

    deinit {
        // Swift 6 does not allow a nonisolated `deinit` to touch a non-Sendable stored property, so
        // the observation carries the cancellation: ending it cancels the reducer's scope.
        observation?.cancel()
    }
}
