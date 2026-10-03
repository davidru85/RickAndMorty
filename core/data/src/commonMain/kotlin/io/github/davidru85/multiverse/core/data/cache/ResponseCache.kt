package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlin.time.Clock

/** A stored entry: the record plus the freshness it was classified with. */
public class CacheHit(
    public val entry: CacheEntry,
    public val record: CachedPayloadRecord,
    public val freshness: CacheFreshness,
) {
    public val page: CharacterPage? get() = CachedPayloadMapper.page(record)

    public val details: CharacterDetails? get() = CachedPayloadMapper.details(record)
}

/** Stub: no read ever answers and no write is retained. */
public class ResponseCache(
    private val storage: CacheStorage,
    private val clock: Clock,
    private val policy: CachePolicy,
    private val logger: AppLogger,
) {
    public val freshness: CachePolicy get() = policy

    public suspend fun read(
        key: CacheKey,
        operation: LogOperation,
        page: Int?,
    ): CacheHit? = null

    public suspend fun miss(
        operation: LogOperation,
        page: Int?,
    ): Unit = Unit

    public suspend fun refuse(
        outcome: io.github.davidru85.multiverse.core.domain.logging.LogOutcome,
        failure: ApiFailure?,
    ): Unit = Unit

    public suspend fun serveFallback(
        hit: CacheHit,
        operation: LogOperation,
        page: Int?,
        failure: ApiFailure,
    ): CacheHit = hit

    public suspend fun store(
        key: CacheKey,
        record: CachedPayloadRecord,
    ): Unit = Unit

    public suspend fun evict(key: CacheKey): Unit = Unit

    public fun classify(entry: CacheEntry): CacheFreshness = CacheFreshness.EXPIRED
}
