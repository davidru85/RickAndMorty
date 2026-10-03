package io.github.davidru85.multiverse.core.data.favorites

import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * The favourites repository (`IC-008`, ADR-0007) over the store of `IC-013`, which owns persistence:
 * the repository keeps no copy of the set.
 *
 * A toggle reads the persisted set and writes the one flip it implies; toggles and clears share one
 * lock, so concurrent writes are serialised and no flip is lost. A write that fails is logged as
 * `LOG-019` and not thrown — the last consistent set stays and the app keeps running (ADR-0007,
 * `SECURITY.md` §6.3) — while a cancellation propagates unchanged. A written toggle is logged as
 * `LOG-018`, which carries no id (`OBSERVABILITY.md` §3 rule 2). Nothing here touches the network.
 */
public class LocalFavoritesRepository(
    private val local: FavoritesLocalDataSource,
    private val logger: AppLogger,
) : FavoritesRepository {
    private val writes = Mutex()

    override fun observe(): Flow<Set<CharacterId>> = local.observe().distinctUntilChanged()

    override suspend fun toggle(id: CharacterId) {
        writes.withLock {
            val marked = id in local.observe().first()
            val written = persist { if (marked) local.remove(id) else local.add(id) }
            if (written) logger.log(LogLevel.INFO) { LogEvent.FavoritesToggled(LogOutcome.SUCCESS) }
        }
    }

    override suspend fun clear() {
        writes.withLock { persist { local.clear() } }
    }

    /** Runs [write]; a failure other than cancellation is logged and reported as `false`. */
    private suspend fun persist(write: suspend () -> Unit): Boolean =
        try {
            write()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            logger.log(LogLevel.ERROR) { LogEvent.FavoritesStoreDegraded(screen = null) }
            false
        }
}
