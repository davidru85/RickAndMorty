package io.github.davidru85.multiverse.feature.characterdetail.domain

import io.github.davidru85.multiverse.core.data.favorites.LocalFavoritesRepository
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-011` — the enrichment the Detail use case requests (`REQ-FUNC-023`,
 * `AC-REQ-FUNC-023-1`/`-2`) and the failure and cancellation it must not swallow
 * (`IC-009`, `CONTRACTS.md` §6).
 *
 * The double honours the seam rather than echoing configuration: it serves one character whose
 * episode list is longer than the catalogue's, so the case observes a real reconciliation by id with
 * an omission warning, and its call history proves the whole enrichment was **one** repository call,
 * never one per episode.
 */
class GetCharacterDetailsTest {
    private fun catalogue() =
        FakeCatalogue(
            characters = listOf(FakeCatalogue.character(id = "1", episodeIds = listOf("1", "2", "3"))),
            episodes = listOf(EpisodeSummary(EpisodeId("1"), "Pilot", "S01E01", "December 2, 2013")),
        )

    @Test
    fun `TEST-UNIT-011 given_enrichment_when_details_are_requested_then_one_repository_call_carries_it`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue())

            val result = GetCharacterDetails(repository)(CharacterId("1"), enrich = true)

            val calls = repository.calls.filterIsInstance<FakeCharacterRepository.Call.Details>()
            assertEquals(1, calls.size, "TEST-UNIT-011: one bounded enrichment call, never one per episode (AC-REQ-FUNC-023-1)")
            assertEquals(CharacterId("1"), calls.single().id, "TEST-UNIT-011: the call names the requested character")
            assertTrue(calls.single().enrich, "TEST-UNIT-011: the enrichment is requested, so the repository orchestrates the batch")

            val success = assertIs<DataResult.Success<CharacterDetails>>(result)
            assertEquals(3, success.value.episodeIds.size, "TEST-UNIT-011: the count comes from the detail response")
            assertEquals(1, success.value.episodeSummaries?.size, "TEST-UNIT-011: the summaries are reconciled by id")
            assertEquals(1, success.warnings.size, "TEST-UNIT-011: an omitted episode is a warning, not a failure")
            assertEquals("2", success.warnings.single().detail, "TEST-UNIT-011: the warning names the first missing id")
        }

    @Test
    fun `TEST-UNIT-011 given_no_enrichment_when_details_are_requested_then_the_summaries_stay_null`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue())

            val result = GetCharacterDetails(repository)(CharacterId("1"), enrich = false)

            val details = assertIs<DataResult.Success<CharacterDetails>>(result).value
            assertNull(details.episodeSummaries, "TEST-UNIT-011: enrich = false leaves enrichment unrequested (AC-REQ-FUNC-023-2)")
            assertEquals(3, details.episodeIds.size, "TEST-UNIT-011: the episode list itself is unaffected")
            assertTrue(repository.calls.filterIsInstance<FakeCharacterRepository.Call.Details>().single().enrich.not())
        }

    @Test
    fun `TEST-UNIT-002 given_a_failure_when_details_are_requested_then_it_is_returned_unchanged`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue())
            repository.failNext(ApiFailure.Timeout)

            val result = GetCharacterDetails(repository)(CharacterId("1"), enrich = true)

            assertEquals(
                ApiFailure.Timeout,
                assertIs<DataResult.Failure>(result).failure,
                "TEST-UNIT-002: a use case does not swallow a failure (IC-009)",
            )
        }

    @Test
    fun `TEST-UNIT-002 given_a_slow_load_when_it_is_cancelled_then_the_cancellation_propagates`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue(), latency = 50.milliseconds)
            val load = async(start = CoroutineStart.UNDISPATCHED) { GetCharacterDetails(repository)(CharacterId("1"), enrich = true) }

            advanceTimeBy(10.milliseconds)
            load.cancel()

            assertFailsWith<CancellationException> { load.await() }
            assertEquals(1, repository.cancellations, "TEST-UNIT-002: a cancelled call is never a Failure (IC-003)")
        }
}

/**
 * `TEST-UNIT-004` — the toggle the Detail screen dispatches (`REQ-FUNC-006`, `AC-REQ-FUNC-006-1`).
 *
 * The double is a store behind `:core:data`'s repository, so the case observes the persisted set
 * rather than a captured argument.
 */
class ToggleFavoriteTest {
    private fun repository(store: FakeFavoritesStore) =
        LocalFavoritesRepository(local = store, logger = ValidatingAppLogger.forRelease(RecordingLogSink()))

    @Test
    fun `TEST-UNIT-004 given_an_unmarked_character_when_toggled_then_the_store_marks_it`() =
        TestTime.run {
            val store = FakeFavoritesStore()

            ToggleFavorite(repository(store))(CharacterId("1"))

            assertEquals(setOf(CharacterId("1")), store.backing.ids, "TEST-UNIT-004: the toggle reached the persisted set")
        }

    @Test
    fun `TEST-UNIT-004 given_a_marked_character_when_toggled_then_the_store_unmarks_it`() =
        TestTime.run {
            val store = FakeFavoritesStore()
            val favorites = repository(store)
            ToggleFavorite(favorites)(CharacterId("1"))

            ToggleFavorite(favorites)(CharacterId("1"))

            assertTrue(store.backing.ids.isEmpty(), "TEST-UNIT-004: a second toggle unmarks the same id")
        }
}
