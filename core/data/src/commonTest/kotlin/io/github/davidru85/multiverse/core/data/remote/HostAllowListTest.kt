package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FixtureLoader
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `TEST-UNIT-025` — the host allow-list on every request (`REQ-SEC-001`, `AC-REQ-SEC-001-1`,
 * `SECURITY.md` §5.1): the shared client refuses cleartext, another host, a sub-domain and a
 * non-default port **before transport**, and a redirect is rejected rather than followed.
 */
class HostAllowListTest {
    @Test
    fun `TEST-UNIT-025 given_a_request_outside_the_allow_list_when_sent_then_it_is_rejected_before_transport`() =
        TestTime.run {
            var engineCalls = 0
            val client =
                HttpClient(
                    MockEngine {
                        engineCalls++
                        respond(
                            FixtureLoader.text("character-page-01.json"),
                            HttpStatusCode.OK,
                            headersOf("content-type", "application/json"),
                        )
                    },
                ) { rickAndMortyDefaults() }

            listOf(
                "http://${RickAndMortyApi.HOST}/api/character",
                "https://evil.example/api/character",
                "https://api.${RickAndMortyApi.HOST}/api/character",
                "https://${RickAndMortyApi.HOST}:8443/api/character",
            ).forEach { url ->
                val outcome = runCatching { client.get(url) }
                assertIs<RejectedRequestException>(outcome.exceptionOrNull(), "TEST-UNIT-025: $url must not reach transport")
            }
            assertEquals(0, engineCalls, "TEST-UNIT-025: a rejected request never reaches the engine")

            client.get("https://${RickAndMortyApi.HOST}/api/character")
            assertEquals(1, engineCalls, "TEST-UNIT-025: the allow-listed host still works")
        }

    @Test
    fun `TEST-UNIT-025 given_a_redirect_response_when_the_adapter_receives_it_then_it_is_rejected_and_never_followed`() =
        TestTime.run { dispatcher ->
            val (raw, served) =
                MockHttp.client(
                    MockHttp.errorRoute(
                        "character-page-beyond-last-404.json",
                        302,
                        headers = mapOf("Location" to "https://evil.example/api/character?page=1"),
                    ),
                )
            val client = raw.config { rickAndMortyDefaults() }

            val result = RestCharacterRemoteDataSource(client, dispatcher, MutableFakeClock()).characterPage(CharacterFilter(), 1)

            val failure = assertIs<DataResult.Failure>(result).failure
            assertIs<ApiFailure.InvalidRequest>(failure, "TEST-UNIT-025: a redirect is rejected (SECURITY.md 5.1)")
            assertEquals(1, served.size, "TEST-UNIT-025: the Location is never requested")
        }
}
