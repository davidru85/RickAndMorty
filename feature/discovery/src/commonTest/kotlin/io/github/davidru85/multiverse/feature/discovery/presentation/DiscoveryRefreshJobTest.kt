package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeCharacterRepository.Call
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-083` — a manual refresh can be awaited (`IC-018`, `DEC-134`, `REQ-FUNC-012`,
 * `AC-REQ-FUNC-012-1`, `TASK-112`).
 *
 * No screen offered a refresh gesture, so `REQ-FUNC-012` was unreachable. Android's pull-to-refresh is
 * bound to `isRefreshing`; iOS's `.refreshable` instead awaits its own work, so the reducer's refresh
 * returns the job of the refresh it starts, which ends exactly when `isRefreshing` clears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryRefreshJobTest {
    @Test
    fun `TEST-UNIT-083 given_content_when_a_refresh_is_started_then_its_job_ends_with_the_network_revalidation`() =
        TestTime.run { dispatcher ->
            val repository =
                FakeCharacterRepository(FakeCatalogue((1..3).map { FakeCatalogue.character("$it") }), latency = 300.milliseconds)
            val reducer =
                DiscoveryReducer(FakeCharacterPager(repository, backgroundScope), backgroundScope, dispatcher, DefaultPresentationFormatters)
            reducer.start()
            advanceTimeBy(400.milliseconds)
            runCurrent()

            val job = reducer.refresh()
            advanceTimeBy(100.milliseconds)
            runCurrent()

            assertTrue(job.isActive, "TEST-UNIT-083: the job is still running while the network answers")
            assertTrue(reducer.state.value.isRefreshing, "TEST-UNIT-083: and the state says so, for a state-bound control")
            advanceTimeBy(300.milliseconds)
            runCurrent()
            assertFalse(job.isActive, "TEST-UNIT-083: the job ends when the refresh ends")
            assertFalse(reducer.state.value.isRefreshing)
            assertEquals(
                PageLoadPolicy.ForceNetwork,
                repository.calls
                    .filterIsInstance<Call.Page>()
                    .last()
                    .policy,
                "TEST-UNIT-083: a refresh goes to the network even with a fresh cache (AC-REQ-FUNC-012-1)",
            )
        }
}
