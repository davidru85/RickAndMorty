import Foundation
import SwiftUI
import UIKit

/// The portrait-seam a card renders through (`UI_SPEC.md` §5.1, `TASK-058`, `REQ-FUNC-021`).
///
/// `GlassCharacterCard` takes a concrete `Image`, so a screen resolves the portrait itself: this is
/// the one seam it resolves through, and a test substitutes an in-memory loader so no case touches
/// the network (`TESTING.md` §4.5). **The key is the image URL verbatim** (`UI_SPEC.md` §5.1,
/// `IC-016`): the URL that arrives is the key, never a re-derived or normalised one, so the Android
/// loader and this one answer the same cache for the same string.
///
/// It is deliberately two methods. [cachedImage(for:)] is the synchronous memory hit that lets a card
/// paint in the frame it appears — and it is what makes a second render of the same URL issue no
/// request (`AC-REQ-FUNC-021-1`). [image(for:)] is the full memory → disk → network path.
@MainActor
public protocol PortraitImageLoading: AnyObject {
    /// The decoded image for [url] when it is already resident, or `nil`.
    func cachedImage(for url: String) -> Image?

    /// The image for [url] through memory → disk → network, or `nil` when the load failed.
    ///
    /// It never throws for a failure: the `nil` is the error state, which [CharacterPortrait] draws
    /// as the branded portal mark — never a broken-image glyph (`UI_SPEC.md` §5.1).
    func image(for url: String) async -> Image?
}

/// The network hop of the image path (`TASK-058`).
///
/// The pipeline owns the memory and disk caches; the transport owns the fetch, so a case can count
/// exactly how many requests a render issues without a URL protocol, a socket or a fixture server.
/// The shipped default is `URLSessionImageDataTransport` over the pipeline's own session.
@MainActor
public protocol ImageDataTransport: AnyObject {
    /// Fetches [url] **verbatim** and returns its body with the HTTP response, or `nil` on failure.
    func fetch(_ url: String) async -> ImageFetchResponse?
}

/// One successful transport fetch: the bytes and the response the disk cache re-stores.
public struct ImageFetchResponse {
    public let data: Data
    public let response: URLResponse

    public init(data: Data, response: URLResponse) {
        self.data = data
        self.response = response
    }
}

/// The shipped transport: one `URLSession` over the pipeline's **own** `URLCache`.
///
/// The session is built from an ephemeral configuration whose `urlCache` is the pipeline's cache
/// instance and whose cookies are never set, so the image path cannot write into, or read from, the
/// process-wide `URLCache.shared` that another part of the app might consult
/// (`AC-REQ-FUNC-021-2`).
@MainActor
public final class URLSessionImageDataTransport: ImageDataTransport {
    private let session: URLSession

    public init(cache: URLCache) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = cache
        configuration.requestCachePolicy = .useProtocolCachePolicy
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        self.session = URLSession(configuration: configuration)
    }

    public func fetch(_ url: String) async -> ImageFetchResponse? {
        guard let requestURL = URL(string: url) else { return nil }
        do {
            let (data, response) = try await session.data(from: requestURL)
            return ImageFetchResponse(data: data, response: response)
        } catch {
            // A failed load is the error state, not a thrown error (`UI_SPEC.md` §5.1).
            return nil
        }
    }
}

/// The memory-cache budget `UI_SPEC.md` §5.2 / `DEC-026` record for the iOS image cache.
public let imageMemoryCacheBytes: Int = 50 * 1024 * 1024

/// The disk-cache budget `UI_SPEC.md` §5.2 / `DEC-026` record for the iOS image cache.
public let imageDiskCacheBytes: Int = 200 * 1024 * 1024

