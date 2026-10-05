package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeCharacterRepository.Call
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-076` — "Clear filters" clears both dimensions in one request (`IC-018`, `DEC-129`,
 * `AC-REQ-FUNC-010-2`, `ERROR_FLOW.md` §5.1, `TASK-112`).
 *
 * The empty state's action used to send `QueryChanged("")`, which keeps the active status, so a list
 * filtered by `Unknown` stayed filtered and was not "the unfiltered first page".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryClearFiltersTest {
    private val settled = DiscoveryReducer.DEFAULT_DEBOUNCE + 1.milliseconds

    @Test
    fun `TEST-UNIT-076 given_a_query_and_a_status_when_filters_are_cleared_then_one_request_loads_the_unfiltered_first_page`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(FakeCatalogue((1..3).map { FakeCatalogue.character("$it") }))
            val reducer =
                DiscoveryReducer(
                    FakeCharacterPager(repository, backgroundScope),
                    backgroundScope,
                    dispatcher,
                    DefaultPresentationFormatters,
                )
            reducer.start()
            runCurrent()
            reducer.onIntent(CharacterListIntent.StatusSelected(StatusFilter.Unknown))
            reducer.onIntent(CharacterListIntent.QueryChanged("zzz"))
            advanceTimeBy(settled)
            runCurrent()
            val before = repository.calls.filterIsInstance<Call.Page>().size

            reducer.onIntent(CharacterListIntent.ClearFilters)
            runCurrent()
            advanceTimeBy(settled)
            runCurrent()

            val pages = repository.calls.filterIsInstance<Call.Page>()
            assertEquals(before + 1, pages.size, "TEST-UNIT-076: clearing is exactly one request, with no late debounced one")
            assertEquals(CharacterFilter(), pages.last().filter, "TEST-UNIT-076: the request is the unfiltered first page")
            assertEquals(1, pages.last().page)
            assertEquals(CharacterFilter(), reducer.state.value.filter, "TEST-UNIT-076: the state shows no query and All")
        }

    @Test
    fun `TEST-UNIT-076 given_a_query_still_settling_when_filters_are_cleared_then_the_superseded_query_is_never_requested`() =
        TestTime.run { dispatcher ->
            val repository = FakeCharacterRepository(FakeCatalogue((1..3).map { FakeCatalogue.character("$it") }))
            val reducer =
                DiscoveryReducer(
                    FakeCharacterPager(repository, backgroundScope),
                    backgroundScope,
                    dispatcher,
                    DefaultPresentationFormatters,
                )
            reducer.start()
            runCurrent()
            reducer.onIntent(CharacterListIntent.StatusSelected(StatusFilter.Dead))
            runCurrent()

            reducer.onIntent(CharacterListIntent.QueryChanged("ric"))
            reducer.onIntent(CharacterListIntent.ClearFilters)
            advanceTimeBy(settled)
            runCurrent()

            val queries = repository.calls.filterIsInstance<Call.Page>().map { it.filter }
            assertEquals(false, queries.any { it.query == "ric" }, "TEST-UNIT-076: the unsettled query is cancelled by the clear")
            assertEquals(CharacterFilter(), queries.last())
        }
}
