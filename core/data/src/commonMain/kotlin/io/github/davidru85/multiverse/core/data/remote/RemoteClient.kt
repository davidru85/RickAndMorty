package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/** The transport timeouts of `API_SPECS.md` §6.3, as injectable configuration rather than constants. */
public data class RemoteTimeouts(
    public val connectMillis: Long = 10_000,
    public val readMillis: Long = 15_000,
    public val callMillis: Long = 20_000,
)

/**
 * The app's defaults for the one Ktor client (`DEC-011`, `TASK-037`), applied by the platform
 * factories and by tests that build a client around `MockEngine`.
 *
 * - Status codes are mapped by the adapters, so no status throws (`expectSuccess = false`).
 * - Redirects are never followed implicitly: a redirect to another host would bypass the host rule of
 *   `REQ-SEC-001`. The allow-listed redirect policy belongs to `TASK-038`.
 * - The timeouts of [timeouts] apply to every request.
 *
 * The composition root that builds the client owns it and closes it; a data source never does.
 */
public fun HttpClientConfig<*>.rickAndMortyDefaults(timeouts: RemoteTimeouts = RemoteTimeouts()) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = timeouts.connectMillis
        socketTimeoutMillis = timeouts.readMillis
        requestTimeoutMillis = timeouts.callMillis
    }
}
