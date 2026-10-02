package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.logging.filterNames
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
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
 * Concurrent identical requests share one execution, retries included (`REQ-REL-002`): the identity
 * is the protocol of [remote], the operation, the page or id, the normalized filter, the enrichment
 * mode and the page-load policy, so `Default` work never satisfies a `ForceNetwork` call (`DEC-086`).
 * The identity is never logged: a joined duplicate is reported to [logger] as `LOG-012` with the
 * filter *names* only, and retries as `LOG-013`. [scope] owns the shared work; closing it cancels that
 * work.
 *
 * There is no response cache yet (`TASK-020`), so `PageLoadPolicy.Default` and `ForceNetwork` both
 * reach the network here; the policy is already part of every request's identity.
 */
public class RemoteCharacterRepository(
    private val remote: CharacterRemoteDataSource,
    scope: CoroutineScope,
    random: Random,
    private val logger: AppLogger,
    private val protocol: RemoteProtocol = RemoteProtocol.Rest,
) : CharacterRepository {
    private val retry = RetryPolicy(random, logger)
    private val pages =
        SingleFlight<PageIdentity, DataResult<CharacterPage>>(scope) { identity, correlationId ->
            logger.log(LogLevel.DEBUG) {
                LogEvent.RequestDeduplicated(
                    LogOperation.CHARACTER_LIST,
                    identity.page,
                    filterNames(identity.query, identity.status),
                    correlationId,
                )
            }
        }
    private val details =
        SingleFlight<DetailsIdentity, DataResult<CharacterDetails>>(scope) { _, correlationId ->
            logger.log(LogLevel.DEBUG) { LogEvent.RequestDeduplicated(LogOperation.CHARACTER_DETAIL, null, emptySet(), correlationId) }
        }

    /** What makes two page loads the same request. */
    private data class PageIdentity(
        val protocol: RemoteProtocol,
        val page: Int,
        val query: String,
        val status: StatusFilter,
        val policy: PageLoadPolicy,
    )

    /** What makes two detail loads the same request. */
    private data class DetailsIdentity(
        val protocol: RemoteProtocol,
        val id: CharacterId,
        val enrich: Boolean,
    )

    override suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy,
    ): DataResult<CharacterPage> {
        // The adapter trims the query and sends nothing for a blank one, so the identity does too.
        val identity = PageIdentity(protocol, page, filter.query.trim(), filter.status, policy)
        return pages.run(identity) { retry.run(LogOperation.CHARACTER_LIST) { remote.characterPage(filter, page) } }
    }

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> = details.run(DetailsIdentity(protocol, id, enrich)) { load(id, enrich) }

    private suspend fun load(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val detail = retry.run(LogOperation.CHARACTER_DETAIL) { remote.characterDetails(id) }
        if (!enrich || detail !is DataResult.Success) return detail
        return when (val episodes = retry.run(LogOperation.EPISODE_BATCH) { remote.episodes(detail.value.episodeIds) }) {
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
