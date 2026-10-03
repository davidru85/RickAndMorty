package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * `TEST-UNIT-009` and `TEST-UNIT-023` — the cache policy, its write admission and its clock.
 *
 * Every case drives the real [ResponseCache] over the behavioural store and moves an injected clock,
 * so the freshness bands, the staleness metadata and the never-cache rule are asserted against the
 * policy rather than against a double's idea of it (`REQ-FUNC-020`, `REQ-REL-004`, `AC-REQ-FUNC-020-3`,
 * `AC-REQ-REL-004-1`).
 */
class ResponseCacheTest {
    private val clock = MutableFakeClock()
    private val storage = FakeCacheStorage()
    private val sink = RecordingLogSink()
    private val logger = ValidatingAppLogger.forDebug(sink)
    private val cache = ResponseCache(storage, clock, CachePolicy(), logger)
    private val key = CacheKey("rest|GET|/api/character|page=1")

    private fun record(characters: Int = 2): CachedPayloadRecord =
        CachedPayloadRecord(
            kind = CachedPayloadRecord.PAGE,
            page =
                CachedPage(
                    characters =
                        (1..characters).map {
                            CachedCharacter(
                                id = it.toString(),
                                name = "Character $it",
                                status = "alive",
                                species = "Human",
                                gender = "male",
                                location = CachedLocation(name = "Earth"),
                                imageUrl = IMAGE_URL,
                            )
                        },
                    page = 1,
                    pageCount = 42,
                    totalCount = 826,
                    nextPage = 2,
                ),
        )

    @Test
    fun `TEST-UNIT-009 given_a_fresh_entry_when_it_is_read_then_it_is_served_and_no_network_is_needed`() {
        TestTime.run {
            cache.store(key, record())
            clock.advanceBy(1.hours.inWholeMilliseconds)

            val hit = assertNotNull(cache.read(key, operation = OPERATION, page = 1), "a stored entry is a hit")
            assertEquals(CacheFreshness.FRESH, hit.freshness, "inside the fresh window the entry answers")
            assertEquals(826, hit.page?.totalCount, "the stored page comes back unchanged")
            assertEquals(2, hit.page?.characters?.size, "and carries everything it held")
            assertNull(hit.details, "a page record holds no detail")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_an_entry_past_freshness_when_it_is_read_then_it_is_stale_and_still_served`() {
        TestTime.run {
            cache.store(key, record())
            clock.advanceBy(48.hours.inWholeMilliseconds)

            val hit = assertNotNull(cache.read(key, operation = OPERATION, page = 1))
            assertEquals(CacheFreshness.STALE, hit.freshness, "24 h..7 d is the revalidate window")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_an_entry_past_the_revalidate_window_when_it_is_read_then_it_is_only_a_fallback`() {
        TestTime.run {
            cache.store(key, record())
            clock.advanceBy(20.days.inWholeMilliseconds)

            val hit = assertNotNull(cache.read(key, operation = OPERATION, page = 1))
            assertEquals(CacheFreshness.OFFLINE_FALLBACK, hit.freshness, "7 d..30 d is the offline fallback")
        }
    }

    @Test
    fun `TEST-UNIT-023 given_an_entry_past_the_offline_window_when_it_is_read_then_it_is_expired`() {
        TestTime.run {
            cache.store(key, record())
            clock.advanceBy(31.days.inWholeMilliseconds)

            val hit = assertNotNull(cache.read(key, operation = OPERATION, page = 1))
            assertEquals(CacheFreshness.EXPIRED, hit.freshness, "past 30 d the entry is not usable")
        }
    }

    @Test
    fun `TEST-UNIT-023 given_a_clock_moved_backwards_when_an_entry_is_classified_then_it_is_fresh_not_expired`() {
        TestTime.run {
            cache.store(key, record())
            // The device clock jumped backwards: a negative age must not be read as expiry.
            val entry = assertNotNull(storage.get(key))
            val backwards = CacheEntry(entry.payload, entry.storedAt + 1.hours)
            assertEquals(CacheFreshness.FRESH, cache.classify(backwards), "a negative age is clamped to fresh")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_an_injected_clock_when_the_policy_is_evaluated_then_no_wall_clock_is_consulted`() {
        TestTime.run {
            cache.store(key, record())
            // Two entries stored at the same instant classify identically however far the test moves,
            // because the only clock in the path is the injected one.
            val first = assertNotNull(cache.read(key, operation = OPERATION, page = 1))
            clock.advanceBy(CachePolicy().fresh.inWholeMilliseconds)
            val second = assertNotNull(cache.read(key, operation = OPERATION, page = 1))
            assertEquals(CacheFreshness.FRESH, first.freshness)
            assertEquals(CacheFreshness.FRESH, second.freshness, "the boundary instant is still fresh")
            clock.advanceBy(1)
            assertEquals(CacheFreshness.STALE, assertNotNull(cache.read(key, operation = OPERATION, page = 1)).freshness)
        }
    }

    @Test
    fun `TEST-UNIT-009 given_an_undecodable_entry_when_it_is_read_then_it_is_a_miss_and_is_discarded`() {
        TestTime.run {
            storage.put(key, CacheEntry("{ not a record".encodeToByteArray(), clock.now()))

            assertNull(cache.read(key, operation = OPERATION, page = 1), "a corrupt entry never reaches a screen")
            assertTrue(storage.keys.isEmpty(), "the corrupt entry is discarded")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_a_store_that_fails_reads_when_a_read_happens_then_it_is_a_miss_not_a_failure`() {
        TestTime.run {
            cache.store(key, record())
            storage.failReads = true

            assertNull(cache.read(key, operation = OPERATION, page = 1), "a store failure is a miss")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_a_store_that_fails_writes_when_a_write_happens_then_the_caller_is_not_failed`() {
        TestTime.run {
            storage.failWrites = true
            cache.store(key, record())

            assertEquals(1, storage.writes, "the write was attempted")
            assertNull(cache.read(key, operation = OPERATION, page = 1), "and the read degrades to a miss")
        }
    }

    @Test
    fun `TEST-UNIT-009 given_the_never_cache_rule_when_a_refusal_is_recorded_then_LOG_008_carries_it`() {
        TestTime.run {
            cache.refuse(outcome = LogOutcome.FAILURE, failure = ApiFailure.EmptyBody)

            val record = sink.records.single { it.catalogueId == "LOG-008" }
            assertEquals("RESPONSE_CACHE", record.fields[LogField.COMPONENT], "the component is the response cache")
            assertEquals("FAILURE", record.fields[LogField.OUTCOME])
            assertEquals("EMPTY_BODY", record.fields[LogField.ERROR_CLASS])
            assertEquals("DEBUG", record.level.name, "a refused write is debug, not an error")
        }
    }

    private companion object {
        val OPERATION = LogOperation.CHARACTER_LIST

        /**
         * A URL-shaped value the cache stores verbatim (`IC-016`: the image URL is the cache key).
         * It names no real host, so the no-live-host rule of `TESTING.md` §4.1 stays satisfied; nothing
         * in this suite fetches it.
         */
        const val IMAGE_URL: String = "https://images.invalid/avatar/portrait.png"
    }
}
