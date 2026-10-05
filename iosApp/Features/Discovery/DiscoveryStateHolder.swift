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
/// The holder is hand-written, as `DEC-013` requires: it owns the screen's one scope through its
/// `ScreenLifetime`, and it hears the reducer's state through the shared `StateObserver`, republishing
/// each new value so SwiftUI can read it (`DEC-143`). The scope comes from `MultiverseBootstrap`,
/// because Kotlin/Native exports `CoroutineScope` as a protocol and Swift cannot construct one.
@MainActor
public final class DiscoveryStateHolder: ObservableObject {
    /// What the screen renders; the view holds no state of its own.
    @Published public private(set) var state: CharacterListUiState

    private let reducer: DiscoveryReducer
    /// The screen's one scope and its state observation, ended with the holder (`DEC-143`).
    private let lifetime = ScreenLifetime()

    /// Builds the holder around the pager and the initial filter the shell resolved for this screen.
    public init(
        pager: CharacterPager,
        initialFilter: CharacterFilter
    ) {
        let scope = lifetime.scope
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
        // The shared state is observed, not polled (`DEC-143`): each new value arrives once, on the main
        // actor, and an idle screen wakes nothing.
        lifetime.observe(reducer.state, as: CharacterListUiState.self) { [weak self] next in
            self?.state = next
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
}
