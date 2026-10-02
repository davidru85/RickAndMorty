package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
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
    private val queuedFailures = ArrayDeque<ApiFailure>()

    /** Every remote call so far, in order, including calls that were later cancelled. */
    public val calls: List<Call> get() = recorded.toList()

    /** Makes the next remote call, of any kind, return [failure] as a value. Failures queue in order. */
    public fun failNext(failure: ApiFailure) {
        queuedFailures.addLast(failure)
    }

    override suspend fun characterPage(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage> {
        recorded += Call.Page(filter, page)
        delay(latency)
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.page(filter, page)
    }

    override suspend fun characterDetails(id: CharacterId): DataResult<CharacterDetails> {
        recorded += Call.Details(id)
        delay(latency)
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.details(id)?.let { DataResult.Success(it, DataSource.NETWORK, isStale = false) }
            ?: DataResult.Failure(ApiFailure.NotFound(FakeCatalogue.CHARACTER_RESOURCE, id.value), DataSource.NETWORK)
    }

    override suspend fun episodes(ids: List<EpisodeId>): DataResult<List<EpisodeSummary>> {
        if (ids.isEmpty()) return DataResult.Success(emptyList(), DataSource.NETWORK, isStale = false)
        recorded += Call.Episodes(ids)
        delay(latency)
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        val (summaries, warnings) = catalogue.episodes(ids)
        return DataResult.Success(summaries, DataSource.NETWORK, isStale = false, warnings = warnings)
    }
}
