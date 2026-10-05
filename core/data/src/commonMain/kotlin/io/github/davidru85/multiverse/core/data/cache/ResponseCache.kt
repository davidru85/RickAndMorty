package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.data.logging.currentCorrelationId
import io.github.davidru85.multiverse.core.data.logging.errorClass
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** A stored entry: the record plus the freshness it was classified with. */
public class CacheHit(
    public val entry: CacheEntry,
    public val record: CachedPayloadRecord,
    public val freshness: CacheFreshness,
) {
    /** The page this hit holds, or `null` when it holds a detail. */
    public val page: CharacterPage? get() = CachedPayloadMapper.page(record)

    /** The detail this hit holds, or `null` when it holds a page. */
    public val details: CharacterDetails? get() = CachedPayloadMapper.details(record)
}

/**
 * The application-level response cache (`IC-012`, `DEC-012`, `DEC-018`,
 * [`adr/0005-caching-strategy.md`](../../../../../../../../../../docs/adr/0005-caching-strategy.md)).
 *
 * It owns identity, write admission, the freshness bands and the `DataResult.source`/`isStale`
 * metadata the UI renders; it delegates only raw bytes to [CacheStorage]. Freshness is computed from
 * the injected [clock], so no read consults the wall clock and a device clock change cannot alter an
 * evaluation (`REQ-REL-004`, `AC-REQ-REL-004-1`).
 *
 * **Write admission.** An entry is written only for a complete, successfully decoded response.
 * [store] is called only with a value the caller admitted, and a refusal is logged as `LOG-008`
 * through [refuse]. Every `ApiFailure`, an empty body and a partial response carrying warnings has no
 * code path to a write, which is what closes the cacheable filtered-`404` hazard
 * (`AC-REQ-FUNC-020-3`, `RISK-005`, `API_SPECS.md` §7.1).
 *
 * **An unreadable entry is a miss.** A record that cannot be decoded is discarded with `LOG-009` and
 * the caller fetches: a corrupt file never reaches a screen as a failure (`IC-012`). A `put` failure
 * is contained the same way, and a cancellation is never contained: it propagates to the caller.
 *
 * Every read logs `LOG-005` (hit) or `LOG-006` (miss) with the catalogue's fields
 * (`OBSERVABILITY.md` §3) — never the key, a query or a body.
 */
public class ResponseCache(
    private val storage: CacheStorage,
    private val clock: Clock,
    private val policy: CachePolicy,
    private val logger: AppLogger,
) {
    /** The policy this cache evaluates with. */
    public val freshness: CachePolicy get() = policy

    /**
     * The entry stored under [key], decoded and classified, or `null` when there is none to use.
     * An entry past the offline fallback is returned classified [CacheFreshness.EXPIRED]: the caller
     * may evict it rather than serve it.
     */
    public suspend fun read(
        key: CacheKey,
        operation: LogOperation,
        page: Int?,
    ): CacheHit? {
        val entry = readEntry(key) ?: return null
        val record = decode(entry.payload)
        if (record == null) {
            logger.log(LogLevel.WARN) { LogEvent.CacheEntryDiscarded(currentCorrelationId()) }
            contained { storage.evict(key) }
            return null
        }
        val freshness = classify(entry)
        logger.log(LogLevel.DEBUG) {
            LogEvent.CacheHit(
                source = DataSource.DISK_CACHE,
                operation = operation,
                page = page,
                isStale = freshness != CacheFreshness.FRESH,
                correlationId = currentCorrelationId(),
            )
        }
        return CacheHit(entry, record, freshness)
    }

    /** Logs a miss (`LOG-006`). Called when the caller decides to go to the network. */
    public suspend fun miss(
        operation: LogOperation,
        page: Int?,
    ) {
        logger.log(LogLevel.DEBUG) { LogEvent.CacheMiss(operation, page, currentCorrelationId()) }
    }

    /**
     * Records that the write guard refused an entry (`LOG-008`). [failure] is the outcome the guard
     * rejected; it is `null` for a usable-but-partial response, refused for the same reason.
     */
    public suspend fun refuse(
        outcome: LogOutcome,
        failure: ApiFailure?,
    ) {
        logger.log(LogLevel.DEBUG) {
            LogEvent.CacheWriteSkipped(outcome, failure?.errorClass(), currentCorrelationId())
        }
    }

    /** Serves a fallback entry (`LOG-007`) and returns it: the fetch failed but content exists. */
    public suspend fun serveFallback(
        hit: CacheHit,
        operation: LogOperation,
        page: Int?,
        failure: ApiFailure,
    ): CacheHit {
        logger.log(LogLevel.WARN) {
            LogEvent.StaleFallbackServed(operation, page, failure.errorClass(), currentCorrelationId())
        }
        return hit
    }

    /** Writes [record] under [key]; a store that refuses the write costs a miss, never an exception. */
    public suspend fun store(
        key: CacheKey,
        record: CachedPayloadRecord,
    ) {
        val entry = CacheEntry(encode(record), clock.now())
        contained { storage.put(key, entry) }
    }

    /** Drops an entry this read found unusable, so the next read is a clean miss. */
    public suspend fun evict(key: CacheKey) {
        contained { storage.evict(key) }
    }

    /** Where [entry] stands against the policy, evaluated on the injected clock. */
    public fun classify(entry: CacheEntry): CacheFreshness {
        val age = clock.now() - entry.storedAt
        // A device clock moved backwards reads as a negative age; it is treated as fresh rather than
        // as expired, and the entry keeps its own stored timestamp (adr/0005-caching-strategy.md).
        if (age.isNegative()) return CacheFreshness.FRESH
        return when {
            age <= policy.fresh -> CacheFreshness.FRESH
            age <= policy.staleWhileRevalidate -> CacheFreshness.STALE
            age <= policy.offlineFallback -> CacheFreshness.OFFLINE_FALLBACK
            else -> CacheFreshness.EXPIRED
        }
    }

    private suspend fun readEntry(key: CacheKey): CacheEntry? = contained { storage.get(key) }

    /**
     * Runs one storage operation whose failure costs a miss, never an exception (`IC-012`). A
     * `CancellationException` is not a storage failure: it is how the caller is stopped, so it reaches
     * the caller unchanged (`AGENTS.md` §8, `AC-REQ-FUNC-022-2`) instead of letting a cancelled load
     * continue as if the store were merely empty.
     */
    private inline fun <T> contained(operation: () -> T): T? =
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

    private fun encode(record: CachedPayloadRecord): ByteArray =
        CACHE_JSON.encodeToString(CachedPayloadRecord.serializer(), record).encodeToByteArray()

    private fun decode(bytes: ByteArray): CachedPayloadRecord? =
        try {
            CACHE_JSON.decodeFromString(CachedPayloadRecord.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        /**
         * The private format of the store. It is not the wire contract: a format change degrades to a
         * cold cache instead of a decoding error, and an unknown record kind fails like any other
         * malformed record.
         */
        val CACHE_JSON: Json = Json { ignoreUnknownKeys = false }
    }
}
