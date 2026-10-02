package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
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
 * `TEST-UNIT-053` — `FakeRemoteSource` honours `IC-011` (`DEC-072`, `DEC-090`): the same outcomes the
 * REST adapter produces for the same logical request, plus the controls a repository or pager test
 * needs. These cases prove the double only; the adapter's own evidence is `TEST-CONTRACT-001`/`003`.
 */
class FakeRemoteSourceTest {
    private val catalogue =
        FakeCatalogue(
            characters = (1..25).map { FakeCatalogue.character(it.toString(), episodeIds = listOf("1", "2")) },
            episodes = listOf(FakeCatalogue.episode("1"), FakeCatalogue.episode("3")),
        )

    @Test
    fun `TEST-UNIT-053 given_a_catalogue_when_pages_are_requested_then_they_page_like_the_adapter`() =
        TestTime.run {
            val source = FakeRemoteSource(catalogue)

            val first = source.characterPage(CharacterFilter(), 1).success()
            val beyond = assertIs<DataResult.Failure>(source.characterPage(CharacterFilter(), 3))
            val none = source.characterPage(CharacterFilter(query = "zzzznotreal"), 1).success()

            assertEquals(listOf(20, 2, 25, 2), listOf(first.characters.size, first.pageCount, first.totalCount, first.nextPage))
            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, "3"), beyond.failure, "TEST-UNIT-053")
            assertTrue(none.characters.isEmpty(), "TEST-UNIT-053: a filtered first page with no match is an empty page")
        }

    @Test
    fun `TEST-UNIT-053 given_an_id_when_details_are_requested_then_unknown_is_not_found_and_no_enrichment_is_attached`() =
        TestTime.run {
            val source = FakeRemoteSource(catalogue)

            val missing = assertIs<DataResult.Failure>(source.characterDetails(CharacterId("999")))
            val details = source.characterDetails(CharacterId("7")).success()

            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER, "999"), missing.failure, "TEST-UNIT-053")
            assertNull(details.episodeSummaries, "TEST-UNIT-053: enrichment is orchestrated above IC-011")
            assertEquals(listOf(EpisodeId("1"), EpisodeId("2")), details.episodeIds)
        }

    @Test
    fun `TEST-UNIT-053 given_episode_ids_when_requested_then_an_empty_list_costs_no_request_and_omissions_are_warnings`() =
        TestTime.run {
            val source = FakeRemoteSource(catalogue)

            val empty = source.episodes(emptyList()).success()
            val callsAfterEmpty = source.calls.size
            val mixed = source.episodes(listOf(EpisodeId("3"), EpisodeId("2"), EpisodeId("1"), EpisodeId("3")))

            assertTrue(empty.isEmpty(), "TEST-UNIT-053")
            assertEquals(0, callsAfterEmpty, "TEST-UNIT-053: an empty batch performs no request (IC-011)")
            assertEquals(listOf(EpisodeId("3"), EpisodeId("1")), mixed.success().map { it.id }, "TEST-UNIT-053: caller order, by id")
            assertEquals(listOf(ApiWarning(RemoteWarnings.MISSING_RESOURCE, "2")), mixed.warnings, "TEST-UNIT-053")
        }

    @Test
    fun `TEST-UNIT-053 given_a_queued_failure_when_the_next_call_runs_then_it_is_a_value_and_the_history_is_kept`() =
        TestTime.run {
            val source = FakeRemoteSource(catalogue)
            source.failNext(ApiFailure.Timeout)

            val failed = assertIs<DataResult.Failure>(source.characterDetails(CharacterId("1")))
            source.characterPage(CharacterFilter(query = "rick"), 2)
            source.episodes(listOf(EpisodeId("1")))

            assertEquals(ApiFailure.Timeout, failed.failure, "TEST-UNIT-053: an expected failure is returned, not thrown")
            assertEquals(
                listOf(
                    FakeRemoteSource.Call.Details(CharacterId("1")),
                    FakeRemoteSource.Call.Page(CharacterFilter(query = "rick"), 2),
                    FakeRemoteSource.Call.Episodes(listOf(EpisodeId("1"))),
                ),
                source.calls,
                "TEST-UNIT-053: every remote call is recorded in order",
            )
        }

    // `advanceTimeBy` is the virtual-time control `TESTING.md` §5 prescribes; it is marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `TEST-UNIT-053 given_latency_when_the_caller_is_cancelled_then_cancellation_propagates`() =
        TestTime.run {
            val source = FakeRemoteSource(catalogue, latency = 500.milliseconds)
            val load = async(start = CoroutineStart.UNDISPATCHED) { source.episodes(listOf(EpisodeId("1"))) }

            advanceTimeBy(200)
            load.cancel()

            assertFailsWith<CancellationException>("TEST-UNIT-053: cancellation is never a failure value") { load.await() }
        }

    private fun <T> DataResult<T>.success(): T =
        when (this) {
            is DataResult.Success -> value
            is DataResult.Failure -> throw AssertionError("TEST-UNIT-053: expected a success, got $failure")
        }
}
