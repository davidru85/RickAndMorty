package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `TEST-UNIT-054` — the Apple engine keeps JSON responses out of `URLCache` (`API-CACHE-003`). A
 * default `NSURLSession` configuration caches through the shared `URLCache` and honours the server's
 * `Cache-Control`, which would defeat the app-owned freshness of `API_SPECS.md` §7.1.
 */
class AppleHttpClientTest {
    @Test
    fun `TEST-UNIT-054 given_a_default_session_configuration_when_the_api_policy_is_applied_then_no_url_cache_is_used`() {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
        configuration.disableResponseCache()

        assertNull(configuration.URLCache, "TEST-UNIT-054: no URLCache (API-CACHE-003)")
        assertEquals(
            NSURLRequestReloadIgnoringLocalCacheData,
            configuration.requestCachePolicy,
            "TEST-UNIT-054: every request ignores any local cache",
        )
    }

    @Test
    fun `TEST-UNIT-054 given_the_apple_factory_when_a_client_is_built_then_it_runs_on_the_darwin_engine`() {
        val client = appleRickAndMortyHttpClient()
        try {
            assertIs<DarwinClientEngineConfig>(client.engine.config, "TEST-UNIT-054: the iOS client runs on Darwin")
        } finally {
            client.close()
        }
    }
}
