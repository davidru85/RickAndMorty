package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FixtureLoader
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `TEST-CONTRACT-001` and `TEST-CONTRACT-003` in fixture/replay mode (`TESTING.md` §11, `DEC-073`):
 * the REST adapter of `IC-011` against the committed fixtures, through Ktor `MockEngine`.
 *
 * The client under test carries the production defaults (`rickAndMortyDefaults`), and every response
 * body comes from a committed fixture; a case asserts the request the adapter built as well as the
 * domain value or failure it returned. No case opens a socket.
 */
class RestCharacterRemoteDataSourceContractTest {
    private fun source(
        client: HttpClient,
        dispatcher: CoroutineDispatcher,
    ) = RestCharacterRemoteDataSource(client, dispatcher)

    private fun client(vararg routes: MockHttp.Route): Pair<HttpClient, MutableList<MockHttp.Served>> {
        val (raw, served) = MockHttp.client(*routes)
        return raw.config { rickAndMortyDefaults() } to served
    }

    private fun <T> DataResult<T>.success(): T =
        when (this) {
            is DataResult.Success -> value
            is DataResult.Failure -> throw AssertionError("expected a success, got $failure")
        }

    private fun <T> DataResult<T>.failure(): ApiFailure =
        when (this) {
            is DataResult.Success -> throw AssertionError("expected a failure, got $value")
            is DataResult.Failure -> failure
        }

    // ------------------------------------------------------------------ TEST-CONTRACT-001

