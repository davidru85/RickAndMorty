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
        _ = try HostedRendering.render(host)

        let labels = Self.accessibilityLabels(in: host.view)
        XCTAssertTrue(
            labels.contains { $0.contains(LocalizedCopy.shared.text(for: "settings_data_source_body")) },
            "the Data source row says how the app fetches characters; read: \(labels)"
        )
    }

    func test_TEST_UI_040_given_favourites_when_rendered_then_the_delete_action_is_system_red() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: settings(canDelete: true)))

        // System red in the dark appearance is #FF453A; the M3 Error it replaces is #F97758.
        let red = try HostedRendering.matchingPixels(in: image) { red, green, blue in
            red > 230 && green < 90 && blue < 80
        }
        XCTAssertGreaterThan(red.count, 100, "Delete favorites is drawn in system red")
    }

    func test_TEST_UI_040_given_the_sections_when_rendered_then_their_headers_are_inset_16_pt() throws {
        let image = try HostedRendering.render(UIHostingController(rootView: settings(canDelete: false)))

        // A header is Subheadline Emphasized in `Label/Secondary`; with the 16 pt inset nothing of it
        // reaches the strip just inside the panels' leading edge.
        let flush = try HostedRendering.matchingPixels(in: image, region: CGRect(x: 18, y: 150, width: 10, height: 600)) {
            red, green, blue in (140...205).contains(red) && (140...205).contains(green) && blue >= red
        }
        XCTAssertLessThan(flush.count, 20, "no header text starts at the panel's edge")
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

    /// Every accessibility label under [element], depth first.
    private static func accessibilityLabels(in element: NSObject) -> [String] {
        var labels: [String] = []
        if let label = element.accessibilityLabel, !label.isEmpty { labels.append(label) }
        if let elements = element.accessibilityElements as? [NSObject] {
            for child in elements { labels += accessibilityLabels(in: child) }
        } else {
            let count = element.accessibilityElementCount()
            if count != NSNotFound, count > 0 {
                for index in 0..<count {
                    if let child = element.accessibilityElement(at: index) as? NSObject {
                        labels += accessibilityLabels(in: child)
                    }
                }
            }
        }
        if let view = element as? UIView {
            for subview in view.subviews { labels += accessibilityLabels(in: subview) }
        }
        return labels
    }
}
