package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.cache.CacheEntry
import io.github.davidru85.multiverse.core.data.cache.CacheKey
import io.github.davidru85.multiverse.core.data.cache.CacheStorage

/**
 * The behavioural double of `IC-012` (`DEC-072`, `TASK-020`): an in-memory store a test can inspect.
 *
 * It keeps exactly what it is given and returns exactly what it kept, so a cache-policy test asserts the
 * policy rather than the store. A test can drive the two failure modes the seam promises to contain —
 * [failReads] and [failWrites] — to prove a broken store degrades to a miss instead of a crash
 * (`IC-012`).
 */
public class FakeCacheStorage : CacheStorage {
    private val entries = mutableMapOf<CacheKey, CacheEntry>()

    /** Reads that throw, so a test can prove a store failure is a miss, never a user-visible failure. */
    public var failReads: Boolean = false

    /** Writes that throw, for the same reason on the write path. */
    public var failWrites: Boolean = false

    /** How many writes were attempted, refused or not. */
    public var writes: Int = 0
        private set

    /** The keys currently stored, in insertion order. */
    public val keys: List<CacheKey> get() = entries.keys.toList()

    /** Clears the store without touching the counters, so one case can be followed by another. */
    public fun reset() {
        entries.clear()
        failReads = false
        failWrites = false
        writes = 0
    }

    override suspend fun get(key: CacheKey): CacheEntry? {
        check(!failReads) { "FakeCacheStorage is configured to fail reads" }
        return entries[key]
    }

    override suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    ) {
        writes++
        check(!failWrites) { "FakeCacheStorage is configured to fail writes" }
        entries[key] = entry
    }

    override suspend fun evict(key: CacheKey) {
        entries.remove(key)
    }

    override suspend fun evictAll() {
        entries.clear()
    }
}
