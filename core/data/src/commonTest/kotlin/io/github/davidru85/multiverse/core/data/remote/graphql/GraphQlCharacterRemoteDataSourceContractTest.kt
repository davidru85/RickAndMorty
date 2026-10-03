package io.github.davidru85.multiverse.core.data.remote.graphql

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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
 * `TEST-CONTRACT-004` in fixture/replay mode (`TESTING.md` §4.3, §11, `DEC-073`): the GraphQL adapter
 * of `IC-011` (`API_SPECS.md` §5, §6.2) against the committed `graphql-*.json` fixtures, through Ktor
 * `MockEngine`.
 *
 * Every case asserts the request the adapter built — method, path, operation name, and that user input
 * travels **only** as variables (§9) — as well as the domain value or `ApiFailure` it returned. Two
 * cases use a synthetic envelope: the committed GraphQL fixtures are list-shaped, so the
 * detail-with-episodes and the episode batch have no capture to replay yet; those literals pin the
 * envelope mapping and the request shape, and the missing captures are reported as a fixture gap
 * rather than invented in a fixture.
 */
class GraphQlCharacterRemoteDataSourceContractTest {
    private class Stack(
        val source: GraphQlCharacterRemoteDataSource,
        val sink: RecordingLogSink,
        val served: MutableList<MockHttp.Served>,
    )

    /** A client with the production defaults; [routes] are first-match-wins on the one endpoint. */
    private fun stack(vararg routes: MockHttp.Route): Stack {
        val sink = RecordingLogSink()
        val (raw, served) = MockHttp.client(*routes)
        return Stack(source(raw, sink), sink, served)
    }

    /** A client answering [routes] in order, each once. */
    private fun sequenced(vararg routes: MockHttp.Route): Stack {
        val sink = RecordingLogSink()
        val (raw, served) = MockHttp.sequence(*routes)
        return Stack(source(raw, sink), sink, served)
    }

    private fun source(
        raw: HttpClient,
        sink: RecordingLogSink,
    ) = GraphQlCharacterRemoteDataSource(
        raw.config { rickAndMortyDefaults() },
        Dispatchers.Unconfined,
        MutableFakeClock(),
        ValidatingAppLogger.forDebug(sink),
    )

    /** A committed fixture served as a GraphQL `POST`. */
    private fun graphQlRoute(
        fixture: String,
        status: Int? = null,
    ) = MockHttp.route(fixture, method = "POST", status = status)

    /** A synthetic envelope, for a request the committed fixtures do not cover. */
    private fun envelope(body: String) =
        MockHttp.Route(
            method = "POST",
            urlContains = "",
            body = body,
            status = 200,
            contentType = "application/json",
        )

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

    /** The JSON body of a request the adapter sent, decoded. */
    private fun MockHttp.Served.payload() = Json.parseToJsonElement(body).jsonObject

    private fun MockHttp.Served.assertGraphQlTransport() {
        assertEquals("POST", method, "TEST-CONTRACT-004: the GraphQL operation is a POST (API_SPECS.md §5.1)")
        val parsed = Url(url)
        assertEquals(URLProtocol.HTTPS, parsed.protocol, "TEST-CONTRACT-004: HTTPS only")
        assertEquals(RickAndMortyApi.HOST, parsed.host, "TEST-CONTRACT-004: the one allow-listed host")
        assertEquals("/graphql", parsed.encodedPath, "TEST-CONTRACT-004: the GraphQL endpoint")
    }

    // ------------------------------------------------------------------ TEST-CONTRACT-004

    @Test
    fun `TEST-CONTRACT-004 given_the_page_fixture_when_page_one_is_requested_then_it_decodes_with_its_metadata`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-page-01.json"))

            val result = stack.source.characterPage(CharacterFilter(), 1)

