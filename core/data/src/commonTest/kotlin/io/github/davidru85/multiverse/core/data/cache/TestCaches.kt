package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.testing.NoCacheStorage
import io.github.davidru85.multiverse.testing.RecordingLogSink
import kotlin.time.Clock

/**
 * The cache a case that is not testing caching injects (`TESTING.md` §6).
 *
 * It is the real [ResponseCache] over a store that retains nothing, so a suite exercises the same
 * read/write path production uses while observing exactly what it observed before the cache existed.
 * A case that asserts the policy builds the cache with [io.github.davidru85.multiverse.testing.FakeCacheStorage]
 * and a moved clock instead.
 */
internal fun bypassedCache(
    clock: Clock,
    logger: ValidatingAppLogger = ValidatingAppLogger.forDebug(RecordingLogSink()),
): ResponseCache = ResponseCache(NoCacheStorage, clock, CachePolicy(), logger)
