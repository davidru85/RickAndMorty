package io.github.davidru85.multiverse.core.data.remote.rest

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
import io.github.davidru85.multiverse.core.data.remote.isTlsFailure
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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * The REST implementation of `IC-011` (`API_SPECS.md` §4, `TASK-037`), the default protocol.
 *
 * Every request is a `GET` to the fixed HTTPS host, built from validated input; nothing in a response
 * changes where the app connects. Each outcome is a `DataResult` with a network source: transport,
 * status and decoding failures are mapped here (`API_SPECS.md` §6.1) and only a
 * `CancellationException` propagates. Decoding runs on [decodingDispatcher], and a `Retry-After`
 * date is measured on [clock], never on the wall clock (`REQ-REL-004`).
 *
 * Every request it sends is logged through [logger] (`IC-024`, `OBSERVABILITY.md` §3): `LOG-001` when
 * it starts and exactly one of `LOG-002` completed, `LOG-003` failed, `LOG-004` rejected by the
 * allow-list or `LOG-014` cancelled when it ends, plus `LOG-022` when a response carries an unknown
 * value. The events carry the operation, the path template, the page, the filter names and the status
 * family — never the URL, a filter value or a body — and a duration measured on [timeSource]. Input
 * rejected before a request exists is not a request and is not logged.
 *
 * The adapter does not retry, coalesce or cache: those are the repository's (`TASK-038`, `TASK-020`).
 * It does not own [client] either — whoever built the client closes it.
 */
