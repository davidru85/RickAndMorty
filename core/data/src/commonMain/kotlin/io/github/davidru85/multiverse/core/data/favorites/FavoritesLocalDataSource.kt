package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.flow.Flow

/**
 * The storage seam of the favourite id set (`IC-013`, ADR-0007, `DEC-017`): one implementation per
 * platform — Preferences DataStore on Android, `UserDefaults` on Apple — measured against one contract
 * (`TEST-INT-003`). [add] and [remove] are idempotent; [clear] empties the set in one write and a clear
 * of the empty set writes nothing; [observe] emits the persisted set to a new collector first, then
 * each change, and never completes. Only canonical id strings are stored. `IC-008` is its only consumer.
 */
public interface FavoritesLocalDataSource {
    public fun observe(): Flow<Set<CharacterId>>

    public suspend fun add(id: CharacterId)

    public suspend fun remove(id: CharacterId)

    public suspend fun clear()
}
