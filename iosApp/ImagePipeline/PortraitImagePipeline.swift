import Foundation
import MultiverseExplorer
import SwiftUI
import UIKit

/// The one host rule a portrait URL must pass before any layer touches it (`REQ-SEC-001`, `DEC-126`):
/// the shared `:core:data` predicate, so iOS and the Android allow-listed client cannot disagree.
public let portraitHostRule: @Sendable (String) -> Bool = { url in
    MultiverseBootstrap.shared.isAllowedImageUrl(url: url)
}

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
/// (`AC-REQ-FUNC-021-2`). Its delegate is the [ImageRedirectPolicy], so a redirect cannot carry a
/// fetch to a host the rule rejects (`DEC-126`).
@MainActor
public final class URLSessionImageDataTransport: ImageDataTransport {
    private let session: URLSession

    public init(cache: URLCache, isAllowed: @escaping @Sendable (String) -> Bool = portraitHostRule) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = cache
        configuration.requestCachePolicy = .useProtocolCachePolicy
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        self.session = URLSession(
            configuration: configuration,
            delegate: ImageRedirectPolicy(isAllowed: isAllowed),
            delegateQueue: nil
        )
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

/// The redirect rule of the image session (`REQ-SEC-001`, `DEC-126`): a redirect is followed only when
/// its target passes the host rule, and refused otherwise, so the fetch ends with the redirect
/// response itself — which the pipeline never admits, because it is not a `2xx`.
public final class ImageRedirectPolicy: NSObject, URLSessionTaskDelegate, Sendable {
    private let isAllowed: @Sendable (String) -> Bool

