package io.github.davidru85.multiverse.core.data.remote.graphql

import io.github.davidru85.multiverse.core.data.logging.currentCorrelationId
import io.github.davidru85.multiverse.core.data.logging.errorClass
import io.github.davidru85.multiverse.core.data.logging.filterNames
import io.github.davidru85.multiverse.core.data.logging.statusFamilyOf
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RejectedRequestException
import io.github.davidru85.multiverse.core.data.remote.RemoteJson
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.data.remote.classifyTransportFailure
import io.github.davidru85.multiverse.core.data.remote.statusFailure
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.FilterName
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.PathTemplate
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * The GraphQL implementation of `IC-011` (`API_SPECS.md` §5, §6.2; `TASK-075`), selected by
 * `remoteProtocol == GraphQl` (`DEC-056`, [`adr/0011-runtime-remote-protocol.md`]).
 *
 * It `POST`s the **checked-in documents of §5.5** — constants in this file, never built from input —
 * to the one allow-listed host at `/graphql`, through the same injected Ktor client as the REST
 * adapter. Every user input travels as a **variable** (`§9`), so a query string, an id or a filter
 * value can never change the document that is sent or the cost and depth the service evaluates.
 *
 * Outcomes map to the same domain values as
 * [io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource] for the same
 * logical request (`§6.1`, `§6.2`, `AC-REQ-FUNC-034-3`), in the order `§6.2` fixes: transport and status
 * families first, then the envelope — an errors-only envelope is `ApiFailure.GraphQl`, a null
 * single-resource root is `NotFound`, a null list root is `MalformedResponse` unless `results`
 * explicitly says the set is empty, and usable data carrying errors is a success **with a warning** so
 * no cache ever stores a partial response. Only a `CancellationException` propagates.
 *
 * Requests are logged exactly as the REST adapter logs them (`IC-024`) — `LOG-001`, one of `LOG-002` /
 * `LOG-003` / `LOG-004` / `LOG-014`, and `LOG-022` — with `protocol = RemoteProtocol.GraphQl`. A
 * variable value, a message from the envelope's `errors` and the document itself never reach a sink.
 *
 * There is no normalized cache and no second HTTP stack (`ADR-0011`): the app-level
 * [io.github.davidru85.multiverse.core.data.cache.ResponseCache] keys both protocols and separates
 * them by the `protocol` component.
 */
