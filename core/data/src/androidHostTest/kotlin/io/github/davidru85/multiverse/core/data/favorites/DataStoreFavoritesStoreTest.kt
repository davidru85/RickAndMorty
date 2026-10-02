package io.github.davidru85.multiverse.core.data.favorites

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-INT-003` against the real Preferences DataStore store on the Android host, and its
 * `TEST-INT-004` cases: the file holds only the canonical ids under one key, a clear leaves every
 * other preference key untouched, and a corrupted file degrades to an empty set plus `LOG-019`
 * instead of a crash (`SECURITY.md` §6.3). Each test owns a temporary directory; a store's scope is
 * cancelled before the file is reopened, as DataStore allows one active instance per file.
 */
class DataStoreFavoritesStoreTest : FavoritesStoreContractTest() {
    private val directory: File = Files.createTempDirectory("favorites").toFile()
    private val file = File(directory, "test.preferences_pb")
    private var job: Job? = null

    override suspend fun TestScope.open(): FavoritesLocalDataSource {
        close()
        val owner = Job(backgroundScope.coroutineContext[Job])
        job = owner
        val dataStore = preferencesDataStore(file, CoroutineScope(backgroundScope.coroutineContext + owner), logger)
        return DataStoreFavoritesLocalDataSource(dataStore)
    }

    override suspend fun TestScope.close() {
        job?.cancelAndJoin()
        job = null
    }

    override fun wipe() {
        file.delete()
    }

    @AfterTest
    fun release() {
        directory.deleteRecursively()
    }

    /** Reads or edits the file directly, as another reader of the same store would. */
    private suspend fun TestScope.raw(edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit = {}): Map<String, Any> {
        close()
        val owner = Job(backgroundScope.coroutineContext[Job])
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(backgroundScope.coroutineContext + owner)) { file }
        dataStore.edit(edit)
        val contents =
            dataStore.data
                .first()
                .asMap()
                .mapKeys { it.key.name }
        owner.cancelAndJoin()
        return contents
    }

    @Test
    fun `TEST-INT-004 given_marked_ids_when_the_file_is_read_then_it_holds_only_the_ids_under_one_key`() =
        TestTime.run {
            val store = open()
            store.add(CharacterId("1"))
            store.add(CharacterId("42"))

            assertEquals(mapOf("favorite_ids" to setOf("1", "42")), raw(), "TEST-INT-004: ids only (REQ-SEC-003, AC-REQ-SEC-003-1)")
        }

    @Test
    fun `TEST-INT-004 given_another_preference_in_the_file_when_favourites_are_cleared_then_it_is_untouched`() =
        TestTime.run {
            raw { it[booleanPreferencesKey("sounds_enabled")] = true }
            val store = open()
            store.add(CharacterId("1"))

            store.clear()

            assertEquals(mapOf("sounds_enabled" to true), raw(), "TEST-INT-004: a clear removes the favourites key only (IC-013)")
        }

    @Test
    fun `TEST-INT-004 given_a_corrupted_file_when_the_store_opens_then_it_reads_empty_logs_LOG_019_and_keeps_working`() =
        TestTime.run {
            file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x13, 0x00, 0x5A, 0x01))

            val store = open()
            val read = store.observe().first()
            store.add(CharacterId("1"))

            assertEquals(emptySet(), read, "TEST-INT-004: an unreadable store degrades to empty, never to a crash")
            assertEquals(listOf("LOG-019"), sink.records.map { it.catalogueId })
            assertEquals(setOf(CharacterId("1")), store.observe().first(), "TEST-INT-004: the store keeps working")
        }
}