    public init(isAllowed: @escaping @Sendable (String) -> Bool) {
        self.isAllowed = isAllowed
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest
    ) async -> URLRequest? {
        guard let target = request.url?.absoluteString, isAllowed(target) else { return nil }
        return request
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
/// **Concurrency (`DEC-141`).** The seam is `@MainActor`, because a view calls it and gets SwiftUI
/// values back, but the work is not done there: the disk read and the decode run in a detached task
/// that prepares the bitmap for display, so a scrolling grid never decodes on the main thread, and the
/// main actor only publishes the result. A URL already loading is not fetched again: every caller that
/// asks for it meanwhile awaits the one in-flight load. The memory cache's cost is the decoded
/// bitmap's bytes, so its budget bounds the memory it actually holds.
@MainActor
public final class PortraitImagePipeline: PortraitImageLoading {
    /// The one instance the app resolves; a test injects its own loader instead.
    public static let shared = PortraitImagePipeline()

    /// The decoded portraits, keyed by the URL verbatim (`UI_SPEC.md` §5.1).
    private let memory: NSCache<NSString, UIImage>

    /// The pipeline's **own** disk cache; never `URLCache.shared` (`AC-REQ-FUNC-021-2`).
    public let cache: URLCache

    private let transport: any ImageDataTransport

    /// The host rule every URL passes before any layer touches it (`DEC-126`).
    private let isAllowed: @Sendable (String) -> Bool

    /// The loads in flight, by URL, so a second caller joins the first instead of fetching again.
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    public init(
        memoryCapacity: Int = imageMemoryCacheBytes,
        diskCapacity: Int = imageDiskCacheBytes,
        cache: URLCache? = nil,
        transport: (any ImageDataTransport)? = nil,
        isAllowed: @escaping @Sendable (String) -> Bool = portraitHostRule
    ) {
        let resolvedCache =
            cache
            ?? URLCache(
                memoryCapacity: memoryCapacity,
                diskCapacity: diskCapacity,
                directory: Self.cacheDirectory
            )
        self.cache = resolvedCache
        self.isAllowed = isAllowed
        self.transport = transport ?? URLSessionImageDataTransport(cache: resolvedCache, isAllowed: isAllowed)

        let memory = NSCache<NSString, UIImage>()
        memory.totalCostLimit = memoryCapacity
        self.memory = memory
    }

    public func cachedImage(for url: String) -> Image? {
        guard !url.isEmpty, isAllowed(url), let image = memory.object(forKey: url as NSString) else { return nil }
        return Image(uiImage: image)
    }

    public func image(for url: String) async -> Image? {
        // The host rule comes first: a URL it rejects reaches no cache and no transport, and the
        // portrait renders its error state (`REQ-SEC-001`, `DEC-126`).
        guard !url.isEmpty, isAllowed(url) else { return nil }

        // 1. Memory: the synchronous hit a second render takes.
        if let cached = memory.object(forKey: url as NSString) { return Image(uiImage: cached) }

        // A load of this URL is already running: join it rather than fetch again.
        if let running = inFlight[url] {
            return await running.value.map { Image(uiImage: $0) }
        }
        let load = Task { await self.load(url) }
        inFlight[url] = load
        let image = await load.value
        inFlight[url] = nil
        return image.map { Image(uiImage: $0) }
    }

    /// The disk → network path for one URL, whose bytes are read and decoded off the main actor.
    private func load(_ url: String) async -> UIImage? {
        // A URL the pipeline cannot parse is a load failure, not a crash.
        guard let parsed = URL(string: url) else { return nil }
        let request = URLRequest(url: parsed)
        let cache = self.cache

        // 2. Disk, keyed by the URL verbatim (`AC-REQ-FUNC-021-1`), read and decoded off the main actor.
        let stored = await Task.detached(priority: .userInitiated) { () -> UIImage? in
            guard let response = cache.cachedResponse(for: request) else { return nil }
            return Self.decode(response.data)
        }.value
        if let stored {
            store(stored, for: url)
            return stored
        }

        // 3. Network: the only path that issues a request. Only a successful HTTP response that ended
        // on an allowed URL is a portrait; anything else is neither shown nor cached.
        guard let fetched = await transport.fetch(url), isAdmissible(fetched.response) else { return nil }
        let data = fetched.data
        guard let image = await Task.detached(priority: .userInitiated, operation: { Self.decode(data) }).value else {
            return nil
        }
        // Re-store under the request's own URL so the key is the image URL, independent of the
        // origin's caching headers.
        cache.storeCachedResponse(CachedURLResponse(response: fetched.response, data: data), for: request)
        store(image, for: url)
        return image
    }

    /// Whether [response] may enter the caches: an HTTP `2xx` whose final URL — after any redirect the
    /// policy followed — still passes the host rule (`DEC-126`).
    private func isAdmissible(_ response: URLResponse) -> Bool {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return false }
        guard let finalURL = http.url?.absoluteString else { return true }
        return isAllowed(finalURL)
    }

    /// The decoded portrait of [data], prepared for display, or `nil` when the bytes are not a
    /// decodable image. It runs off the main actor, so the first draw does not decode either.
    ///
    /// The decode is at the source resolution the API provides (300 × 300, `UI_SPEC.md` §5.1); the
    /// pipeline never upscales and never claims a higher resolution (`AC-REQ-FUNC-005-3`).
    nonisolated private static func decode(_ data: Data) -> UIImage? {
        guard !data.isEmpty, let image = UIImage(data: data) else { return nil }
        return image.preparingForDisplay() ?? image
    }

    /// The memory a decoded [image] holds: its pixel width × height × 4 bytes, at its own scale. It is
    /// the memory cache's cost, so the 50 MB budget bounds decoded bitmaps rather than compressed
    /// bodies (`DEC-141`).
    nonisolated static func memoryCost(of image: UIImage) -> Int {
        Int(image.size.width * image.scale) * Int(image.size.height * image.scale) * 4
    }

    private func store(_ image: UIImage, for url: String) {
        memory.setObject(image, forKey: url as NSString, cost: Self.memoryCost(of: image))
    }

    /// The pipeline's own disk-cache directory, inside the app's private caches
    /// (`SECURITY.md` §3: public image bytes only, evictable by the OS).
    private static var cacheDirectory: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("portrait-images", isDirectory: true)
    }
}