    @Test
    fun `TEST-CONTRACT-001 given_the_first_page_fixture_when_page_one_is_requested_then_it_decodes_with_its_metadata`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("character-page-01.json"))

            val result = source(client, dispatcher).characterPage(CharacterFilter(), 1)

            val page = result.success()
            assertEquals(DataSource.NETWORK, result.source, "TEST-CONTRACT-001: a remote value comes from the network")
            assertEquals(20, page.characters.size, "TEST-CONTRACT-001: the server page size")
            assertEquals(CharacterId("1"), page.characters.first().id, "TEST-CONTRACT-001: REST ids become canonical strings")
            assertEquals(listOf(1, 42, 826, 2, null), listOf(page.page, page.pageCount, page.totalCount, page.nextPage, page.previousPage))
            val url = Url(served.single().url)
            assertEquals(URLProtocol.HTTPS, url.protocol, "TEST-CONTRACT-001: HTTPS only")
            assertEquals(RickAndMortyApi.HOST, url.host, "TEST-CONTRACT-001: the configured host only")
            assertEquals("/api/character", url.encodedPath)
            assertEquals("1", url.parameters["page"])
            assertNull(url.parameters["name"], "TEST-CONTRACT-001: a blank query sends no name")
            assertNull(url.parameters["status"], "TEST-CONTRACT-001: All sends no status (IC-010)")
        }

    @Test
    fun `TEST-CONTRACT-001 given_middle_and_last_page_fixtures_when_requested_then_next_is_parsed_and_null_ends_pagination`() =
        TestTime.run { dispatcher ->
            val (client, _) =
                client(
                    MockHttp.route("character-page-21.json", urlContains = "page=21"),
                    MockHttp.route("character-page-42.json", urlContains = "page=42"),
                )
            val adapter = source(client, dispatcher)

            val middle = adapter.characterPage(CharacterFilter(), 21).success()
            val last = adapter.characterPage(CharacterFilter(), 42).success()

            assertEquals(listOf(22, 20), listOf(middle.nextPage, middle.previousPage), "TEST-CONTRACT-001: page numbers from the links")
            assertNull(last.nextPage, "TEST-CONTRACT-001: info.next == null is the end of pagination (API-CHAR-002)")
            assertEquals(41, last.previousPage)
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_combined_filter_when_a_later_page_is_requested_then_every_value_is_encoded_and_preserved`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("character-page-21.json"))
            val adapter = source(client, dispatcher)

            adapter.characterPage(CharacterFilter(query = "  Rick Sánchez & co/2 ", status = StatusFilter.Alive), 2)
            adapter.characterPage(CharacterFilter(query = "   ", status = StatusFilter.Unknown), 3)

            val filtered = Url(served[0].url)
            assertEquals("Rick Sánchez & co/2", filtered.parameters["name"], "TEST-CONTRACT-001: the query is trimmed and encoded")
            assertEquals("alive", filtered.parameters["status"], "TEST-CONTRACT-001: canonical lowercase status (API-CHAR-004)")
            assertEquals("2", filtered.parameters["page"], "TEST-CONTRACT-001: the page travels with the filter")
            assertTrue("&co" !in filtered.encodedQuery, "TEST-CONTRACT-001: an ampersand in the query is encoded")
            val blank = Url(served[1].url)
            assertNull(blank.parameters["name"], "TEST-CONTRACT-001: a blank query sends no name (AC-REQ-FUNC-003-3)")
            assertEquals("unknown", blank.parameters["status"])
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_filtered_404_on_the_first_page_when_requested_then_it_is_an_empty_success`() =
        TestTime.run { dispatcher ->
            val (client, _) = client(MockHttp.route("character-filter-empty-404.json"))

            val result = source(client, dispatcher).characterPage(CharacterFilter(query = "zzzznotreal"), 1)

            val page = result.success()
            assertTrue(page.characters.isEmpty(), "TEST-CONTRACT-001: a filtered 404 is an empty page (API-CHAR-005)")
            assertEquals(
                listOf(1, null, null, null, null),
                listOf(page.page, page.pageCount, page.totalCount, page.nextPage, page.previousPage),
            )
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_404_that_is_not_a_filtered_first_page_when_requested_then_it_is_not_found`() =
        TestTime.run { dispatcher ->
            val (client, _) = client(MockHttp.route("character-page-beyond-last-404.json"))
            val adapter = source(client, dispatcher)

            assertEquals(
                ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, "43"),
                adapter.characterPage(CharacterFilter(), 43).failure(),
                "TEST-CONTRACT-001: deciding that a paging 404 ends pagination is the pager's job (IC-014)",
            )
            assertEquals(
                ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, "2"),
                adapter.characterPage(CharacterFilter(status = StatusFilter.Dead), 2).failure(),
                "TEST-CONTRACT-001: only the first page of a filter is an empty result",
            )
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_detail_fixture_and_a_404_when_details_are_requested_then_the_object_decodes_and_404_is_not_found`() =
        TestTime.run { dispatcher ->
            val (client, served) =
                client(
                    MockHttp.route("character-detail.json", urlContains = "/character/1"),
                    MockHttp.route("character-detail-not-found-404.json", urlContains = "/character/99999"),
                )
            val adapter = source(client, dispatcher)

            val details = adapter.characterDetails(CharacterId("1")).success()
            val missing = adapter.characterDetails(CharacterId("99999")).failure()

            assertEquals(CharacterId("1"), details.id)
            assertNull(details.episodeSummaries, "TEST-CONTRACT-001: enrichment is orchestrated above the seam")
            assertEquals("/api/character/1", Url(served[0].url).encodedPath, "TEST-CONTRACT-001: the single-resource route")
            assertEquals(ApiFailure.NotFound(RemoteResources.CHARACTER, "99999"), missing, "TEST-CONTRACT-001 (API-CHAR-003)")
        }

    @Test
    fun `TEST-CONTRACT-001 given_one_or_several_episode_ids_when_requested_then_object_and_array_routes_are_used`() =
        TestTime.run { dispatcher ->
            val (client, served) =
                client(
                    MockHttp.route("episode-batch.json", urlContains = "/episode/1,2,3"),
                    MockHttp.route("episode-single.json", urlContains = "/episode/1"),
                )
            val adapter = source(client, dispatcher)

            val single = adapter.episodes(listOf(EpisodeId("1"))).success()
            val batch = adapter.episodes(listOf(EpisodeId("1"), EpisodeId("2"), EpisodeId("3"))).success()

            assertEquals("/api/episode/1", Url(served[0].url).encodedPath, "TEST-CONTRACT-001: one id uses the object route")
            assertEquals("/api/episode/1,2,3", Url(served[1].url).encodedPath, "TEST-CONTRACT-001: several ids use the array route")
            assertEquals(listOf("S01E01"), single.map { it.code })
            assertEquals(listOf(EpisodeId("1"), EpisodeId("2"), EpisodeId("3")), batch.map { it.id })
            assertEquals("December 2, 2013", batch.first().airDate, "TEST-CONTRACT-001: air_date stays a display string")
        }

    @Test
    fun `TEST-CONTRACT-001 given_no_episode_id_when_requested_then_no_request_is_issued`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("episode-batch.json"))

            val result = source(client, dispatcher).episodes(emptyList())

            assertEquals(emptyList(), result.success(), "TEST-CONTRACT-001: an empty batch is an empty list")
            assertTrue(served.isEmpty(), "TEST-CONTRACT-001: an empty batch performs no request (API-CHAR-006)")
        }

    @Test
    fun `TEST-CONTRACT-001 given_more_ids_than_a_chunk_when_requested_then_chunks_are_bounded_and_results_are_reconciled_by_id`() =
        TestTime.run { dispatcher ->
            // The batch fixture holds episodes 1, 2 and 3; the singleton route serves episode 1's object,
            // which does not match the requested id 21. Reconciliation is by id, never by position.
            val (client, served) =
                client(
                    MockHttp.route("episode-batch.json", urlContains = "/episode/1,2"),
                    MockHttp.route("episode-single.json", urlContains = "/episode/21"),
                )
            val ids = (1..21).map { EpisodeId(it.toString()) }

            val result = source(client, dispatcher).episodes(ids)

            assertEquals(
                listOf("/api/episode/" + (1..20).joinToString(","), "/api/episode/21"),
                served.map { Url(it.url).encodedPath },
                "TEST-CONTRACT-001: chunks of at most 20, and the final singleton uses the object route",
            )
            assertEquals(listOf(EpisodeId("1"), EpisodeId("2"), EpisodeId("3")), result.success().map { it.id })
            assertEquals(
                (4..21).map { ApiWarning(RemoteWarnings.MISSING_RESOURCE, it.toString()) },
                result.warnings,
                "TEST-CONTRACT-001: every omitted id is a warning, not a failure",
            )
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_foreign_pagination_host_when_a_page_decodes_then_it_is_rejected_and_nothing_is_followed`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("character-page-01-foreign-next.json"))

            val failure = source(client, dispatcher).characterPage(CharacterFilter(), 1).failure()

            assertIs<ApiFailure.InvalidRequest>(failure, "TEST-CONTRACT-001: a foreign next URL is rejected (REQ-SEC-001)")
            assertEquals(1, served.size, "TEST-CONTRACT-001: the foreign URL is never requested")
        }

    @Test
    fun `TEST-CONTRACT-001 given_input_the_adapter_cannot_route_when_requested_then_it_fails_without_a_request`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("character-page-01.json"))
            val adapter = source(client, dispatcher)

            assertIs<ApiFailure.InvalidRequest>(adapter.characterPage(CharacterFilter(), 0).failure())
            assertIs<ApiFailure.InvalidRequest>(adapter.characterDetails(CharacterId("1/../2")).failure())
            assertIs<ApiFailure.InvalidRequest>(adapter.episodes(listOf(EpisodeId("1"), EpisodeId("x,2"))).failure())
            assertTrue(served.isEmpty(), "TEST-CONTRACT-001: an unroutable request is rejected before transport")
        }

    @Test
    fun `TEST-CONTRACT-001 given_error_statuses_when_requested_then_each_maps_to_its_failure_family`() =
        TestTime.run { dispatcher ->
            val body = "character-page-beyond-last-404.json"
            val (client, _) =
                client(
                    MockHttp.errorRoute(body, 500, urlContains = "page=1&"),
                    MockHttp.errorRoute(body, 429, urlContains = "page=2&", headers = mapOf("Retry-After" to "7")),
                    MockHttp.errorRoute(body, 429, urlContains = "page=3&"),
                    MockHttp.errorRoute(body, 408, urlContains = "page=4&"),
                    MockHttp.errorRoute(body, 400, urlContains = "page=5&"),
                )
            val adapter = source(client, dispatcher)
            // The status filter is appended after the page, so `page=N&` names one route exactly.
            val filter = CharacterFilter(status = StatusFilter.Alive)

            assertEquals(ApiFailure.Server(500), adapter.characterPage(filter, 1).failure(), "TEST-CONTRACT-001 (API-ERR-007)")
            assertEquals(ApiFailure.RateLimited(7), adapter.characterPage(filter, 2).failure(), "TEST-CONTRACT-001 (API-ERR-005)")
            assertEquals(ApiFailure.RateLimited(null), adapter.characterPage(filter, 3).failure(), "TEST-CONTRACT-001: no advice, no delay")
            assertEquals(ApiFailure.Timeout, adapter.characterPage(filter, 4).failure(), "TEST-CONTRACT-001 (API-ERR-004)")
            assertIs<ApiFailure.InvalidRequest>(adapter.characterPage(filter, 5).failure(), "TEST-CONTRACT-001 (API-ERR-006)")
        }

    @Test
    fun `TEST-CONTRACT-001 given_a_transport_timeout_or_failure_when_requested_then_it_maps_without_escaping`() =
        TestTime.run { dispatcher ->
            val timeout = HttpClient(MockEngine { throw HttpRequestTimeoutException("request", 20_000) }) { rickAndMortyDefaults() }
            val broken = IOException("connection reset")
            val reset = HttpClient(MockEngine { throw broken }) { rickAndMortyDefaults() }

            assertEquals(ApiFailure.Timeout, source(timeout, dispatcher).characterPage(CharacterFilter(), 1).failure())
            // Coroutines may recover the stack trace into a copy, so the cause is compared by type and message.
            val unknown =
                assertIs<ApiFailure.Unknown>(
                    source(reset, dispatcher).characterPage(CharacterFilter(), 1).failure(),
                    "TEST-CONTRACT-001: an unclassified transport failure is Unknown, never an escaping exception",
                )
            assertIs<IOException>(unknown.cause, "TEST-CONTRACT-001: the transport cause is kept")
            assertEquals(broken.message, unknown.cause?.message)
        }

    // ------------------------------------------------------------------ TEST-CONTRACT-003

    @Test
    fun `TEST-CONTRACT-003 given_malformed_and_empty_bodies_when_decoded_then_they_map_to_their_failures`() =
        TestTime.run { dispatcher ->
            val (client, _) =
                client(
                    MockHttp.route("malformed-body.txt", urlContains = "page=1"),
                    MockHttp.route("empty-body.txt", urlContains = "page=2"),
                )
            val adapter = source(client, dispatcher)

            assertEquals(
                ApiFailure.MalformedResponse,
                adapter.characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-003 (AC-REQ-NFR-004-1)",
            )
            assertEquals(ApiFailure.EmptyBody, adapter.characterPage(CharacterFilter(), 2).failure(), "TEST-CONTRACT-003 (API-ERR-008)")
        }

    @Test
    fun `TEST-CONTRACT-003 given_a_missing_required_field_when_decoded_then_it_is_malformed_rather_than_defaulted`() =
        TestTime.run { dispatcher ->
            val (client, _) = client(MockHttp.route("character-detail-missing-name.json"))

            val failure = source(client, dispatcher).characterDetails(CharacterId("1")).failure()

            assertEquals(ApiFailure.MalformedResponse, failure, "TEST-CONTRACT-003: a missing name is never defaulted")
        }

    @Test
    fun `TEST-CONTRACT-003 given_unknown_values_and_an_empty_reference_when_decoded_then_they_are_preserved_without_a_crash`() =
        TestTime.run { dispatcher ->
            val (client, _) =
                client(
                    MockHttp.route("character-detail-unknown-enums.json", urlContains = "/character/1"),
                    MockHttp.route("character-detail-unknown-reference.json", urlContains = "/character/8"),
                    MockHttp.route("character-detail-empty-type.json", urlContains = "/character/2"),
                )
            val adapter = source(client, dispatcher)
            val unknownEnums = Json.parseToJsonElement(FixtureLoader.text("character-detail-unknown-enums.json")).jsonObject

            val enums = adapter.characterDetails(CharacterId("1")).success()
            val reference = adapter.characterDetails(CharacterId("8")).success()
            val emptyType = adapter.characterDetails(CharacterId("2")).success()

            assertEquals(
                CharacterStatus.Unsupported(unknownEnums.getValue("status").jsonPrimitive.content),
                enums.status,
                "TEST-CONTRACT-003: an unknown status is preserved verbatim (AC-REQ-NFR-004-2)",
            )
            assertEquals(
                CharacterGender.Unsupported(unknownEnums.getValue("gender").jsonPrimitive.content),
                enums.gender,
                "TEST-CONTRACT-003: an unknown gender is preserved verbatim",
            )
            assertNull(reference.origin.id, "TEST-CONTRACT-003: an empty reference URL has no id")
            assertEquals("unknown", reference.origin.name, "TEST-CONTRACT-003: the raw name is kept for presentation")
            assertNull(emptyType.type, "TEST-CONTRACT-003: an empty type is absent in the domain")
        }

    @Test
    fun `TEST-CONTRACT-003 given_a_relation_url_on_a_foreign_host_when_decoded_then_the_response_is_rejected`() =
        TestTime.run { dispatcher ->
            val (client, served) = client(MockHttp.route("character-detail-foreign-episode.json"))

            val failure = source(client, dispatcher).characterDetails(CharacterId("1")).failure()

            assertIs<ApiFailure.InvalidRequest>(failure, "TEST-CONTRACT-003: a foreign relation URL is rejected (REQ-SEC-001)")
            assertEquals(1, served.size, "TEST-CONTRACT-003: nothing is followed")
        }

    // `runCurrent` is the virtual-time control `TESTING.md` §5 prescribes; it is marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `TEST-CONTRACT-003 given_a_caller_cancelled_mid_request_when_awaited_then_cancellation_propagates_and_is_never_a_failure`() =
        TestTime.run { dispatcher ->
            val hanging = HttpClient(MockEngine { awaitCancellation() }) { rickAndMortyDefaults() }
            val adapter = source(hanging, dispatcher)

            val load = async(start = CoroutineStart.UNDISPATCHED) { adapter.characterPage(CharacterFilter(), 1) }
            runCurrent()
            load.cancel()

            assertFailsWith<CancellationException>("TEST-CONTRACT-003: cancellation is control flow (API-ERR-017)") {
                load.await()
            }
        }
}
