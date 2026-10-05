package io.github.davidru85.multiverse.core.presentation.splash

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `TEST-UI-006` — the splash timing policy (`REQ-FUNC-007`, `AC-REQ-FUNC-007-1`…`-3`, `DEC-098`) — and
 * `TEST-UNIT-084`, the request it makes, now that one gate serves both shells (`IC-026`, `DEC-136`).
 *
 * The policy is proved on **virtual time**: the gate is driven by the test scheduler, so a case asserts
 * "completed at 1.2 s" without waiting and the three-second ceiling is an instant. The cases live in
 * common code, so they run on every target the gate ships to, iOS included. `:core:presentation` may
 * reach `:core:domain` only (`R3`), so the repositories are the small doubles below rather than the
 * `:core:testing` fakes; none performs I/O.
 */
class SplashGateTest {
    @Test
    fun `TEST-UI-006 given_data_ready_sooner_than_the_minimum_when_the_gate_waits_then_it_completes_at_the_minimum`() =
        runTest {
            // The page arrives after 300 ms, so the minimum is what decides the instant.
            val gate = gate(PageAfter(300.milliseconds))
            val completedAt = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MINIMUM - 1.milliseconds)
            runCurrent()
            assertNull(completedAt.value, "the splash must not complete before the minimum")

            advanceUntilIdle()
            assertEquals(SplashGate.MINIMUM, completedAt.value)
        }

    @Test
    fun `TEST-UI-006 given_data_slower_than_the_minimum_when_the_gate_waits_then_it_completes_when_the_page_settles`() =
        runTest {
            val gate = gate(PageAfter(2.seconds))
            val completedAt = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MINIMUM + 100.milliseconds)
            runCurrent()
            assertNull(completedAt.value, "a slow page keeps the splash past the minimum")

            advanceUntilIdle()
            assertEquals(2.seconds, completedAt.value, "the gate completes when the page settles")
            assertTrue(completedAt.value!! <= SplashGate.MAXIMUM, "the completion is inside the ceiling")
        }

    @Test
    fun `TEST-UI-006 given_data_that_never_arrives_when_the_ceiling_reaches_then_it_completes_with_no_outcome`() =
        runTest {
            val gate = gate(NeverSettlesRepository())
            val completion = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MAXIMUM - 1.milliseconds)
            runCurrent()
            assertNull(completion.value, "the splash must not outlast the ceiling")

            advanceUntilIdle()
            assertEquals(SplashGate.MAXIMUM, completion.value)
            assertNull(completion.outcome, "no page settled, so the caller gets no outcome")
        }

    @Test
    fun `TEST-UI-006 given_an_offline_first_page_when_the_gate_waits_then_it_completes_and_reports_the_failure`() =
        runTest {
            val gate = gate(OfflineRepository())
            val completion = whenItCompletes(gate)
            advanceUntilIdle()

            assertEquals(SplashGate.MINIMUM, completion.value, "an offline page completes the gate at the minimum, not at the ceiling")
            val outcome = assertIs<DataResult.Failure>(completion.outcome, "the failure is returned so Discovery renders its error state")
            assertEquals(ApiFailure.Offline, outcome.failure)
        }

    @Test
    fun `TEST-UI-006 given_the_documented_bounds_when_they_are_read_then_they_are_one_point_two_and_three_seconds`() {
        assertEquals(1_200L, SplashGate.MINIMUM.inWholeMilliseconds)
        assertEquals(3_000L, SplashGate.MAXIMUM.inWholeMilliseconds)
        assertTrue(SplashGate.MINIMUM < SplashGate.MAXIMUM, "the minimum is below the ceiling")
        assertTrue(SplashGate.MAXIMUM < 10.seconds, "the ceiling is a real bound, not a minute")
    }

    @Test
    fun `TEST-UNIT-084 given_the_gate_when_it_waits_then_it_asks_for_the_page_the_first_screen_loads`() =
        runTest {
            // Discovery's first load is page 1 of the unfiltered list under the default policy, so the
            // splash's request warms exactly the entry that load reads, on both shells (`DEC-136`).
            val repository = PageAfter(100.milliseconds)
            whenItCompletes(gate(repository))
            advanceUntilIdle()

            assertEquals(listOf(Triple(CharacterFilter(), 1, PageLoadPolicy.Default)), repository.requests)
        }

    private fun TestScope.gate(repository: CharacterRepository) = SplashGate(repository, StandardTestDispatcher(testScheduler))

    /** What the gate returned, and the virtual instant at which it did; `null` while it is pending. */
    private class Completion {
        var value: Duration? = null
        var outcome: DataResult<*>? = null
    }

    /**
     * Runs the gate as a child of the test's coroutine, so its completion happens inside the
     * scheduler the case advances. `awaitReady` is the only thing that decides the instant; nothing
     * here reads a clock.
     */
    private fun TestScope.whenItCompletes(gate: SplashGate): Completion {
        val completion = Completion()
        val start = testScheduler.currentTime
        launch {
            completion.outcome = gate.awaitReady()
            completion.value = (testScheduler.currentTime - start).milliseconds
        }
        runCurrent()
        return completion
    }

    /** A first page that settles successfully after [latency], recording each request. */
    private class PageAfter(
        private val latency: Duration,
    ) : CharacterRepository {
        val requests = mutableListOf<Triple<CharacterFilter, Int, PageLoadPolicy>>()

        override suspend fun page(
            filter: CharacterFilter,
            page: Int,
            policy: PageLoadPolicy,
        ): DataResult<CharacterPage> {
            requests += Triple(filter, page, policy)
            delay(latency)
            val result = CharacterPage(emptyList(), page = 1, pageCount = 1, totalCount = 0, nextPage = null, previousPage = null)
            return DataResult.Success(result, DataSource.NETWORK, isStale = false)
        }

        override suspend fun details(
            id: CharacterId,
            enrich: Boolean,
        ): DataResult<CharacterDetails> = error("the splash gate never requests details")
    }

    /** A page that never settles, so only the ceiling can end the wait. */
    private class NeverSettlesRepository : CharacterRepository {
        override suspend fun page(
            filter: CharacterFilter,
            page: Int,
            policy: PageLoadPolicy,
        ): DataResult<CharacterPage> = awaitCancellation()

        override suspend fun details(
            id: CharacterId,
            enrich: Boolean,
        ): DataResult<CharacterDetails> = error("the splash gate never requests details")
    }

    /** A first page that fails as a transport failure, which is a result rather than a reason to wait. */
    private class OfflineRepository : CharacterRepository {
        override suspend fun page(
            filter: CharacterFilter,
            page: Int,
            policy: PageLoadPolicy,
        ): DataResult<CharacterPage> = DataResult.Failure(ApiFailure.Offline, source = DataSource.NETWORK)

        override suspend fun details(
            id: CharacterId,
            enrich: Boolean,
        ): DataResult<CharacterDetails> = error("the splash gate never requests details")
    }
}
