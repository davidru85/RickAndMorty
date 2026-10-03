package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * The behavioural double of `IC-011` (`DEC-072`, `DEC-090`), for the repository and pager tests
 * that sit above the remote seam (`TASK-038`, `TASK-039`).
 *
 * It answers from [catalogue] with the outcomes the REST adapter produces for the same logical
 * request — the requested page only, `NotFound` beyond the last page or for an unknown id, no
 * enrichment, episodes reconciled by id with omissions as warnings — and an empty episode list costs
 * no request, exactly as on the real seam. A [latency] spent in virtual time lets a test cancel a
 * caller mid-call; queued failures come back as values; [calls] records every remote call in order.
 */
public class FakeRemoteSource(
    private val catalogue: FakeCatalogue,
    private val latency: Duration = Duration.ZERO,
) : CharacterRemoteDataSource {
    /** One remote call the double received. */
    public sealed interface Call {
        public data class Page(
            public val filter: CharacterFilter,
            public val page: Int,
        ) : Call

        public data class Details(
            public val id: CharacterId,
        ) : Call

        public data class Episodes(
            public val ids: List<EpisodeId>,
        ) : Call
    }

    private val recorded = mutableListOf<Call>()
    private val queuedFailures = mutableListOf<Queued>()

    /** Every remote call so far, in order, including calls that were later cancelled. */
    public val calls: List<Call> get() = recorded.toList()

    /** How many remote calls were cancelled while they waited out [latency]. */
    public var cancellations: Int = 0
        private set

    /** Which remote call a queued failure is waiting for. */
    public enum class Kind { Page, Details, Episodes }

    /** One queued failure: what to answer, and which kind of call consumes it. */
    private data class Queued(
        val failure: ApiFailure,
        val kind: Kind?,
    )

    /**
     * Makes the next remote call of [kind] — or of any kind, when [kind] is `null` — return [failure]
     * as a value. Failures queue in order.
     *
     * The [kind] filter exists because a repository test often needs one call of a sequence to
     * succeed and a later one to fail: an enriched detail whose episode batch fails is a partial
     * value, and which call failed decides whether the value is partial at all.
     */
    public fun failNext(
        failure: ApiFailure,
        kind: Kind? = null,
    ) {
        queuedFailures.addLast(Queued(failure, kind))
    }

    /** The failure reserved for [kind], consumed in queue order, or `null` when none waits. */
    private fun nextFailure(kind: Kind): ApiFailure? {
        val index = queuedFailures.indexOfFirst { it.kind == null || it.kind == kind }
        if (index < 0) return null
        return queuedFailures.removeAt(index).failure
    }

    private suspend fun respondAfterLatency() {
        try {
            delay(latency)
        } catch (cancellation: CancellationException) {
            cancellations++
            throw cancellation
        }
    }

    override suspend fun characterPage(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage> {
        recorded += Call.Page(filter, page)
        respondAfterLatency()
        nextFailure(Kind.Page)?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.page(filter, page)
    }

    override suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails> {
        recorded += Call.Details(id)
        respondAfterLatency()
        nextFailure(Kind.Details)?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.details(id)?.let { DataResult.Success(it, DataSource.NETWORK, isStale = false) }
            ?: DataResult.Failure(ApiFailure.NotFound(RemoteResources.CHARACTER, id.value), DataSource.NETWORK)
    }

    override suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        if (ids.isEmpty()) return DataResult.Success(emptyList(), DataSource.NETWORK, isStale = false)
        recorded += Call.Episodes(ids)
        respondAfterLatency()
        nextFailure(Kind.Episodes)?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        val (summaries, warnings) = catalogue.episodes(ids)
        return DataResult.Success(summaries, DataSource.NETWORK, isStale = false, warnings = warnings)
    }
}
