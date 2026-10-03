package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import io.github.davidru85.multiverse.testing.NoCacheStorage
import kotlin.time.Clock

/**
 * The cache a case that is not testing caching injects (`TESTING.md` §6).
 *
 * It is the real [ResponseCache] over a store that retains nothing — so the suite exercises the same
 * read/write path production uses — but its logger is built at the **release** threshold
 * (`ValidatingAppLogger.forRelease`, `DEC-039`), so the `LOG-005`…`LOG-009` cache events a read and a
 * refused write emit never reach the suite's sink. A request-path suite therefore keeps asserting the
 * request events it owns, exactly as it did before the cache existed, instead of pinning an event list
 * that a cache implementation is free to extend.
 *
 * A case that asserts the policy itself builds [ResponseCache] with a
 * [io.github.davidru85.multiverse.testing.FakeCacheStorage] and a debug-threshold logger.
 */
internal fun bypassedCache(clock: Clock): ResponseCache =
    ResponseCache(NoCacheStorage, clock, CachePolicy(), ValidatingAppLogger.forRelease(DiscardingLogSink))

/** The sink a bypassed cache writes to when its threshold admits an event: nothing is kept. */
private object DiscardingLogSink : LogSink {
    override fun write(record: LogRecord): Unit = Unit
}
