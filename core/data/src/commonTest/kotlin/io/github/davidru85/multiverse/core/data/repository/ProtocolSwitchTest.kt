package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CacheKeyBuilder
import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.RemoteProtocolSource
import io.github.davidru85.multiverse.core.data.remote.settingsProtocolSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeRemoteSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-034`, `TEST-UNIT-048` and `TEST-UNIT-049` — the runtime protocol switch of `REQ-FUNC-034`
 * (`AC-REQ-FUNC-034-1`…`AC-REQ-FUNC-034-4`, `DEC-056`, `adr/0011-runtime-remote-protocol.md`).
 *
 * The active protocol is a per-request decision: the repository asks its [RemoteProtocolSource], and
 * the pager observes the same source's changes. A switch is therefore an identity change — the load
 * in flight is cancelled, the pager resets to page 1 and reloads through the newly selected adapter,
 * and the two protocols' cache keys never collide, so a switch neither evicts nor reuses the other's
 * entries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolSwitchTest {
    private val all = CharacterFilter()

    private val restCatalogue = FakeCatalogue((1..45).map { FakeCatalogue.character("$it", name = "Rest $it") })
    private val graphQlCatalogue = FakeCatalogue((1..45).map { FakeCatalogue.character("$it", name = "GraphQl $it") })

    /** The active protocol as one mutable source, observed by the repository and by the pager. */
    private class TestProtocolSource(
        private val protocols: MutableStateFlow<RemoteProtocol>,
    ) : RemoteProtocolSource {
        override suspend fun current(): RemoteProtocol = protocols.value

        override fun changes(): Flow<RemoteProtocol> = protocols
    }

    private fun logger() = ValidatingAppLogger.forDebug(RecordingLogSink())

    private fun cache(storage: FakeCacheStorage) = ResponseCache(storage, MutableFakeClock(), CachePolicy(), logger())

    /**
     * The repository under the test's own scope and virtual dispatcher: the shared work must run in the
     * test scheduler's time, or a latency the case reasons about elapses on a real thread.
     */
    private fun TestScope.repository(
        rest: FakeRemoteSource,
        graphQl: FakeRemoteSource,
        protocols: MutableStateFlow<RemoteProtocol>,
        storage: FakeCacheStorage = FakeCacheStorage(),
    ) = RemoteCharacterRepository(
        rest = rest,
        graphQl = graphQl,
        scope = this,
        random = FixedRandom(0.5),
        logger = logger(),
        cache = cache(storage),
        protocols = TestProtocolSource(protocols),
    )

    private fun TestScope.pager(
        repository: RemoteCharacterRepository,
        protocols: Flow<RemoteProtocol>,
    ) = RepositoryCharacterPager(repository, this, logger(), protocolChanges = protocols)

    /** Every state the pager publishes, in order: an unconfined collector sees each value it is set to. */
    private fun TestScope.record(pager: CharacterPager): List<PagerState> {
        val states = mutableListOf<PagerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pager.state.collect { states += it } }
        return states
    }

    private fun <T> DataResult<T>.value(): T =
        when (this) {
            is DataResult.Success -> value
            is DataResult.Failure -> throw AssertionError("expected a success, got $failure")
        }

    private fun FakeRemoteSource.Call.page() = this as FakeRemoteSource.Call.Page

    @Test
    fun `TEST-UNIT-034 given_a_fresh_install_when_a_page_loads_then_the_rest_adapter_answers`() =
        TestTime.run {
            // `AC-REQ-FUNC-034-1`: `AppSettings.remoteProtocol` defaults to REST, and the source
            // resolves the default without a settings read.
            val rest = FakeRemoteSource(restCatalogue)
            val graphQl = FakeRemoteSource(graphQlCatalogue)

            val page = repository(rest, graphQl, MutableStateFlow(RemoteProtocol.Rest)).page(all, 1).value()

            assertEquals("Rest 1", page.characters.first().name, "TEST-UNIT-034: the default protocol is REST")
            assertEquals(1, rest.calls.size)
            assertTrue(graphQl.calls.isEmpty(), "TEST-UNIT-034: the unselected adapter is never called")
        }

    @Test
    fun `TEST-UNIT-034 given_the_settings_store_when_the_protocol_is_read_then_it_is_the_persisted_choice`() =
        TestTime.run {
            val store = FakeAppSettingsStore()
            val source = settingsProtocolSource(settings = FakeRepository(store))

            assertEquals(RemoteProtocol.Rest, source.current(), "TEST-UNIT-034: a fresh install reads REST (AC-REQ-FUNC-034-1)")
            store.write(
                io.github.davidru85.multiverse.core.domain.model
                    .AppSettings(remoteProtocol = RemoteProtocol.GraphQl),
            )
            assertEquals(
                RemoteProtocol.GraphQl,
                source.current(),
                "TEST-UNIT-034: the next request sees the stored choice (IC-021)",
            )
        }

    @Test
    fun `TEST-UNIT-048 given_a_page_load_in_flight_when_the_protocol_changes_then_it_is_cancelled_and_no_old_item_is_emitted`() =
        TestTime.run {
            val rest = FakeRemoteSource(restCatalogue, latency = 300.milliseconds)
            val graphQl = FakeRemoteSource(graphQlCatalogue)
            val protocols = MutableStateFlow(RemoteProtocol.Rest)
            val pager = pager(repository(rest, graphQl, protocols), protocols)
            val states = record(pager)

            val superseded = async { pager.setFilter(all) }
            advanceTimeBy(100)
            protocols.value = RemoteProtocol.GraphQl
            advanceUntilIdle()
            superseded.await()

            assertEquals(1, rest.cancellations, "TEST-UNIT-048: the switch cancels the load in flight (AC-REQ-FUNC-034-2)")
            assertEquals(
                listOf(1 to all, 1 to all),
                listOf(
                    rest.calls
                        .single()
                        .page()
                        .let { it.page to it.filter },
                ) +
                    listOf(
                        graphQl.calls
                            .single()
                            .page()
                            .let { it.page to it.filter },
                    ),
                "TEST-UNIT-048: page 1 of the same filter is reloaded through the newly selected adapter",
            )
            assertTrue(
                states.none { state -> state.items.any { it.name.startsWith("Rest ") } },
                "TEST-UNIT-048: no item fetched through the previous protocol is ever emitted",
            )
            assertTrue(states.none { it.failure != null }, "TEST-UNIT-048: a cancellation is never a failure (API-ERR-017)")
            assertEquals(
                (1..20).map { "GraphQl $it" },
                pager.state.value.items
                    .map { it.name },
            )
            assertEquals(all, pager.state.value.filter, "TEST-UNIT-048: the filter is kept; only the identity changed")
            assertFalse(pager.state.value.isStale)
        }

    @Test
    fun `TEST-UNIT-048 given_loaded_pages_when_the_protocol_changes_then_the_pager_resets_to_page_one`() =
        TestTime.run {
            val rest = FakeRemoteSource(restCatalogue)
            val graphQl = FakeRemoteSource(graphQlCatalogue)
            val protocols = MutableStateFlow(RemoteProtocol.Rest)
            val pager = pager(repository(rest, graphQl, protocols), protocols)
            pager.setFilter(all)
            assertEquals(20, pager.state.value.items.size)

            pager.next()
            assertEquals(40, pager.state.value.items.size, "the append is in place before the switch")

            protocols.value = RemoteProtocol.GraphQl
            advanceUntilIdle()

            assertEquals(
                (1..20).map { "GraphQl $it" },
                pager.state.value.items
                    .map { it.name },
                "TEST-UNIT-048: a switch resets to page 1 rather than appending to the other protocol's pages",
            )
            assertFalse(pager.state.value.isEndReached)
        }

    @Test
    fun `TEST-UNIT-049 given_the_same_filter_and_page_when_the_identity_is_built_for_both_protocols_then_it_differs`() =
        TestTime.run {
            val rest = CacheKeyBuilder.page(RemoteProtocol.Rest, all, 1).value
            val graphQl = CacheKeyBuilder.page(RemoteProtocol.GraphQl, all, 1).value

            assertTrue(rest.startsWith("rest|GET|/api/character|"), "TEST-UNIT-049: the REST identity is the GET of the list route")
            assertTrue(
                graphQl.startsWith("graphql|POST|/graphql|"),
                "TEST-UNIT-049: the GraphQL identity is the POST of /graphql (ADR-0011)",
            )
            assertTrue(rest != graphQl, "TEST-UNIT-049: the protocol is part of the identity (AC-REQ-FUNC-034-4)")
        }

    @Test
    fun `TEST-UNIT-049 given_a_cached_page_when_the_protocol_changes_then_the_other_entry_is_neither_read_nor_evicted`() =
        TestTime.run {
            val storage = FakeCacheStorage()
            val rest = FakeRemoteSource(restCatalogue)
            val graphQl = FakeRemoteSource(graphQlCatalogue)
            val protocols = MutableStateFlow(RemoteProtocol.Rest)
            val repository = repository(rest, graphQl, protocols, storage)

            val overRest = repository.page(all, 1).value()
            protocols.value = RemoteProtocol.GraphQl
            val overGraphQl = repository.page(all, 1).value()

            assertEquals("Rest 1", overRest.characters.first().name)
            assertEquals(
                "GraphQl 1",
                overGraphQl.characters.first().name,
                "TEST-UNIT-049: the switch reaches the network, never the other protocol's entry",
            )
            assertEquals(1, graphQl.calls.size, "TEST-UNIT-049: and it does not reuse the REST entry")
            assertEquals(2, storage.keys.size, "TEST-UNIT-049: a switch evicts nothing (AC-REQ-FUNC-034-4)")
            assertTrue(storage.keys.any { it.value.startsWith("rest|GET|/api/character|") }, "TEST-UNIT-049: the REST entry survives")
            assertTrue(storage.keys.any { it.value.startsWith("graphql|POST|/graphql|") }, "TEST-UNIT-049: and GraphQL has its own")
        }

    @Test
    fun `TEST-UNIT-049 given_a_switch_back_when_the_page_is_read_then_the_first_protocol_entry_is_still_served`() =
        TestTime.run {
            val storage = FakeCacheStorage()
            val rest = FakeRemoteSource(restCatalogue)
            val graphQl = FakeRemoteSource(graphQlCatalogue)
            val protocols = MutableStateFlow(RemoteProtocol.Rest)
            val repository = repository(rest, graphQl, protocols, storage)
            repository.page(all, 1)
            protocols.value = RemoteProtocol.GraphQl
            repository.page(all, 1)

            protocols.value = RemoteProtocol.Rest
            val served = repository.page(all, 1)

            assertEquals(
                DataSource.DISK_CACHE,
                served.source,
                "TEST-UNIT-049: the REST entry was never evicted by the switch (AC-REQ-FUNC-034-4)",
            )
            assertEquals(
                "Rest 1",
                served
                    .value()
                    .characters
                    .first()
                    .name,
            )
            assertEquals(1, rest.calls.size, "TEST-UNIT-049: and the cached page is not fetched again")
        }

    /** The `IC-021` repository over the harness's store, for the source's own case. */
    private class FakeRepository(
        private val store: FakeAppSettingsStore,
    ) : AppSettingsRepository {
        override fun observe(): Flow<io.github.davidru85.multiverse.core.domain.model.AppSettings> = MutableStateFlow(store.value)

        override suspend fun update(
            change: (
                io.github.davidru85.multiverse.core.domain.model.AppSettings,
            ) -> io.github.davidru85.multiverse.core.domain.model.AppSettings,
        ) {
            store.write(change(store.value))
        }
    }
}
