package io.github.davidru85.multiverse.feature.settings.presentation

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The `IC-021` double the Settings state-holder cases own (`TESTING.md` §9.1).
 *
 * It honours the seam rather than echoing configuration: [observe] hands a new collector the current
 * value first and then each distinct change, and [update] applies its function to the latest value
 * under one serialised section, counting both the invocation and the write that actually changed the
 * value — so a selection of the protocol already in force is observable as **no** invocation at all
 * (`IC-023`) and an equal-value update would be observable as an invocation with no write.
 *
 * It is not evidence for `IC-021`: the real repository's semantics are `TEST-UNIT-046`'s.
 */
internal class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    private val current = MutableStateFlow(initial)

    /** How many times a use case reached the seam, changed or not. */
    var updates: Int = 0
        private set

    /** How many updates actually replaced the stored value. */
    var writes: Int = 0
        private set

    /** The value a reader sees now. */
    val settings: AppSettings get() = current.value

    override fun observe(): Flow<AppSettings> = current

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        updates++
        val next = change(current.value)
        if (next != current.value) {
            current.value = next
            writes++
        }
    }

    /** Emits [value] as another writer's change would, without counting as this repository's write. */
    fun emit(value: AppSettings) {
        current.value = value
    }
}

/**
 * The `IC-008` double over [FakeFavoritesStore], which owns the set's semantics.
 *
 * It reuses the store the harness already proves and adds the one observation the settings cases
 * need: how many times `clear()` was invoked. A view never reaches it directly — the state holder
 * goes through `ClearFavorites` — so the count is the use case's invocation count.
 */
internal class FakeFavoritesRepository(
    val store: FakeFavoritesStore = FakeFavoritesStore(),
) : FavoritesRepository {
    /** How many times `clear()` was invoked, whether or not the write then succeeded. */
    var clears: Int = 0
        private set

    /** The persisted ids now. */
    val ids: Set<CharacterId> get() = store.backing.ids

    override fun observe(): Flow<Set<CharacterId>> = store.observe()

    override suspend fun toggle(id: CharacterId) {
        if (id in store.backing.ids) store.remove(id) else store.add(id)
    }

    override suspend fun clear() {
        clears++
        store.clear()
    }

    /** Persists [id], as a toggle elsewhere would. */
    suspend fun mark(id: CharacterId) {
        store.add(id)
    }
}
