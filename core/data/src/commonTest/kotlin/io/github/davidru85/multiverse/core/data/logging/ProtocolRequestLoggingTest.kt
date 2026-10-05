package io.github.davidru85.multiverse.core.data.logging

import io.github.davidru85.multiverse.core.data.cache.bypassedCache
import io.github.davidru85.multiverse.core.data.remote.RemoteProtocolSource
import io.github.davidru85.multiverse.core.data.remote.graphql.GraphQlCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.logging.LogField
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-072` — the request events of both protocols, through the one logging contract
 * (`IC-024`, `OBSERVABILITY.md` §3, `REQ-OBS-001`, `TASK-116`).
 *
 * The owner asked to see the REST and the GraphQL requests in a debug build's log. Both adapters are
 * the production ones, over committed captures, behind the production repository and its protocol
 * choice; the logger is the validating one over a recording sink, at the threshold a debug build and a
 * release build use. A request must be told apart by its `protocol` field (`LOG-001` carries it, and a
 * completion joins its start through the correlation id), and a switch must show on the very next
 * request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolRequestLoggingTest {
    private val all = CharacterFilter()

    /** The active protocol, as the settings source supplies it; a replayed value is not a switch. */
    private class Protocols(
        val selected: MutableStateFlow<RemoteProtocol>,
    ) : RemoteProtocolSource {
        override suspend fun current(): RemoteProtocol = selected.value

        override fun changes(): Flow<RemoteProtocol> = selected.drop(1)
    }

    private fun TestScope.repository(
        logger: ValidatingAppLogger,
        protocols: Protocols,
    ): RemoteCharacterRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client =
            MockHttp
                .client(
                    MockHttp.route("character-page-01.json", urlContains = "/api/character"),
                    MockHttp.route("graphql-page-01.json", method = "POST"),
                    dispatcher = dispatcher,
                ).first
                .config { rickAndMortyDefaults() }
        val clock = MutableFakeClock()
        return RemoteCharacterRepository(
            rest = RestCharacterRemoteDataSource(client, dispatcher, clock, logger, testScheduler.timeSource),
            graphQl = GraphQlCharacterRemoteDataSource(client, dispatcher, clock, logger, testScheduler.timeSource),
            scope = backgroundScope,
            random = FixedRandom(0.5),
            logger = logger,
            cache = bypassedCache(clock),
            protocols = protocols,
        )
    }

    private fun List<LogRecord>.protocolsOf(catalogueId: String): List<String?> =
        filter { it.catalogueId == catalogueId }.map { it.fields[LogField.PROTOCOL] }

    @Test
    fun `TEST-UNIT-072 given_a_debug_logger_when_a_rest_then_a_graphql_request_run_then_each_is_logged_with_its_protocol`() =
        TestTime.run {
            val sink = RecordingLogSink()
            val protocols = Protocols(MutableStateFlow(RemoteProtocol.Rest))
            val repository = repository(ValidatingAppLogger.forDebug(sink), protocols)

            assertTrue(repository.page(all, 1) is DataResult.Success, "TEST-UNIT-072: the REST page loads")
            protocols.selected.value = RemoteProtocol.GraphQl
            assertTrue(repository.page(all, 1) is DataResult.Success, "TEST-UNIT-072: the GraphQL page loads")

            assertEquals(
                listOf("REST", "GRAPHQL"),
                sink.records.protocolsOf("LOG-001"),
                "TEST-UNIT-072: each request start names its protocol, and the switch shows on the next request",
            )
            // `LOG-002` carries no protocol of its own (`OBSERVABILITY.md` §3): it joins its start
            // through the correlation id, so each completion must resolve to a start of each protocol.
            val startedProtocol =
                sink.records
                    .filter { it.catalogueId == "LOG-001" }
                    .associate { it.fields[LogField.CORRELATION_ID] to it.fields[LogField.PROTOCOL] }
            assertEquals(
                listOf("REST", "GRAPHQL"),
                sink.records.filter { it.catalogueId == "LOG-002" }.map { startedProtocol[it.fields[LogField.CORRELATION_ID]] },
                "TEST-UNIT-072: each completion joins the start of its own protocol through its correlation id",
            )
        }

    @Test
    fun `TEST-UNIT-072 given_the_release_logger_when_both_protocols_request_then_no_request_event_reaches_the_sink`() =
        TestTime.run {
            val sink = RecordingLogSink()
            val protocols = Protocols(MutableStateFlow(RemoteProtocol.Rest))
            val repository = repository(ValidatingAppLogger.forRelease(sink), protocols)

            repository.page(all, 1)
            protocols.selected.value = RemoteProtocol.GraphQl
            repository.page(all, 1)

            assertEquals(
                emptyList(),
                sink.records.filter { it.catalogueId == "LOG-001" || it.catalogueId == "LOG-002" },
                "TEST-UNIT-072: a release build logs at ERROR only, so successful requests leave no record (DEC-039)",
            )
        }
}