/// The app's one portrait pipeline (`UI_SPEC.md` §5.2, `DEC-026`, `TASK-058`).
///
/// The iOS peer of the Android Coil `ImageLoader`, and deliberately built from first-party APIs
/// alone (`UI_SPEC.md` §5.2: "No third-party loader is needed for the MVP — dependency restraint"):
///
/// 1. **Memory** — an `NSCache` of the decoded `UIImage`, keyed by the URL verbatim. `NSCache` is the
///    spec's own choice and evicts under pressure without the app policing a footprint.
/// 2. **Disk** — a `URLCache` the pipeline owns, at the spec's ≈50 MB memory / 200 MB disk, on the
///    URL session the transport fetches through.
/// 3. **Network** — [ImageDataTransport], which the shipped app resolves to the URL-session
///    transport and a case substitutes with a counting stub.
///
/// **Isolation (`AC-REQ-FUNC-021-2`).** The disk layer is a **dedicated** `URLCache` instance, never
/// `URLCache.shared`: image bytes land only in this pipeline's two caches, and a JSON response cache
/// — the Kotlin `ResponseCache` over its `CacheStorage`, or any process-wide URL cache — can never
/// observe them. After a successful fetch the response is re-stored under the request's URL with a
/// `CachedURLResponse`, so the cache key **is** the image URL (`UI_SPEC.md` §5.1) and a second render
/// is answered without a transport call whatever the origin's `Cache-Control` says
/// (`AC-REQ-FUNC-021-1`).
///
/// The pipeline is `@MainActor` because its seam is called from the view's main-actor context and the
/// images it hands back are `SwiftUI`/`UIKit` values; the URL session underneath performs its own
/// work off the main thread.
@MainActor
public final class PortraitImagePipeline: PortraitImageLoading {
    /// The one instance the app resolves; a test injects its own loader instead.
    public static let shared = PortraitImagePipeline()

    /// The decoded portraits, keyed by the URL verbatim (`UI_SPEC.md` §5.1).
    private let memory: NSCache<NSString, UIImage>

    /// The pipeline's **own** disk cache; never `URLCache.shared` (`AC-REQ-FUNC-021-2`).
    public let cache: URLCache

    private let transport: any ImageDataTransport

    public init(
        memoryCapacity: Int = imageMemoryCacheBytes,
        diskCapacity: Int = imageDiskCacheBytes,
        cache: URLCache? = nil,
        transport: (any ImageDataTransport)? = nil
    ) {
        let resolvedCache =
            cache
            ?? URLCache(
                memoryCapacity: memoryCapacity,
                diskCapacity: diskCapacity,
                directory: Self.cacheDirectory
            )
        self.cache = resolvedCache
        self.transport = transport ?? URLSessionImageDataTransport(cache: resolvedCache)

        let memory = NSCache<NSString, UIImage>()
        memory.totalCostLimit = memoryCapacity
        self.memory = memory
    }

    public func cachedImage(for url: String) -> Image? {
        guard !url.isEmpty, let image = memory.object(forKey: url as NSString) else { return nil }
        return Image(uiImage: image)
    }

    public func image(for url: String) async -> Image? {
        guard !url.isEmpty else { return nil }

        // 1. Memory: the synchronous hit a second render takes.
        if let cached = memory.object(forKey: url as NSString) { return Image(uiImage: cached) }

        // A URL the pipeline cannot parse is a load failure, not a crash.
        guard let parsed = URL(string: url) else { return nil }
        let request = URLRequest(url: parsed)

        // 2. Disk, keyed by the URL verbatim (`AC-REQ-FUNC-021-1`).
        if let stored = cache.cachedResponse(for: request), let image = decode(stored.data) {
            store(image, data: stored.data, for: url)
            return Image(uiImage: image)
        }

        // 3. Network: the only path that issues a request.
        guard let fetched = await transport.fetch(url), let image = decode(fetched.data) else {
            return nil
        }
        // Re-store under the request's own URL so the key is the image URL, independent of the
        // origin's caching headers.
        let cached = CachedURLResponse(response: fetched.response, data: fetched.data)
        cache.storeCachedResponse(cached, for: request)
        store(image, data: fetched.data, for: url)
        return Image(uiImage: image)
    }

    /// The decoded portrait of [data], or `nil` when the bytes are not a decodable image.
    ///
    /// The decode is at the source resolution the API provides (300 × 300, `UI_SPEC.md` §5.1); the
    /// pipeline never upscales and never claims a higher resolution (`AC-REQ-FUNC-005-3`).
    private func decode(_ data: Data) -> UIImage? {
        guard !data.isEmpty else { return nil }
        return UIImage(data: data)
    }

    private func store(_ image: UIImage, data: Data, for url: String) {
        memory.setObject(image, forKey: url as NSString, cost: data.count)
    }

    /// The pipeline's own disk-cache directory, inside the app's private caches
    /// (`SECURITY.md` §3: public image bytes only, evictable by the OS).
    private static var cacheDirectory: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("portrait-images", isDirectory: true)
    }
}
