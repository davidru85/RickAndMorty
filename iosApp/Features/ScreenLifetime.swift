import Foundation
import MultiverseExplorer

/// One iOS screen's shared-code lifetime (`DEC-143`, `IC-014`): the **one** coroutine scope its state
/// holder runs everything in, and the observers through which the holder hears the shared state.
///
/// A holder keeps one as a stored property. When the holder goes, so does this object, and its
/// `deinit` closes every observer and cancels the scope — the pager, the reducer and every collector
/// in it end together, as `viewModelScope` ends them on Android. It is a class of its own because a
/// `@MainActor` holder's `deinit` may not touch its non-`Sendable` Kotlin values under Swift 6.
final class ScreenLifetime: @unchecked Sendable {
    /// The screen's scope: a supervisor job on the main queue (`MultiverseBootstrap.screenScope`).
    let scope: Kotlinx_coroutines_coreCoroutineScope

    private var observers: [StateObserver<AnyObject>] = []

    init() {
        scope = MultiverseBootstrap.shared.screenScope()
    }

    /// Calls [onEach] with the current value of [flow] and then with each new one, on the main actor,
    /// until this lifetime ends. A value that is not a `T` is ignored.
    @MainActor
    func observe<T>(
        _ flow: Kotlinx_coroutines_coreStateFlow,
        as type: T.Type,
        onEach: @escaping @MainActor (T) -> Void
    ) {
        let observer = StateObserver<AnyObject>(
            flow: flow,
            dispatcher: MultiverseBootstrap.shared.mainDispatcher()
        ) { value in
            guard let value = value as? T else { return }
            // The observer collects on the main queue, so the value already arrives on the main actor.
            MainActor.assumeIsolated { onEach(value) }
        }
        observers.append(observer)
    }

    deinit {
        observers.forEach { $0.close() }
        MultiverseBootstrap.shared.cancelScope(scope: scope)
    }
}
