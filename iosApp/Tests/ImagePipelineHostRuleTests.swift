@testable import MultiverseApp
import MultiverseExplorer
import XCTest

/// `TEST-UNIT-071`'s pipeline half — the iOS portrait pipeline applies the configured host rule
/// before any layer touches a URL, and admits only a successful HTTP response to its caches
/// (`REQ-SEC-001`, `AC-REQ-SEC-001-1`, `SECURITY.md` §5, `DEC-126`, `TASK-111`).
///
/// Android's Coil loader fetches through the allow-listed Ktor client, so a foreign portrait URL costs
/// it zero transport calls; the iOS pipeline used a raw `URLSession` for whatever host a payload named.
/// The transport here records every fetch and never touches a network (`TESTING.md` §4.1).
@MainActor
final class ImagePipelineHostRuleTests: XCTestCase {
    func test_TEST_UNIT_071_given_a_url_outside_the_host_rule_when_loaded_then_nothing_is_fetched() async {
        let transport = RecordingImageTransport(status: 200)
        let pipeline = PortraitImagePipeline(cache: Self.isolatedCache(), transport: transport)
        var rejected: [String] = []
        rejected.append("https://evil.example/avatar/1.jpeg")
        rejected.append("http://rickandmortyapi.com/api/character/avatar/1.jpeg")
        rejected.append("https://cdn.rickandmortyapi.com/api/character/avatar/1.jpeg")
        rejected.append("https://rickandmortyapi.com:8443/api/character/avatar/1.jpeg")
        rejected.append("https://user@rickandmortyapi.com/api/character/avatar/1.jpeg")

        for url in rejected {
            let image = await pipeline.image(for: url)
            XCTAssertNil(image, "\(url) must render the portrait's error state")
        }

        XCTAssertEqual(transport.requestedUrls, [], "a URL outside the host rule costs zero transport calls")
    }

    func test_TEST_UNIT_071_given_an_unsuccessful_response_when_loaded_then_it_is_neither_shown_nor_cached() async {
        let transport = RecordingImageTransport(status: 404)
        let pipeline = PortraitImagePipeline(cache: Self.isolatedCache(), transport: transport)
        let url = "https://rickandmortyapi.com/api/character/avatar/1.jpeg"

        let first = await pipeline.image(for: url)
        let second = await pipeline.image(for: url)

        XCTAssertNil(first, "a 404 body is not a portrait, even when its bytes decode")
        XCTAssertNil(second, "and it was not admitted to a cache")
        XCTAssertEqual(transport.requestedUrls.count, 2, "each load had to go back to the transport")
    }

    func test_TEST_UNIT_071_given_a_redirect_when_the_policy_decides_then_only_allowed_hosts_follow() async throws {
        let policy = ImageRedirectPolicy(isAllowed: portraitHostRule)
        let origin = try XCTUnwrap(URL(string: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"))
        let session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: origin)
        defer { task.cancel() }
        let redirect = try XCTUnwrap(
            HTTPURLResponse(url: origin, statusCode: 302, httpVersion: "HTTP/1.1", headerFields: nil)
        )
        let foreign = URLRequest(url: try XCTUnwrap(URL(string: "https://evil.example/avatar/1.jpeg")))
        let allowedURL = try XCTUnwrap(URL(string: "https://rickandmortyapi.com/api/character/avatar/2.jpeg"))
        let allowed = URLRequest(url: allowedURL)

        let refused = await policy.urlSession(
            session, task: task, willPerformHTTPRedirection: redirect, newRequest: foreign)
        let followed = await policy.urlSession(
            session, task: task, willPerformHTTPRedirection: redirect, newRequest: allowed)

        XCTAssertNil(refused, "a redirect to a foreign host is refused before transport")
        XCTAssertEqual(followed?.url, allowed.url, "a redirect that stays on the configured host is followed")
    }

    /// A disk cache in a directory no other case — and not the app on this simulator — shares, so a
    /// hit can only come from this case's own loads (`TESTING.md` §4.4).
    private static func isolatedCache() -> URLCache {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("image-host-rule-tests-\(UUID().uuidString)", isDirectory: true)
        return URLCache(memoryCapacity: 1 << 20, diskCapacity: 4 << 20, directory: directory)
    }
}

/// A transport that records every fetch and answers with a 1 × 1 PNG under the given HTTP status.
@MainActor
private final class RecordingImageTransport: ImageDataTransport {
    private let status: Int
    private(set) var requestedUrls: [String] = []

    init(status: Int) {
        self.status = status
    }

    func fetch(_ url: String) async -> ImageFetchResponse? {
        requestedUrls.append(url)
        guard
            let requestURL = URL(string: url),
            let response = HTTPURLResponse(
                url: requestURL, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)
        else {
            return nil
        }
        return ImageFetchResponse(data: Self.tinyPNG, response: response)
    }

    /// A 1 × 1 PNG, so a rejection is the rule's and never a decode failure's.
    private static let tinyPNG =
        Data(
            base64Encoded:
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
        ) ?? Data()
}
