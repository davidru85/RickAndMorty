package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeRemoteSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-021` — concurrent identical requests share one remote call (`REQ-REL-002`,
 * `AC-REQ-REL-002-1`, `CONTRACTS.md` `IC-007`), with the scope ownership `TASK-038` defines: the shared
 * work runs in the repository's owner scope, a cancelled waiter stops waiting without cancelling the
 * other waiters, the work is cancelled when no waiter remains or the owner closes, and a finished
 * entry is removed so a later identical call runs again. The remote is the `IC-011` double with
 * latency spent in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestCoalescingTest {
    private val catalogue =
        FakeCatalogue(
            characters = (1..30).map { FakeCatalogue.character("$it", name = "Rick $it", episodeIds = listOf("1")) },
            episodes = listOf(FakeCatalogue.episode("1")),
        )

    private fun remote() = FakeRemoteSource(catalogue, latency = 300.milliseconds)

    @Test
    fun `TEST-UNIT-021 given_two_concurrent_identical_page_loads_when_both_run_then_one_remote_call_serves_both`() =
        TestTime.run {
            val remote = remote()
            val repository =
                RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            val first = async { repository.page(CharacterFilter(query = "Rick"), 1) }
            val second = async { repository.page(CharacterFilter(query = "  Rick "), 1) }

            assertEquals(first.await(), second.await(), "TEST-UNIT-021: both callers receive the shared outcome")
            assertEquals(1, remote.calls.size, "TEST-UNIT-021: one call for two identical loads (the query is normalized)")
        }

    @Test
    fun `TEST-UNIT-021 given_requests_that_differ_in_identity_when_concurrent_then_each_runs`() =
        TestTime.run {
            val remote = remote()
            val repository =
                RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            listOf(
                async { repository.page(CharacterFilter(), 1) },
                async { repository.page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork) },
                async { repository.page(CharacterFilter(), 2) },
                async { repository.page(CharacterFilter(status = StatusFilter.Alive), 1) },
                async { repository.details(CharacterId("1"), enrich = false) },
                async { repository.details(CharacterId("1"), enrich = true) },
            ).forEach { it.await() }

            assertEquals(
                6,
                remote.calls.count { it !is FakeRemoteSource.Call.Episodes },
                "TEST-UNIT-021: policy, page, filter and enrichment are all part of the identity (DEC-086)",
            )
        }

    @Test
    fun `TEST-UNIT-021 given_one_cancelled_waiter_when_another_still_waits_then_the_shared_call_completes_for_it`() =
        TestTime.run {
            val remote = remote()
            val repository =
                RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            val leaving = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }
            val staying = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }
            advanceTimeBy(100)
            leaving.cancel()

            assertFailsWith<CancellationException>("TEST-UNIT-021") { leaving.await() }
            staying.await()
            assertEquals(1, remote.calls.size, "TEST-UNIT-021: still one remote call")
            assertEquals(0, remote.cancellations, "TEST-UNIT-021: one waiter leaving does not cancel the others")
        }

    @Test
    fun `TEST-UNIT-021 given_every_waiter_cancelled_when_none_remains_then_the_shared_call_is_cancelled`() =
        TestTime.run {
            val remote = remote()
            val repository =
                RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }
            advanceTimeBy(100)
            first.cancel()
            second.cancel()
            // `advanceUntilIdle` stops once only `backgroundScope` work is left; the cancelled shared
            // call resumes at this instant, so running the current instant is what reaches it.
            runCurrent()

            assertEquals(1, remote.cancellations, "TEST-UNIT-021: work nobody waits for is cancelled, not left running")
        }

    @Test
    fun `TEST-UNIT-021 given_a_finished_call_when_the_same_request_comes_again_then_it_runs_again`() =
        TestTime.run {
            val remote = remote()
            val repository =
                RemoteCharacterRepository(remote, backgroundScope, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            repository.page(CharacterFilter(), 1)
            repository.page(CharacterFilter(), 1)

            assertEquals(2, remote.calls.size, "TEST-UNIT-021: coalescing is not caching; a finished entry is removed")
        }

    @Test
    fun `TEST-UNIT-021 given_the_owner_scope_closed_when_a_shared_call_is_in_flight_then_it_is_cancelled`() =
        TestTime.run {
            val remote = remote()
            val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val repository = RemoteCharacterRepository(remote, owner, FixedRandom(0.5), ValidatingAppLogger.forDebug(RecordingLogSink()))

            val waiter = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }
            advanceTimeBy(100)
            owner.cancel()
            advanceUntilIdle()

            assertFailsWith<CancellationException>("TEST-UNIT-021: the owner's end ends the shared work") { waiter.await() }
            assertEquals(1, remote.cancellations)
        }
}
