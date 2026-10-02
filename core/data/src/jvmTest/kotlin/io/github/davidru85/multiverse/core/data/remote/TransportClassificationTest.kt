package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `TEST-UNIT-022` — the JVM half of the transport classification (`API_SPECS.md` §6.1): a TLS
 * failure is `Unknown` and is never retried, a connectivity failure is `Offline` and is. The
 * failures reach the real adapter through `MockEngine`; no socket is opened.
 */
class TransportClassificationTest {
    private var captured: ApiFailure? = null

    private fun failureOf(thrown: Throwable): ApiFailure {
        TestTime.run { dispatcher ->
            val client = HttpClient(MockEngine { throw thrown }) { rickAndMortyDefaults() }
            val result = RestCharacterRemoteDataSource(client, dispatcher).characterPage(CharacterFilter(), 1)
            captured = (result as DataResult.Failure).failure
        }
        return captured!!
    }

    @Test
    fun `TEST-UNIT-022 given_a_tls_handshake_failure_when_requested_then_it_is_unknown_and_not_offline`() {
        assertIs<ApiFailure.Unknown>(
            failureOf(SSLHandshakeException("certificate not trusted")),
            "TEST-UNIT-022: TLS is never retried (API-ERR-003)",
        )
    }

    @Test
    fun `TEST-UNIT-022 given_an_unresolvable_host_when_requested_then_it_is_offline`() {
        assertEquals(
            ApiFailure.Offline,
            failureOf(UnknownHostException("no route")),
            "TEST-UNIT-022: a connectivity failure is Offline (API-ERR-001)",
        )
    }
}
