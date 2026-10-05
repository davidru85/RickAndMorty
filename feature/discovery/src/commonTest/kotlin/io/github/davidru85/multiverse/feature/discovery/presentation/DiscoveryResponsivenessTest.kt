package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-065` — the Discovery intent loop stays responsive while a load is in flight (`IC-018`,
 * `DEC-124`, `AC-REQ-FUNC-003-2`, `AC-REQ-FUNC-004-1`, `TASK-111`).
 *
 * A load can take three attempts with backoff and a 20 s call timeout each. If the reducer awaited
 * `next()`, `refresh()` or `retry()` inside its intent loop, a status change made during that load
 * would wait in the buffer until the load ended: the chips would not respond, and the superseded load
 * would not be cancelled. The pager here suspends each of those calls until the case releases it, so
 * a status change arrives while one is genuinely in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryResponsivenessTest {
    private fun TestScope.reducer(pager: CharacterPager) =
        DiscoveryReducer(
            pager = pager,
            scope = backgroundScope,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            formatters = DefaultPresentationFormatters,
        )

    private fun assertStatusChangeIsHandledDuring(
        intent: CharacterListIntent,
        pager: GatedPager,
    ) = TestTime.run {
        val reducer = reducer(pager)
        reducer.start()
        runCurrent()

        reducer.onIntent(intent)
        runCurrent()
        reducer.onIntent(CharacterListIntent.StatusSelected(StatusFilter.Dead))
        runCurrent()

        assertEquals(
            StatusFilter.Dead,
            pager.requested.last().status,
            "TEST-UNIT-065: a status change during $intent must reach the pager before that load ends",
        )
        assertEquals(StatusFilter.Dead, reducer.state.value.filter.status, "TEST-UNIT-065: and the state must follow it")
        pager.release()
    }

    @Test
    fun `TEST-UNIT-065 given_an_append_in_flight_when_a_status_is_selected_then_it_is_handled_at_once`() =
        assertStatusChangeIsHandledDuring(CharacterListIntent.LoadNextPage, GatedPager())

    @Test
    fun `TEST-UNIT-065 given_a_refresh_in_flight_when_a_status_is_selected_then_it_is_handled_at_once`() =
        assertStatusChangeIsHandledDuring(CharacterListIntent.Refresh, GatedPager())

    @Test
    fun `TEST-UNIT-065 given_a_retry_in_flight_when_a_status_is_selected_then_it_is_handled_at_once`() =
        assertStatusChangeIsHandledDuring(CharacterListIntent.Retry, GatedPager(failure = ApiFailure.Offline))
}

/**
 * An `IC-014` double whose `next()`, `refresh()` and `retry()` suspend until [release]. `setFilter`
 * answers at once with an empty page for the requested filter, and records it.
 */
private class GatedPager(
    failure: ApiFailure? = null,
) : CharacterPager {
    private val gate = CompletableDeferred<Unit>()
    private val mutableState =
        MutableStateFlow(
            PagerState(
                filter = CharacterFilter(),
                items = emptyList(),
                totalCount = null,
                isAppending = false,
                isEndReached = false,
                isStale = false,
                failure = failure,
            ),
        )

    val requested = mutableListOf<CharacterFilter>()

    override val state: StateFlow<PagerState> = mutableState

    override suspend fun setFilter(filter: CharacterFilter) {
        requested += filter
        mutableState.update { it.copy(filter = filter, items = emptyList(), failure = null) }
    }

    override suspend fun next(): Unit = gate.await()

    override suspend fun refresh(): Unit = gate.await()

    override suspend fun retry(): Unit = gate.await()

    fun release() {
        gate.complete(Unit)
    }
}
