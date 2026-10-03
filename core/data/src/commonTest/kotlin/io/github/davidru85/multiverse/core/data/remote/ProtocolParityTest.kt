package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.graphql.GraphQlCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-CONTRACT-005` — protocol parity (`API_SPECS.md` §10.2, `AC-REQ-FUNC-034-3`,
 * `adr/0011-runtime-remote-protocol.md` validation criteria).
 *
 * The two `IC-011` implementations must return equal domain values for the same logical request, which
 * is what lets a screen switch protocol without knowing which one answered it. This suite drives both
 * adapters over the committed captures of the *same* request — `character-page-01.json` for REST and
 * `graphql-page-01.json` for GraphQL, both captured from page 1 — and compares the mapped domain values.
 */
class ProtocolParityTest {
    private fun rest(client: HttpClient) =
        RestCharacterRemoteDataSource(client, Dispatchers.Unconfined, MutableFakeClock(), ValidatingAppLogger.forDebug(RecordingLogSink()))

    private fun graphQl(client: HttpClient) =
        GraphQlCharacterRemoteDataSource(client, Dispatchers.Unconfined, MutableFakeClock(), ValidatingAppLogger.forDebug(RecordingLogSink()))

    private fun client(route: MockHttp.Route): HttpClient =
        MockHttp.client(route).first.config { rickAndMortyDefaults() }

    @Test
    fun `TEST-CONTRACT-005 given_the_same_page_captured_over_both_protocols_when_mapped_then_the_domain_values_are_equal`() =
        TestTime.run {
            val overRest =
                rest(client(MockHttp.route("character-page-01.json")))
                    .characterPage(CharacterFilter(), 1)
            val overGraphQl =
                graphQl(client(MockHttp.route("graphql-page-01.json", method = "POST")))
                    .characterPage(CharacterFilter(), 1)

            val restValue = (overRest as DataResult.Success).value
            val graphQlValue = (overGraphQl as DataResult.Success).value

            assertEquals(
                restValue,
                graphQlValue,
                "TEST-CONTRACT-005: the same logical page maps to the same domain value over either protocol (AC-REQ-FUNC-034-3)",
            )
        }

    @Test
    fun `TEST-CONTRACT-005 given_a_filter_with_no_match_when_both_protocols_answer_then_both_are_an_empty_page`() =
        TestTime.run {
            // REST answers the filtered first page with 404 and GraphQL with 200/results: [] — the
            // different wire shapes of one domain outcome (API_SPECS.md §6.1, §6.2, API-ERR-014).
            val overRest =
                rest(client(MockHttp.route("character-filter-empty-404.json")))
                    .characterPage(CharacterFilter(query = "zzzznotreal"), 1)
            val overGraphQl =
                graphQl(client(MockHttp.route("graphql-empty-filter.json", method = "POST")))
                    .characterPage(CharacterFilter(query = "zzzznotreal"), 1)

            assertEquals(
                (overRest as DataResult.Success).value,
                (overGraphQl as DataResult.Success).value,
                "TEST-CONTRACT-005: an empty filtered result is an empty page on both (AC-REQ-FUNC-034-3)",
            )
        }
}
