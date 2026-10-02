package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.engine.mock.MockEngine
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `TEST-UNIT-022` — the Apple half of the transport classification (`API_SPECS.md` §6.1): the Darwin
 * engine reports `NSURLErrorDomain` codes, and the TLS codes are `Unknown` (never retried) while the
 * connectivity codes are `Offline`. The errors reach the real adapter through `MockEngine`.
 */
class TransportClassificationTest {
    private var captured: ApiFailure? = null

    private fun failureOf(code: Long): ApiFailure {
        TestTime.run { dispatcher ->
            val error = NSError.errorWithDomain(NSURLErrorDomain, code, null)
            val client = HttpClient(MockEngine { throw DarwinHttpRequestException(error) }) { rickAndMortyDefaults() }
            val result = RestCharacterRemoteDataSource(client, dispatcher).characterPage(CharacterFilter(), 1)
            captured = (result as DataResult.Failure).failure
        }
        return captured!!
    }

    @Test
    fun `TEST-UNIT-022 given_an_untrusted_server_certificate_when_requested_then_it_is_unknown_and_not_offline`() {
        assertIs<ApiFailure.Unknown>(
            failureOf(NSURLErrorServerCertificateUntrusted),
            "TEST-UNIT-022: TLS is never retried (API-ERR-003)",
        )
    }

    @Test
    fun `TEST-UNIT-022 given_no_internet_connection_when_requested_then_it_is_offline`() {
        assertEquals(ApiFailure.Offline, failureOf(NSURLErrorNotConnectedToInternet), "TEST-UNIT-022: API-ERR-001")
    }
}
