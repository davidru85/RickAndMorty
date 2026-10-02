package io.github.davidru85.multiverse.core.domain.repository

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.flow.Flow

/**
 * The favourite id set (`IC-008`). [observe] is hot, conflated and never completes; [toggle] and
 * [clear] are serialised writes that return after the change is persisted. The set is local to the
 * device and the implementation performs no network request. Implemented by `TASK-040`.
 */
public interface FavoritesRepository {
    public fun observe(): Flow<Set<CharacterId>>

    public suspend fun toggle(id: CharacterId)

    public suspend fun clear()
}
