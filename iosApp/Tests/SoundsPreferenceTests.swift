@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-109`, the iOS half of the preference (`REQ-FUNC-036`, `AC-REQ-FUNC-036-3`, `TASK-139`,
/// `DEC-162`).
///
/// The iOS selection sound decides from the shared Sounds preference (`IC-021`), which only Kotlin can
/// observe; `core/ios` hands it to Swift. The case drives the change the way the user does — the Sounds
/// toggle's intent through the Settings state holder — over the app's own graph, and expects the stored
/// value first and then each change, with no restart. It restores the stored value before it ends.
@MainActor
final class SoundsPreferenceTests: XCTestCase {
    private func settle() {
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }

    func test_TEST_UNIT_109_given_the_shared_preference_when_sounds_is_toggled_then_swift_observes_each_value() {
        var delivered: [Bool] = []
        let observation = MultiverseBootstrap.shared.observeSoundsEnabled { delivered.append($0.boolValue) }
        defer { observation.close() }
        settle()
        guard let stored = delivered.last else {
            return XCTFail("TEST-UNIT-109: the stored value is delivered first")
        }

        let resolved = MultiverseBootstrap.shared.settingsDependencies()
        let settings = SettingsStateHolder(
            observeAppSettings: resolved.observeAppSettings,
            updateAppSettings: resolved.updateAppSettings,
            observeFavoriteIds: resolved.observeFavoriteIds,
            clearFavorites: resolved.clearFavorites
        )
        settings.onIntent(SettingsScreen.soundsIntent(enabled: !stored))
        settle()
        XCTAssertEqual(delivered.last, !stored, "TEST-UNIT-109: a change in Settings reaches Swift at once")

        settings.onIntent(SettingsScreen.soundsIntent(enabled: stored))
        settle()
        XCTAssertEqual(delivered.last, stored, "TEST-UNIT-109: and so does the change back")
    }
}
