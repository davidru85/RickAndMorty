@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UI-018`'s iOS half — every failure message renders with its placeholder resolved
/// (`ERROR_FLOW.md` §4.1, `AC-REQ-FUNC-022-1`, `DEC-123`, `TASK-111`).
///
/// The message comes from the shared formatter and is resolved by the one Swift helper every screen
/// uses, so these cases drive exactly that path: a template whose countdown is missing would otherwise
/// reach the screen with a raw `%1$ld`.
@MainActor
final class FailureMessageFormattingTests: XCTestCase {
    private func rendered(_ failure: ApiFailure) -> String {
        CharacterPresentation.message(DefaultPresentationFormatters.shared.failureMessage(failure: failure))
    }

    func test_TEST_UI_018_given_a_rate_limit_with_advice_when_the_message_renders_then_the_countdown_is_substituted() {
        let text = rendered(ApiFailureRateLimited(retryAfterSeconds: KotlinLong(longLong: 30)))

        XCTAssertEqual(text, "Too many jumps. Try again in 30 s.")
        XCTAssertFalse(text.contains("%"), "no format specifier may reach the screen")
    }

    func test_TEST_UI_018_given_a_rate_limit_without_advice_when_the_message_renders_then_no_placeholder_shows() {
        let text = rendered(ApiFailureRateLimited(retryAfterSeconds: nil))

        XCTAssertEqual(text, "Too many jumps. Try again shortly.")
        XCTAssertFalse(text.contains("%"), "a missing countdown must not leave its specifier behind")
    }

    func test_TEST_UI_018_given_spanish_copy_when_rate_limit_messages_render_then_placeholders_resolve() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "es", ofType: "lproj"), "the app bundle ships es")
        let spanish = LocalizedCopy(bundle: try XCTUnwrap(Bundle(path: path)))
        let formatters = DefaultPresentationFormatters.shared

        let withAdvice = CharacterPresentation.message(
            formatters.failureMessage(failure: ApiFailureRateLimited(retryAfterSeconds: KotlinLong(longLong: 30))),
            copy: spanish
        )
        let withoutAdvice = CharacterPresentation.message(
            formatters.failureMessage(failure: ApiFailureRateLimited(retryAfterSeconds: nil)),
            copy: spanish
        )

        XCTAssertEqual(withAdvice, "Demasiados saltos. Inténtalo de nuevo en 30 s.")
        XCTAssertEqual(withoutAdvice, "Demasiados saltos. Inténtalo de nuevo en un momento.")
    }
}
