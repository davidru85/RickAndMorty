package io.github.davidru85.multiverse.testing

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
import kotlin.time.Duration

/**
 * The behavioural double of `IC-007` (`DEC-072`, `DEC-090`), for consumers of the repository seam.
 *
 * It serves [catalogue] with the seam's semantics — the requested page only, an empty success for a
 * filter that matches nothing, `NotFound` for an unknown id, episode summaries only when enrichment is
 * requested — and adds what a consumer's test needs to control: a [latency] spent in virtual time, so
 * a caller can be cancelled mid-call; queued failures returned as values; and the [calls] history,
 * including each page-load policy. A cancelled call propagates its `CancellationException` and never
 * becomes a failure.
 *
 * It is not evidence for the real repository: the real coalescing, retry and cache behaviour is
 * `TASK-038`'s and is tested against the real implementation.
 */
public class FakeCharacterRepository(
    private val catalogue: FakeCatalogue,
    private val latency: Duration = Duration.ZERO,
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
        delay(latency)
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        return catalogue.page(filter, page)
    }

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        recorded += Call.Details(id, enrich)
        delay(latency)
        queuedFailures.removeFirstOrNull()?.let { return DataResult.Failure(it, DataSource.NETWORK) }
        val character =
            catalogue.details(id)
                ?: return DataResult.Failure(ApiFailure.NotFound(FakeCatalogue.CHARACTER_RESOURCE, id.value), DataSource.NETWORK)
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
