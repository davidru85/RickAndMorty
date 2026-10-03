package io.github.davidru85.multiverse.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-046`'s platform half — the Android settings store over a real DataStore.
 *
 * The store is built once for the file, because DataStore permits exactly one instance per file per
 * process — which is also what the composition root does. The cases then assert the key names, the
 * stable protocol string and the fresh-install default against the technology that ships rather than
 * against the in-memory double (`IC-022`, `TESTING.md` §6.2). Durability across a real process restart
 * is a device-level event and stays with `TEST-INT-004` (`TESTING.md` §14.2).
 */
class DataStoreAppSettingsLocalDataSourceTest {
    private val directory: File = Files.createTempDirectory("multiverse-settings-test").toFile()
    private val file: File = directory.resolve("settings.preferences_pb")
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun store() = DataStoreAppSettingsLocalDataSource(dataStore)

    @Test
    fun `TEST-UNIT-046 given_a_fresh_store_when_it_is_read_then_the_fresh_install_defaults_are_returned`() =
        runTest {
            assertEquals(AppSettings(), store().read(), "Sounds off and REST on a fresh install")
        }

    @Test
    fun `TEST-UNIT-046 given_a_written_value_when_it_is_read_back_then_only_the_two_settings_keys_are_persisted`() =
        runTest {
            store().write(AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl))

            assertEquals(
                AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl),
                store().read(),
                "the value round-trips through the store",
            )
            assertEquals(
                setOf("sounds_enabled", "remote_protocol"),
                dataStore.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
                    .toSet(),
                "the store owns exactly the two IC-022 keys and no personal data (REQ-SEC-003)",
            )
        }

    @Test
    fun `TEST-UNIT-046 given_a_stored_protocol_string_when_it_is_unknown_then_the_store_reads_rest`() =
        runTest {
            // Writing the key directly is what an older build or a foreign process leaves behind.
            dataStore.edit { preferences -> preferences[stringPreferencesKey("remote_protocol")] = "quux" }

            assertEquals(
                RemoteProtocol.Rest,
                store().read().remoteProtocol,
                "an unrecognised value degrades to the IC-021 default rather than failing (IC-022)",
            )
        }

    @Test
    fun `TEST-UNIT-046 given_the_protocol_when_it_is_written_then_it_is_the_stable_string_not_an_ordinal`() =
        runTest {
            store().write(AppSettings(remoteProtocol = RemoteProtocol.GraphQl))

            assertEquals(
                "graphql",
                dataStore.data.first()[stringPreferencesKey("remote_protocol")],
                "an enum reorder must not change a stored choice (IC-022)",
            )
        }
}
