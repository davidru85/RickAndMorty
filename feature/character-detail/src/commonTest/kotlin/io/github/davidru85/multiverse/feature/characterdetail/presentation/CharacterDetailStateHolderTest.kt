package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.data.favorites.LocalFavoritesRepository
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
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
 * `TEST-UNIT-002`, `TEST-UNIT-011` and `TEST-UNIT-004` — the shared Detail state holder
 * (`IC-019`, `REQ-FUNC-002`, `REQ-FUNC-006`, `REQ-FUNC-023`).
 *
 * The holder owns the rules a platform holder must not re-derive (`CONTRACTS.md` §7): it renders the
 * hand-off's card before the load, keeps it through a failure, maps a successful detail through
 * `IC-017`, and reconciles the favourite flag with `ObserveFavoriteIds`. The catalogue is the one the
 * repository serves, and the header is built the way the grid builds it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterDetailStateHolderTest {
    private val catalogue = FakeCatalogue(characters = listOf(FakeCatalogue.character(id = "1", episodeIds = listOf("1"))))

    /** The card the grid hands over (`IC-016`). */
    private val card =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Character 1",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

    private fun favorites(store: FakeFavoritesStore) =
        LocalFavoritesRepository(local = store, logger = ValidatingAppLogger.forRelease(RecordingLogSink()))

    private fun TestScope.holder(
        repository: FakeCharacterRepository,
        favorites: LocalFavoritesRepository,
        header: CharacterCardUi? = null,
    ) = CharacterDetailStateHolder(
        id = CharacterId("1"),
        header = header,
        getDetails = GetCharacterDetails(repository),
        toggleFavorite = ToggleFavorite(favorites),
        observeFavoriteIds = ObserveFavoriteIds(favorites),
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
        formatters = DefaultPresentationFormatters,
    )

    @Test
    fun `TEST-UNIT-002 given_a_hand_off_card_when_the_holder_starts_then_the_header_renders_before_any_response`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue, latency = 20.milliseconds)
            val holder = holder(repository, favorites(FakeFavoritesStore()), header = card)

            val initial = holder.state.value
            assertEquals(card, initial.header, "TEST-UNIT-002: the hand-off card is the first state's header")
            assertEquals(LoadState.Loading, initial.loadState, "TEST-UNIT-002: nothing has answered yet")

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(LoadState.Content, holder.state.value.loadState, "TEST-UNIT-002: the detail then arrives")
            assertEquals(card, holder.state.value.header, "TEST-UNIT-002: the list-provided header is never replaced")
        }

    @Test
    fun `TEST-UNIT-002 given_a_failing_detail_when_it_loads_then_the_header_survives_into_the_inline_retry_state`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Offline)
            val holder = holder(repository, favorites(FakeFavoritesStore()), header = card)

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(card, holder.state.value.header, "TEST-UNIT-002: a failure keeps the known fields (AC-REQ-FUNC-002-3)")
            assertEquals(
                LoadState.Error(ApiFailure.Offline),
                holder.state.value.loadState,
                "TEST-UNIT-002: the inline retry replaces the info list",
            )
            assertTrue(
                holder.state.value.info
                    .isEmpty(),
                "TEST-UNIT-002: no rows render on a failure",
            )
        }

    @Test
    fun `TEST-UNIT-011 given_no_enrichment_when_the_detail_loads_then_the_count_renders_and_first_seen_is_absent`() =
        TestTime.run {
            val holder = holder(FakeCharacterRepository(catalogue), favorites(FakeFavoritesStore()))

            holder.start()
            advanceTimeBy(100.milliseconds)

            assertEquals(1, holder.state.value.episodeCount, "TEST-UNIT-011: the count comes from episodeIds.size")
            assertTrue(
                holder.state.value.info
                    .none { it.kind == InfoRowKind.FirstSeenIn },
                "TEST-UNIT-011: the catalogue holds no episode summary, so the row is absent (AC-REQ-FUNC-023-2)",
            )
        }

    @Test
    fun `TEST-UNIT-002 given_a_failed_load_when_retry_is_dispatched_then_a_fresh_attempt_clears_the_error`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Timeout)
            val holder = holder(repository, favorites(FakeFavoritesStore()))

            holder.start()
            advanceTimeBy(100.milliseconds)
            assertTrue(holder.state.value.loadState is LoadState.Error, "TEST-UNIT-002: the first attempt failed")

            holder.onIntent(CharacterDetailIntent.Retry)
            advanceTimeBy(100.milliseconds)

            assertEquals(
                LoadState.Content,
                holder.state.value.loadState,
                "TEST-UNIT-002: a retry starts a fresh attempt and clears the error",
            )
        }

    @Test
    fun `TEST-UNIT-004 given_an_unmarked_character_when_the_toggle_intent_arrives_then_the_flag_flips_and_the_store_follows`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val holder = holder(FakeCharacterRepository(catalogue), favorites(store))

            holder.start()
            advanceTimeBy(100.milliseconds)
            assertTrue(!holder.state.value.isFavorite, "TEST-UNIT-004: the character starts unmarked")

            holder.onIntent(CharacterDetailIntent.ToggleFavorite)
            advanceTimeBy(100.milliseconds)

            assertTrue(holder.state.value.isFavorite, "TEST-UNIT-004: the flag flips immediately on the intent (AC-REQ-FUNC-006-1)")
            assertEquals(setOf(CharacterId("1")), store.backing.ids, "TEST-UNIT-004: the write follows and the flag reconciles with it")
        }

    @Test
    fun `TEST-UNIT-004 given_a_favourite_written_elsewhere_when_it_is_observed_then_the_flag_reconciles_without_an_intent`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val favorites = favorites(store)
            val holder = holder(FakeCharacterRepository(catalogue), favorites)

            holder.start()
            advanceTimeBy(100.milliseconds)

            favorites.toggle(CharacterId("1"))
            advanceTimeBy(50.milliseconds)

            assertTrue(holder.state.value.isFavorite, "TEST-UNIT-004: an ObserveFavoriteIds emission reconciles the flag (IC-009)")
        }
}
