package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours

/**
 * `TEST-PERF-003` — a cache-hit page render issues **zero** network requests
 * (`REQ-NFR-003`, `AC-REQ-NFR-003-3`, `PERF-006`).
 *
 * The assertion is deterministic, so `TESTING.md` §10 puts it in the **blocking gate** with the
 * shared tests rather than in the device-dependent measurement job. The functional half of the same
 * behaviour is `TEST-UNIT-009`, which counts calls on the `IC-011` double; this case counts them on
 * the **real transport** instead: the subject is the real `RestCharacterRemoteDataSource` behind the
 * fixture-driven `MockEngine`, so the number this case reads is the number of HTTP requests the
 * engine actually served, not the number of times a fake was entered.
 *
 * That distinction is the whole point of the id. A fake can only prove the repository asked *its
 * seam* again; only the engine proves nothing crossed the wire.
 */
class CacheHitZeroNetworkTest {
    private val clock = MutableFakeClock()
    private val storage = FakeCacheStorage()
    private val sink = RecordingLogSink()
    private val logger = ValidatingAppLogger.forDebug(sink)

    private fun TestScope.repository(remote: RestCharacterRemoteDataSource) =
        RemoteCharacterRepository(
            remote = remote,
            scope = backgroundScope,
            random = FixedRandom(0.5),
            logger = logger,
            cache = ResponseCache(storage, clock, CachePolicy(), logger),
        )

    private fun remote(client: HttpClient) =
        RestCharacterRemoteDataSource(
            client = client,
            decodingDispatcher = UnconfinedTestDispatcher(),
            clock = clock,
            logger = logger,
        )

    /** The real adapter needs the shared client defaults, exactly as the composition root gives them. */
    private fun client(vararg routes: MockHttp.Route): Pair<HttpClient, MutableList<MockHttp.Served>> {
        val (raw, served) = MockHttp.client(*routes)
        return raw.config { rickAndMortyDefaults() } to served
    }

    @Test
    fun `TEST-PERF-003 given_a_fresh_cache_entry_when_the_page_renders_again_then_the_engine_served_no_second_request`() =
        TestTime.run {
            val (client, served) = client(MockHttp.route(fixture = "character-page-01.json", urlContains = "/character"))
            val subject = repository(remote(client))

            val first = subject.page(CharacterFilter(), 1)
            assertIs<DataResult.Success<*>>(first)
            assertEquals(1, served.size, "the first render fetches the page once")

            // Inside the 24 h fresh window (`API_SPECS.md` §7.3) the entry is served from the cache, so
            // the engine must not see the request at all (`PERF-006`).
            clock.advanceBy(1.hours.inWholeMilliseconds)
            val second = subject.page(CharacterFilter(), 1)

            assertIs<DataResult.Success<*>>(second)
            assertEquals(
                expected = 1,
                actual = served.size,
                message = "a cache-hit render issues zero network requests (AC-REQ-NFR-003-3): the engine " +
                    "served ${served.size} request(s), ${served.map { it.url }}",
            )
        }

    @Test
    fun `TEST-PERF-003 given_a_second_identical_call_when_it_loads_then_only_the_first_crossed_the_engine`() =
        TestTime.run {
            val (client, served) = client(MockHttp.route(fixture = "character-page-01.json", urlContains = "/character"))
            val subject = repository(remote(client))

            // Two calls with no clock movement at all: the second is inside the fresh window by
            // construction, so the assertion cannot be confounded by a window boundary.
            assertIs<DataResult.Success<*>>(subject.page(CharacterFilter(), 1))
            assertIs<DataResult.Success<*>>(subject.page(CharacterFilter(), 1))

            assertEquals(
                expected = 1,
                actual = served.size,
                message = "the second call is served from the cache; the engine served ${served.size} request(s)",
            )
        }

    @Test
    fun `TEST-PERF-003 given_a_refresh_when_it_runs_then_it_does_go_to_the_engine`() =
        TestTime.run {
            // The control that keeps the assertion honest: if the engine never saw anything, the two
            // cases above would pass for the wrong reason. A refresh is network-first by policy
            // (`AC-REQ-FUNC-012-1`), so this case proves the harness can observe a request.
            val (client, served) = client(MockHttp.route(fixture = "character-page-01.json", urlContains = "/character"))
            val subject = repository(remote(client))

            assertIs<DataResult.Success<*>>(subject.page(CharacterFilter(), 1))
            assertIs<DataResult.Success<*>>(subject.page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork))

            assertEquals(
                expected = 2,
                actual = served.size,
                message = "a refresh always performs a request, so the engine must have served a second one",
            )
        }
}
