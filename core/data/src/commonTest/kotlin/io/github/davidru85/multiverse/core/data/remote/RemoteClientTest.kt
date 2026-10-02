package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.testing.FixtureLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-054` — the reusable REST client configuration (`TASK-037`): the decoding settings of
 * `SECURITY.md` §12.2, the timeouts of `API_SPECS.md` §6.3, and the redirect policy, as the
 * production defaults apply them to a client the composition root owns.
 */
class RemoteClientTest {
    @Serializable
    private data class Probe(
        val id: Int,
        val name: String,
    )

    @Test
    fun `TEST-UNIT-054 given_the_pinned_decoder_when_input_is_extra_or_lenient_then_extra_fields_pass_and_leniency_fails`() {
        assertEquals(Probe(1, "Rick"), RemoteJson.decodeFromString(Probe.serializer(), """{"id":1,"name":"Rick","added":true}"""))
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-054: lenient JSON is malformed (SECURITY.md 12.2)") {
            RemoteJson.decodeFromString(Probe.serializer(), """{id:1,name:Rick}""")
        }
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-054: a missing required field is never defaulted") {
            RemoteJson.decodeFromString(Probe.serializer(), """{"id":1}""")
        }
        val settings = RemoteJson.configuration
        assertTrue(settings.ignoreUnknownKeys, "TEST-UNIT-054")
        assertFalse(settings.coerceInputValues || settings.isLenient || settings.allowSpecialFloatingPointValues, "TEST-UNIT-054")
    }

    @Test
    fun `TEST-UNIT-054 given_the_defaults_when_a_request_is_sent_then_the_documented_timeouts_travel_with_it_and_redirects_are_not_followed`() =
        runTest {
            var captured: HttpRequestData? = null
            val engine =
                MockEngine { request ->
                    captured = request
                    respond(
                        content = FixtureLoader.text("character-page-01.json"),
                        status = HttpStatusCode.OK,
                        headers = headersOf("content-type", "application/json"),
                    )
                }
            val client = HttpClient(engine) { rickAndMortyDefaults() }

            client.get("https://${RickAndMortyApi.HOST}/api/character")

            val timeouts = captured?.getCapabilityOrNull(HttpTimeoutCapability)
            assertEquals(10_000L, timeouts?.connectTimeoutMillis, "TEST-UNIT-054: connect timeout (API_SPECS.md 6.3)")
            assertEquals(15_000L, timeouts?.socketTimeoutMillis, "TEST-UNIT-054: read timeout")
            assertEquals(20_000L, timeouts?.requestTimeoutMillis, "TEST-UNIT-054: total call timeout")
            assertEquals(null, client.pluginOrNull(HttpRedirect), "TEST-UNIT-054: redirects are never followed implicitly")
        }
}
