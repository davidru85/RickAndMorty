@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import UIKit
import XCTest

/// `TEST-UI-040` — the iOS Settings as Figma `102:322`/`123:529` draw it (`UI_SPEC.md` §4.2, §6.5,
/// `CONF-80`, `TASK-114`).
///
/// The Data source row had no subtitle, the destructive action used the M3 Error `#F97758` where the
/// iOS kit's destructive row is system red, and the section headers sat flush with the panels instead
/// of 16 pt in.
@MainActor
final class SettingsFidelityTests: XCTestCase {
    func test_TEST_UI_040_given_the_data_section_when_rendered_then_the_data_source_row_has_its_subtitle() throws {
        let host = UIHostingController(rootView: settings(canDelete: false))
        let image = try HostedRendering.render(host)

        // The picker is UIKit's segmented control; the Data source row sits right above it, and its
        // subtitle is the only `Label/Secondary` text there — the title above it is primary white.
        let picker = try XCTUnwrap(Self.find(UISegmentedControl.self, in: host.view), "the data-source picker renders")
        let frame = picker.convert(picker.bounds, to: nil)
        let subtitle = try HostedRendering.matchingPixels(
            in: image,
            region: CGRect(x: 60, y: frame.minY - 34, width: 280, height: 30)
        ) { red, green, blue in Self.isSecondaryLabel(red, green, blue) }
        XCTAssertGreaterThan(subtitle.count, 150, "the Data source row says how the app fetches characters")
    }

    func test_TEST_UI_040_given_favourites_when_rendered_then_the_delete_action_is_system_red() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: settings(canDelete: true)))

        // System red in the dark appearance is #FF453A; the M3 Error it replaces is #F97758.
        let red = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            red > 230 && green < 90 && blue < 80
        }
        XCTAssertGreaterThan(red.count, 100, "Delete favorites is drawn in system red")
    }

    func test_TEST_UI_040_given_a_section_when_rendered_then_its_header_is_inset_16_pt() throws {
        // The section alone, 370 pt wide and centred, so its panel's leading edge is at x = 16, over
        // plain black, so the header is the only light text on the screen.
        let section = SettingsSection(header: "Preferences") { Color.clear.frame(height: 68) }
            .frame(width: MultiverseDimensions.glassContainerWidth)
            .padding(.top, 100)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.black)
            .environment(\.multiverseGlassPath, .material)
        let image = try HostedRendering.render(UIHostingController(rootView: section))

        // The header is the topmost light thing on the screen; measured in its own 16 pt strip, so the
        // highlight on the panel's rounded corner below it does not count.
        let light = { (red: Int, green: Int, blue: Int) in red > 140 && green > 140 && blue > 140 }
        let top = try XCTUnwrap(try HostedRendering.matchingPixels(in: image, matches: light).bounds, "the header is painted")
        let header = try HostedRendering.matchingPixels(
            in: image,
            region: CGRect(x: 0, y: top.minY, width: 402, height: 16),
            matches: light
        )
        let bounds = try XCTUnwrap(header.bounds)
        XCTAssertEqual(bounds.minX, 32, accuracy: 2, "the header starts 16 pt in from the panel's edge (Figma 123:374)")
    }

    private func settings(canDelete: Bool) -> some View {
        SettingsScreen(
            state: SettingsUiState(
                soundsEnabled: false,
                remoteProtocol: RemoteProtocol.rest,
                canDeleteFavorites: canDelete,
                isConfirmingDelete: false
            ),
            onIntent: { _ in }
        )
        .environment(\.multiverseGlassPath, .material)
    }

    /// `Label/Secondary` (#EBEBF5 at 68 %) over the dark glass: a light grey with a blue lean, which the
    /// neutral grey of a panel's rim does not have.
    private static func isSecondaryLabel(_ red: Int, _ green: Int, _ blue: Int) -> Bool {
        (120...215).contains(red) && (120...215).contains(green) && blue - red >= 4
    }

    private static func find<T: UIView>(_ type: T.Type, in view: UIView) -> T? {
        if let match = view as? T { return match }
        for subview in view.subviews {
            if let match = find(type, in: subview) { return match }
        }
        return nil
    }
}
