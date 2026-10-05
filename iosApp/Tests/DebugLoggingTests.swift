@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-073` — the iOS build's request-log threshold follows the compiled variant (`DEC-039`,
/// `DEC-127`, `OBSERVABILITY.md` §5, `TASK-116`).
///
/// A Debug build links the debug Kotlin framework and must let the `DEBUG` and `INFO` request events
/// of both protocols (`LOG-001`, `LOG-002`) reach the unified log, as the Android debug variant does in
/// Logcat; a Release build links the release framework and keeps `ERROR` only. The case reads the
/// logger the shared graph binds, and asserts the half its own configuration compiles.
@MainActor
final class DebugLoggingTests: XCTestCase {
    func test_TEST_UNIT_073_given_this_build_when_the_shared_logger_is_read_then_its_threshold_is_the_variants() {
        let logger = MultiverseBootstrap.shared.logger()

        #if DEBUG
            XCTAssertTrue(logger.isEnabled(level: LogLevel.debug), "a Debug build logs each request start (LOG-001)")
            XCTAssertTrue(logger.isEnabled(level: LogLevel.info), "and each request completion (LOG-002)")
        #else
            XCTAssertFalse(logger.isEnabled(level: LogLevel.debug), "a Release build logs no request start")
            XCTAssertFalse(logger.isEnabled(level: LogLevel.info), "nor a request completion")
        #endif
        XCTAssertTrue(logger.isEnabled(level: LogLevel.error), "every build logs a failure (DEC-039)")
    }
}
