package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-INT-003` — the one favourites store contract (`IC-013`, ADR-0007, `TESTING.md` §6.2), written
 * once and run against each platform's real store: the Preferences DataStore actual on the Android
 * host and the `UserDefaults` actual on the Apple simulator. A platform subclass supplies the store
 * over storage it owns; closing and reopening it over the same storage is a process restart, and
 * wiping that storage is a reinstall. The platform-specific cases of each subclass are `TEST-INT-004`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class FavoritesStoreContractTest {
    /** Every record the stores of this test wrote. */
    protected val sink = RecordingLogSink()

    protected val logger: ValidatingAppLogger = ValidatingAppLogger.forDebug(sink)

    /** A store over this test's storage; the previous one is closed first. */
    protected abstract suspend fun TestScope.open(): FavoritesLocalDataSource

    /** Releases what [open] acquired, so the same storage can be reopened. */
    protected abstract suspend fun TestScope.close()

    /** Deletes this test's storage, as a reinstall or a clear of app data would. */
    protected abstract fun wipe()

    private fun ids(vararg values: String) = values.map(::CharacterId).toSet()

    @Test
    fun `TEST-INT-003 given_a_fresh_store_when_ids_are_added_and_removed_then_the_set_follows_and_idempotent_calls_emit_nothing`() =
        TestTime.run {
            val store = open()
            val seen = mutableListOf<Set<CharacterId>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.observe().toList(seen) }

            store.add(CharacterId("1"))
            store.add(CharacterId("1"))
            store.add(CharacterId("2"))
            store.remove(CharacterId("3"))
            store.remove(CharacterId("1"))
            runCurrent()

            assertEquals(ids("2"), store.observe().first(), "TEST-INT-003: add and remove are set operations")
            assertEquals(emptySet(), seen.first(), "TEST-INT-003: a fresh store is empty")
            assertTrue(seen.zipWithNext().none { (a, b) -> a == b }, "TEST-INT-003: an unchanged set is never re-emitted: $seen")
            close()
        }

    @Test
    fun `TEST-INT-003 given_ids_when_the_store_is_reopened_then_a_new_collector_first_sees_the_persisted_set`() =
        TestTime.run {
            open().run {
                add(CharacterId("1"))
                add(CharacterId("42"))
            }
            close()

            val reopened = open()

            assertEquals(ids("1", "42"), reopened.observe().first(), "TEST-INT-003: a restart keeps the set, and no empty set comes first")
            close()
        }

    @Test
    fun `TEST-INT-003 given_marked_ids_when_cleared_then_the_set_is_empty_and_stays_empty_after_a_restart`() =
        TestTime.run {
            val store = open()
            store.add(CharacterId("1"))
            store.add(CharacterId("2"))

            store.clear()
            store.clear()
            close()

            assertEquals(emptySet(), open().observe().first(), "TEST-INT-003: one clear empties the persisted set")
            close()
        }

    @Test
    fun `TEST-INT-003 given_concurrent_writes_when_they_overlap_then_every_write_lands`() =
        TestTime.run {
            val store = open()

            (1..20).map { async { store.add(CharacterId("$it")) } }.awaitAll()
            (1..20 step 2).map { async { store.remove(CharacterId("$it")) } }.awaitAll()

            assertEquals((2..20 step 2).map { CharacterId("$it") }.toSet(), store.observe().first(), "TEST-INT-003: no write is lost")
            close()
        }

    @Test
    fun `TEST-INT-003 given_a_large_set_and_canonical_strings_when_reopened_then_every_id_round_trips_exactly`() =
        TestTime.run {
            val many = (1..300).map { CharacterId("$it") } + CharacterId("007") + CharacterId("7")
            val store = open()
            many.forEach { store.add(it) }
            close()

            assertEquals(many.toSet(), open().observe().first(), "TEST-INT-003: ids are canonical strings, never coerced (IC-001)")
            close()
        }

    @Test
    fun `TEST-INT-003 given_a_marked_set_when_the_storage_is_wiped_then_a_reopened_store_is_empty`() =
        TestTime.run {
            open().add(CharacterId("1"))
            close()

            wipe()

            assertEquals(emptySet(), open().observe().first(), "TEST-INT-003: no id leaks across a reinstall")
            close()
        }
}
