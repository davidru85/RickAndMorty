package io.github.davidru85.multiverse.core.data.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * The Android store of `IC-013` (ADR-0007, `DEC-017`): the favourite ids as one string-set key of a
 * Preferences DataStore.
 *
 * DataStore makes each `edit` an atomic, durable read-modify-write and skips a write that changes
 * nothing, so [add] and [remove] are idempotent, and [clear] removes the favourites key in one write
 * while every other key of the file stays. [observe] emits the persisted set to a new collector once
 * DataStore has read the file, off the caller's thread, then each change.
 */
public class DataStoreFavoritesLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) : FavoritesLocalDataSource {
    override fun observe(): Flow<Set<CharacterId>> =
        dataStore.data
            .map { preferences -> preferences[FAVORITE_IDS].orEmpty().mapTo(mutableSetOf(), ::CharacterId).toSet() }
            .distinctUntilChanged()

    override suspend fun add(id: CharacterId) {
        dataStore.edit { preferences -> preferences[FAVORITE_IDS] = preferences[FAVORITE_IDS].orEmpty() + id.value }
    }

    override suspend fun remove(id: CharacterId) {
        dataStore.edit { preferences ->
            val remaining = preferences[FAVORITE_IDS].orEmpty() - id.value
            if (remaining.isEmpty()) preferences.remove(FAVORITE_IDS) else preferences[FAVORITE_IDS] = remaining
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(FAVORITE_IDS) }
    }

    private companion object {
        /** The one key the favourites own in the file (`SECURITY.md` §3). */
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
    }
}

/**
 * The app's Preferences DataStore over [file] — a `.preferences_pb` file in the app's private files
 * directory, which the composition root supplies — running in [scope]. A file that cannot be decoded
 * is replaced by an empty one and reported as `LOG-019`, so a corrupted store degrades to an empty set
 * rather than a crash (`SECURITY.md` §6.3). One instance per file per process, as DataStore requires.
 */
public fun preferencesDataStore(
    file: File,
    scope: CoroutineScope,
    logger: AppLogger,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler =
            ReplaceFileCorruptionHandler {
                logger.log(LogLevel.ERROR) { LogEvent.FavoritesStoreDegraded(screen = null) }
                emptyPreferences()
            },
        scope = scope,
        produceFile = { file },
    )
