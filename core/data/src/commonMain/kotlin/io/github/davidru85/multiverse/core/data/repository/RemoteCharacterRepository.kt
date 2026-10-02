package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random

/**
 * The repository of `IC-007` over a remote data source of `IC-011` (`TASK-038`).
 *
 * Every remote call goes through one [RetryPolicy] — the only retry layer of the data path — so a
 * transient failure costs at most three attempts and a non-retryable one exactly one (`DEC-084`).
 * `details(id, enrich = true)` adds one bounded episode call; a failed enrichment keeps the detail,
 * with `episodeSummaries == null` and an `enrichment-failed` warning so no cache ever stores the
 * partial value (`ERROR_FLOW.md` §7).
 *
 * There is no response cache yet (`TASK-020`), so `PageLoadPolicy.Default` and `ForceNetwork` both
 * reach the network here; the policy is nevertheless part of every request's identity (`DEC-086`).
 * [scope] owns the shared work of coalesced requests; closing it cancels that work.
 */
public class RemoteCharacterRepository(
    private val remote: CharacterRemoteDataSource,
    scope: CoroutineScope,
    random: Random,
) : CharacterRepository {
    private val retry = RetryPolicy(random)

    override suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy,
    ): DataResult<CharacterPage> = retry.run { remote.characterPage(filter, page) }

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val detail = retry.run { remote.characterDetails(id) }
        if (!enrich || detail !is DataResult.Success) return detail
        return when (val episodes = retry.run { remote.episodes(detail.value.episodeIds) }) {
            is DataResult.Success ->
                detail.copy(
                    value = detail.value.copy(episodeSummaries = episodes.value),
                    warnings = detail.warnings + episodes.warnings,
                )
            is DataResult.Failure ->
                detail.copy(warnings = detail.warnings + ApiWarning(RemoteWarnings.ENRICHMENT_FAILED))
        }
    }
}
