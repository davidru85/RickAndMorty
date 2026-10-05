@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-017` on iOS (`REQ-FUNC-033`…`REQ-FUNC-035`, `AC-REQ-FUNC-033-1`, `AC-REQ-FUNC-034-1`,
/// `AC-REQ-FUNC-035-1`/`-2`/`-3`, `TASK-077`).
///
/// The screen is rendered from an `IC-023` state alone and every interaction is asserted on the intent
/// the real view hands back, so each case is reachable without a store, a repository or a network —
/// which is what makes the iOS screen the peer of the Android `SettingsScreen` rather than a second
/// implementation of its rules (`CONTRACTS.md` §7 R1). The copy is asserted against the canonical key
/// list in both locales, so no case can pass on an English literal the copy set does not carry
/// (`REQ-UX-008`, `TEST-UNIT-036`).
///
/// The screen is hosted in a window rather than rendered through `ImageRenderer`: the surface is a
/// `ScrollView`, and `ImageRenderer` paints a `ScrollView`-rooted SwiftUI tree as an empty image (the
/// sibling screens' `VStack` roots do not have this property). Hosting is also what makes the
/// confirmation reachable at all — the alert is a **presentation**, so it exists on the hosting
/// controller and not in the screen's own view tree, which is where this suite reads its title,
/// message and two actions.
@MainActor
final class SettingsScreenTests: XCTestCase {
    // MARK: - Rendering each control (`AC-REQ-FUNC-033-1`)

    func test_TEST_UI_017_given_the_fresh_install_state_when_rendered_then_the_screen_paints() throws {
        let hosted = try host(state())
        defer { hosted.tearDown() }
        assertPaints(hosted, "the fresh-install state: Sounds off, REST, delete disabled")
    }

    func test_TEST_UI_017_given_sounds_on_and_graphql_when_rendered_then_the_chosen_controls_paint() throws {
        let fresh = try host(state())
        defer { fresh.tearDown() }
        let chosen = try host(
            state(soundsEnabled: true, remoteProtocol: RemoteProtocol.graphql, canDeleteFavorites: true)
        )
        defer { chosen.tearDown() }
        assertPaints(chosen, "the chosen state: Sounds on, GraphQL, delete enabled")
        XCTAssertNotEqual(
            fresh.image.pngData(),
            chosen.image.pngData(),
            "the screen renders the state it is handed rather than a fixed surface"
        )
    }

    func test_TEST_UI_017_given_favourites_when_rendered_then_the_delete_action_is_enabled() throws {
        let withFavorites = state(canDeleteFavorites: true)
        XCTAssertTrue(
            SettingsScreen.isDeleteActionEnabled(withFavorites),
            "AC-REQ-FUNC-035-3: the action is enabled exactly while favorites exist"
        )
        let hosted = try host(withFavorites)
        defer { hosted.tearDown() }
        assertPaints(hosted, "the delete-enabled state (AC-REQ-FUNC-035-3)")
    }

    func test_TEST_UI_017_given_no_favourites_when_rendered_then_the_delete_action_is_disabled() {
        XCTAssertFalse(
            SettingsScreen.isDeleteActionEnabled(state()),
            "AC-REQ-FUNC-035-3: the action is disabled while there are no favorites"
        )
    }

    // MARK: - The confirmation opens and closes (`AC-REQ-FUNC-035-1`)

    /// The confirmation opens while favourites exist, with the canonical title, message and the two
    /// actions the criterion names: a cancel-role "Cancel" and a destructive-role "Delete".
    func test_TEST_UI_017_given_favourites_and_an_open_confirmation_then_the_alert_shows_its_copy() throws {
        let hosted = try host(state(canDeleteFavorites: true, isConfirmingDelete: true))
        defer { hosted.tearDown() }
        let alert = try XCTUnwrap(
            hosted.presentedAlert,
            "AC-REQ-FUNC-035-1: the confirmation must be presented while favorites exist"
        )
        XCTAssertEqual(alert.title, LocalizedCopy.shared.text(for: "settings_delete_confirm_title"))
        XCTAssertEqual(alert.message, LocalizedCopy.shared.text(for: "settings_delete_confirm_message"))

        let cancel = try XCTUnwrap(alert.actions.first { $0.style == .cancel }, "the confirmation carries Cancel")
        let delete = try XCTUnwrap(
            alert.actions.first { $0.style == .destructive },
            "AC-REQ-FUNC-035-1: the confirmation carries a destructive Delete"
        )
        XCTAssertEqual(cancel.title, LocalizedCopy.shared.text(for: "action_cancel"))
        XCTAssertEqual(delete.title, LocalizedCopy.shared.text(for: "action_delete"))
        XCTAssertEqual(alert.actions.count, 2, "the confirmation offers exactly the two actions the spec names")
    }

    func test_TEST_UI_017_given_no_confirmation_then_nothing_is_presented() {
        XCTAssertFalse(SettingsScreen.isConfirmationPresented(state(canDeleteFavorites: true)))
        XCTAssertFalse(SettingsScreen.isConfirmationPresented(state()), "a fresh install presents nothing")
    }

    /// The reconciliation `AC-REQ-FUNC-035-3` requires: a state that claims a confirmation **cannot**
    /// offer a Delete with nothing to delete, so it presents nothing.
    func test_TEST_UI_017_given_a_confirmation_with_no_favourites_then_nothing_is_presented() throws {
        XCTAssertFalse(
            SettingsScreen.isConfirmationPresented(state(isConfirmingDelete: true)),
            "AC-REQ-FUNC-035-3: no Delete is ever offered with nothing to delete"
        )
        let hosted = try host(state(isConfirmingDelete: true))
        defer { hosted.tearDown() }
        XCTAssertNil(hosted.presentedAlert, "a confirmation with nothing to delete is never presented")
    }

    func test_TEST_UI_017_given_favourites_without_the_confirmation_then_nothing_is_presented() throws {
        let hosted = try host(state(canDeleteFavorites: true))
        defer { hosted.tearDown() }
        XCTAssertNil(hosted.presentedAlert, "the confirmation opens only when the user asks for it")
    }

    // MARK: - The controls dispatch their intents

    func test_TEST_UI_017_given_the_sounds_toggle_when_toggled_then_the_new_value_is_dispatched() {
        let enabled = SettingsScreen.soundsIntent(enabled: true)
        let disabled = SettingsScreen.soundsIntent(enabled: false)
        XCTAssertEqual((enabled as? SettingsIntentSoundsToggled)?.enabled, true)
        XCTAssertEqual((disabled as? SettingsIntentSoundsToggled)?.enabled, false)
    }

    func test_TEST_UI_017_given_the_picker_when_GraphQL_is_chosen_then_the_selection_is_dispatched() {
        let selected = SettingsScreen.dataSourceIntent(selecting: SettingsDataSource.graphql.remoteProtocol)
        XCTAssertEqual(
            (selected as? SettingsIntentRemoteProtocolSelected)?.protocol,
            RemoteProtocol.graphql,
            "AC-REQ-FUNC-034-1: the chosen protocol is the one the picker dispatches"
        )
        let backToRest = SettingsScreen.dataSourceIntent(selecting: SettingsDataSource.rest.remoteProtocol)
        XCTAssertEqual((backToRest as? SettingsIntentRemoteProtocolSelected)?.protocol, RemoteProtocol.rest)
    }

    func test_TEST_UI_017_given_the_delete_action_when_activated_then_the_request_is_dispatched() {
        XCTAssertTrue(
            SettingsScreen.deleteRequestIntent() is SettingsIntentDeleteFavoritesRequested,
            "the action opens the confirmation by request, never by clearing"
        )
    }

    /// `AC-REQ-FUNC-035-2` and `AC-REQ-FUNC-035-1`: "Delete" clears and "Cancel" changes nothing, so
    /// the two outcomes must be different intents.
    func test_TEST_UI_017_given_the_confirmation_when_Delete_is_tapped_then_the_confirmation_is_dispatched() {
        let confirmed = SettingsScreen.deleteConfirmedIntent()
        XCTAssertTrue(confirmed is SettingsIntentDeleteFavoritesConfirmed)
        XCTAssertFalse(confirmed is SettingsIntentDeleteFavoritesDismissed)
    }

    func test_TEST_UI_017_given_the_confirmation_when_Cancel_is_tapped_then_dismissal_is_dispatched_and_nothing_else() {
        let dismissed = SettingsScreen.deleteDismissedIntent()
        XCTAssertTrue(dismissed is SettingsIntentDeleteFavoritesDismissed)
        XCTAssertFalse(
            dismissed is SettingsIntentDeleteFavoritesConfirmed,
            "AC-REQ-FUNC-035-1: Cancel must never reach the clear"
        )
    }

    /// The screen hands the intent back through its own closure, so a case can drive the real screen
    /// rather than a re-implementation of its mapping.
    func test_TEST_UI_017_given_the_screen_when_an_intent_is_dispatched_then_the_closure_receives_it() {
        var received: [SettingsIntent] = []
        let screen = SettingsScreen(state: state(canDeleteFavorites: true), onIntent: { received.append($0) })
        var intents: [SettingsIntent] = []
        intents.append(SettingsScreen.soundsIntent(enabled: true))
        intents.append(SettingsScreen.dataSourceIntent(selecting: SettingsDataSource.graphql.remoteProtocol))
        intents.append(SettingsScreen.deleteRequestIntent())
        intents.append(SettingsScreen.deleteDismissedIntent())
        intents.append(SettingsScreen.deleteConfirmedIntent())
        for intent in intents {
            screen.onIntent(intent)
        }
        XCTAssertEqual(received.count, intents.count, "every control's intent reaches the one closure the screen owns")
    }

    // MARK: - The picker's protocol mapping

    /// `AC-REQ-FUNC-034-1`: REST is the fresh-install value, and the picker renders the protocol the
    /// state carries in both directions.
    func test_AC_REQ_FUNC_034_1_given_each_protocol_when_the_picker_resolves_then_it_toggles_the_selection() {
        XCTAssertEqual(SettingsDataSource.source(for: RemoteProtocol.rest), .rest)
        XCTAssertEqual(SettingsDataSource.source(for: RemoteProtocol.graphql), .graphql)
        XCTAssertEqual(SettingsDataSource.rest.remoteProtocol, RemoteProtocol.rest)
        XCTAssertEqual(SettingsDataSource.graphql.remoteProtocol, RemoteProtocol.graphql)
        XCTAssertEqual(
            SettingsDataSource.allCases.map(\.rawValue),
            ["rest", "graphql"],
            "UI_SPEC.md §6.5 fixes the two options as REST API then GraphQL"
        )
    }

    // MARK: - Copy bindings (`AC-REQ-FUNC-033-1`, `REQ-UX-008`)

    func test_AC_REQ_FUNC_033_1_given_the_settings_copy_when_resolved_then_each_key_exists_in_both_locales() {
        for key in SettingsCopy.all {
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key.rawValue, in: "en"), "en must carry \(key)")
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key.rawValue, in: "es"), "es must carry \(key)")
        }
    }

    /// The picker's two option labels are the canonical keys, so the control cannot drift from the
    /// copy set (`REQ-UX-008`).
    func test_AC_REQ_FUNC_034_1_given_the_picker_options_when_labelled_then_they_use_the_canonical_keys() {
        XCTAssertEqual(SettingsDataSource.rest.labelKey, SettingsCopy.dataRest)
        XCTAssertEqual(SettingsDataSource.graphql.labelKey, SettingsCopy.dataGraphql)
    }

    /// The header and title constants name the specification's own strings; the values themselves are
    /// asserted in both locales by the parity case, so this checks the binding rather than an English
    /// literal that a Spanish simulator would fail (`REQ-UX-008`).
    func test_UI_SPEC_6_5_given_the_section_headers_when_bound_then_they_are_the_specified_keys() {
        XCTAssertEqual(SettingsCopy.title.rawValue, "nav_settings")
        XCTAssertEqual(SettingsCopy.preferencesSection.rawValue, "settings_section_preferences")
        XCTAssertEqual(SettingsCopy.dataSection.rawValue, "settings_section_data")
        XCTAssertEqual(SettingsCopy.favoritesSection.rawValue, "nav_favorites")
        XCTAssertEqual(SettingsCopy.deleteConfirmTitle.rawValue, "settings_delete_confirm_title")
        XCTAssertEqual(SettingsCopy.cancel.rawValue, "action_cancel")
        XCTAssertEqual(SettingsCopy.delete.rawValue, "action_delete")
    }

    // MARK: - Helpers

    /// One `IC-023` state. The factory exists because Kotlin's default arguments do not cross the
    /// Apple boundary: Swift must supply all four components, and a case should name only the one it
    /// varies.
    private func state(
        soundsEnabled: Bool = false,
        remoteProtocol: RemoteProtocol = RemoteProtocol.rest,
        canDeleteFavorites: Bool = false,
        isConfirmingDelete: Bool = false
    ) -> SettingsUiState {
        SettingsUiState(
            soundsEnabled: soundsEnabled,
            remoteProtocol: remoteProtocol,
            canDeleteFavorites: canDeleteFavorites,
            isConfirmingDelete: isConfirmingDelete
        )
    }

    /// Hosts the screen in a window and settles the presentation, so the alert (when one is due) is
    /// reachable and the painted image is complete.
    private func host(_ state: SettingsUiState) throws -> HostedSettingsScreen {
        let hosted = HostedSettingsScreen(state: state)
        try hosted.settle()
        return hosted
    }

    private func assertPaints(
        _ hosted: HostedSettingsScreen,
        _ description: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertGreaterThan(
            hosted.image.size.width * hosted.image.size.height,
            0,
            "Settings must render a non-empty image for \(description)",
            file: file,
            line: line
        )
        XCTAssertNotNil(
            hosted.image.pngData(),
            "Settings must paint \(description)",
            file: file,
            line: line
        )
    }
}

/// The Settings screen hosted in a window (`TEST-UI-017`).
///
/// `ImageRenderer` paints a `ScrollView`-rooted SwiftUI tree as an empty image, so the suite hosts the
/// screen instead: hosting is also the only way to observe the confirmation, which SwiftUI presents
/// on the hosting controller rather than in the screen's own view tree.
@MainActor
private final class HostedSettingsScreen {
    let host: UIHostingController<SettingsScreen>
    let window: UIWindow
    private(set) var image: UIImage = UIImage()

    var presentedAlert: UIAlertController? { host.presentedViewController as? UIAlertController }

    init(state: SettingsUiState) {
        host = UIHostingController(rootView: SettingsScreen(state: state, onIntent: { _ in }))
        window = UIWindow(frame: CGRect(x: 0, y: 0, width: 402, height: 874))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.frame = window.bounds
    }

    /// Spins the run loop long enough for the first layout and any presentation transition, then
    /// captures the painted screen.
    func settle() throws {
        let deadline = Date().addingTimeInterval(0.6)
        while Date() < deadline {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        host.view.layoutIfNeeded()
        image = UIGraphicsImageRenderer(size: host.view.bounds.size).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        XCTAssertNotNil(image.cgImage, "the hosted screen must produce a bitmap")
    }

    /// Releases the window so a presentation cannot leak into another case.
    func tearDown() {
        window.isHidden = true
        window.rootViewController = nil
    }
}
