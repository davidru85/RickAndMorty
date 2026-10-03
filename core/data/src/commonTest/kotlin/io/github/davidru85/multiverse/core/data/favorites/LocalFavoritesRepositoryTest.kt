package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-004` — the favourites repository (`IC-008`, ADR-0007) over the `IC-013` double: a toggle
 * flips exactly once per call, concurrent toggles and clears are serialised so no update is lost, a
 * clear is one write, the set survives a simulated restart, a write failure is logged and never thrown
 * while a cancellation propagates, and `ObserveFavoriteIds` (`IC-009`) reads the real repository. The
 * real platform stores are `TEST-INT-003`/`TEST-INT-004`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalFavoritesRepositoryTest {
    private val rick = CharacterId("424242")

    private class Subject(
        val store: FakeFavoritesStore,
        val sink: RecordingLogSink,
        val repository: LocalFavoritesRepository,
    )

    private fun subject(store: FakeFavoritesStore = FakeFavoritesStore()): Subject {
        val sink = RecordingLogSink()
        return Subject(store, sink, LocalFavoritesRepository(store, ValidatingAppLogger.forDebug(sink)))
    }

    @Test
    fun `TEST-UNIT-004 given_a_character_when_toggled_twice_then_it_is_marked_and_unmarked_once_each`() =
        TestTime.run {
            val s = subject()
            val seen = mutableListOf<Set<CharacterId>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.repository.observe().toList(seen) }

            s.repository.toggle(rick)
            s.repository.toggle(rick)

            assertEquals(listOf(emptySet(), setOf(rick), emptySet()), seen, "TEST-UNIT-004: one flip per call, emitted once each")
            assertEquals(2, s.store.backing.writes)
        }

    @Test
    fun `TEST-UNIT-004 given_a_toggle_when_the_store_is_reopened_then_the_character_is_still_marked`() =
        TestTime.run {
            val backing = FakeFavoritesStore.Backing()
            subject(FakeFavoritesStore(backing)).repository.toggle(rick)

            val reopened = subject(FakeFavoritesStore(backing)).repository

            assertEquals(setOf(rick), reopened.observe().first(), "TEST-UNIT-004: persistence belongs to the store (AC-REQ-FUNC-006-2)")
        }

    @Test
    fun `TEST-UNIT-004 given_concurrent_toggles_of_one_character_when_they_overlap_then_no_flip_is_lost`() =
        TestTime.run {
            val s = subject(FakeFavoritesStore(latency = 50.milliseconds))

            List(2) { async { s.repository.toggle(rick) } }.awaitAll()
            (1..10).map { async { s.repository.toggle(CharacterId("$it")) } }.awaitAll()

            assertEquals(
                (1..10).map { CharacterId("$it") }.toSet(),
                s.store.backing.ids,
                "TEST-UNIT-004: two flips of one id cancel out and ten distinct flips all land",
            )
        }

    @Test
    fun `TEST-UNIT-004 given_marked_characters_when_cleared_then_one_write_empties_the_set_and_a_second_clear_does_nothing`() =
        TestTime.run {
            val s = subject()
            s.repository.toggle(rick)
            s.repository.toggle(CharacterId("1"))
            val seen = mutableListOf<Set<CharacterId>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.repository.observe().toList(seen) }

            s.repository.clear()
            s.repository.clear()

            assertEquals(listOf(setOf(rick, CharacterId("1")), emptySet()), seen, "TEST-UNIT-004: the empty set is emitted once")
            assertEquals(3, s.store.backing.writes, "TEST-UNIT-004: two toggles, one clear, and nothing for the empty clear")
        }

    @Test
    fun `TEST-UNIT-004 given_a_failing_write_when_toggled_then_nothing_is_thrown_the_set_is_kept_and_the_failure_is_logged`() =
        TestTime.run {
            val s = subject()
            s.store.failNextWrite()

            s.repository.toggle(rick)

            assertEquals(emptySet(), s.repository.observe().first(), "TEST-UNIT-004: the last consistent set stays (ADR-0007)")
            val degraded = s.sink.records.single()
            assertEquals("LOG-019", degraded.catalogueId)
            assertEquals(LogLevel.ERROR, degraded.level)
            assertEquals("FAVORITES_STORE", degraded.fields[LogField.COMPONENT])
            assertEquals("UNKNOWN", degraded.fields[LogField.ERROR_CLASS])
        }

    @Test
    fun `TEST-UNIT-004 given_a_toggle_when_it_is_written_then_it_is_logged_without_the_id`() =
        TestTime.run {
            val s = subject()

            s.repository.toggle(rick)

            val toggled = s.sink.records.single()
            assertEquals("LOG-018", toggled.catalogueId)
            assertEquals(LogLevel.INFO, toggled.level)
            assertEquals(mapOf(LogField.COMPONENT to "FAVORITES_STORE", LogField.OUTCOME to "SUCCESS"), toggled.fields)
            assertTrue(
                s.sink.records.none { rick.value in it.toString() },
                "TEST-UNIT-004: no id reaches a sink (OBSERVABILITY.md 3 rule 2)",
            )
        }

    @Test
    fun `TEST-UNIT-004 given_a_toggle_cancelled_mid_write_then_the_cancellation_propagates_and_is_not_a_store_failure`() =
        TestTime.run {
            val s = subject(FakeFavoritesStore(latency = 50.milliseconds))

            val toggle = async(start = CoroutineStart.UNDISPATCHED) { s.repository.toggle(rick) }
            advanceTimeBy(10)
            toggle.cancel()

            assertFailsWith<CancellationException>("TEST-UNIT-004") { toggle.await() }
            assertEquals(emptyList(), s.sink.records.map { it.catalogueId }, "TEST-UNIT-004: a cancellation is never logged as a failure")
        }

    @Test
    fun `TEST-UNIT-004 given_the_cross_feature_use_case_when_observed_then_it_reads_the_real_repository`() =
        TestTime.run {
            val s = subject()
            s.repository.toggle(CharacterId("1"))
            val observeFavoriteIds = ObserveFavoriteIds(s.repository)
            val seen = mutableListOf<Set<CharacterId>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeFavoriteIds().toList(seen) }

            s.repository.toggle(rick)

            assertEquals(
                listOf(setOf(CharacterId("1")), setOf(CharacterId("1"), rick)),
                seen,
                "TEST-UNIT-004: persisted set first, then changes (IC-009)",
            )
        }
}
