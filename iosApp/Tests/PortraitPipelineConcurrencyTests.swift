@testable import MultiverseApp
import SwiftUI
import UIKit
import XCTest

/// `TEST-UNIT-093` — the portrait pipeline under concurrent cells (`UI_SPEC.md` §5.2, `DEC-141`,
/// `REQ-NFR-003`, `TASK-114`).
///
/// Two cells asking for one URL at once issued two requests, and the memory cache counted an image's
/// compressed bytes — about 20 KB for a portrait — rather than the decoded 300 × 300 bitmap's 360 KB,
/// so its 50 MB budget held roughly eighteen times more than intended. The transport here is a gate
/// the case opens, so the overlap is certain without any timing.
@MainActor
final class PortraitPipelineConcurrencyTests: XCTestCase {
    func test_TEST_UNIT_093_given_two_loads_of_one_url_at_once_when_both_finish_then_one_request_was_issued() async {
        let transport = GatedImageTransport()
        let pipeline = PortraitImagePipeline(
            cache: URLCache(memoryCapacity: 1 << 20, diskCapacity: 4 << 20, directory: Self.isolatedDirectory()),
            transport: transport,
            isAllowed: { _ in true }
        )

        let first = Task { await pipeline.image(for: Self.url) }
        let second = Task { await pipeline.image(for: Self.url) }
        // Let both loads run up to their first suspension before the response is let through.
        for _ in 0..<20 { await Task.yield() }
        transport.open()

        let images = await (first.value, second.value)
        XCTAssertNotNil(images.0, "the first load gets the portrait")
        XCTAssertNotNil(images.1, "the second load gets the same portrait")
        XCTAssertEqual(transport.requests, 1, "a URL in flight is fetched once however many cells ask")
    }

    func test_TEST_UNIT_093_given_a_decoded_portrait_when_costed_then_its_cost_is_the_bitmap_bytes() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 300, height: 300), format: format).image { context in
            UIColor.green.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 300, height: 300))
        }
        XCTAssertEqual(PortraitImagePipeline.memoryCost(of: image), 300 * 300 * 4, "width × height × 4 bytes")
    }

    private static let url = "https://rickandmortyapi.com/api/character/avatar/1.jpeg"

    private static func isolatedDirectory() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("portrait-pipeline-tests-\(UUID().uuidString)", isDirectory: true)
    }
}

/// A transport that holds every fetch until the case opens it, then answers each with a 1 × 1 PNG.
@MainActor
private final class GatedImageTransport: ImageDataTransport {
    private(set) var requests = 0
    private var isOpen = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    func open() {
        isOpen = true
        waiting.forEach { $0.resume() }
        waiting.removeAll()
    }

    func fetch(_ url: String) async -> ImageFetchResponse? {
        requests += 1
        if !isOpen {
            await withCheckedContinuation { waiting.append($0) }
        }
        guard
            let requestURL = URL(string: url),
            let response = HTTPURLResponse(url: requestURL, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil),
            let png = Data(
                base64Encoded:
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
            )
        else {
            return nil
        }
        return ImageFetchResponse(data: png, response: response)
    }
}
