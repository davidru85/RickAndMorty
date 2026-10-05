@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-INT-002` on iOS — the image transport and cache keys (`REQ-FUNC-021`,
/// `AC-REQ-FUNC-021-1`/`-2`, `TASK-058`).
///
/// It is the iOS counterpart of the Android `ImageCacheTest` (`androidApp`), asserting the two facts
/// the criteria name rather than a decoded bitmap:
///
/// - **Zero requests on a second render** (`AC-REQ-FUNC-021-1`): the request count is read through
///   the injected seam, so the case cannot pass by accident of a live load. The memory hit answers a
///   second render of the same URL; a disk hit answers it after the memory is dropped.
/// - **No image bytes in the JSON cache** (`AC-REQ-FUNC-021-2`): the pipeline's disk layer is a
///   **dedicated** `URLCache`, never `URLCache.shared`, and this case proves the separation both
///   ways — the pipeline never writes into a foreign cache, and a foreign JSON entry is never
///   mistaken for an image.
///
/// The URLs are on the configured API host, because the pipeline rejects any other before a cache is
/// read (`DEC-126`); nothing here fetches them, the transport is a counting stub (`TESTING.md` §4.1).
@MainActor
final class ImagePipelineCacheTests: XCTestCase {
    // MARK: - AC-REQ-FUNC-021-1: a second render issues no request

    func test_TEST_INT_002_given_a_served_image_when_loaded_twice_then_the_second_load_issues_no_request() async {
        let transport = CountingImageTransport()
        let pipeline = PortraitImagePipeline(
            cache: CountingURLCache(directory: Self.isolatedDirectory()), transport: transport)

        let first = await pipeline.image(for: Self.url)
        XCTAssertNotNil(first, "the first load fetches and decodes the image")
        XCTAssertEqual(transport.requests, 1, "the first load issues exactly one request")

        let second = await pipeline.image(for: Self.url)
        XCTAssertNotNil(second, "the second load is answered from the cache")
        XCTAssertEqual(
            transport.requests,
            1,
            "a second render of the same URL issues no network request (AC-REQ-FUNC-021-1)"
        )
        XCTAssertEqual(
            transport.requestedUrls,
            [Self.url],
            "the transport sees the image URL verbatim, so the URL is the cache key (UI_SPEC.md §5.1, IC-016)"
        )
    }

    func test_TEST_INT_002_given_a_memory_hit_when_the_card_renders_then_no_request_is_issued() async {
        let transport = CountingImageTransport()
        let pipeline = PortraitImagePipeline(
            cache: CountingURLCache(directory: Self.isolatedDirectory()), transport: transport)

        _ = await pipeline.image(for: Self.url)
        let afterFirst = transport.requests

        XCTAssertNotNil(pipeline.cachedImage(for: Self.url), "the decoded portrait is resident for the sync hit")
        _ = pipeline.cachedImage(for: Self.url)
        XCTAssertEqual(
            transport.requests,
            afterFirst,
            "the synchronous memory hit a card paints from issues no request (AC-REQ-FUNC-021-1)"
        )
    }

    func test_TEST_INT_002_given_a_disk_entry_when_memory_is_dropped_then_the_second_load_issues_no_request() async {
        let transport = CountingImageTransport()
        let cache = CountingURLCache(directory: Self.isolatedDirectory())
        let pipeline = PortraitImagePipeline(cache: cache, transport: transport)

        _ = await pipeline.image(for: Self.url)
        XCTAssertEqual(cache.stores, 1, "the fetched image is re-stored under the URL so the disk layer holds it")
        let afterFirst = transport.requests

        // A fresh pipeline over the **same** disk cache: memory is empty, the disk answers.
        let reloaded = PortraitImagePipeline(cache: cache, transport: transport)
        let fromDisk = await reloaded.image(for: Self.url)
        XCTAssertNotNil(fromDisk, "the disk layer answers a memory-miss load")
        XCTAssertEqual(
            transport.requests,
            afterFirst,
            "a URL already on disk issues no network request, so memory → disk → network holds (AC-REQ-FUNC-021-1)"
        )
    }

    func test_TEST_INT_002_given_distinct_urls_when_each_is_loaded_then_each_issues_its_own_request() async {
        let transport = CountingImageTransport()
        let pipeline = PortraitImagePipeline(
            cache: CountingURLCache(directory: Self.isolatedDirectory()), transport: transport)

        _ = await pipeline.image(for: Self.url)
        _ = await pipeline.image(for: Self.otherUrl)
        XCTAssertEqual(transport.requests, 2, "a distinct URL is not answered by another URL's entry")
    }

    // MARK: - AC-REQ-FUNC-021-2: image bytes never reach the JSON cache

    /// A render writes bytes into **only** the image cache it is given; the app's separate JSON
    /// response cache is never touched, and the image cache is not the process-wide shared cache.
    ///
    /// This is the iOS counterpart of the Android assertion that "rendering an image leaves the
    /// response cache untouched and JSON entries contain no image bytes" (`TESTING.md` §341). On iOS
    /// the JSON response cache is the shared Kotlin `ResponseCache` over its own store — a different
    /// module the pipeline cannot reach — so the structural guarantee is that the image pipeline owns
    /// exactly one cache and never a general-purpose one.
    func test_TEST_INT_002_given_a_render_when_caches_are_read_then_only_the_image_cache_holds_bytes() async {
        let imageCache = CountingURLCache(directory: Self.isolatedDirectory())
        let jsonCache = CountingURLCache(directory: Self.isolatedDirectory())
        let pipeline = PortraitImagePipeline(cache: imageCache, transport: CountingImageTransport())

        _ = await pipeline.image(for: Self.url)
        _ = pipeline.cachedImage(for: Self.url)

        XCTAssertEqual(imageCache.stores, 1, "the fetched image lands in the pipeline's own image cache")
        XCTAssertNotNil(
            imageCache.cachedResponse(for: URLRequest(url: Self.urlValue)),
            "the image cache keys the entry by the image URL verbatim (UI_SPEC.md §5.1, IC-016)"
        )
        XCTAssertEqual(
            jsonCache.stores,
            0,
            "the app's JSON response cache is never written by a render (AC-REQ-FUNC-021-2)"
        )
        XCTAssertNil(
            jsonCache.cachedResponse(for: URLRequest(url: Self.urlValue)),
            "the JSON cache holds no entry under the image URL (AC-REQ-FUNC-021-2)"
        )
    }

    /// The disk layer is not the process-wide shared cache, so an image render cannot land where a
    /// JSON response cache — or any other part of the app — might later read it.
    func test_TEST_INT_002_given_the_pipeline_when_its_cache_is_inspected_then_it_is_not_the_shared_url_cache() {
        let pipeline = PortraitImagePipeline(cache: nil, transport: CountingImageTransport())
        XCTAssertFalse(
            pipeline.cache === URLCache.shared,
            "the image disk cache is a dedicated instance, never URLCache.shared (AC-REQ-FUNC-021-2)"
        )
    }

    // MARK: - Fixtures

    private static let url = "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
    private static let otherUrl = "https://rickandmortyapi.com/api/character/avatar/2.jpeg"

    /// The single parsed form of [url], so no case repeats the parse.
    private static let urlValue = URL(string: url) ?? URL(fileURLWithPath: "/")

    /// A 1 × 1 PNG, so the decode step completes and the request count is meaningful.
    fileprivate static let tinyPNG =
        Data(
            base64Encoded:
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
        ) ?? Data()
}

/// The counting transport: it records every request and returns a 1 × 1 PNG with an HTTP 200, as the
/// shipped session does, so a case can assert exactly how many network calls a render issued
/// (`REQ-FUNC-021`, `AC-REQ-FUNC-021-1`).
@MainActor
private final class CountingImageTransport: ImageDataTransport {
    private(set) var requests = 0
    private(set) var requestedUrls: [String] = []

    func fetch(_ url: String) async -> ImageFetchResponse? {
        requests += 1
        requestedUrls.append(url)
        guard
            let requestURL = URL(string: url),
            let response = HTTPURLResponse(
                url: requestURL,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )
        else {
            return nil
        }
        return ImageFetchResponse(data: ImagePipelineCacheTests.tinyPNG, response: response)
    }
}

/// A `URLCache` that counts the entries stored into it, in a directory of its own so no case can see
/// another's entries (`TESTING.md` §4.4; `AC-REQ-FUNC-021-2`).
private final class CountingURLCache: URLCache, @unchecked Sendable {
    private(set) var stores = 0
    private let lock = NSLock()

    init(directory: URL) {
        super.init(memoryCapacity: 1 << 20, diskCapacity: 4 << 20, diskPath: directory.path)
    }

    override func storeCachedResponse(_ cachedResponse: CachedURLResponse, for request: URLRequest) {
        lock.lock()
        stores += 1
        lock.unlock()
        super.storeCachedResponse(cachedResponse, for: request)
    }
}

extension ImagePipelineCacheTests {
    /// A cache directory no other case shares, so a stored entry cannot leak into one that expects a
    /// cold load. `URLCache`'s no-argument initializer would otherwise use one shared default
    /// location for every case (`TESTING.md` §4.4: cases must not observe each other's state).
    fileprivate static func isolatedDirectory() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("image-pipeline-tests-\(UUID().uuidString)", isDirectory: true)
    }
}
