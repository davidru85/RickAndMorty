package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.RemoteProtocolSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.testing.FakeCacheStorage
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeRemoteSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-077` — a protocol switch resets the pager exactly as a filter change does (`IC-014`,
 * `DEC-130`, `AC-REQ-FUNC-034-2`, `TASK-112`).
 *
 * The switch is an identity change: page 1 of the newly selected protocol is "the list", and the items
 * the other protocol loaded must not stay on screen while it loads. Before this case the pager kept the
 * previous protocol's items, total and end flag on screen, with no loading state, until the new page
 * arrived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolResetTest {
    private val all = CharacterFilter()
    private val rest = FakeCatalogue((1..45).map { FakeCatalogue.character("$it", name = "Rest $it") })
    private val graphQl = FakeCatalogue((1..45).map { FakeCatalogue.character("$it", name = "GraphQl $it") })

    private class Protocols(
        val selected: MutableStateFlow<RemoteProtocol>,
    ) : RemoteProtocolSource {
        override suspend fun current(): RemoteProtocol = selected.value

        override fun changes(): Flow<RemoteProtocol> = selected.drop(1)
    }

    @Test
    fun `TEST-UNIT-077 given_loaded_items_when_the_protocol_changes_then_none_stays_on_screen_while_the_new_page_loads`() =
        TestTime.run {
            val owner = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            try {
                val protocols = Protocols(MutableStateFlow(RemoteProtocol.Rest))
                val logger = ValidatingAppLogger.forDebug(RecordingLogSink())
                val repository =
                    RemoteCharacterRepository(
                        rest = FakeRemoteSource(rest),
                        graphQl = FakeRemoteSource(graphQl, latency = 300.milliseconds),
                        scope = owner,
                        random = FixedRandom(0.5),
                        logger = logger,
                        cache = ResponseCache(FakeCacheStorage(), MutableFakeClock(), CachePolicy(), logger),
                        protocols = protocols,
                    )
                val pager = RepositoryCharacterPager(repository, owner, logger, protocolChanges = protocols.changes())
                pager.setFilter(all)
                assertEquals(20, pager.state.value.items.size, "TEST-UNIT-077: the REST page is on screen before the switch")

                protocols.selected.value = RemoteProtocol.GraphQl
                advanceTimeBy(100)

                val during = pager.state.value
                assertTrue(during.items.isEmpty(), "TEST-UNIT-077: no item of the previous protocol stays while the new page loads")
                assertNull(during.totalCount, "TEST-UNIT-077: nor its total")
                assertEquals(false, during.isEndReached, "TEST-UNIT-077: nor its end flag")
                assertTrue(during.isLoading, "TEST-UNIT-077: the pager says a first page is loading, so the screen shows Loading")

                advanceUntilIdle()
                assertEquals((1..20).map { "GraphQl $it" }, pager.state.value.items.map { it.name })
                assertEquals(false, pager.state.value.isLoading, "TEST-UNIT-077: and stops saying so once it is published")
            } finally {
                owner.cancel()
            }
        }
}
