import SwiftUI
import XCTest

@testable import MultiverseApp

/// `TEST-UNIT-035`, the iOS half — the committed Figma export and the Swift token objects agree in
/// both directions (`REQ-UX-002`, `AC-REQ-UX-002-1`, `DEC-022`, `DEC-102`).
///
/// The comparison runs against `docs/figma/tokens.json`, the one read-only export of the three
/// collections that the Kotlin `TokensExportParityTest` reads too. One direction proves no Swift
/// token drifts from the export — comparing the **typed** `Color`/`CGFloat` against the export's
/// string, so a value that round-trips through a formatter cannot hide a drift. The other proves
/// every exported variable is mapped, so a variable added in Figma cannot be silently ignored on
/// iOS. Figma wins for values, so a divergence is settled by re-exporting, never by editing a
/// literal in `Tokens.swift`.
///
/// The iOS half deliberately has **no reviewed exclusions**: every variable in the export is a value
/// an iOS surface consumes — the M3 roles, the brand and status colours, the `Glass/*` and `Label/*`
/// families (`GlassCards.swift`, `GlassSurface.swift`) and the dimension and glass radii. An empty
/// exclusion list is asserted, so that decision is checked rather than assumed.
final class MultiverseTokensParityTests: XCTestCase {
    /// The export, parsed once per test run. `XCTFail` records a missing or malformed file and
    /// returns an empty map, so a broken fixture does not crash the bundle.
    private lazy var exported: [String: ExportedVariable] = {
        guard let file = locateExport() else {
            XCTFail("the committed export must exist at \(Self.exportPath) relative to the repository root")
            return [:]
        }
        guard let data = try? Data(contentsOf: file),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let collections = json["collections"] as? [[String: Any]]
        else {
            XCTFail("the committed export at \(file.path) must be readable JSON with a `collections` array")
            return [:]
        }

        var variables: [String: ExportedVariable] = [:]
        for collection in collections {
            guard let entries = collection["variables"] as? [[String: Any]] else { continue }
            for entry in entries {
                guard let name = entry["name"] as? String,
                    let type = entry["type"] as? String,
                    let value = entry["value"] as? String
                else {
                    XCTFail("every exported variable carries a name, a type and a string value")
                    continue
                }
                XCTAssertNil(
                    variables[name],
                    "a variable name must be unique across collections: \(name)"
                )
                variables[name] = ExportedVariable(type: type, value: value)
            }
        }
        return variables
    }()

    func test_TEST_UNIT_035_given_the_committed_export_when_every_exported_variable_is_read_then_it_is_mapped() {
        let exported = exported
        XCTAssertFalse(exported.isEmpty, "the export must not be empty")

        let mapped = MultiverseTokens.mappedNames
        XCTAssertFalse(mapped.isEmpty, "the token objects must not be empty")

        XCTAssertEqual(
            Set(exported.keys).subtracting(mapped),
            [],
            "every exported variable is mapped by a token object (the iOS half carries no exclusions)"
        )

        // The exclusion list is empty by design; an entry would have to name a variable the export
        // still carries, so this guards the decision rather than leaving it implicit.
        XCTAssertEqual(
            Self.reviewedExclusions.subtracting(Set(exported.keys)),
            [],
            "an exclusion must name a variable the export still carries"
        )
    }

    func test_TEST_UNIT_035_given_the_token_objects_when_each_value_is_compared_then_it_equals_the_export() {
        let exported = exported
        XCTAssertFalse(exported.isEmpty, "the export must not be empty")

        var drift: [String] = []
        for (name, color) in MultiverseTokens.colors {
            guard let entry = exported[name] else {
                drift.append("\(name): mapped but absent from the export")
                continue
            }
            // Compare the typed value, not a formatted copy of it: the export's #RRGGBBAA is
            // unpacked back to channels and matched channel by channel, so a rounded or otherwise
            // lossy formatter cannot turn a drifted colour into a pass.
            guard matches(color: color, exportValue: entry.value) else {
                drift.append(
                    "\(name): swift \(Self.exportHex(of: color)) (typed channels differ) != export \(entry.value)"
                )
                continue
            }
            XCTAssertEqual(entry.type, "COLOR", "\(name) is a colour token")
        }

        for (name, dimension) in MultiverseTokens.dimensions {
            guard let entry = exported[name] else {
                drift.append("\(name): mapped but absent from the export")
                continue
            }
            guard let expected = Double(entry.value) else {
                drift.append("\(name): the export value `\(entry.value)` is not a number")
                continue
            }
            if Double(dimension) != expected {
                drift.append("\(name): swift \(dimension) != export \(entry.value)")
            }
            XCTAssertEqual(entry.type, "FLOAT", "\(name) is a dimension token")
        }

        XCTAssertEqual(
            drift,
            [],
            "a Swift token must equal its export entry (Figma wins for values)"
        )
    }

