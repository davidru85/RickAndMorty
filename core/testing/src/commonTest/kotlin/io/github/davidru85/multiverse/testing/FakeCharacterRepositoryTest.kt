package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * `TEST-UNIT-053` — `FakeCharacterRepository` honours `IC-007` rather than echoing configuration
 * (`TESTING.md` §6.1, `DEC-072`, `DEC-090`). These cases prove the double's semantics only; they are
 * never evidence for the real repository.
 */
class FakeCharacterRepositoryTest {
    private val catalogue =
        FakeCatalogue(
            characters =
                (1..45).map { n ->
                    FakeCatalogue.character(
                        id = n.toString(),
                        name = if (n % 9 == 0) "Rick $n" else "Morty $n",
                        status = if (n % 2 == 0) CharacterStatus.Alive else CharacterStatus.Dead,
                        episodeIds = listOf("1", "2", "99"),
                    )
                },
            episodes = listOf(FakeCatalogue.episode("2"), FakeCatalogue.episode("1")),
        )

    @Test
    fun `TEST-UNIT-053 given_a_catalogue_when_a_page_is_requested_then_only_that_page_is_returned_with_server_metadata`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)

            val first = repository.page(CharacterFilter(), 1).success()
            val last = repository.page(CharacterFilter(), 3).success()

            assertEquals((1..20).map { CharacterId("$it") }, first.characters.map { it.id }, "TEST-UNIT-053")
            assertEquals(
                listOf(3, 45, 2, null),
                listOf(first.pageCount, first.totalCount, first.nextPage, first.previousPage),
                "TEST-UNIT-053: page metadata is derived from the catalogue, as the server derives it",
            )
            assertEquals((41..45).map { CharacterId("$it") }, last.characters.map { it.id }, "TEST-UNIT-053")
            assertNull(last.nextPage, "TEST-UNIT-053: the last page ends pagination")
            assertEquals(2, repository.calls.size, "TEST-UNIT-053: one call per page, never a later page")
        }

    @Test
    fun `TEST-UNIT-053 given_a_filter_when_a_page_is_requested_then_query_and_status_apply_and_no_match_is_an_empty_success`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)

            val rick = repository.page(CharacterFilter(query = "  rick ", status = StatusFilter.Dead), 1).success()
            assertEquals(
                listOf("Rick 9", "Rick 27", "Rick 45"),
                rick.characters.map { it.name },
                "TEST-UNIT-053: the trimmed query and the status both apply",
            )

            val empty = repository.page(CharacterFilter(query = "zzzznotreal"), 1).success()
            assertTrue(empty.characters.isEmpty(), "TEST-UNIT-053: no match is a success, not a failure (IC-007)")
            assertNull(empty.totalCount, "TEST-UNIT-053: an unestablished total stays null, never 0")
        }

    @Test
    fun `TEST-UNIT-053 given_an_id_when_details_are_requested_then_unknown_is_not_found_and_enrichment_follows_the_flag`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)

            val missing = assertIs<DataResult.Failure>(repository.details(CharacterId("999"), enrich = false))
            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER, "999"), missing.failure, "TEST-UNIT-053")

            val plain = repository.details(CharacterId("9"), enrich = false).success()
            val enriched = repository.details(CharacterId("9"), enrich = true)
            assertNull(plain.episodeSummaries, "TEST-UNIT-053: no enrichment unless requested")
            assertEquals(
                listOf(EpisodeId("1"), EpisodeId("2")),
                enriched.success().episodeSummaries?.map { it.id },
                "TEST-UNIT-053: summaries follow the character's episode order, matched by id",
            )
            assertEquals(listOf("99"), enriched.warnings.map { it.detail }, "TEST-UNIT-053: an absent episode is a warning")
        }

    @Test
    fun `TEST-UNIT-053 given_a_queued_failure_when_the_next_call_runs_then_it_is_a_value_and_the_following_call_recovers`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)
            repository.failNext(ApiFailure.Server(503))

            val failed = assertIs<DataResult.Failure>(repository.page(CharacterFilter(), 1))
            assertEquals(ApiFailure.Server(503), failed.failure, "TEST-UNIT-053: an expected failure is returned, not thrown")
            repository.page(CharacterFilter(), 1).success()
        }

    // `advanceTimeBy` is the virtual-time control `TESTING.md` §5 prescribes; it is marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `TEST-UNIT-053 given_latency_when_the_caller_is_cancelled_then_cancellation_propagates_and_no_result_is_produced`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue, latency = 300.milliseconds)
            val load = async(start = CoroutineStart.UNDISPATCHED) { repository.page(CharacterFilter(), 1) }

            advanceTimeBy(100)
            load.cancel()

            assertFailsWith<CancellationException>("TEST-UNIT-053: cancellation is never mapped to a failure") { load.await() }
            assertEquals(1, repository.calls.size, "TEST-UNIT-053: the cancelled call was still recorded")
            assertEquals(1, repository.cancellations, "TEST-UNIT-053: the cancellation is observable")
        }

    @Test
    fun `TEST-UNIT-053 given_the_served_catalogue_replaced_when_the_next_call_runs_then_it_answers_from_the_new_data`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)

            repository.page(CharacterFilter(), 3)
            repository.serve(FakeCatalogue((1..15).map { FakeCatalogue.character(it.toString()) }))

            assertEquals(
                DataResult.Failure(ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, "3"), DataSource.NETWORK),
                repository.page(CharacterFilter(), 3),
                "TEST-UNIT-053: a catalogue that shrank no longer has the page",
            )
        }

    @Test
    fun `TEST-UNIT-053 given_a_cached_catalogue_when_pages_load_then_only_force_network_bypasses_a_servable_entry`() =
        TestTime.run {
            val cachedCatalogue = FakeCatalogue((1..25).map { FakeCatalogue.character(it.toString(), name = "Cached $it") })
            val repository = FakeCharacterRepository(catalogue, cached = cachedCatalogue, cachedIsStale = true)
            repository.failNext(ApiFailure.Offline)

            val fromCache = repository.page(CharacterFilter(), 1)
            val fromNetwork = repository.page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork)
            val beyondCache = repository.page(CharacterFilter(), 3)

            assertEquals(
                DataResult.Success(cachedCatalogue.page(CharacterFilter(), 1).success(), DataSource.MEMORY_CACHE, isStale = true),
                fromCache,
                "TEST-UNIT-053: Default is served by a servable cached entry, with its provenance",
            )
            assertEquals(
                DataResult.Failure(ApiFailure.Offline, DataSource.NETWORK),
                fromNetwork,
                "TEST-UNIT-053: ForceNetwork reaches the network even though the cache could serve it",
            )
            assertEquals(
                "Morty 41",
                beyondCache
                    .success()
                    .characters
                    .first()
                    .name,
                "TEST-UNIT-053: a cache miss goes to the network",
            )
        }

    @Test
    fun `TEST-UNIT-053 given_calls_when_they_complete_then_the_history_records_filter_page_policy_and_enrichment`() =
        TestTime.run {
            val repository = FakeCharacterRepository(catalogue)
            val filter = CharacterFilter(query = "rick", status = StatusFilter.Alive)

            repository.page(filter, 1)
            repository.page(filter, 1, PageLoadPolicy.ForceNetwork)
            repository.details(CharacterId("2"), enrich = true)

            assertEquals(
                listOf(
                    FakeCharacterRepository.Call.Page(filter, 1, PageLoadPolicy.Default),
                    FakeCharacterRepository.Call.Page(filter, 1, PageLoadPolicy.ForceNetwork),
                    FakeCharacterRepository.Call.Details(CharacterId("2"), enrich = true),
                ),
                repository.calls,
                "TEST-UNIT-053: the history is observable, including the page-load policy (DEC-086)",
            )
        }

    private fun <T> DataResult<T>.success(): T =
        when (this) {
            is DataResult.Success -> value
            is DataResult.Failure -> throw AssertionError("TEST-UNIT-053: expected a success, got $failure")
        }
}
