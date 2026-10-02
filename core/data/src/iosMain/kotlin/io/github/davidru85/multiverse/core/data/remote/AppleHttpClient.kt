package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSessionConfiguration

/**
 * The iOS client: the Darwin engine under Ktor (`DEC-011`), with the app's defaults. The caller —
 * the iOS shell (`TASK-051`) — owns the returned client and closes it.
 */
public fun appleRickAndMortyHttpClient(timeouts: RemoteTimeouts = RemoteTimeouts()): HttpClient =
    HttpClient(Darwin) {
        engine { configureSession { disableResponseCache() } }
        rickAndMortyDefaults(timeouts)
    }

/**
 * Keeps JSON responses out of `URLCache` (`API-CACHE-003`): a default session caches through the
 * shared `URLCache` and honours the server's `Cache-Control`, which would defeat the app-owned
 * freshness of `API_SPECS.md` §7.1.
 */
internal fun NSURLSessionConfiguration.disableResponseCache() {
    URLCache = null
    requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
}