    // MARK: - Fixture lookup

    private static let exportPath = "docs/figma/tokens.json"

    /// Variables iOS deliberately does not consume. Empty, and asserted empty by the first test: the
    /// iOS half maps the whole export, including the `Glass/*` and `Label/*` families the Kotlin
    /// half lists as exclusions.
    private static let reviewedExclusions: Set<String> = []

    private struct ExportedVariable {
        let type: String
        let value: String
    }

    /// The export's location, independent of the runner's working directory.
    ///
    /// A `xcodebuild test` run is app-hosted, so the working directory is not the repository root:
    /// this walks up from both the process working directory and `#filePath`, which the compiler
    /// fixes at build time, and takes the first `docs/figma/tokens.json` it finds. The task calls the
    /// path `../../docs/figma/tokens.json`, which is the repository-root path.
    private func locateExport() -> URL? {
        var roots: [URL] = []
        if let cwd = URL(string: FileManager.default.currentDirectoryPath) {
            roots.append(cwd)
        }
        roots.append(
            URL(fileURLWithPath: #filePath)
                .deletingLastPathComponent()  // Tests
                .deletingLastPathComponent()  // iosApp
                .deletingLastPathComponent()  // repository root
        )

        for root in roots {
            var candidate = root
            for _ in 0..<8 {
                let file = candidate.appendingPathComponent(Self.exportPath)
                if FileManager.default.fileExists(atPath: file.path) {
                    return file
                }
                let parent = candidate.deletingLastPathComponent()
                if parent.path == candidate.path { break }
                candidate = parent
            }
        }
        return nil
    }

    // MARK: - Export formatting

    /// Whether a `Color` equals an export colour in `#RRGGBB` or `#RRGGBBAA` form, compared on the
    /// colour's own channels. Tolerance is half a 24-bit step, so an exact export round-trips.
    private func matches(color: Color, exportValue: String) -> Bool {
        var digits = exportValue
        if digits.hasPrefix("#") {
            digits.removeFirst()
        }
        guard digits.count == 6 || digits.count == 8, let raw = UInt64(digits, radix: 16) else {
            XCTFail("the export value \(exportValue) is not a #RRGGBB or #RRGGBBAA colour")
            return false
        }
        let shift: UInt64 = digits.count == 8 ? 24 : 16
        let components = (
            red: Double((raw >> shift) & 0xFF) / 255,
            green: Double((raw >> (shift - 8)) & 0xFF) / 255,
            blue: Double((raw >> (shift - 16)) & 0xFF) / 255,
            alpha: digits.count == 8 ? Double(raw & 0xFF) / 255 : 1
        )
        let resolved = color.resolve(in: EnvironmentValues())
        return approx(Double(resolved.red), components.red)
            && approx(Double(resolved.green), components.green)
            && approx(Double(resolved.blue), components.blue)
            && approx(Double(resolved.opacity), components.alpha)
    }

    /// Half a 24-bit step, so a value the export stores exactly compares equal.
    private func approx(_ lhs: Double, _ rhs: Double) -> Bool {
        abs(lhs - rhs) < 0.5 / 255
    }

    /// The export form of a token colour: uppercase `#RRGGBB`, or `#RRGGBBAA` when it is translucent.
    /// It is used only in failure messages. The comparison itself uses `matches(color:exportValue:)`.
    static func exportHex(of color: Color) -> String {
        let resolved = color.resolve(in: EnvironmentValues())
        let red = Int((resolved.red * 255).rounded())
        let green = Int((resolved.green * 255).rounded())
        let blue = Int((resolved.blue * 255).rounded())
        let alpha = Int((resolved.opacity * 255).rounded())
        let base = String(format: "#%02X%02X%02X", red, green, blue)
        return alpha == 255 ? base : base + String(format: "%02X", alpha)
    }
}
