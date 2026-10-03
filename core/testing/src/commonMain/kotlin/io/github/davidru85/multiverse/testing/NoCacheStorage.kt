package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.cache.CacheEntry
import io.github.davidru85.multiverse.core.data.cache.CacheKey
import io.github.davidru85.multiverse.core.data.cache.CacheStorage

/**
 * A store that retains nothing (`IC-012`, `DEC-072`).
 *
 * A case that tests the retry policy, the coalescing scope ownership or the transport logging is not
 * testing the cache, and a real store would change what those cases observe: a repeat load inside the
 * freshness window stops reaching the network (`AC-REQ-FUNC-020-1`). Injecting this store makes that
 * isolation explicit instead of implicit, and the cache suites use [FakeCacheStorage] when the policy
 * is what is under test.
 */
public object NoCacheStorage : CacheStorage {
    override suspend fun get(key: CacheKey): CacheEntry? = null

    override suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    ): Unit = Unit

    override suspend fun evict(key: CacheKey): Unit = Unit

    override suspend fun evictAll(): Unit = Unit
}
