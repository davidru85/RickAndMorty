package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The settings repository (`IC-021`, `DEC-055`, `adr/0010-settings-destination.md`) over the store of
 * `IC-022`: the user's Sounds preference and the remote-data-source choice.
 *
 * It keeps one in-process snapshot of the persisted value, so [observe] is hot, conflated and emits
 * the current value to a new collector immediately without touching the store until it changes — which
 * is what lets `Settings` render before the store has answered, and what keeps a second screen from
 * starting a second disk read. [update] runs under one lock, reads the latest persisted value, applies
 * [change] and writes only when the result differs, so concurrent updates are serialised and an
 * unchanged value neither writes nor emits.
 *
 * The protocol is persisted through `IC-022` by its stable string, so an unknown stored value reads as
 * [RemoteProtocol.Rest] and an enum reorder cannot change a stored choice.
 */
public class LocalAppSettingsRepository(
    private val local: AppSettingsLocalDataSource,
) : AppSettingsRepository {
    private val writes = Mutex()
    private val cache = MutableStateFlow<AppSettings?>(null)

    override fun observe(): Flow<AppSettings> = cache.filterNotNull().distinctUntilChanged()

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        writes.withLock {
            val before = stored()
            val after = change(before)
            if (after == before) return@withLock
            local.write(after)
            cache.value = after
        }
    }

    /** The current value, for a caller that has no flow collection yet. */
    public suspend fun current(): AppSettings = stored()

    /** The persisted value, read once per process and kept until a write replaces it. */
    private suspend fun stored(): AppSettings = cache.value ?: local.read().also { cache.value = it }
}
