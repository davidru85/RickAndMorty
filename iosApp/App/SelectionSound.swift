import Foundation
import MultiverseExplorer

/// What plays the selection sound of `REQ-FUNC-036` (`DEC-162`). The shell asks it to play on each
/// selection the user makes — a change of destination on the tab bar, and every tap of the Discovery
/// status selector — and the implementation decides what plays, and whether.
@MainActor
protocol SelectionSoundPlaying: AnyObject {
    func play()
}

/// Plays [player] only while the Sounds preference is on (`REQ-FUNC-036`, `AC-REQ-FUNC-036-3`), as the
/// Android shell's gate does.
///
/// The preference starts from the fresh-install value, off (`AC-REQ-FUNC-033-2`), so a tap before the
/// shared store has answered plays nothing; after that it is the last value `core/ios` delivered, so a
/// change made in Settings applies from the next tap, without a restart. The observation ends with this
/// object.
@MainActor
final class SelectionSound: SelectionSoundPlaying {
    /// Starts observing the preference, delivering each value on the main actor, until the returned
    /// subscription goes.
    typealias Observe = @MainActor (_ onEach: @escaping @MainActor (Bool) -> Void) -> SoundsSubscription

    private let player: any SelectionSoundPlaying
    private var isEnabled = false
    private var subscription: SoundsSubscription?

    init(player: any SelectionSoundPlaying, observe: Observe = SelectionSound.observeSharedPreference) {
        self.player = player
        subscription = observe { [weak self] enabled in
            self?.isEnabled = enabled
        }
    }

    func play() {
        if isEnabled { player.play() }
    }

    /// The shared Sounds preference (`IC-021`), which `core/ios` delivers on the main queue.
    static func observeSharedPreference(_ onEach: @escaping @MainActor (Bool) -> Void) -> SoundsSubscription {
        let observation = MultiverseBootstrap.shared.observeSoundsEnabled { enabled in
            MainActor.assumeIsolated { onEach(enabled.boolValue) }
        }
        return SoundsSubscription { observation.close() }
    }
}

/// Ends an observation when it goes. A class of its own, as `ScreenLifetime` is, because a `@MainActor`
/// owner's `deinit` may not touch its non-`Sendable` Kotlin values under Swift 6.
final class SoundsSubscription: @unchecked Sendable {
    private let cancel: () -> Void

    init(cancel: @escaping () -> Void) {
        self.cancel = cancel
    }

    deinit {
        cancel()
    }
}