            val page = result.success()
            assertEquals(DataSource.NETWORK, result.source, "TEST-CONTRACT-004: a remote value comes from the network")
            assertEquals(20, page.characters.size, "TEST-CONTRACT-004: the server page size")
            assertEquals(
                "1",
                page.characters
                    .first()
                    .id.value,
                "TEST-CONTRACT-004: a GraphQL ID becomes the canonical string",
            )
            assertEquals("Rick Sanchez", page.characters.first().name)
            assertEquals(
                "Citadel of Ricks",
                page.characters
                    .first()
                    .lastKnownLocation.name,
            )
            assertEquals(
                listOf(1, 42, 826, 2, null),
                listOf(page.page, page.pageCount, page.totalCount, page.nextPage, page.previousPage),
                "TEST-CONTRACT-004: info carries page numbers, not URLs (API_SPECS.md §5.3)",
            )
            val sent = stack.served.single()
            sent.assertGraphQlTransport()
            assertEquals(
                "CharacterPage",
                sent
                    .payload()
                    .getValue("operationName")
                    .jsonPrimitive.content,
            )
            assertEquals(
                1,
                sent
                    .payload()
                    .getValue("variables")
                    .jsonObject
                    .getValue("page")
                    .jsonPrimitive.int,
                "TEST-CONTRACT-004: the page is a variable",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_filter_when_a_page_is_requested_then_the_values_travel_only_as_variables`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-page-01.json"))
            val query = "Rick Sanchez & co"

            stack.source.characterPage(CharacterFilter(query = "  $query  ", status = StatusFilter.Alive), 2)

            val sent = stack.served.single()
            val document =
                sent
                    .payload()
                    .getValue("query")
                    .jsonPrimitive.content
            val body = sent.body
            assertTrue(query !in document, "TEST-CONTRACT-004: user input is never interpolated into the query text (§9)")
            assertTrue("alive" !in document, "TEST-CONTRACT-004: nor is a filter value")
            assertTrue(
                document.contains("characters(page: \$page, filter: \$filter)"),
                "TEST-CONTRACT-004: the checked-in document of API_SPECS.md §5.5 is the static query",
            )
            val variables = sent.payload().getValue("variables").jsonObject
            assertEquals(2, variables.getValue("page").jsonPrimitive.int)
            val filter = variables.getValue("filter").jsonObject
            assertEquals(
                query,
                filter.getValue("name").jsonPrimitive.content,
                "TEST-CONTRACT-004: the query is trimmed, then sent as a variable",
            )
            assertEquals("alive", filter.getValue("status").jsonPrimitive.content, "TEST-CONTRACT-004: canonical lowercase status")
            assertTrue(
                stack.sink.records.none { record -> record.fields.values.any { it.contains("Rick") } },
                "TEST-CONTRACT-004: no variable value reaches a sink",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_blank_filter_when_a_page_is_requested_then_no_filter_variable_is_sent`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-page-01.json"))

            stack.source.characterPage(CharacterFilter(query = "   "), 1)

            val variables =
                stack.served
                    .single()
                    .payload()
                    .getValue("variables")
                    .jsonObject
            assertTrue("filter" !in variables, "TEST-CONTRACT-004: a blank filter sends no filter object (AC-REQ-FUNC-003-3)")
        }

    @Test
    fun `TEST-CONTRACT-004 given_the_empty_filter_fixture_when_requested_then_it_is_an_empty_success_not_a_malformed_failure`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-empty-filter.json"))

            val result = stack.source.characterPage(CharacterFilter(query = "zzzznotreal"), 1)

