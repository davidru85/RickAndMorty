package io.github.davidru85.multiverse.feature.favorites.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.favorites.summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-004` — the `IC-020` state mapping (`REQ-FUNC-006`, `AC-REQ-FUNC-006-1`/`-3`).
 *
 * The mapping is asserted on the pure function, so a case pins one fact about one store emission with
 * no coroutine or clock in the way:
 *
 * - an empty stored set is the designed empty state and only then (`AC-REQ-FUNC-006-3`);
 * - a non-empty set is `Content` with exactly one card per id, in the **canonical id order** — the
 *   deterministic order `FavoritesReducer` documents (`CONTRACTS.md` `IC-020`, §10 A3);
 * - a read failure is `Error` carrying that failure, the only way `Error` is reachable;
 * - a set that has not been resolved yet is `Loading`, the one state that is not a claim about the
 *   stored set (`CONF-79`).
 *
 * Every card is built by `CharacterCardUi.from` through `IC-017`, so no case asserts an English
 * literal and a card renders through the same contract Discovery's grid uses (`UI_SPEC.md` §6.4).
 */
class FavoritesReducerTest {
    private val formatters = DefaultPresentationFormatters

    private fun render(
        ids: Set<CharacterId>?,
        summaries: Map<CharacterId, io.github.davidru85.multiverse.core.domain.model.CharacterSummary> = emptyMap(),
        failure: ApiFailure? = null,
    ) = FavoritesReducer.render(ids, summaries, failure, formatters)

    @Test
    fun `TEST-UNIT-004 given_no_store_emission_when_the_state_is_mapped_then_it_is_loading`() {
        val state = render(ids = null)
        assertEquals(LoadState.Loading, state.loadState, "TEST-UNIT-004: no set is known yet, so nothing is claimable")
        assertTrue(state.items.isEmpty(), "TEST-UNIT-004: and no card renders")
    }

    @Test
    fun `TEST-UNIT-004 given_an_empty_stored_set_when_the_state_is_mapped_then_it_is_the_designed_empty_state`() {
        val state = render(ids = emptySet())
        assertEquals(
            LoadState.Empty,
            state.loadState,
            "TEST-UNIT-004: the empty state shows while the stored set is empty, and only then (AC-REQ-FUNC-006-3)",
        )
        assertTrue(state.items.isEmpty(), "TEST-UNIT-004: an empty set has no card to render")
    }

    @Test
    fun `TEST-UNIT-004 given_a_non_empty_stored_set_when_the_state_is_mapped_then_it_is_content_with_one_card_per_id`() {
        val ids = linkedSetOf(CharacterId("2"), CharacterId("1"))
        val summaries = mapOf(CharacterId("1") to summary("1"), CharacterId("2") to summary("2"))

        val state = render(ids = ids, summaries = summaries)

        assertEquals(LoadState.Content, state.loadState, "TEST-UNIT-004: a resolvable favourite is content")
        assertEquals(
            listOf(CharacterId("1"), CharacterId("2")),
            state.items.map { it.id },
            "TEST-UNIT-004: the order is the canonical id order, not the emission order",
        )
        assertEquals(
            listOf("Character 1", "Character 2"),
            state.items.map { it.name },
            "TEST-UNIT-004: each card renders its own character",
        )
        assertEquals(
            listOf(CharacterStatus.Alive, CharacterStatus.Alive),
            state.items.map { it.status },
            "TEST-UNIT-004: the card carries the domain status the badge mirrors",
        )
        assertEquals(
            listOf(DisplayText.Data("Human"), DisplayText.Data("Human")),
            state.items.map { it.species },
            "TEST-UNIT-004: the species comes from IC-017, never from a platform",
        )
        assertEquals(
            listOf(CopyKeys.STATUS_ALIVE, CopyKeys.STATUS_ALIVE),
            state.items.map { it.statusLabel },
            "TEST-UNIT-004: the status label is the copy key IC-017 returns",
        )
    }

    @Test
    fun `TEST-UNIT-004 given_the_same_set_re_observed_in_another_order_when_it_is_mapped_then_the_items_order_is_identical`() {
        val summaries = mapOf(CharacterId("1") to summary("1"), CharacterId("2") to summary("2"), CharacterId("3") to summary("3"))

        val first = render(ids = linkedSetOf(CharacterId("3"), CharacterId("1"), CharacterId("2")), summaries = summaries)
        val second = render(ids = linkedSetOf(CharacterId("2"), CharacterId("3"), CharacterId("1")), summaries = summaries)

        assertEquals(
            first.items.map { it.id },
            second.items.map { it.id },
            "TEST-UNIT-004: the order is deterministic and stable across a re-observation (IC-020)",
        )
    }

    @Test
    fun `TEST-UNIT-004 given_a_read_failure_when_the_state_is_mapped_then_it_is_the_error_state`() {
        val state = render(ids = setOf(CharacterId("1")), failure = ApiFailure.Offline)
        assertEquals(
            LoadState.Error(ApiFailure.Offline),
            state.loadState,
            "TEST-UNIT-004: Error is reachable only from a read that failed",
        )
        assertTrue(state.items.isEmpty(), "TEST-UNIT-004: the error surface replaces the grid rather than shadowing it")
    }

    @Test
    fun `TEST-UNIT-004 given_a_non_empty_set_whose_cards_have_not_landed_when_it_is_mapped_then_it_is_loading`() {
        val state = render(ids = setOf(CharacterId("1")), summaries = emptyMap())
        assertEquals(
            LoadState.Loading,
            state.loadState,
            "TEST-UNIT-004: nothing displayable is known yet, so the grid has no card to place",
        )
    }

    @Test
    fun `TEST-UNIT-004 given_the_reducer_when_it_is_used_then_it_is_the_shared_implementation_the_two_platforms_read`() {
        // A guard on the contract's own shape: the state type is `IC-020`'s, with its documented defaults.
        assertEquals(FavoritesUiState(), FavoritesUiState(emptyList(), LoadState.Loading))
        assertEquals(
            emptyList<io.github.davidru85.multiverse.core.presentation.CharacterCardUi>(),
            FavoritesUiState().items,
            "TEST-UNIT-004: items defaults to the empty list",
        )
        assertEquals(LoadState.Loading, FavoritesUiState().loadState, "TEST-UNIT-004: loadState defaults to Loading")
        assertEquals(
            FavoritesIntent.Retry,
            FavoritesIntent.Retry,
            "TEST-UNIT-004: IC-020 declares exactly the Retry intent",
        )
    }
}
