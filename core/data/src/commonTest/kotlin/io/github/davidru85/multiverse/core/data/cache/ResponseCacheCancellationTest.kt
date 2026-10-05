package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `TEST-UNIT-068` — the response cache contains a storage failure but never a cancellation
 * (`IC-012`, `AC-REQ-FUNC-022-2`, `AGENTS.md` §8, `TASK-111`).
 *
 * An unreadable or unwritable store costs a miss, never an exception (`IC-012`). A
 * `CancellationException` is not a storage failure: it is how structured concurrency stops the
 * caller, and a cache that swallowed it would keep a cancelled load running and let it publish. Each
 * operation is driven over a store that throws one or the other.
 */
class ResponseCacheCancellationTest {
    private val key = CacheKey("rest|GET|/api/character|page=1")
    private val record =
        CachedPayloadRecord(
            kind = CachedPayloadRecord.PAGE,
            page = CachedPage(characters = emptyList(), page = 1, pageCount = 1, totalCount = 0, nextPage = null),
        )

    private fun cache(storage: CacheStorage) =
        ResponseCache(storage, MutableFakeClock(), CachePolicy(), ValidatingAppLogger.forDebug(RecordingLogSink()))

    @Test
    fun `TEST-UNIT-068 given_a_cancelled_caller_when_the_cache_reads_then_the_cancellation_propagates`() =
        TestTime.run {
            assertFailsWith<CancellationException>("TEST-UNIT-068: a read must not turn a cancellation into a miss") {
                cache(ThrowingStorage { CancellationException("cancelled") }).read(key, LogOperation.CHARACTER_LIST, page = 1)
            }
        }

    @Test
    fun `TEST-UNIT-068 given_a_cancelled_caller_when_the_cache_writes_then_the_cancellation_propagates`() =
        TestTime.run {
            assertFailsWith<CancellationException>("TEST-UNIT-068: a write must not swallow a cancellation") {
                cache(ThrowingStorage { CancellationException("cancelled") }).store(key, record)
            }
        }

    @Test
    fun `TEST-UNIT-068 given_a_cancelled_caller_when_the_cache_evicts_then_the_cancellation_propagates`() =
        TestTime.run {
            assertFailsWith<CancellationException>("TEST-UNIT-068: an eviction must not swallow a cancellation") {
                cache(ThrowingStorage { CancellationException("cancelled") }).evict(key)
            }
        }

    @Test
    fun `TEST-UNIT-068 given_a_failing_store_when_the_cache_is_used_then_the_failure_is_still_contained`() =
        TestTime.run {
            val cache = cache(ThrowingStorage { IllegalStateException("disk full") })

            assertNull(cache.read(key, LogOperation.CHARACTER_LIST, page = 1), "TEST-UNIT-068: an unreadable store is a miss")
            cache.store(key, record)
            cache.evict(key)
        }
}

/** A store whose every operation throws what [failure] builds. */
private class ThrowingStorage(
    private val failure: () -> Exception,
) : CacheStorage {
    override suspend fun get(key: CacheKey): CacheEntry? = throw failure()

    override suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    ): Unit = throw failure()

    override suspend fun evict(key: CacheKey): Unit = throw failure()

    override suspend fun evictAll(): Unit = throw failure()
}