            val page = result.success()
            assertTrue(page.characters.isEmpty(), "TEST-CONTRACT-004: an empty results list is an empty page (API_SPECS.md §5.3)")
            assertEquals(
                listOf(1, null, null, null, null),
                listOf(page.page, page.pageCount, page.totalCount, page.nextPage, page.previousPage),
                "TEST-CONTRACT-004: null metadata stays null, never zero (AC-REQ-FUNC-001-3)",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_the_null_root_fixture_when_a_detail_is_requested_then_it_is_not_found`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-null-root.json"))

            val failure = stack.source.characterDetails(CharacterId("99999")).failure()

            assertEquals(
                ApiFailure.NotFound(RemoteResources.CHARACTER, "99999"),
                failure,
                "TEST-CONTRACT-004: 200 with data.character == null is NotFound (API_SPECS.md §5.5)",
            )
            val payload = stack.served.single().payload()
            assertEquals("CharacterDetail", payload.getValue("operationName").jsonPrimitive.content)
            assertEquals(
                "99999",
                payload
                    .getValue("variables")
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_an_errors_only_envelope_when_it_is_usable_then_it_maps_to_the_graph_ql_failure`() =
        TestTime.run {
            // The envelope shape of API_SPECS.md §5.1: HTTP success with errors and no data. The live
            // capture this fixture came from was answered 400, which §6.2 step 1 maps by status family
            // first; the GraphQl family is the 2xx case, so the fixture is served as §5.1 describes it.
            val stack = stack(graphQlRoute("graphql-errors-only.json", status = 200))

            val failure = stack.source.characterPage(CharacterFilter(), 1).failure()

            val graphQl = assertIs<ApiFailure.GraphQl>(failure, "TEST-CONTRACT-004: data == null with errors is GraphQl")
            assertEquals(setOf("GRAPHQL_VALIDATION_FAILED"), graphQl.codes)
            assertTrue(graphQl.messages.isNotEmpty(), "TEST-CONTRACT-004: the envelope's messages are carried on the failure")
            assertTrue(
                stack.sink.records.none { record -> record.fields.values.any { it.contains("Cannot query field") } },
                "TEST-CONTRACT-004: a message is never logged",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_rejected_query_when_answered_then_it_is_invalid_without_echoing_the_server_message`() =
        TestTime.run {
            val validation = stack(graphQlRoute("graphql-validation-error-400.json"))
            val depth = stack(MockHttp.errorRoute("graphql-errors-only.json", 413, method = "POST"))

            val refused = validation.source.characterPage(CharacterFilter(), 1).failure()
            val tooDeep = depth.source.characterPage(CharacterFilter(), 1).failure()

            val invalid =
                assertIs<ApiFailure.InvalidRequest>(refused, "TEST-CONTRACT-004: a validation rejection is InvalidRequest (API-ERR-012)")
            assertTrue(
                invalid.detail?.contains("unknownField") != true,
                "TEST-CONTRACT-004: the server's message is never echoed into a failure",
            )
            assertIs<ApiFailure.InvalidRequest>(tooDeep, "TEST-CONTRACT-004: a depth rejection is InvalidRequest (API-ERR-013)")
        }

    @Test
    fun `TEST-CONTRACT-004 given_usable_data_with_errors_when_requested_then_it_is_a_success_with_a_warning`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-partial-with-errors.json"))

            val result = stack.source.characterPage(CharacterFilter(), 1)

            val page = result.success()
            assertEquals(20, page.characters.size, "TEST-CONTRACT-004: a partial response renders the data it carries (API-ERR-011)")
            assertEquals(
                listOf(RemoteWarnings.PARTIAL_RESPONSE),
                result.warnings.map { it.code },
                "TEST-CONTRACT-004: and it is marked, so nothing caches it",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_detail_envelope_when_requested_then_the_episode_id_list_is_mapped`() =
        TestTime.run {
            val stack =
                stack(
                    envelope(
                        """{"data":{"character":{"id":"1","name":"Rick Sanchez","status":"Alive","species":"Human","type":"",""" +
                            """"gender":"Male","image":"https://example.invalid/api/character/avatar/1.jpeg",""" +
                            """"created":"2017-11-04T18:48:46.250Z",""" +
                            """"origin":{"id":"1","name":"Earth (C-137)","type":"Planet","dimension":"C-137"},""" +
                            """"location":{"id":"3","name":"Citadel of Ricks","type":"Space station","dimension":"unknown"},""" +
                            """"episode":[{"id":"1","name":"Pilot","episode":"S01E01","air_date":"December 2, 2013"}]}}}""",
                    ),
                )

            val details = stack.source.characterDetails(CharacterId("1")).success()

            assertEquals(CharacterId("1"), details.id)
            assertEquals(listOf(EpisodeId("1")), details.episodeIds, "TEST-CONTRACT-004: the detail carries the episode id list")
            assertNull(details.episodeSummaries, "TEST-CONTRACT-004: enrichment is orchestrated above IC-011, as on REST")
            assertEquals("1", details.origin.id?.value, "TEST-CONTRACT-004: a GraphQL relation exposes its own id")
            assertEquals("Earth (C-137)", details.origin.name)
            assertEquals("3", details.lastKnownLocation.id?.value)
            assertEquals(
                "2017-11-04T18:48:46.250Z",
                details.createdAt.toString(),
                "TEST-CONTRACT-004: created maps to the domain instant",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_episode_ids_when_requested_then_the_batch_is_one_request_and_an_empty_batch_is_none`() =
        TestTime.run {
            val stack =
                stack(
                    envelope(
                        """{"data":{"episodesByIds":[{"id":"1","name":"Pilot","episode":"S01E01","air_date":"December 2, 2013"},""" +
                            """{"id":"2","name":"Lawnmower Dog","episode":"S01E02","air_date":"December 9, 2013"}]}}""",
                    ),
                )

            val empty = stack.source.episodes(emptyList())
            val batch = stack.source.episodes(listOf(EpisodeId("1"), EpisodeId("2"))).success()

            assertEquals(emptyList(), empty.success(), "TEST-CONTRACT-004: an empty batch is an empty list")
            assertEquals(listOf("S01E01", "S01E02"), batch.map { it.code })
            assertEquals("December 2, 2013", batch.first().airDate, "TEST-CONTRACT-004: air_date stays a display string")
            val payload = stack.served.single().payload()
            assertEquals("EpisodesByIds", payload.getValue("operationName").jsonPrimitive.content)
            assertEquals(
                listOf("1", "2"),
                payload
                    .getValue("variables")
                    .jsonObject
                    .getValue("ids")
                    .jsonArray
                    .map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_missing_required_field_when_mapped_then_it_is_malformed_rather_than_defaulted`() =
        TestTime.run {
            val stack = stack(envelope("""{"data":{"characters":{"info":{"count":1},"results":[{"id":"1"}]}}}"""))

            val failure = stack.source.characterPage(CharacterFilter(), 1).failure()

            assertEquals(
                ApiFailure.MalformedResponse,
                failure,
                "TEST-CONTRACT-004: a result without the fields the screen renders fails, it is not defaulted (§6.2 step 6)",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_input_the_adapter_cannot_route_when_requested_then_it_fails_without_a_request`() =
        TestTime.run {
            val stack = stack(graphQlRoute("graphql-page-01.json"))

            assertIs<ApiFailure.InvalidRequest>(stack.source.characterPage(CharacterFilter(), 0).failure())
            assertIs<ApiFailure.InvalidRequest>(stack.source.characterDetails(CharacterId("  ")).failure())
            assertIs<ApiFailure.InvalidRequest>(stack.source.episodes(listOf(EpisodeId("1"), EpisodeId(""))).failure())
            assertTrue(stack.served.isEmpty(), "TEST-CONTRACT-004: an unroutable request is rejected before transport")
        }

    @Test
    fun `TEST-CONTRACT-004 given_error_statuses_when_requested_then_each_maps_to_its_failure_family`() =
        TestTime.run {
            val server = stack(MockHttp.errorRoute("graphql-errors-only.json", 500, method = "POST"))
            val limited =
                stack(MockHttp.errorRoute("graphql-errors-only.json", 429, method = "POST", headers = mapOf("Retry-After" to "7")))
            val timeout = stack(MockHttp.errorRoute("graphql-errors-only.json", 408, method = "POST"))
            val blank = stack(envelope(""))

            assertEquals(
                ApiFailure.Server(500),
                server.source.characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-004 (API-ERR-007)",
            )
            assertEquals(
                ApiFailure.RateLimited(7),
                limited.source.characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-004 (API-ERR-005)",
            )
            assertEquals(
                ApiFailure.Timeout,
                timeout.source.characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-004 (API-ERR-004)",
            )
            assertEquals(
                ApiFailure.EmptyBody,
                blank.source.characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-004: a success with no body is EmptyBody (API-ERR-008)",
            )
        }

    @Test
    fun `TEST-CONTRACT-004 given_a_transport_failure_when_requested_then_it_maps_without_escaping`() =
        TestTime.run {
            val timeout = HttpClient(MockEngine { throw HttpRequestTimeoutException("request", 20_000) }) { rickAndMortyDefaults() }
            val reset = HttpClient(MockEngine { throw IOException("connection reset") }) { rickAndMortyDefaults() }
            val unexpected = HttpClient(MockEngine { throw IllegalStateException("engine defect") }) { rickAndMortyDefaults() }

            fun source(client: HttpClient) =
                GraphQlCharacterRemoteDataSource(
                    client,
                    Dispatchers.Unconfined,
                    MutableFakeClock(),
                    ValidatingAppLogger.forDebug(RecordingLogSink()),
                )

            assertEquals(ApiFailure.Timeout, source(timeout).characterPage(CharacterFilter(), 1).failure())
            assertEquals(
                ApiFailure.Offline,
                source(reset).characterPage(CharacterFilter(), 1).failure(),
                "TEST-CONTRACT-004: a connectivity failure is Offline, never an escaping exception",
            )
            assertIs<ApiFailure.Unknown>(source(unexpected).characterPage(CharacterFilter(), 1).failure())
        }

    // `runCurrent` is the virtual-time control `TESTING.md` §5 prescribes; it is marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `TEST-CONTRACT-004 given_a_caller_cancelled_mid_request_when_awaited_then_cancellation_propagates`() =
        TestTime.run {
            val hanging = HttpClient(MockEngine { awaitCancellation() }) { rickAndMortyDefaults() }
            val source =
                GraphQlCharacterRemoteDataSource(
                    hanging,
                    Dispatchers.Unconfined,
                    MutableFakeClock(),
                    ValidatingAppLogger.forDebug(RecordingLogSink()),
                )

            val load = async(start = CoroutineStart.UNDISPATCHED) { source.characterPage(CharacterFilter(), 1) }
            runCurrent()
            load.cancel()

            assertFailsWith<CancellationException>("TEST-CONTRACT-004: cancellation is control flow (API-ERR-017)") {
                load.await()
            }
        }
}
