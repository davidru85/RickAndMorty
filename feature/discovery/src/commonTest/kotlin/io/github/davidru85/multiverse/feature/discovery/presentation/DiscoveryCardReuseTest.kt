package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `TEST-UNIT-102` — Discovery re-maps only what changed (`REQ-NFR-003`, `TASK-115`).
 *
 * Every pager emission — an `isAppending` toggle included — mapped the whole accumulated list (up to
 * 826 summaries) into new `CharacterCardUi` objects, so the screen received a new list of new cards
 * each time and recomposed the grid. An unchanged list is now the same list, and an unchanged summary
 * keeps its card.
 */
class DiscoveryCardReuseTest {
    @Test
    fun `TEST-UNIT-102 given_unchanged_items_when_only_a_flag_changes_then_the_same_cards_are_rendered`() =
        runTest {
            val reducer = reducer()
            val items = listOf(summary("1"), summary("2"))
            val base = pagerState(items)

            val first = reducer.render(base, isLoading = false)
            val appending = reducer.render(base.copy(isAppending = true), isLoading = false)

            assertSame(first.items, appending.items, "an unchanged list is the same list")
        }

    @Test
    fun `TEST-UNIT-102 given_a_grown_list_when_rendered_then_the_known_cards_are_kept`() =
        runTest {
            val reducer = reducer()
            val items = listOf(summary("1"), summary("2"))
            val first = reducer.render(pagerState(items), isLoading = false)

            val grown = reducer.render(pagerState(items + summary("3")), isLoading = false)

            assertEquals(3, grown.items.size)
            assertSame(first.items[0], grown.items[0], "a summary that did not change keeps its card")
            assertSame(first.items[1], grown.items[1])
        }

    private fun kotlinx.coroutines.test.TestScope.reducer() =
        DiscoveryReducer(
            pager = FakeCharacterPager(FakeCharacterRepository(FakeCatalogue(emptyList())), backgroundScope),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            formatters = DefaultPresentationFormatters,
        )

    private fun pagerState(items: List<CharacterSummary>) =
        PagerState(
            filter = CharacterFilter(),
            items = items,
            totalCount = items.size,
            isAppending = false,
            isEndReached = false,
            isStale = false,
            failure = null,
        )

    private fun summary(id: String) =
        CharacterSummary(
            id = CharacterId(id),
            name = "Character $id",
            status = CharacterStatus.Alive,
            species = "Human",
            type = null,
            gender = CharacterGender.Unknown,
            lastKnownLocation = LocationSummary(id = null, name = "Earth"),
            imageUrl = "https://images.example.test/$id.jpeg",
        )
}
