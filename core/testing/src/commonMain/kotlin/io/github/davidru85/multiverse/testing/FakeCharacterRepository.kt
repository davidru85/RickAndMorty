package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.remote.RemoteResources
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * The behavioural double of `IC-007` (`DEC-072`, `DEC-090`), for consumers of the repository seam.
 *
 * It serves [catalogue] with the seam's semantics — the requested page only, an empty success for a
 * filter that matches nothing, `NotFound` for an unknown id, episode summaries only when enrichment is
 * requested — and adds what a consumer's test needs to control: a [latency] spent in virtual time, so
 * a caller can be cancelled mid-call; queued failures returned as values; and the [calls] history,
 * including each page-load policy. A cancelled call propagates its `CancellationException`, is
 * counted in [cancellations], and never becomes a failure.
 *
 * With a [cached] catalogue it is freshness-aware (`DEC-086`, `TASK-039`): a `PageLoadPolicy.Default`
 * page load that [cached] can serve is answered from it as a `MEMORY_CACHE` success, stale when
 * [cachedIsStale] says so, and only `ForceNetwork` — or a page [cached] cannot serve — reaches
 * [catalogue], the network. That is a stand-in for the cache's serving decision, not for its freshness
 * rules, which the production cache owns (`TASK-020`). Queued failures stand for the network, so a
 * cache hit never consumes one.
 *
 * It is not evidence for the real repository: the real coalescing, retry and cache behaviour is
 * `TASK-038`'s and `TASK-020`'s and is tested against the real implementation.
 */
public class FakeCharacterRepository(
    private var catalogue: FakeCatalogue,
    private val latency: Duration = Duration.ZERO,
    private val cached: FakeCatalogue? = null,
    private val cachedIsStale: Boolean = false,
) : CharacterRepository {
    /** One call the double received. */
    public sealed interface Call {
        public data class Page(
            public val filter: CharacterFilter,
            public val page: Int,
            public val policy: PageLoadPolicy,
        ) : Call

        public data class Details(
            public val id: CharacterId,
            public val enrich: Boolean,
        ) : Call
    }

    private val recorded = mutableListOf<Call>()
    private val queuedFailures = ArrayDeque<ApiFailure>()

    /** Every call received so far, in order, including calls that were later cancelled. */
    public val calls: List<Call> get() = recorded.toList()

    /** How many calls were cancelled while they waited out [latency]. */
    public var cancellations: Int = 0
        private set

    /**
     * Replaces what the network serves from the next call on, as the server's data changes between
     * requests — a character inserted ahead of a loaded page, a catalogue that shrank. [cached] is
     * unaffected.
     */
    public fun serve(network: FakeCatalogue) {
        catalogue = network
    }

    /** Makes the next call, of either kind, return [failure] as a value. Failures queue in order. */
    public fun failNext(failure: ApiFailure) {
        queuedFailures.addLast(failure)
    }

    override suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy,
    ): DataResult<CharacterPage> {
        recorded += Call.Page(filter, page, policy)
        respondAfterLatency()
        if (policy == PageLoadPolicy.Default) cachedPage(filter, page)?.let { return it }
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.page(filter, page)
    }

    private fun cachedPage(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage>? {
        val hit = cached?.page(filter, page) as? DataResult.Success ?: return null
        return DataResult.Success(hit.value, DataSource.MEMORY_CACHE, isStale = cachedIsStale, warnings = hit.warnings)
    }

    private suspend fun respondAfterLatency() {
        try {
            delay(latency)
        } catch (cancellation: CancellationException) {
            cancellations++
            throw cancellation
        }
    }

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        recorded += Call.Details(id, enrich)
        respondAfterLatency()
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        val character =
            catalogue.details(id)
                ?: return DataResult.Failure(ApiFailure.NotFound(RemoteResources.CHARACTER, id.value), DataSource.NETWORK)
        if (!enrich) return DataResult.Success(character, DataSource.NETWORK, isStale = false)
        val (summaries, warnings) = catalogue.episodes(character.episodeIds)
        return DataResult.Success(
            character.copy(episodeSummaries = summaries),
            DataSource.NETWORK,
            isStale = false,
            warnings = warnings,
        )
    }
}
