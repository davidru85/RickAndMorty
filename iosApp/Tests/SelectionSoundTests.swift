import AVFoundation
@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-109` and `TEST-UNIT-110` on iOS — the selection sound plays only while Sounds is on, and it
/// is the low-latency form `DEC-162` names (`REQ-FUNC-036`, `AC-REQ-FUNC-036-3`, `AC-REQ-FUNC-036-4`,
/// `TASK-139`).
///
/// The gate is driven with an observation the case controls, so it asserts the rule rather than the shared
/// store, whose delivery `SoundsPreferenceTests` proves. The bundled file is read back from the app bundle:
/// Linear PCM in a CAF file, mono, at 48 kHz, starting with the sound and ending with it — the owner's
/// recording lasts 0.758 s, the sound itself about 0.664 s.
@MainActor
final class SelectionSoundTests: XCTestCase {
    func test_TEST_UNIT_109_given_the_preference_when_it_changes_then_the_sound_plays_only_while_it_is_on() {
        let player = RecordingSelectionSound()
        var deliver: (@MainActor (Bool) -> Void)?
        var ended = false
        var sound: SelectionSound? = SelectionSound(player: player) { onEach in
            deliver = onEach
            return SoundsSubscription { ended = true }
        }

        sound?.play()
        XCTAssertEqual(player.plays, 0, "TEST-UNIT-109: off until the store answers, as on a fresh install")

        deliver?(true)
        sound?.play()
        XCTAssertEqual(player.plays, 1, "TEST-UNIT-109: turned on, the next tap plays")

        deliver?(false)
        sound?.play()
        XCTAssertEqual(player.plays, 1, "TEST-UNIT-109: turned off, the next tap plays nothing")

        sound = nil
        XCTAssertTrue(ended, "TEST-UNIT-109: the observation ends with the sound")
    }

    func test_TEST_UNIT_110_given_the_bundled_sound_when_it_is_read_then_it_is_trimmed_mono_linear_pcm_at_48_khz() throws {
        let url = try XCTUnwrap(
            Bundle.main.url(forResource: "selection_sound", withExtension: "caf"),
            "TEST-UNIT-110: the app bundle carries the selection sound"
        )
        let file = try AVAudioFile(forReading: url)
        let format = file.fileFormat
        XCTAssertEqual(format.settings[AVFormatIDKey] as? UInt32, kAudioFormatLinearPCM, "TEST-UNIT-110: Linear PCM")
        XCTAssertEqual(format.commonFormat, .pcmFormatInt16, "TEST-UNIT-110: 16-bit samples")
        XCTAssertEqual(format.channelCount, 1, "TEST-UNIT-110: mono")
        XCTAssertEqual(format.sampleRate, 48_000, "TEST-UNIT-110: at the 48 kHz the hardware plays")

        let seconds = Double(file.length) / format.sampleRate
        XCTAssertTrue((0.664...0.700).contains(seconds), "TEST-UNIT-110: the sound is whole, its silence gone (\(seconds) s)")

        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)))
        try file.read(into: buffer)
        let channel = try XCTUnwrap(buffer.floatChannelData?[0])
        let samples = UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength))
        // −60 dBFS: below it the recording is silence.
        let audible: (Float) -> Bool = { abs($0) > 0.001 }
        let first = try XCTUnwrap(samples.firstIndex(where: audible))
        let last = try XCTUnwrap(samples.lastIndex(where: audible))
        XCTAssertLessThanOrEqual(Double(first) / format.sampleRate, 0.005, "TEST-UNIT-110: the sound starts at once")
        XCTAssertLessThanOrEqual(
            Double(samples.count - 1 - last) / format.sampleRate,
            0.010,
            "TEST-UNIT-110: and nothing silent follows it"
        )
    }

    func test_TEST_UNIT_110_given_the_player_when_it_is_built_then_the_bundled_sound_is_prepared() {
        XCTAssertTrue(BundledSoundPlayer().isPrepared, "TEST-UNIT-110: the sound is loaded before the first tap")
    }
}

/// A selection sound that counts the plays it is asked for, for the cases that assert when the shell asks.
@MainActor
final class RecordingSelectionSound: SelectionSoundPlaying {
    private(set) var plays = 0

    func play() {
        plays += 1
    }
}
