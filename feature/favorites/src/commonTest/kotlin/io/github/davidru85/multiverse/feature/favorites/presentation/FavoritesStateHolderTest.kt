package io.github.davidru85.multiverse.feature.favorites.presentation

import io.github.davidru85.multiverse.core.data.favorites.LocalFavoritesRepository
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.favorites.domain.ResolveFavoriteCards
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-004` and `TEST-UNIT-040` — the shared Favorites state holder (`IC-020`, `REQ-FUNC-006`,
 * `DESIGN.md` §4.5, `CONF-79`).
 *
 * The holder owns the rules a platform holder must not re-derive (`CONTRACTS.md` §7): it awaits the
 * first store emission (`Loading` only until then), shows the designed empty state exactly while the
 * stored set is empty (`AC-REQ-FUNC-006-3`), resolves the cards through the cached path, and re-attempts
 * a failed read on `Retry` (`IC-020`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStateHolderTest {
    private val catalogue = FakeCatalogue(characters = (1..3).map { FakeCatalogue.character("$it", name = "Rick $it") })

    private fun favorites(store: FakeFavoritesStore) =
        LocalFavoritesRepository(local = store, logger = ValidatingAppLogger.forRelease(RecordingLogSink()))

    private fun TestScope.holder(
        store: FakeFavoritesStore,
        repository: FakeCharacterRepository = FakeCharacterRepository(catalogue),
    ) = FavoritesStateHolder(
        observeFavoriteIds = ObserveFavoriteIds(favorites(store)),
        resolveFavoriteCards = ResolveFavoriteCards(repository, dispatcher = StandardTestDispatcher(testScheduler)),
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `TEST-UNIT-004 given_a_cold_holder_when_it_starts_then_it_is_loading_until_the_first_store_emission`() =
        TestTime.run {
            val holder = holder(FakeFavoritesStore())

            val before = holder.state.value
            assertEquals(
                LoadState.Loading,
                before.loadState,
                "TEST-UNIT-004: Loading appears only while the first store emission is awaited (IC-020)",
            )
            assertTrue(before.items.isEmpty(), "TEST-UNIT-004: and no card renders before it")

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(
                LoadState.Empty,
                holder.state.value.loadState,
                "TEST-UNIT-004: the store's first emission is the empty set, so the designed empty state shows",
            )
        }

    @Test
    fun `TEST-UNIT-004 given_a_stored_favourite_when_the_holder_starts_then_the_grid_is_content_with_its_card`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            favorites(store).toggle(CharacterId("2"))
            val holder = holder(store)

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(LoadState.Content, holder.state.value.loadState, "TEST-UNIT-004: a displayable favourite is content")
            assertEquals(
                listOf(CharacterId("2")),
                holder.state.value.items.map { it.id },
                "TEST-UNIT-004: the grid renders the stored character's card",
            )
            assertEquals("Rick 2", holder.state.value.items.single().name, "TEST-UNIT-004: with its own name")
        }

    @Test
    fun `TEST-UNIT-004 given_favourites_written_in_arbitrary_order_when_the_grid_renders_then_the_cards_are_in_the_canonical_order`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val favorites = favorites(store)
            favorites.toggle(CharacterId("3"))
            favorites.toggle(CharacterId("1"))
            favorites.toggle(CharacterId("2"))
            val holder = holder(store)

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(
                listOf(CharacterId("1"), CharacterId("2"), CharacterId("3")),
                holder.state.value.items.map { it.id },
                "TEST-UNIT-004: the order is the canonical id order, stable across a re-observation (IC-020)",
            )
        }

    @Test
    fun `TEST-UNIT-004 given_a_store_that_empties_when_it_emits_then_the_grid_returns_to_the_designed_empty_state`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val favorites = favorites(store)
            favorites.toggle(CharacterId("1"))
            val holder = holder(store)

            holder.start()
            advanceTimeBy(100.milliseconds)
            assertTrue(holder.state.value.items.isNotEmpty(), "TEST-UNIT-004: the grid starts populated")

            favorites.clear()
            advanceTimeBy(100.milliseconds)

            assertEquals(
                LoadState.Empty,
                holder.state.value.loadState,
                "TEST-UNIT-004: every observer of a clear reaches the empty state without a refresh (DESIGN.md 4.5)",
            )
        }

    @Test
    fun `TEST-UNIT-040 given_a_read_that_fails_when_the_grid_renders_then_the_full_surface_error_is_shown`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            favorites(store).toggle(CharacterId("1"))
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(io.github.davidru85.multiverse.core.domain.result.ApiFailure.Offline)
            val holder = holder(store, repository)

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(
                LoadState.Error(io.github.davidru85.multiverse.core.domain.result.ApiFailure.Offline),
                holder.state.value.loadState,
                "TEST-UNIT-040: Error is reached from a read that failed, carrying that failure",
            )
            assertTrue(holder.state.value.items.isEmpty(), "TEST-UNIT-040: the error surface replaces the grid")
        }

    @Test
    fun `TEST-UNIT-040 given_a_failed_read_when_retry_is_dispatched_then_a_fresh_attempt_clears_the_error`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            favorites(store).toggle(CharacterId("1"))
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(io.github.davidru85.multiverse.core.domain.result.ApiFailure.Timeout)
            val holder = holder(store, repository)

            holder.start()
            advanceTimeBy(100.milliseconds)
            assertTrue(holder.state.value.loadState is LoadState.Error, "TEST-UNIT-040: the first attempt failed")

            holder.onIntent(FavoritesIntent.Retry)
            advanceTimeBy(100.milliseconds)

            assertEquals(
                LoadState.Content,
                holder.state.value.loadState,
                "TEST-UNIT-040: Retry re-attempts the failed read and clears the error (IC-020)",
            )
            assertEquals(
                listOf(CharacterId("1")),
                holder.state.value.items.map { it.id },
                "TEST-UNIT-040: and the resolved card renders",
            )
        }

    @Test
    fun `TEST-UNIT-040 given_a_failed_read_when_retry_succeeds_then_the_second_attempt_is_a_fresh_repository_call`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            favorites(store).toggle(CharacterId("1"))
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(io.github.davidru85.multiverse.core.domain.result.ApiFailure.Server(503))
            val holder = holder(store, repository)

            holder.start()
            advanceTimeBy(100.milliseconds)
            holder.onIntent(FavoritesIntent.Retry)
            advanceTimeBy(100.milliseconds)

            assertEquals(
                2,
                repository.calls.filterIsInstance<FakeCharacterRepository.Call.Details>().size,
                "TEST-UNIT-040: each attempt reads the repository again rather than replaying a cached outcome",
            )
        }
}
