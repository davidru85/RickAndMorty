package io.github.davidru85.multiverse.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import kotlinx.coroutines.flow.first

/**
 * The Android store of `IC-022` (`DEC-017`, ADR-0010): the two settings as keys of a Preferences
 * DataStore, the same store technology the favourites use and a different file.
 *
 * The protocol is written as its stable string, so an enum reorder cannot change a stored choice and
 * an unrecognised value reads as [RemoteProtocol.Rest] (`IC-022`); a missing key reads as the
 * `IC-021` default for that field. Every field is applied in one `edit`, so a write is atomic.
 *
 * The file belongs to the composition root, which builds it beside the favourites file but from its
 * own name, so the two stores share no key and neither can clear the other.
 */
public class DataStoreAppSettingsLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsLocalDataSource {
    override suspend fun read(): AppSettings {
        val preferences = dataStore.data.first()
        return AppSettings(
            soundsEnabled = preferences[SOUNDS_ENABLED] ?: AppSettings().soundsEnabled,
            remoteProtocol = protocolFromWire(preferences[REMOTE_PROTOCOL]),
        )
    }

    override suspend fun write(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[SOUNDS_ENABLED] = settings.soundsEnabled
            preferences[REMOTE_PROTOCOL] = settings.remoteProtocol.wireName()
        }
    }

    private companion object {
        /** The two keys this store owns (`SECURITY.md` §3); the favourites' file has its own. */
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val REMOTE_PROTOCOL = stringPreferencesKey("remote_protocol")
    }
}

/** The stable stored form of a protocol (`IC-022`): never an ordinal, never a display name. */
internal fun RemoteProtocol.wireName(): String =
    when (this) {
        RemoteProtocol.Rest -> "rest"
        RemoteProtocol.GraphQl -> "graphql"
    }

/** The protocol an unrecognised or missing stored value reads as: the default, never an error. */
internal fun protocolFromWire(value: String?): RemoteProtocol =
    when (value) {
        "graphql" -> RemoteProtocol.GraphQl
        else -> RemoteProtocol.Rest
    }
