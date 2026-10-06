import AVFoundation

/// The bundled selection sound (`REQ-FUNC-036`, `AC-REQ-FUNC-036-4`, `DEC-162`).
///
/// `selection_sound.caf` is Linear PCM, so nothing is decoded at a tap, and the player prepares it when it
/// is built — at launch — so a tap starts it from memory. A tap while it is playing restarts it rather than
/// layering a second one, as Android's one-stream pool does.
///
/// The session is **ambient** (`DEC-163`): the sound follows the Ring/Silent switch, so a silenced phone
/// stays silent, and it mixes with whatever else is playing instead of interrupting it.
@MainActor
final class BundledSoundPlayer: SelectionSoundPlaying {
    static let resourceName = "selection_sound"
    static let resourceExtension = "caf"

    private let player: AVAudioPlayer?

    init(bundle: Bundle = .main, session: AVAudioSession = .sharedInstance()) {
        do {
            try session.setCategory(.ambient)
        } catch {
            assertionFailure("the ambient audio session was refused: \(error)")
        }
        guard let url = bundle.url(forResource: Self.resourceName, withExtension: Self.resourceExtension) else {
            assertionFailure("the app bundle carries no \(Self.resourceName).\(Self.resourceExtension)")
            player = nil
            return
        }
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            self.player = player
        } catch {
            assertionFailure("the selection sound could not be opened: \(error)")
            player = nil
        }
    }

    /// Whether the sound is loaded and ready. A missing or unreadable file leaves the app silent in a
    /// release build — the sound is feedback — and fails loudly in a debug one.
    var isPrepared: Bool { player != nil }

    func play() {
        guard let player else { return }
        player.currentTime = 0
        player.play()
    }
}
