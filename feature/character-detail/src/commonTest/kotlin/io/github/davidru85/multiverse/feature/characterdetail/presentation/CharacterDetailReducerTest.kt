package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.details
import io.github.davidru85.multiverse.feature.characterdetail.firstEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-002` and `TEST-UNIT-011` — the `IC-019` state mapping (`REQ-FUNC-002`, `REQ-FUNC-023`,
 * `AC-REQ-FUNC-002-1`…`-3`, `AC-REQ-FUNC-023-2`).
 *
 * The mapping is asserted on the pure function, so a case pins one fact about one `DataResult` with
 * no coroutine or clock in the way. Every display value the state carries comes from `IC-017` or from
 * `CharacterCardUi.from`; no case asserts an English literal, and a `header` is built the way the
 * grid builds it (`IC-016`).
 */
class CharacterDetailReducerTest {
    private val formatters = DefaultPresentationFormatters

    /** The card the list hands over for Rick, mapped the way the grid maps it (`IC-016`). */
    private fun listProvidedCard(): CharacterCardUi =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Rick Sanchez",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
        )

    @Test
    fun `TEST-UNIT-002 given_a_list_provided_card_when_no_detail_has_answered_then_the_header_renders_and_the_load_is_pending`() {
        val card = listProvidedCard()
        val state =
            CharacterDetailReducer.render(
                header = card,
                result = null,
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertEquals(card, state.header, "TEST-UNIT-002: the list-provided card renders before the network answers")
        assertEquals(LoadState.Loading, state.loadState, "TEST-UNIT-002: no detail has answered yet")
        assertNull(state.episodeCount, "TEST-UNIT-002: the count arrives with the detail response")
        assertNull(state.dimension, "TEST-UNIT-002: the dimension arrives with the detail response")
        assertTrue(state.info.isEmpty(), "TEST-UNIT-002: no rows before the detail response")
    }

    @Test
    fun `TEST-UNIT-002 given_a_list_provided_card_when_the_detail_fails_then_the_header_survives_and_the_state_is_error`() {
        val card = listProvidedCard()
        val failure = io.github.davidru85.multiverse.core.domain.result.ApiFailure.Offline
        val state =
            CharacterDetailReducer.render(
                header = card,
                result = DataResult.Failure(failure, DataSource.NETWORK),
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertEquals(card, state.header, "TEST-UNIT-002: a detail failure never clears a non-null header (AC-REQ-FUNC-002-3)")
        assertEquals(LoadState.Error(failure), state.loadState, "TEST-UNIT-002: the failure is the load state, so the inline retry can offer itself")
        assertTrue(state.info.isEmpty(), "TEST-UNIT-002: the inline error replaces the info list, not the header")
    }

    @Test
    fun `TEST-UNIT-002 given_a_successful_detail_when_it_renders_then_every_display_value_is_the_shared_derivation`() {
        val state =
            CharacterDetailReducer.render(
                header = null,
                result =
                    DataResult.Success(
                        details(
                            origin = io.github.davidru85.multiverse.core.domain.model.LocationSummary(id = null, name = "Earth (C-137)"),
                            episodeIds = listOf("1", "2", "3"),
                            episodeSummaries = listOf(firstEpisode()),
                        ),
                        DataSource.NETWORK,
                        isStale = false,
                    ),
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertEquals(3, state.episodeCount, "TEST-UNIT-002: the count is episodeIds.size")
        assertEquals("C-137", state.dimension, "TEST-UNIT-002: the dimension is IC-017.dimensionText")
        assertEquals(LoadState.Content, state.loadState, "TEST-UNIT-002: a decoded detail is content")
        assertEquals(
            listOf(InfoRowKind.Origin, InfoRowKind.LastKnownLocation, InfoRowKind.FirstSeenIn),
            state.info.map { it.kind },
            "TEST-UNIT-002: the row order is fixed as Origin, LastKnownLocation, FirstSeenIn",
        )
        assertEquals(
            listOf(CopyKeys.DETAIL_INFO_ORIGIN, CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION, CopyKeys.DETAIL_INFO_FIRST_SEEN_IN),
            state.info.map { it.copyKey },
            "TEST-UNIT-002: a row carries its copy key, never an English label (REQ-FUNC-013)",
        )
        assertEquals(
            listOf("Earth (C-137)", "Citadel of Ricks", "Pilot · S01E01"),
            state.info.map { it.value },
            "TEST-UNIT-002: a row's value is the data text, and first seen is name and code",
        )
        assertEquals("Rick Sanchez", state.header?.name, "TEST-UNIT-002: a deep link with no list card still gets a header from the detail")
    }

    @Test
    fun `TEST-UNIT-011 given_no_enrichment_when_the_detail_renders_then_first_seen_is_absent_while_the_episode_count_still_renders`() {
        val state =
            CharacterDetailReducer.render(
                header = null,
                result = DataResult.Success(details(episodeIds = listOf("1", "2", "3"), episodeSummaries = null), DataSource.NETWORK, isStale = false),
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertEquals(3, state.episodeCount, "TEST-UNIT-011: the count comes from episodeIds.size, so it survives a missing enrichment")
        assertTrue(
            state.info.none { it.kind == InfoRowKind.FirstSeenIn },
            "TEST-UNIT-011: episodeSummaries == null makes the row absent, not empty (AC-REQ-FUNC-023-2)",
        )
        assertEquals(
            listOf(InfoRowKind.Origin, InfoRowKind.LastKnownLocation),
            state.info.map { it.kind },
            "TEST-UNIT-011: only the independent rows remain, in order",
        )
    }

    @Test
    fun `TEST-UNIT-011 given_an_empty_enrichment_when_the_detail_renders_then_first_seen_is_absent_and_the_count_renders`() {
        val state =
            CharacterDetailReducer.render(
                header = null,
                result = DataResult.Success(details(episodeIds = emptyList(), episodeSummaries = emptyList()), DataSource.NETWORK, isStale = false),
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertEquals(0, state.episodeCount, "TEST-UNIT-011: an empty episode list is a count of zero, not a hidden tile")
        assertTrue(
            state.info.none { it.kind == InfoRowKind.FirstSeenIn },
            "TEST-UNIT-011: an enrichment that found no episode hides the row (IC-017.firstSeenText returns null)",
        )
    }

    @Test
    fun `TEST-UNIT-002 given_an_absent_origin_value_when_the_detail_renders_then_the_row_is_absent_and_the_dimension_tile_is_hidden`() {
        val state =
            CharacterDetailReducer.render(
                header = null,
                result =
                    DataResult.Success(
                        details(
                            species = "unknown",
                            status = CharacterStatus.Unknown,
                            origin = io.github.davidru85.multiverse.core.domain.model.LocationSummary(id = null, name = "unknown"),
                            lastKnownLocation = io.github.davidru85.multiverse.core.domain.model.LocationSummary(id = null, name = "unknown"),
                            episodeIds = listOf("1"),
                            episodeSummaries = null,
                        ),
                        DataSource.NETWORK,
                        isStale = false,
                    ),
                isFavorite = false,
                enrichRequested = false,
                formatters = formatters,
            )
        assertTrue(state.info.isEmpty(), "TEST-UNIT-002: an unknown origin and location make their rows absent rather than raw")
        assertNull(state.dimension, "TEST-UNIT-002: null means hide the tile; no placeholder is invented (UI_SPEC.md 6.3)")
        assertEquals(1, state.episodeCount, "TEST-UNIT-002: the count is independent of the origin")
        assertEquals(
            CopyKeys.VALUE_UNKNOWN,
            (state.header?.species as? DisplayText.Copy)?.key,
            "TEST-UNIT-002: an unknown species is the one Unknown presentation",
        )
    }

    @Test
    fun `TEST-UNIT-004 given_a_stored_favourite_when_the_state_renders_then_the_flag_mirrors_the_store`() {
        val marked =
            CharacterDetailReducer.render(
                header = null,
                result = DataResult.Success(details(), DataSource.NETWORK, isStale = false),
                isFavorite = true,
                enrichRequested = true,
                formatters = formatters,
            )
        assertTrue(marked.isFavorite, "TEST-UNIT-004: the flag mirrors the observed set")
        val unmarked =
            CharacterDetailReducer.render(
                header = null,
                result = DataResult.Success(details(), DataSource.NETWORK, isStale = false),
                isFavorite = false,
                enrichRequested = true,
                formatters = formatters,
            )
        assertTrue(!unmarked.isFavorite, "TEST-UNIT-004: an unmarked character renders unmarked")
    }
}
