@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-097` and `TEST-UNIT-098` — copy keys named, never typed, and one way to fill a template
/// (`DEC-144`, `DEC-123`, `TASK-115`).
///
/// Every iOS screen typed its copy keys as strings, so a typo surfaced only as the key on screen; and
/// templates were filled three ways — `replacingOccurrences`, `String(format:)` with a guessed type, and
/// not at all. The Swift `CopyKey` enum is held to the shared `CopyKeys` list, and `LocalizedCopy` fills
/// every template with typed arguments.
@MainActor
final class CopyKeyParityTests: XCTestCase {
    func test_TEST_UNIT_097_given_the_swift_enum_when_compared_then_it_names_exactly_the_shared_keys() {
        let shared = Set(CopyKeys.shared.all.map { CharacterPresentation.key($0) })
        let swift = Set(CopyKey.allCases.map(\.rawValue))

        XCTAssertEqual(swift.subtracting(shared), [], "a Swift case names a key the shared list does not")
        XCTAssertEqual(shared.subtracting(swift), [], "a shared key has no Swift case")
    }

    func test_TEST_UNIT_098_given_typed_arguments_when_a_template_is_filled_then_each_lands_in_its_specifier() {
        let copy = LocalizedCopy(bundle: .main)

        XCTAssertEqual(
            copy.text(for: .charactersCount, arguments: [.text("826")]),
            "826 characters across the multiverse"
        )
        XCTAssertEqual(
            copy.text(for: .errorMessageRateLimited, arguments: [.number(30)]),
            "Too many jumps. Try again in 30 s.",
            "a number reaches %1$ld as a number"
        )
        XCTAssertEqual(
            copy.text(for: .emptySearchMessage, arguments: [.text("100%")]),
            "No one in this dimension matches “100%”",
            "a text argument is substituted verbatim, even with a percent sign"
        )
        XCTAssertEqual(copy.text(for: .navCharacters, arguments: []), "Characters", "no arguments, the plain text")
    }
}
