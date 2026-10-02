package io.github.davidru85.multiverse.core.domain

import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `IC-003`/`IC-004`: the result envelope's own invariants, enforced where the value is built. */
class DataResultTest {
    @Test
    fun `TEST-UNIT-052 given_a_network_source_when_a_stale_success_is_built_then_it_is_rejected`() {
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-052: stale content always comes from a cache") {
            DataResult.Success(value = 1, source = DataSource.NETWORK, isStale = true)
        }
        // The converse does not hold: a cache hit may be fresh, and a network value is never stale.
        val cached = DataResult.Success(value = 1, source = DataSource.DISK_CACHE, isStale = true)
        val fresh = DataResult.Success(value = 1, source = DataSource.MEMORY_CACHE, isStale = false)
        val network = DataResult.Success(value = 1, source = DataSource.NETWORK, isStale = false)
        assertTrue(cached.isStale && !fresh.isStale && !network.isStale, "TEST-UNIT-052")
    }

    @Test
    fun `TEST-UNIT-052 given_warnings_when_either_outcome_is_built_then_they_are_carried_and_default_to_none`() {
        val warning = ApiWarning(code = "missing-resource", detail = "7")
        val success: DataResult<Int> = DataResult.Success(1, DataSource.NETWORK, isStale = false, warnings = listOf(warning))
        val failure: DataResult<Int> = DataResult.Failure(ApiFailure.EmptyBody, DataSource.NETWORK)
        assertEquals(listOf(warning), success.warnings, "TEST-UNIT-052: warnings survive on a success")
        assertEquals(emptyList(), failure.warnings, "TEST-UNIT-052: no warning unless one was produced")
    }

    @Test
    fun `TEST-UNIT-052 given_a_cancellation_when_wrapped_as_an_unknown_failure_then_it_is_rejected`() {
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-052: cancellation is control flow (ERROR_FLOW.md 6)") {
            ApiFailure.Unknown(CancellationException("superseded"))
        }
        val tls = IllegalStateException("handshake")
        assertEquals(tls, ApiFailure.Unknown(tls).cause, "TEST-UNIT-052: any other cause is kept")
    }
}
