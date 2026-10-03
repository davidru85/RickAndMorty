package io.github.davidru85.multiverse.app.splash

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UI-006` — the splash timing policy (`REQ-FUNC-007`, `AC-REQ-FUNC-007-1`…`-3`, `DEC-098`).
 *
 * The policy is proved on **virtual time**: the gate is driven by the test scheduler, so a case asserts
 * "completed at 1.2 s" without waiting and the three-second ceiling is an instant. The repository is
 * the shared fake, so no network is involved, and a purpose-built double covers the two cases the fake
 * cannot express: a page that never settles, and an offline page.
 */
class SplashGateTest {
    @Test
    fun `TEST-UI-006 given_data_ready_sooner_than_the_minimum_when_the_gate_waits_then_it_completes_at_the_minimum`() =
        runTest {
            // The page arrives after 300 ms, so the minimum is what decides the instant.
            val gate = gate(FakeCharacterRepository(catalogue = catalogue(), latency = 300.milliseconds))
            val completedAt = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MINIMUM - 1.milliseconds)
            runCurrent()
            assertEquals("the splash must not complete before the minimum", null, completedAt.value)

            advanceUntilIdle()
            assertEquals(SplashGate.MINIMUM, completedAt.value)
        }

    @Test
    fun `TEST-UI-006 given_data_slower_than_the_minimum_when_the_gate_waits_then_it_completes_when_the_page_settles`() =
        runTest {
            val gate = gate(FakeCharacterRepository(catalogue = catalogue(), latency = 2.seconds))
            val completedAt = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MINIMUM + 100.milliseconds)
            runCurrent()
            assertEquals("a slow page keeps the splash past the minimum", null, completedAt.value)

            advanceUntilIdle()
            assertEquals("the gate completes when the page settles", 2.seconds, completedAt.value)
            assertTrue("the completion is inside the ceiling", completedAt.value!! <= SplashGate.MAXIMUM)
        }

    @Test
    fun `TEST-UI-006 given_data_that_never_arrives_when_the_ceiling_reaches_then_it_completes_with_no_outcome`() =
        runTest {
            val gate = gate(NeverSettlesRepository())
            val completion = whenItCompletes(gate)

            advanceTimeBy(SplashGate.MAXIMUM - 1.milliseconds)
            runCurrent()
            assertEquals("the splash must not outlast the ceiling", null, completion.value)

            advanceUntilIdle()
            assertEquals(SplashGate.MAXIMUM, completion.value)
            assertEquals("no page settled, so the caller gets no outcome", null, completion.outcome)
        }

    @Test
    fun `TEST-UI-006 given_an_offline_first_page_when_the_gate_waits_then_it_completes_and_reports_the_failure`() =
        runTest {
            val gate = gate(OfflineRepository())
            val completion = whenItCompletes(gate)
            advanceUntilIdle()

            assertEquals("an offline page completes the gate at the minimum, not at the ceiling", SplashGate.MINIMUM, completion.value)
            val outcome = completion.outcome
            assertTrue(
                "the failure is returned so Discovery renders its error state (AC-REQ-FUNC-007-2)",
                outcome is DataResult.Failure,
            )
            assertEquals(ApiFailure.Offline, (outcome as DataResult.Failure).failure)
        }

    @Test
    fun `TEST-UI-006 given_the_documented_bounds_when_they_are_read_then_they_are_one_point_two_and_three_seconds`() {
        assertEquals(1_200L, SplashGate.MINIMUM.inWholeMilliseconds)
        assertEquals(3_000L, SplashGate.MAXIMUM.inWholeMilliseconds)
        assertTrue("the minimum is below the ceiling", SplashGate.MINIMUM < SplashGate.MAXIMUM)
        assertTrue("the ceiling is a real bound, not a minute", SplashGate.MAXIMUM < 10.seconds)
    }

    private fun TestScope.gate(repository: CharacterRepository) =
        SplashGate(repository, StandardTestDispatcher(testScheduler))

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

    private fun catalogue(): FakeCatalogue = FakeCatalogue(List(3) { FakeCatalogue.character("${it + 1}") })

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
