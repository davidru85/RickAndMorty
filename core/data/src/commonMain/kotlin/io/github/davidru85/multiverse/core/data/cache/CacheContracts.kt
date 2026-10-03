package io.github.davidru85.multiverse.core.data.cache

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * The persistence seam of the response cache (`IC-012`, `DEC-018`, [`adr/0005-caching-strategy.md`]).
 *
 * It stores bytes and the metadata needed to revalidate, and nothing else: freshness, staleness and
 * write admission are the policy of [ResponseCache] above it, evaluated against an injected clock.
 * The complete normalized request identity (`REQ-REL-001`) is [CacheKey]; only [CacheKeyBuilder]
 * builds one.
 */
@JvmInline
public value class CacheKey(
    public val value: String,
)

/**
 * One stored response: its payload and the two facts a later read needs.
 *
 * [storedAt] is epoch time as the injected clock read it, never the wall clock at read time, so a
 * device clock change cannot alter a freshness evaluation (`REQ-REL-004`, `AC-REQ-REL-004-1`).
 * [validator] carries the server's `ETag` when it sent one; it is metadata only — no revalidation
 * round trip is assumed or performed (`adr/0005-caching-strategy.md`).
 */
public class CacheEntry(
    public val payload: ByteArray,
    public val storedAt: Instant,
    public val validator: String? = null,
)

/**
 * The blob store (`IC-012`). One implementation per platform, supplied by the composition root, so
 * this module stays platform-free and the store is testable through [ResponseCache] in `commonTest`.
 *
 * `get` returns `null` for a miss and never throws: an unreadable or corrupt entry is a miss, never a
 * user-visible failure (`REQ-FUNC-020`). A `put` failure is contained by the caller, which degrades to
 * a cache miss rather than propagating an exception to a screen. No method reads the wall clock.
 */
public interface CacheStorage {
    public suspend fun get(key: CacheKey): CacheEntry?

    public suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    )

    public suspend fun evict(key: CacheKey)

    /** Clears response payloads only: never the favourites store (`IC-013`) or the preferences (`IC-022`). */
    public suspend fun evictAll()
}
