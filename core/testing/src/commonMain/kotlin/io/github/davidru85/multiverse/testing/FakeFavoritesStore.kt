package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * The behavioural double of `IC-013` (`DEC-072`, `TASK-040`), for the favourites repository's tests.
 *
 * It honours the seam rather than echoing configuration: `add` and `remove` are idempotent set
 * operations, `clear` empties the set in one write and an unchanged set is never written or
 * re-emitted, writes are serialised, and [observe] hands every new collector the persisted set first,
 * then each distinct change, without ever completing. What survives a simulated process restart is
 * the [backing] state, held outside the store instance: a new store over the same [Backing] reads what
 * the old one wrote. A [latency] spent in virtual time inside each write lets a test interleave
 * concurrent writers, and [failNextWrite] makes the next write throw, as a platform store's I/O can.
 *
 * It is not evidence of persistence: the real DataStore and `UserDefaults` stores are proved by
 * `TEST-INT-003`/`TEST-INT-004` against the same contract.
 */
public class FakeFavoritesStore(
    public val backing: Backing = Backing(),
    private val latency: Duration = Duration.ZERO,
) : FavoritesLocalDataSource {
    /** The persisted state a simulated restart keeps: share one between two store instances. */
    public class Backing {
        internal val persisted = MutableStateFlow<Set<CharacterId>>(emptySet())

        /** The ids persisted now. */
        public val ids: Set<CharacterId> get() = persisted.value

        /** How many writes changed the persisted set. */
        public var writes: Int = 0
            internal set
    }

    private val mutex = Mutex()
    private var failuresToThrow = 0

    /** Makes the next write — of any kind — throw instead of persisting. */
    public fun failNextWrite() {
        failuresToThrow++
    }

    override fun observe(): Flow<Set<CharacterId>> = backing.persisted.asStateFlow()

    override suspend fun add(id: CharacterId): Unit = write { it + id }

    override suspend fun remove(id: CharacterId): Unit = write { it - id }

    override suspend fun clear(): Unit = write { emptySet() }

    private suspend fun write(change: (Set<CharacterId>) -> Set<CharacterId>) {
        mutex.withLock {
            delay(latency)
            if (failuresToThrow > 0) {
                failuresToThrow--
                throw IllegalStateException("simulated favourites write failure")
            }
            val next = change(backing.persisted.value)
            if (next != backing.persisted.value) {
                backing.persisted.value = next
                backing.writes++
            }
        }
    }
}
