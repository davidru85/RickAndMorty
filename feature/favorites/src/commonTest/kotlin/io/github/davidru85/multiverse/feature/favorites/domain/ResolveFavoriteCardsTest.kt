package io.github.davidru85.multiverse.feature.favorites.domain

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeRemoteSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-040` — the favourites read path (`IC-020`, `CONF-79`, `DESIGN.md` §4.5).
 *
 * `IC-020`'s sentence "this feature issues no remote request" is contradicted by its own state type —
 * `items` is `List<CharacterCardUi>` — and by `IC-008`/`IC-013` storing ids only. `DESIGN.md` §4.5,
 * higher in the precedence order, settles it: "the UI shows the favourite state instantly and
 * re-fetches details through the normal cached path, so no database is required". This suite pins the
 * three properties that decision implies:
 *
 * - the resolution reads through `CharacterRepository.details(id, enrich = false)`, the seam's own
 *   single-id read, so it reuses the cached path instead of a second data source;
 * - a second resolution of the same id inside the fresh window costs **no network call** — asserted
 *   against the real repository over the real `ResponseCache`, so the claim is about the production
 *   path rather than about a double;
 * - the concurrency is **bounded**, so a large stored set cannot fan out one request per id at once,
 *   and a failure comes back as a value rather than as a thrown exception.
 */
class ResolveFavoriteCardsTest {
    private val catalogue =
        FakeCatalogue(
            characters = (1..6).map { FakeCatalogue.character("$it", name = "Rick $it") },
        )

    @Test
    fun `TEST-UNIT-040 given_favourite_ids_when_they_are_resolved_then_each_is_read_by_id_and_returns_its_summary`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            val resolve = ResolveFavoriteCards(repository, dispatcher = dispatcher)

            val resolution = resolve(setOf(CharacterId("1"), CharacterId("3")))

            assertEquals(
                setOf(CharacterId("1"), CharacterId("3")),
                resolution.summaries.keys,
                "TEST-UNIT-040: every favourite resolves to its own character",
            )
            assertEquals(
                "Rick 1",
                resolution.summaries.getValue(CharacterId("1")).name,
                "TEST-UNIT-040: the summary is the catalogue's own value",
            )
            assertNull(resolution.failure, "TEST-UNIT-040: a clean resolution carries no failure")
            val reads = repository.calls.filterIsInstance<FakeCharacterRepository.Call.Details>()
            assertEquals(2, reads.size, "TEST-UNIT-040: one by-id read per favourite, never a list sweep")
            assertTrue(
                reads.all { !it.enrich },
                "TEST-UNIT-040: the resolution never pays for the episode enrichment the cards do not show",
            )
        }

    @Test
    fun `TEST-UNIT-040 given_a_repeat_resolution_inside_the_fresh_window_when_it_runs_then_it_costs_no_network_call`() =
        TestTime.run { dispatcher ->
            val clock = MutableFakeClock()
            val remote = FakeRemoteSource(catalogue)
            val repository =
                RemoteCharacterRepository(
                    remote = remote,
                    // The repository's coalescing and its background revalidation belong to a scope the
                    // test owns and closes, exactly as `ResponseCacheIntegrationTest` assembles it; the
                    // test scope itself would leave that work uncompleted.
                    scope = backgroundScope,
                    random = FixedRandom(0.5),
                    logger = ValidatingAppLogger.forRelease(RecordingLogSink()),
                    cache = ResponseCache(FakeCacheStorage(), clock, CachePolicy(), ValidatingAppLogger.forRelease(RecordingLogSink())),
                )
            val resolve = ResolveFavoriteCards(repository, dispatcher = dispatcher)

            val first = resolve(setOf(CharacterId("1"), CharacterId("2")))
            assertEquals(2, remote.calls.size, "TEST-UNIT-040: the first resolution reaches the network once per id")

            clock.advanceBy(1.hours.inWholeMilliseconds)
            val second = resolve(setOf(CharacterId("1"), CharacterId("2")))

            assertEquals(2, remote.calls.size, "TEST-UNIT-040: the second resolution inside the fresh window is served from the cache")
            assertEquals(
                first.summaries.keys,
                second.summaries.keys,
                "TEST-UNIT-040: and it returns the same characters",
            )
            assertNull(second.failure, "TEST-UNIT-040: a cached resolution is not a failure")
        }

    @Test
    fun `TEST-UNIT-040 given_more_favourites_than_the_bound_when_they_are_resolved_then_no_more_than_the_bound_are_in_flight`() =
        TestTime.run { dispatcher ->
            val recording = ConcurrencyRecordingRepository(latency = 20.milliseconds)
            val resolve = ResolveFavoriteCards(recording, maxConcurrent = 2, dispatcher = dispatcher)

            val resolution = resolve((1..6).map { CharacterId("$it") }.toSet())

            assertEquals(6, resolution.summaries.size, "TEST-UNIT-040: every favourite still resolves")
            assertEquals(6, recording.reads, "TEST-UNIT-040: one read per id")
            assertTrue(
                recording.maxInFlight <= 2,
                "TEST-UNIT-040: the resolution is bounded, not one request per id (measured ${recording.maxInFlight})",
            )
            assertTrue(
                recording.maxInFlight > 1,
                "TEST-UNIT-040: and it is concurrent within the bound rather than serial",
            )
        }

    @Test
    fun `TEST-UNIT-040 given_a_read_that_fails_when_the_ids_are_resolved_then_the_failure_is_returned_as_a_value`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Offline)
            val resolve = ResolveFavoriteCards(repository, dispatcher = dispatcher)

            val resolution = resolve(setOf(CharacterId("1")))

            assertEquals(ApiFailure.Offline, resolution.failure, "TEST-UNIT-040: the failure is a value, never thrown")
            assertTrue(resolution.summaries.isEmpty(), "TEST-UNIT-040: nothing resolved for the failing read")
        }

    @Test
    fun `TEST-UNIT-040 given_one_failing_id_among_several_when_they_are_resolved_then_the_failure_is_reported_and_the_rest_resolve`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Timeout)
            val resolve = ResolveFavoriteCards(repository, maxConcurrent = 1, dispatcher = dispatcher)

            val resolution = resolve(listOf(CharacterId("1"), CharacterId("2")).toSet())

            assertEquals(ApiFailure.Timeout, resolution.failure, "TEST-UNIT-040: a failure among several is still reported")
            assertEquals(
                1,
                resolution.summaries.size,
                "TEST-UNIT-040: the id that failed carries no summary, and the other one still does",
            )
        }

    @Test
    fun `TEST-UNIT-040 given_an_empty_stored_set_when_it_is_resolved_then_no_request_is_made`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(catalogue)
            val resolve = ResolveFavoriteCards(repository, dispatcher = dispatcher)

            val resolution = resolve(emptySet())

            assertTrue(resolution.summaries.isEmpty(), "TEST-UNIT-040: an empty set resolves to nothing")
            assertTrue(
                repository.calls.isEmpty(),
                "TEST-UNIT-040: and costs no request at all, because the designed empty state shows then",
            )
            assertNull(resolution.failure, "TEST-UNIT-040: an empty set is not a failure")
        }

    /**
     * The repository that measures the concurrency, so the bound is asserted on what the seam observed
     * rather than on the parameter the call site passed. It serves a fixed detail per id, because the
     * subject here is the bound, not the catalogue's own projection.
     */
    private class ConcurrencyRecordingRepository(
        private val latency: kotlin.time.Duration,
    ) : CharacterRepository {
        private val details: Map<CharacterId, CharacterDetails> =
            (1..6).associate { id ->
                CharacterId("$id") to FakeCatalogue.character("$id", name = "Rick $id")
            }

        var maxInFlight: Int = 0
            private set

        var reads: Int = 0
            private set

        private var inFlight: Int = 0

        override suspend fun page(
            filter: io.github.davidru85.multiverse.core.domain.model.CharacterFilter,
            page: Int,
            policy: io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy,
        ): DataResult<io.github.davidru85.multiverse.core.domain.model.CharacterPage> =
            error("the favourites resolution reads details by id, never a list page")

        override suspend fun details(
            id: CharacterId,
            enrich: Boolean,
        ): DataResult<CharacterDetails> {
            reads++
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                delay(latency)
                val character =
                    details[id] ?: return DataResult.Failure(ApiFailure.NotFound("character", id.value), DataSource.NETWORK)
                return DataResult.Success(character, DataSource.NETWORK, isStale = false)
            } finally {
                inFlight--
            }
        }
    }
}
