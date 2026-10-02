package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RemoteJson
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.cancellation.CancellationException

/**
 * The REST implementation of `IC-011` (`API_SPECS.md` §4, `TASK-037`), the default protocol.
 *
 * Every request is a `GET` to the fixed HTTPS host, built from validated input; nothing in a response
 * changes where the app connects. Each outcome is a `DataResult` with a network source: transport,
 * status and decoding failures are mapped here (`API_SPECS.md` §6.1) and only a
 * `CancellationException` propagates. Decoding runs on [decodingDispatcher].
 *
 * The adapter does not retry, coalesce or cache: those are the repository's (`TASK-038`, `TASK-020`).
 * It does not own [client] either — whoever built the client closes it.
 */
public class RestCharacterRemoteDataSource(
    private val client: HttpClient,
    private val decodingDispatcher: CoroutineDispatcher,
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
        return when (val exchange = exchange(url)) {
            is Exchange.Failed -> failure(exchange.failure)
            is Exchange.Answered ->
                when {
                    // `API-CHAR-005`: the first page of a filter that matches nothing is an empty page.
                    exchange.status == NOT_FOUND && (query.isNotEmpty() || status != null) && page == 1 ->
                        success(CharacterPage(emptyList(), page, pageCount = null, totalCount = null, nextPage = null, previousPage = null))
                    // Any other list `404` is reported as such; ending pagination on it is the pager's call.
                    exchange.status == NOT_FOUND ->
                        failure(ApiFailure.NotFound(RemoteResources.CHARACTER_PAGE, page.toString()))
                    else -> exchange.read(RestPageDto.serializer(RestCharacterDto.serializer())) { it.toPage(page) }
                }
        }
    }

    override suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails> {
        if (!id.value.isRestId()) return failure(ApiFailure.InvalidRequest(DETAIL_ID))
        return when (val exchange = exchange(RickAndMortyApi.url(RickAndMortyApi.CHARACTER, id.value))) {
            is Exchange.Failed -> failure(exchange.failure)
            is Exchange.Answered ->
                if (exchange.status == NOT_FOUND) {
                    failure(ApiFailure.NotFound(RemoteResources.CHARACTER, id.value))
                } else {
                    exchange.read(RestCharacterDto.serializer()) { dto ->
                        // A single-resource response for another id is not this character.
                        dto.toDetails().also { if (it.id != id) throw RejectedPayload(ApiFailure.MalformedResponse) }
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

    private suspend fun episode(id: EpisodeId): DataResult<List<EpisodeSummary>> =
        when (val exchange = exchange(RickAndMortyApi.url(RickAndMortyApi.EPISODE, id.value))) {
            is Exchange.Failed -> failure(exchange.failure)
            is Exchange.Answered ->
                if (exchange.status == NOT_FOUND) {
                    success(emptyList())
                } else {
                    exchange.read(RestEpisodeDto.serializer()) { listOf(it.toSummary()) }
                }
        }

    private suspend fun episodeBatch(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        val url = RickAndMortyApi.url(RickAndMortyApi.EPISODE, ids.joinToString(",") { it.value })
        return when (val exchange = exchange(url)) {
            is Exchange.Failed -> failure(exchange.failure)
            is Exchange.Answered ->
                if (exchange.status == NOT_FOUND) {
                    success(emptyList())
                } else {
                    exchange.read(ListSerializer(RestEpisodeDto.serializer())) { dtos -> dtos.map { it.toSummary() } }
                }
        }
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

    private suspend fun exchange(url: Url): Exchange =
        try {
            val response = client.get(url)
            Exchange.Answered(response.status.value, response.bodyAsText(), response.headers[HttpHeaders.RetryAfter])
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: HttpRequestTimeoutException) {
            Exchange.Failed(ApiFailure.Timeout)
        } catch (_: ConnectTimeoutException) {
            Exchange.Failed(ApiFailure.Timeout)
        } catch (_: SocketTimeoutException) {
            Exchange.Failed(ApiFailure.Timeout)
        } catch (transport: Exception) {
            // A transport failure observed while the caller is being cancelled is the cancellation.
            currentCoroutineContext().ensureActive()
            // `API-ERR-001`/`API-ERR-003`: the cause is kept; telling offline from TLS is `TASK-038`'s.
            Exchange.Failed(ApiFailure.Unknown(transport))
        }

    /** Maps a non-`404` response: its status family, then its body, then the domain mapping. */
    private suspend fun <D, T> Exchange.Answered.read(
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
                success(map(dto))
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
            TOO_MANY_REQUESTS -> ApiFailure.RateLimited(retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 })
            in 400..499 -> ApiFailure.InvalidRequest("http-$status")
            in 500..599 -> ApiFailure.Server(status)
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
    }
}
