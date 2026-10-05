@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-027` — the iOS debug diagnostics sheet (`REQ-OBS-002`, `AC-REQ-OBS-002-1`,
/// `OBSERVABILITY.md` §5, `DEC-147`, `TASK-119`).
///
/// A Debug build attaches `:core:diagnostics`' recorder to the shared logger and shows its rows — the
/// last failure and the data source first — in a read-only sheet; a Release build attaches no recorder,
/// and the sheet and its entry are not compiled into it at all. The case asserts the half its own
/// configuration compiles, so it runs in both.
@MainActor
final class DiagnosticsSheetTests: XCTestCase {
    func test_TEST_UI_027_given_this_build_when_diagnostics_are_observed_then_only_a_debug_build_has_them() {
        var delivered: [DiagnosticsLine] = []
        let observation = MultiverseBootstrap.shared.observeDiagnostics { delivered = $0 }
        defer { observation?.close() }

        #if DEBUG
            XCTAssertNotNil(observation, "a Debug build attaches the recorder")
            RunLoop.main.run(until: Date().addingTimeInterval(0.3))
            XCTAssertEqual(delivered.first?.label, "last failure", "the last failure comes first (REQ-OBS-002)")
            XCTAssertTrue(delivered.contains { $0.label == "data source" }, "and the data source is shown")
        #else
            XCTAssertNil(observation, "a Release build attaches no recorder (AC-REQ-OBS-002-1)")
            XCTAssertTrue(delivered.isEmpty)
        #endif
    }

    #if DEBUG
        func test_TEST_UI_027_given_the_sheet_when_it_opens_then_it_lists_every_row() throws {
            let model = DiagnosticsSheetModel()
            RunLoop.main.run(until: Date().addingTimeInterval(0.3))

            XCTAssertEqual(model.rows.count, 9, "every item of OBSERVABILITY.md §5, one row each")
            let image = try HostedRendering.render(UIHostingController(rootView: DiagnosticsSheet(model: model)))
            let text = try HostedRendering.nearWhitePixels(in: image)
            XCTAssertGreaterThan(text, 1_000, "the sheet paints its rows")
        }
    #endif
}
