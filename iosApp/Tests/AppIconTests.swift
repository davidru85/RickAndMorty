import XCTest

/// `TEST-UNIT-104` — the iOS app icon (`UI_SPEC.md` §10.2, `REQ-UX-002`, `DEC-150`, `TASK-122`).
///
/// The asset catalog compiler names the app icon in the built app's `Info.plist` only when it compiled
/// one, so the declaration is what tells the Home Screen to show the brand rather than the system
/// placeholder. The case reads the host app's own bundle, so it checks the app that actually ran.
final class AppIconTests: XCTestCase {
    func test_TEST_UNIT_104_given_the_built_app_when_its_icons_are_read_then_it_names_its_icon() throws {
        let declared = Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any]
        let icons = try XCTUnwrap(declared, "the app declares no icon")
        let primary = try XCTUnwrap(icons["CFBundlePrimaryIcon"] as? [String: Any])
        XCTAssertEqual(primary["CFBundleIconName"] as? String, "AppIcon")
    }
}
