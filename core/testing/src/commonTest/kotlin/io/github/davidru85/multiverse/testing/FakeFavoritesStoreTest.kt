package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-053` — `FakeFavoritesStore` honours `IC-013` rather than echoing configuration
 * (`TESTING.md` §6.1, `DEC-072`). These cases prove the double's semantics only; the real stores are
 * `TEST-INT-003`/`TEST-INT-004`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeFavoritesStoreTest {
    private val rick = CharacterId("1")
    private val morty = CharacterId("2")

    @Test
    fun `TEST-UNIT-053 given_writes_when_observed_then_the_current_set_comes_first_and_only_changes_follow`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val seen = mutableListOf<Set<CharacterId>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.observe().toList(seen) }

            store.add(rick)
            store.add(rick)
            store.remove(morty)
            store.clear()
            store.clear()

            assertEquals(listOf(emptySet(), setOf(rick), emptySet()), seen, "TEST-UNIT-053: idempotent operations emit nothing")
            assertEquals(2, store.backing.writes, "TEST-UNIT-053: an unchanged set is never written")
        }

    @Test
    fun `TEST-UNIT-053 given_a_shared_backing_when_a_new_store_reads_then_it_sees_what_the_old_one_wrote`() =
        TestTime.run {
            val backing = FakeFavoritesStore.Backing()
            FakeFavoritesStore(backing).add(rick)

            assertEquals(setOf(rick), FakeFavoritesStore(backing).observe().first(), "TEST-UNIT-053: the backing is the restart boundary")
            assertEquals(emptySet(), FakeFavoritesStore().observe().first(), "TEST-UNIT-053: a fresh backing is a fresh install")
        }

    @Test
    fun `TEST-UNIT-053 given_concurrent_writers_when_they_overlap_then_every_write_lands`() =
        TestTime.run {
            val store = FakeFavoritesStore(latency = 10.milliseconds)

            (1..20).map { async { store.add(CharacterId("$it")) } }.awaitAll()

            assertEquals((1..20).map { CharacterId("$it") }.toSet(), store.backing.ids, "TEST-UNIT-053: writes are serialised")
        }

    @Test
    fun `TEST-UNIT-053 given_a_queued_failure_when_the_next_write_runs_then_it_throws_once_and_persists_nothing`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            store.failNextWrite()

            assertFailsWith<IllegalStateException>("TEST-UNIT-053") { store.add(rick) }
            store.add(morty)

            assertEquals(setOf(morty), store.backing.ids, "TEST-UNIT-053: the failed write persisted nothing, the next one lands")
        }
}