public class GraphQlCharacterRemoteDataSource(
    private val client: HttpClient,
    private val decodingDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val logger: AppLogger,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : CharacterRemoteDataSource {
    override suspend fun characterPage(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage> {
        if (page < 1) return failure(ApiFailure.InvalidRequest(DETAIL_PAGE))
        val query = filter.query.trim()
        val variables =
            buildJsonObject {
                put("page", JsonPrimitive(page))
                filterVariable(query, filter.status)?.let { put("filter", it) }
            }
        val request =
            Request(LogOperation.CHARACTER_LIST, CHARACTER_PAGE_OPERATION, PathTemplate.CHARACTER, page, filterNames(query, filter.status))
        return observed(request) { trace ->
            when (val exchange = exchange(CHARACTER_PAGE_DOCUMENT, request, variables, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered -> exchange.read(trace, GraphQlPageDataDto.serializer()) { it.toPage(page) }
            }
        }
    }

    override suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails> {
        if (!id.value.isGraphQlId()) return failure(ApiFailure.InvalidRequest(DETAIL_ID))
        val variables = buildJsonObject { put("id", JsonPrimitive(id.value)) }
        val request = Request(LogOperation.CHARACTER_DETAIL, CHARACTER_DETAIL_OPERATION, PathTemplate.CHARACTER_BY_ID)
        return observed(request) { trace ->
            when (val exchange = exchange(CHARACTER_DETAIL_DOCUMENT, request, variables, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    exchange.read(trace, GraphQlDetailDataDto.serializer()) { data ->
                        // `§6.2` step 4: a null single-resource root without errors is NotFound; a
                        // detail for another id is not this character.
                        val character = data.character ?: throw GraphQlRootNull(ApiFailure.NotFound(RemoteResources.CHARACTER, id.value))
                        character.toDetails().also { if (it.id != id) throw RejectedGraphQlPayload() }
                    }
            }
        }
    }

    override suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        val requested = ids.distinct()
        if (requested.isEmpty()) return success(emptyList())
        if (requested.any { !it.value.isGraphQlId() }) return failure(ApiFailure.InvalidRequest(DETAIL_ID))

        // The GraphQL schema has no single-episode-id operation, so a bounded batch is one operation
        // and a final singleton keeps to it (`API_SPECS.md` §5.2; `IC-011`'s chunk rule).
        val found = LinkedHashMap<EpisodeId, EpisodeSummary>()
        for (chunk in requested.chunked(BATCH_CHUNK)) {
            when (val outcome = episodeBatch(chunk)) {
                is DataResult.Failure -> return outcome
                is DataResult.Success ->
                    outcome.value
                        .filter { it.id in chunk && it.id !in found }
                        .forEach { found[it.id] = it }
            }
        }
        // Reconciled by id, in the caller's order; an omitted id is a warning, not a failure.
        val warnings = requested.filterNot { it in found }.map { ApiWarning(RemoteWarnings.MISSING_RESOURCE, it.value) }
        return DataResult.Success(requested.mapNotNull { found[it] }, DataSource.NETWORK, isStale = false, warnings = warnings)
    }

    private suspend fun episodeBatch(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        val variables = buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it.value) })) }
        return observed(EPISODE_REQUEST) { trace ->
            when (val exchange = exchange(EPISODES_BY_IDS_DOCUMENT, EPISODE_REQUEST, variables, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    exchange.read(trace, GraphQlEpisodesDataDto.serializer()) { data ->
                        // A list root that is null is a malformed payload, not an empty batch (§6.2
                        // step 4): an empty batch is `[]`, which the screen renders as no rows.
                        (data.episodes ?: throw RejectedGraphQlPayload()).map { it?.toSummary() ?: throw RejectedGraphQlPayload() }
                    }
            }
        }
    }

    /**
     * The filter object of `FilterCharacter` (`§5.3`), omitting a blank name and `All` — exactly the
     * parameters the REST adapter sends, so both protocols answer the same logical request. A filter
     * with nothing to send is omitted entirely, so the request carries no empty object.
     */
    private fun filterVariable(
        query: String,
        status: StatusFilter,
    ): JsonObject? {
        val filter =
            buildJsonObject {
                if (query.isNotEmpty()) put("name", JsonPrimitive(query))
                status.wireValue()?.let { put("status", JsonPrimitive(it)) }
            }
        return filter.takeIf { it.isNotEmpty() }
    }

    /**
     * What one request is, as its log events describe it — never its variables or its document.
     *
     * [operationName] is the checked-in document's own name (`CharacterPage`, …), which is what
     * `API_SPECS.md` §5.1 puts in the envelope; [operation] is the closed `LOG-###` operation, and the
     * two are deliberately different vocabularies.
     */
    private class Request(
        val operation: LogOperation,
        val operationName: String,
        val template: PathTemplate,
        val page: Int? = null,
        val filterNames: Set<FilterName> = emptySet(),
    )

    /** What one request observed on its way: the status family it was answered with, and how it ended. */
    private class Trace(
        val request: Request,
        val correlationId: String?,
    ) {
        var statusFamily: StatusFamily? = null
        var rejected: Boolean = false
    }

    /**
     * Sends one request through [send] and logs it: `LOG-001` first, then exactly one terminal event.
     * The duration is the request's own, read on [timeSource].
     */
    private suspend fun <T> observed(
        request: Request,
        send: suspend (Trace) -> DataResult<T>,
    ): DataResult<T> {
        val trace = Trace(request, currentCorrelationId())
        logger.log(LogLevel.DEBUG) {
            LogEvent.RequestStarted(
                request.operation,
                request.template,
                request.page,
                request.filterNames,
                RemoteProtocol.GraphQl,
                trace.correlationId,
            )
        }
        val started = timeSource.markNow()
        val result =
            try {
                send(trace)
            } catch (cancellation: CancellationException) {
                logger.log(LogLevel.DEBUG) { LogEvent.RequestCancelled(request.operation, trace.correlationId) }
                throw cancellation
            }
        val durationMs = started.elapsedNow().inWholeMilliseconds
        when (result) {
            is DataResult.Success ->
                logger.log(LogLevel.INFO) {
                    LogEvent.RequestCompleted(
                        request.operation,
                        request.template,
                        request.page,
                        trace.statusFamily ?: StatusFamily.SUCCESSFUL,
                        durationMs,
                        trace.correlationId,
                        if (result.value.isEmptyResult()) LogOutcome.EMPTY else LogOutcome.SUCCESS,
                    )
                }
            is DataResult.Failure ->
                if (trace.rejected) {
                    logger.log(LogLevel.ERROR) { LogEvent.ForeignHostRejected(request.operation, screen = null, trace.correlationId) }
                } else {
                    logger.log(LogLevel.ERROR) {
                        LogEvent.RequestFailed(
                            request.operation,
                            request.template,
                            request.page,
                            trace.statusFamily,
                            result.failure.errorClass(),
                            durationMs,
                            trace.correlationId,
                        )
                    }
                }
        }
        return result
    }

    private fun Any?.isEmptyResult(): Boolean = (this is CharacterPage && characters.isEmpty()) || (this is List<*> && isEmpty())

    /** What one `POST` produced: a response to map, or a transport failure already classified. */
    private sealed interface Exchange {
        class Answered(
            val status: Int,
            val body: String,
            val retryAfter: String?,
        ) : Exchange

        class Failed(
            val failure: ApiFailure,
        ) : Exchange
    }

    private suspend fun exchange(
        document: String,
        request: Request,
        variables: JsonObject,
        trace: Trace,
    ): Exchange =
        try {
            val response =
                client.post(RickAndMortyApi.graphQl()) {
                    contentType(ContentType.Application.Json)
                    setBody(envelopeJson(request.operationName, document, variables))
                }
            trace.statusFamily = statusFamilyOf(response.status.value)
            Exchange.Answered(response.status.value, response.bodyAsText(), response.headers[HttpHeaders.RetryAfter])
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RejectedRequestException) {
            trace.rejected = true
            Exchange.Failed(ApiFailure.InvalidRequest(FOREIGN_HOST))
        } catch (_: HttpRequestTimeoutException) {
            trace.statusFamily = StatusFamily.NO_RESPONSE
            Exchange.Failed(ApiFailure.Timeout)
        } catch (_: ConnectTimeoutException) {
            trace.statusFamily = StatusFamily.NO_RESPONSE
            Exchange.Failed(ApiFailure.Timeout)
        } catch (_: SocketTimeoutException) {
            trace.statusFamily = StatusFamily.NO_RESPONSE
            Exchange.Failed(ApiFailure.Timeout)
        } catch (transport: Exception) {
            // A transport failure observed while the caller is being cancelled is the cancellation.
            currentCoroutineContext().ensureActive()
            trace.statusFamily = StatusFamily.NO_RESPONSE
            Exchange.Failed(classifyTransportFailure(transport))
        }

    /** The `application/json` envelope of `API_SPECS.md` §5.1, with the document as a JSON string. */
    private fun envelopeJson(
        operationName: String,
        document: String,
        variables: JsonObject,
    ): String =
        buildJsonObject {
            put("operationName", JsonPrimitive(operationName))
            put("query", JsonPrimitive(document))
            put("variables", variables)
        }.toString()

    /**
     * Maps one answered `POST` in the order `API_SPECS.md` §6.2 fixes: the status family first, then the
     * envelope, then the data it carries.
     *
     * The envelope is decoded in two steps — the generic [GraphQlRawEnvelopeDto] first, so `data ==
     * null` and `errors` are visible before any typed decode, then the data element into [deserializer].
     * Usable data accompanied by errors is a success carrying a `partial-response` warning
     * (`§6.2` step 5, `API-ERR-011`), which is what makes the write guard refuse it.
     */
    private suspend fun <D, T> Exchange.Answered.read(
        trace: Trace,
        deserializer: DeserializationStrategy<D>,
        map: (D) -> T,
    ): DataResult<T> {
        statusFailure(status, retryAfter, clock.now())?.let { return failure(it) }
        if (body.isBlank()) return failure(ApiFailure.EmptyBody)
        return withContext(decodingDispatcher) {
            val envelope =
                try {
                    RemoteJson.decodeFromString(GraphQlRawEnvelopeDto.serializer(), body)
                } catch (_: IllegalArgumentException) {
                    // `SerializationException` included: malformed JSON or an envelope without `data`.
                    return@withContext failure(ApiFailure.MalformedResponse)
                }
            val data = envelope.data
            if (data == null) {
                return@withContext if (envelope.errors.isNullOrEmpty()) {
                    failure(ApiFailure.MalformedResponse)
                } else {
                    // `§6.2` step 3: the errors-only envelope, carrying codes and messages but never a
                    // message in the log.
                    failure(ApiFailure.GraphQl(codes = envelope.codes(), messages = envelope.messages()))
                }
            }
            try {
                val value = map(RemoteJson.decodeFromJsonElement(deserializer, data))
                if (value.preservesUnknownValue()) {
                    logger.log(LogLevel.DEBUG) { LogEvent.UnknownValuePreserved(trace.request.operation, trace.request.template) }
                }
                if (envelope.errors.isNullOrEmpty()) {
                    success(value)
                } else {
                    DataResult.Success(
                        value,
                        DataSource.NETWORK,
                        isStale = false,
                        warnings = listOf(ApiWarning(RemoteWarnings.PARTIAL_RESPONSE)),
                    )
                }
            } catch (rootNull: GraphQlRootNull) {
                failure(rootNull.failure)
            } catch (_: RejectedGraphQlPayload) {
                failure(ApiFailure.MalformedResponse)
            } catch (_: IllegalArgumentException) {
                // A data element that does not decode is malformed, not a crash.
                failure(ApiFailure.MalformedResponse)
            }
        }
    }

    /** The error codes an errors-only envelope carries (`API_ERR-012`/`013`); the schema is open. */
    private fun GraphQlRawEnvelopeDto.codes(): Set<String> = errors.orEmpty().mapNotNull { it.extensions?.code }.toSet()

    /** The messages the failure carries; they are never logged (`REQ-SEC-005`). */
    private fun GraphQlRawEnvelopeDto.messages(): List<String> = errors.orEmpty().map { it.message }

    /** Whether a mapped value preserved an unknown remote enum value (`AC-REQ-NFR-004-2`). */
    private fun Any?.preservesUnknownValue(): Boolean =
        when (this) {
            is CharacterPage -> characters.any { it.status is CharacterStatus.Unsupported || it.gender is CharacterGender.Unsupported }
            is CharacterDetails -> status is CharacterStatus.Unsupported || gender is CharacterGender.Unsupported
            else -> false
        }

    private fun StatusFilter.wireValue(): String? =
        when (this) {
            StatusFilter.All -> null
            StatusFilter.Alive -> "alive"
            StatusFilter.Dead -> "dead"
            StatusFilter.Unknown -> "unknown"
        }

    private fun <T> success(value: T): DataResult<T> = DataResult.Success(value, DataSource.NETWORK, isStale = false)

    private fun failure(failure: ApiFailure): DataResult.Failure = DataResult.Failure(failure, DataSource.NETWORK)

    private companion object {
        const val BATCH_CHUNK = 20
        const val DETAIL_PAGE = "page"
        const val DETAIL_ID = "id"

        /** The detail a link outside the allow-list carries; never the URL itself. */
        const val FOREIGN_HOST = "foreign-host"

        /** The operation names `API_SPECS.md` §5.1's envelope carries; they name the documents below. */
        const val CHARACTER_PAGE_OPERATION = "CharacterPage"
        const val CHARACTER_DETAIL_OPERATION = "CharacterDetail"
        const val EPISODES_BY_IDS_OPERATION = "EpisodesByIds"

        /**
         * The checked-in operations of `API_SPECS.md` §5.5, verbatim. They are constants in source
         * (`§5.6`, `§9`): input is sent as variables and never concatenated into a document, so the
         * text the service evaluates has a fixed cost and depth.
         */
        val CHARACTER_PAGE_DOCUMENT: String =
            """query CharacterPage(${'$'}page: Int, ${'$'}filter: FilterCharacter) {
              |  characters(page: ${'$'}page, filter: ${'$'}filter) {
              |    info { count pages next prev }
              |    results { id name status species type gender image location { id name } }
              |  }
              |}
            """.trimMargin()

        val CHARACTER_DETAIL_DOCUMENT: String =
            """query CharacterDetail(${'$'}id: ID!) {
              |  character(id: ${'$'}id) {
              |    id name status species type gender image created
              |    origin { id name type dimension }
              |    location { id name type dimension }
              |    episode { id name episode air_date }
              |  }
              |}
            """.trimMargin()

        val EPISODES_BY_IDS_DOCUMENT: String =
            """query EpisodesByIds(${'$'}ids: [ID!]!) {
              |  episodesByIds(ids: ${'$'}ids) {
              |    id name episode air_date
              |  }
              |}
            """.trimMargin()

        val EPISODE_REQUEST = Request(LogOperation.EPISODE_BATCH, EPISODES_BY_IDS_OPERATION, PathTemplate.EPISODES_BY_IDS)
    }
}
