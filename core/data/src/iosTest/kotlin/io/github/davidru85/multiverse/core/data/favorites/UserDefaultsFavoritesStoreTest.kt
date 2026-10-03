package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-INT-003` against the real `UserDefaults` store on the Apple simulator, and its
 * `TEST-INT-004` cases: the stored value is the list of canonical ids under one namespaced key, a
 * clear leaves every other key of the suite untouched, and a malformed stored value degrades to the
 * readable part plus `LOG-019` (`SECURITY.md` §6.3). Each test owns a uniquely named suite and removes
 * it afterwards; a new store over the suite is a process restart.
 */
class UserDefaultsFavoritesStoreTest : FavoritesStoreContractTest() {
    private val suite = "io.github.davidru85.multiverse.test.favorites.${Random.nextLong().toULong()}"

    private fun defaults() = requireNotNull(NSUserDefaults(suiteName = suite))

    override suspend fun TestScope.open(): FavoritesLocalDataSource = UserDefaultsFavoritesLocalDataSource(defaults(), logger)

    override suspend fun TestScope.close() = Unit

    override fun wipe() {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
    }

    @AfterTest
    fun release() {
        wipe()
    }

    @Test
    fun `TEST-INT-004 given_marked_ids_when_the_suite_is_read_then_it_holds_only_the_ids_under_one_key`() =
        TestTime.run {
            val store = open()
            store.add(CharacterId("42"))
            store.add(CharacterId("1"))

            assertEquals(listOf("1", "42"), defaults().arrayForKey(UserDefaultsFavoritesLocalDataSource.KEY), "TEST-INT-004: ids only")
            assertEquals(
                listOf(UserDefaultsFavoritesLocalDataSource.KEY),
                defaults()
                    .dictionaryRepresentation()
                    .keys
                    .map { it.toString() }
                    .filter { it.startsWith("multiverse.") },
                "TEST-INT-004: one key in the app's namespace (REQ-SEC-003, AC-REQ-SEC-003-1)",
            )
        }

    @Test
    fun `TEST-INT-004 given_another_key_in_the_suite_when_favourites_are_cleared_then_it_is_untouched`() =
        TestTime.run {
            defaults().setBool(true, forKey = "multiverse.settings.soundsEnabled")
            val store = open()
            store.add(CharacterId("1"))

            store.clear()

            assertEquals(null, defaults().objectForKey(UserDefaultsFavoritesLocalDataSource.KEY))
            assertEquals(
                true,
                defaults().boolForKey("multiverse.settings.soundsEnabled"),
                "TEST-INT-004: a clear removes the favourites key only",
            )
        }

    @Test
    fun `TEST-INT-004 given_a_malformed_stored_value_when_the_store_opens_then_it_reads_the_valid_part_and_logs_LOG_019`() =
        TestTime.run {
            defaults().setObject(listOf("1", 2, "3"), forKey = UserDefaultsFavoritesLocalDataSource.KEY)

            val read = open().observe().first()

            assertEquals(setOf(CharacterId("1"), CharacterId("3")), read, "TEST-INT-004: an unreadable entry is dropped, never a crash")
            assertEquals(listOf("LOG-019"), sink.records.map { it.catalogueId })
        }

    @Test
    fun `TEST-INT-004 given_a_value_of_the_wrong_type_when_the_store_opens_then_it_reads_empty_and_logs_LOG_019`() =
        TestTime.run {
            defaults().setObject("1,2", forKey = UserDefaultsFavoritesLocalDataSource.KEY)

            assertEquals(emptySet(), open().observe().first(), "TEST-INT-004: a value that is not a list reads as empty")
            assertEquals(listOf("LOG-019"), sink.records.map { it.catalogueId })
        }
}
