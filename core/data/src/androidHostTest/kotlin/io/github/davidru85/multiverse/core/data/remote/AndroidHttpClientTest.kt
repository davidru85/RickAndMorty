package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.engine.okhttp.OkHttpConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `TEST-UNIT-054` — the Android engine keeps JSON responses out of any engine-level cache
 * (`API-CACHE-003`): the app owns freshness, and the server's 90-day `immutable` directive would
 * otherwise pin a page (`API_SPECS.md` §7.1).
 */
class AndroidHttpClientTest {
    @Test
    fun `TEST-UNIT-054 given_the_android_factory_when_a_client_is_built_then_its_okhttp_engine_has_no_response_cache`() {
        val client = androidRickAndMortyHttpClient()
        try {
            val config = assertIs<OkHttpConfig>(client.engine.config, "TEST-UNIT-054: the Android client runs on OkHttp")
            val okHttp = config.preconfigured
            assertNull(okHttp?.cache, "TEST-UNIT-054: no OkHttp disk cache (API-CACHE-003)")
            assertEquals(false, okHttp?.followRedirects, "TEST-UNIT-054: OkHttp never follows a redirect on its own")
            // `DEC-084`: the repository's policy is the only retry layer; OkHttp retrying a failed connection
            // underneath it would multiply the three-attempt budget.
            assertEquals(false, okHttp?.retryOnConnectionFailure, "TEST-UNIT-054: OkHttp never retries on its own")
        } finally {
            client.close()
        }
    }
}
