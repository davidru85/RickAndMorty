package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

/**
 * The Apple store of `IC-013` (ADR-0007, `DEC-017`): the favourite ids as one sorted string array
 * under a namespaced key of [defaults].
 *
 * `UserDefaults` is not reactive, so the store keeps the observable set in process: it is read once
 * when the store is built and updated on every write, so [observe] hands a new collector the persisted
 * set first without touching storage. Writes from another process or an app extension are not
 * observed; the app is single-process (ADR-0007). Writes are serialised, a write that changes nothing
 * is skipped, and [clear] removes the key in one write while every other key stays. A stored value
 * that is not a list of strings keeps its readable part and is reported as `LOG-019` (`SECURITY.md`
 * §6.3).
 */
public class UserDefaultsFavoritesLocalDataSource(
    private val defaults: NSUserDefaults,
    private val logger: AppLogger,
) : FavoritesLocalDataSource {
    private val writes = Mutex()
    private val persisted = MutableStateFlow(read())

    override fun observe(): Flow<Set<CharacterId>> = persisted.asStateFlow()

    override suspend fun add(id: CharacterId): Unit = write { it + id }

    override suspend fun remove(id: CharacterId): Unit = write { it - id }

    override suspend fun clear(): Unit = write { emptySet() }

    private suspend fun write(change: (Set<CharacterId>) -> Set<CharacterId>) {
        writes.withLock {
            val next = change(persisted.value)
            if (next == persisted.value) return@withLock
            if (next.isEmpty()) {
                defaults.removeObjectForKey(KEY)
            } else {
                defaults.setObject(next.map { it.value }.sorted(), forKey = KEY)
            }
            persisted.value = next
        }
    }

    private fun read(): Set<CharacterId> {
        val stored = defaults.objectForKey(KEY) ?: return emptySet()
        val values = stored as? List<*>
        if (values == null || values.any { it !is String }) {
            logger.log(LogLevel.ERROR) { LogEvent.FavoritesStoreDegraded(screen = null) }
        }
        return values
            .orEmpty()
            .filterIsInstance<String>()
            .mapTo(mutableSetOf(), ::CharacterId)
            .toSet()
    }

    public companion object {
        /** The one key the favourites own in the suite (`SECURITY.md` §3). */
        public const val KEY: String = "multiverse.favorites.ids"
    }
}