public class RestCharacterRemoteDataSource(
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
        val status = filter.status.wireValue()
        val parameters =
            buildList {
                add("page" to page.toString())
                if (query.isNotEmpty()) add("name" to query)
                if (status != null) add("status" to status)
            }
        val url = RickAndMortyApi.url(RickAndMortyApi.CHARACTER, parameters = parameters)
        val request = Request(LogOperation.CHARACTER_LIST, PathTemplate.CHARACTER, page, filterNames(query, filter.status))
        return observed(request) { trace ->
            when (val exchange = exchange(url, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    when {
                        // `API-CHAR-005`: the first page of a filter that matches nothing is an empty page.
                        exchange.status == NOT_FOUND && (query.isNotEmpty() || status != null) && page == 1 ->
                            success(
                                CharacterPage(emptyList(), page, pageCount = null, totalCount = null, nextPage = null, previousPage = null),
                            )
                        // Any other list `404` is reported as such; ending pagination on it is the pager's call.
                        exchange.status == NOT_FOUND ->
                            failure(ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, page.toString()))
                        else -> exchange.read(trace, RestPageDto.serializer(RestCharacterDto.serializer())) { it.toPage(page) }
                    }
            }
        }
    }

    override suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails> {
        if (!id.value.isRestId()) return failure(ApiFailure.InvalidRequest(DETAIL_ID))
        val url = RickAndMortyApi.url(RickAndMortyApi.CHARACTER, id.value)
        return observed(Request(LogOperation.CHARACTER_DETAIL, PathTemplate.CHARACTER_BY_ID)) { trace ->
            when (val exchange = exchange(url, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    if (exchange.status == NOT_FOUND) {
                        failure(ApiFailure.NotFound(RemoteResources.CHARACTER, id.value))
                    } else {
                        exchange.read(trace, RestCharacterDto.serializer()) { dto ->
                            // A single-resource response for another id is not this character.
                            dto.toDetails().also { if (it.id != id) throw RejectedPayload(ApiFailure.MalformedResponse) }
                        }
                    }
            }
        }
    }

    override suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        val requested = ids.distinct()
        if (requested.isEmpty()) return success(emptyList())
        if (requested.any { !it.value.isRestId() }) return failure(ApiFailure.InvalidRequest(DETAIL_ID))

        // `API-CHAR-006`: bounded chunks; a chunk of one uses the single-resource route, because a
        // batch route needs at least two ids and answers with an array.
        val found = LinkedHashMap<EpisodeId, EpisodeSummary>()
        for (chunk in requested.chunked(BATCH_CHUNK)) {
            when (val outcome = if (chunk.size == 1) episode(chunk.single()) else episodeBatch(chunk)) {
                is DataResult.Failure -> return outcome
                is DataResult.Success ->
                    outcome.value
                        .filter { it.id in chunk && it.id !in found }
                        .forEach { found[it.id] = it }
            }
        }
        // Reconciled by id, in the caller's order; an omitted id is a warning, not a failure.
        val warnings =
            requested
                .filterNot { it in found }
                .map { ApiWarning(RemoteWarnings.MISSING_RESOURCE, it.value) }
        return DataResult.Success(requested.mapNotNull { found[it] }, DataSource.NETWORK, isStale = false, warnings = warnings)
    }

    private suspend fun episode(id: EpisodeId): DataResult<List<EpisodeSummary>> {
        val url = RickAndMortyApi.url(RickAndMortyApi.EPISODE, id.value)
        return observed(EPISODE_REQUEST) { trace ->
            when (val exchange = exchange(url, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    if (exchange.status == NOT_FOUND) {
                        success(emptyList())
                    } else {
                        exchange.read(trace, RestEpisodeDto.serializer()) { listOf(it.toSummary()) }
                    }
            }
        }
    }

    private suspend fun episodeBatch(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        val url = RickAndMortyApi.url(RickAndMortyApi.EPISODE, ids.joinToString(",") { it.value })
        return observed(EPISODE_REQUEST) { trace ->
            when (val exchange = exchange(url, trace)) {
                is Exchange.Failed -> failure(exchange.failure)
                is Exchange.Answered ->
                    if (exchange.status == NOT_FOUND) {
                        success(emptyList())
                    } else {
                        exchange.read(trace, ListSerializer(RestEpisodeDto.serializer())) { dtos -> dtos.map { it.toSummary() } }
                    }
            }
        }
    }

    /** What one request is, as its log events describe it: never its URL or its filter values. */
    private class Request(
        val operation: LogOperation,
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
                RemoteProtocol.Rest,
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

    /** Whether a mapped value preserved an unknown remote enum value (`AC-REQ-NFR-004-2`). */
    private fun Any?.preservesUnknownValue(): Boolean =
        when (this) {
            is CharacterPage -> characters.any { it.status is CharacterStatus.Unsupported || it.gender is CharacterGender.Unsupported }
            is CharacterDetails -> status is CharacterStatus.Unsupported || gender is CharacterGender.Unsupported
            else -> false
        }

    /** What one `GET` produced: a response to map, or a transport failure already classified. */
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
        url: Url,
        trace: Trace,
    ): Exchange =
        try {
            val response = client.get(url)
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
            Exchange.Failed(classifyTransport(transport))
        }

    /**
     * `API_SPECS.md` §6.1: a TLS failure is `Unknown` and never retried (`API-ERR-003`); any other I/O
     * failure is a connectivity failure, `Offline`, retried within the budget (`API-ERR-001`); anything
     * else is not a transport failure at all and stays `Unknown` with its cause.
     */
    private fun classifyTransport(failure: Exception): ApiFailure =
        when {
            failure.isTlsFailure() -> ApiFailure.Unknown(failure)
            failure is IOException -> ApiFailure.Offline
            else -> ApiFailure.Unknown(failure)
        }

    /** Maps a non-`404` response: its status family, then its body, then the domain mapping. */
    private suspend fun <D, T> Exchange.Answered.read(
        trace: Trace,
        deserializer: DeserializationStrategy<D>,
        map: (D) -> T,
    ): DataResult<T> {
        statusFailure()?.let { return failure(it) }
        if (body.isBlank()) return failure(ApiFailure.EmptyBody)
        return withContext(decodingDispatcher) {
            val dto =
                try {
                    RemoteJson.decodeFromString(deserializer, body)
                } catch (_: IllegalArgumentException) {
                    // `SerializationException` included: malformed JSON or a missing required field.
                    return@withContext failure(ApiFailure.MalformedResponse)
                }
            try {
                val value = map(dto)
                if (value.preservesUnknownValue()) {
                    logger.log(LogLevel.DEBUG) { LogEvent.UnknownValuePreserved(trace.request.operation, trace.request.template) }
                }
                success(value)
            } catch (rejected: RejectedPayload) {
                failure(rejected.failure)
            }
        }
    }

    /** `API_SPECS.md` §6.1 status families; `null` for a success. */
    private fun Exchange.Answered.statusFailure(): ApiFailure? =
        when (status) {
            in 200..299 -> null
            REQUEST_TIMEOUT -> ApiFailure.Timeout
            TOO_MANY_REQUESTS -> ApiFailure.RateLimited(retryAfterSeconds(retryAfter, clock.now()))
            in 400..499 -> ApiFailure.InvalidRequest("http-$status")
            in 500..599 -> ApiFailure.Server(status)
            // A redirect is never followed, so its `Location` is never requested (`REQ-SEC-001`).
            in 300..399 -> ApiFailure.InvalidRequest(DETAIL_REDIRECT)
            else -> ApiFailure.Unknown(null)
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
        const val NOT_FOUND = 404
        const val REQUEST_TIMEOUT = 408
        const val TOO_MANY_REQUESTS = 429
        const val BATCH_CHUNK = 20
        const val DETAIL_PAGE = "page"
        const val DETAIL_ID = "id"
        const val DETAIL_REDIRECT = "redirect"
        val EPISODE_REQUEST = Request(LogOperation.EPISODE_BATCH, PathTemplate.EPISODES_BY_IDS)
    }
}
