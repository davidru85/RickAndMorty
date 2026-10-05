package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.cache.CacheFreshness
import io.github.davidru85.multiverse.core.data.cache.CacheHit
import io.github.davidru85.multiverse.core.data.cache.CacheKey
import io.github.davidru85.multiverse.core.data.cache.CacheKeyBuilder
import io.github.davidru85.multiverse.core.data.cache.CachedPayloadMapper
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.filterNames
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.RemoteProtocolSource
import io.github.davidru85.multiverse.core.data.remote.RemoteWarnings
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOperation
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
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
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The repository of `IC-007` over a remote data source of `IC-011` (`TASK-038`), with the
 * application-level response cache of `IC-012` (`TASK-020`, `DEC-012`, `DEC-018`).
 *
 * Every remote call goes through one [RetryPolicy] — the only retry layer of the data path — so a
 * transient failure costs at most three attempts and a non-retryable one exactly one (`DEC-084`).
 * `details(id, enrich = true)` adds one bounded episode call; a failed enrichment keeps the detail,
 * with `episodeSummaries == null` and an `enrichment-failed` warning so no cache ever stores the
 * partial value (`ERROR_FLOW.md` §7).
 *
 * **Cache read policy (`API_SPECS.md` §7.3).** A read classifies the entry for its request identity:
 *
 * - **fresh** — answers the request with no network call (`AC-REQ-FUNC-020-1`); a manual refresh still
 *   reaches the network;
 * - **stale** — served at once with `isStale = true`, and **revalidated in the background** so the
 *   first render never blocks (`DEC-012`);
 * - **offline fallback** — the network is tried first and the entry answers only when it fails, with
 *   `isStale = true` (`AC-REQ-FUNC-020-2`);
 * - **expired** — evicted; the network answers.
 *
 * `PageLoadPolicy.ForceNetwork` — the manual refresh — bypasses every band and always reaches the
 * network, which is what keeps `IC-014.refresh()` meaningful against the production cache rather than
 * only against a freshness-aware double (`DEC-086`, `AC-REQ-FUNC-012-1`).
 *
 * **Write admission (`AC-REQ-FUNC-020-3`).** Only a complete, decoded success carrying no warnings is
 * written. A failure, an empty body and a partial response take the refusal path instead, so the
 * cacheable filtered `404` cannot be stored (`RISK-005`).
 *
 * Concurrent identical requests share one execution, retries included (`REQ-REL-002`): the identity is
 * the protocol preference, the operation, the page or id, the normalized filter, the enrichment mode
 * and the page-load policy, so `Default` work never satisfies a `ForceNetwork` call (`DEC-086`). The
 * identity is never logged: a joined duplicate is reported to [logger] as `LOG-012` with the filter
 * *names* only, and retries as `LOG-013`. [scope] owns the shared work; closing it cancels that work.
 */
public class RemoteCharacterRepository(
    private val rest: CharacterRemoteDataSource,
    private val graphQl: CharacterRemoteDataSource,
    private val scope: CoroutineScope,
    random: Random,
    private val logger: AppLogger,
    private val cache: ResponseCache,
    private val protocols: RemoteProtocolSource = RemoteProtocolSource.Rest,
) : CharacterRepository {
    /**
     * The REST-only shape of `TASK-038`, kept so the suites that predate the switch keep compiling.
     * Both adapters are then the same source, and the protocol stays REST (`AC-REQ-FUNC-034-1`).
     */
    public constructor(
        remote: CharacterRemoteDataSource,
        scope: CoroutineScope,
        random: Random,
        logger: AppLogger,
        cache: ResponseCache,
        protocol: RemoteProtocol = RemoteProtocol.Rest,
    ) : this(remote, remote, scope, random, logger, cache, RemoteProtocolSource.Rest)

    /**
     * The adapter a request must use. It is resolved once per request, from the protocol that request's
     * identity was built with, so a change of preference cannot move a request mid-flight onto another
     * adapter (`AC-REQ-FUNC-034-2`).
     */
    private fun adapterFor(protocol: RemoteProtocol): CharacterRemoteDataSource =
        when (protocol) {
            RemoteProtocol.Rest -> rest
            RemoteProtocol.GraphQl -> graphQl
        }

    /** The protocol this request is served under; part of its identity and of its cache key. */
    private suspend fun protocol(): RemoteProtocol = protocols.current()

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

    /** One background revalidation per key, so a burst of reads starts one request, not a fan-out. */
    private val revalidating = mutableSetOf<CacheKey>()
    private val revalidationLock = Mutex()

    /**
     * The page revalidations in flight, by key. A forced load of the same page joins one rather than
     * sending a second request for the same entry (`DEC-130`, `REQ-REL-002`). Guarded by
     * [revalidationLock].
     */
    private val pageRevalidations = mutableMapOf<CacheKey, Deferred<DataResult<CharacterPage>>>()

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
        // The active protocol is read once per request and is part of the identity, so a switch can
        // never be satisfied by work already in flight for the other protocol (DEC-086, ADR-0011).
        val protocol = protocol()
        val identity = PageIdentity(protocol, page, filter.query.trim(), filter.status, policy)
        return pages.run(identity) { loadPage(protocol, filter, page, policy) }
    }

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val protocol = protocol()
        return details.run(DetailsIdentity(protocol, id, enrich)) { loadDetails(protocol, id, enrich) }
    }

    /** One page request through the cache's read policy, or straight to the network under `ForceNetwork`. */
    private suspend fun loadPage(
        protocol: RemoteProtocol,
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy,
    ): DataResult<CharacterPage> {
        val key = CacheKeyBuilder.page(protocol, filter, page)
        if (policy == PageLoadPolicy.ForceNetwork) {
            // A revalidation of this entry is already on the network: its answer is the one a forced
            // load would get, so the two cost one request (`DEC-130`).
            revalidationLock.withLock { pageRevalidations[key] }?.let { return it.await() }
        }
        val hit = cache.read(key, LogOperation.CHARACTER_LIST, page)
        val cached = hit?.page
        if (cached != null && policy == PageLoadPolicy.Default) {
            when (hit.freshness) {
                CacheFreshness.FRESH ->
                    return DataResult.Success(cached, DataSource.DISK_CACHE, isStale = false)
                CacheFreshness.STALE -> {
                    revalidatePage(protocol, key, filter, page)
                    return DataResult.Success(cached, DataSource.DISK_CACHE, isStale = true)
                }
                CacheFreshness.OFFLINE_FALLBACK -> Unit // Keep it; the network answers first.
                CacheFreshness.EXPIRED -> cache.evict(key)
            }
        }
        cache.miss(LogOperation.CHARACTER_LIST, page)
        val outcome = retry.run(LogOperation.CHARACTER_LIST) { adapterFor(protocol).characterPage(filter, page) }
        return when (outcome) {
            is DataResult.Success -> {
                admitPage(outcome, page)?.let { cache.store(key, it) }
                outcome
            }
            is DataResult.Failure -> {
                cache.refuse(LogOutcome.FAILURE, outcome.failure)
                fallback(hit, outcome, LogOperation.CHARACTER_LIST) { it.page }
            }
        }
    }

    private suspend fun loadDetails(
        protocol: RemoteProtocol,
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val key = CacheKeyBuilder.details(protocol, id, enrich)
        val hit = cache.read(key, LogOperation.CHARACTER_DETAIL, null)
        val cached = hit?.details
        if (cached != null) {
            when (hit.freshness) {
                CacheFreshness.FRESH ->
                    return DataResult.Success(cached, DataSource.DISK_CACHE, isStale = false)
                CacheFreshness.STALE -> {
                    revalidateDetails(protocol, key, id, enrich)
                    return DataResult.Success(cached, DataSource.DISK_CACHE, isStale = true)
                }
                CacheFreshness.OFFLINE_FALLBACK -> Unit
                CacheFreshness.EXPIRED -> cache.evict(key)
            }
        }
        cache.miss(LogOperation.CHARACTER_DETAIL, null)
        return when (val fetched = fetchDetails(protocol, key, id, enrich)) {
            is DataResult.Failure -> fallback(hit, fetched, LogOperation.CHARACTER_DETAIL) { it.details }
            is DataResult.Success -> fetched
        }
    }

    /**
     * One detail through the network — with its episodes when [enrich] asks for them — stored only
     * when complete: a failed enrichment returns the detail with a warning and writes nothing
     * (`ERROR_FLOW.md` §7). The first load and a background revalidation share it, so a revalidated
     * enriched entry is as whole as the one it replaces (`DEC-130`).
     */
    private suspend fun fetchDetails(
        protocol: RemoteProtocol,
        key: CacheKey,
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val detail = retry.run(LogOperation.CHARACTER_DETAIL) { adapterFor(protocol).characterDetails(id) }
        if (detail !is DataResult.Success) {
            if (detail is DataResult.Failure) cache.refuse(LogOutcome.FAILURE, detail.failure)
            return detail
        }
        if (!enrich) {
            admitDetails(detail)?.let { cache.store(key, it) }
            return detail
        }
        return when (val episodes = retry.run(LogOperation.EPISODE_BATCH) { adapterFor(protocol).episodes(detail.value.episodeIds) }) {
            is DataResult.Success -> {
                val enriched =
                    detail.copy(
                        value = detail.value.copy(episodeSummaries = episodes.value),
                        warnings = detail.warnings + episodes.warnings,
                    )
                if (enriched is DataResult.Success) {
                    val record = admitDetails(enriched)
                    if (record == null) {
                        cache.refuse(LogOutcome.SUCCESS, null)
                    } else {
                        cache.store(key, record)
                    }
                }
                enriched
            }
            is DataResult.Failure -> {
                cache.refuse(LogOutcome.SUCCESS, null)
                detail.copy(warnings = detail.warnings + ApiWarning(RemoteWarnings.ENRICHMENT_FAILED))
            }
        }
    }

    /**
     * Revalidates [key] behind the caller, so a stale entry renders at once and the next read is fresh
     * (`DEC-012`). One request per key at a time: a burst of reads starts one, not a fan-out. The work
     * runs in [scope], so closing it cancels the revalidation with every other shared call.
     */
    private suspend fun revalidatePage(
        protocol: RemoteProtocol,
        key: CacheKey,
        filter: CharacterFilter,
        page: Int,
    ) {
        if (!beginRevalidation(key)) return
        // Registered before it starts, so a forced load can never join a revalidation that has already
        // ended, and removed when it ends whatever its outcome.
        val revalidation =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    val outcome = retry.run(LogOperation.CHARACTER_LIST) { adapterFor(protocol).characterPage(filter, page) }
                    if (outcome is DataResult.Success) {
                        admitPage(outcome, page)?.let { cache.store(key, it) }
                    } else if (outcome is DataResult.Failure) {
                        // A failed revalidation is silent: the caller already has content, and the cache
                        // keeps the entry it served (ERROR_FLOW.md §8).
                        cache.refuse(LogOutcome.FAILURE, outcome.failure)
                    }
                    outcome
                } finally {
                    withContext(NonCancellable) {
                        revalidationLock.withLock { pageRevalidations -= key }
                        endRevalidation(key)
                    }
                }
            }
        revalidationLock.withLock { pageRevalidations[key] = revalidation }
        revalidation.start()
    }

    private suspend fun revalidateDetails(
        protocol: RemoteProtocol,
        key: CacheKey,
        id: CharacterId,
        enrich: Boolean,
    ) {
        if (!beginRevalidation(key)) return
        scope.launch {
            try {
                // The same fetch as a first load, enrichment included, so the stored result is complete
                // or nothing is stored; no request's answer is discarded (`DEC-130`).
                fetchDetails(protocol, key, id, enrich)
            } finally {
                withContext(NonCancellable) { endRevalidation(key) }
            }
        }
    }

    private suspend fun beginRevalidation(key: CacheKey): Boolean =
        revalidationLock.withLock {
            if (key in revalidating) {
                false
            } else {
                revalidating += key
                true
            }
        }

    private suspend fun endRevalidation(key: CacheKey) {
        revalidationLock.withLock { revalidating -= key }
    }

    /**
     * The record of a page outcome the write guard admits, or `null` when it must be refused.
     *
     * A success with warnings is partial (`API-ERR-011`) and is never stored. An empty success for the
     * first page of a filtered request is `REQ-FUNC-010`'s empty state — a normal outcome — and is
     * stored under the same key (`adr/0005-caching-strategy.md`).
     */
    private fun admitPage(
        outcome: DataResult.Success<CharacterPage>,
        page: Int,
    ) = if (outcome.warnings.isEmpty() && outcome.value.page == page) {
        CachedPayloadMapper.page(outcome.value)
    } else {
        null
    }

    /**
     * The record of a detail outcome the write guard admits, or `null` when it must be refused.
     *
     * A failed enrichment produces a partial value (`ERROR_FLOW.md` §7) and a partial GraphQL response
     * produces warnings (`API-ERR-011`); neither is ever written, because a cached partial detail would
     * permanently hide the rows it omits (`AC-REQ-FUNC-020-3`).
     */
    private fun admitDetails(outcome: DataResult.Success<CharacterDetails>) =
        if (outcome.warnings.isEmpty()) CachedPayloadMapper.details(outcome.value) else null

    /**
     * The offline fallback of a failed read: an in-window entry, or the failure itself.
     *
     * The entry is served through [ResponseCache.serveFallback], which is what emits `LOG-007`; the
     * result reports `DISK_CACHE` and `isStale = true`, so the screen can mark content it is still
     * showing (`AC-REQ-FUNC-020-2`, `ERROR_FLOW.md` §9).
     */
    private suspend fun <T> fallback(
        hit: CacheHit?,
        failure: DataResult.Failure,
        operation: LogOperation,
        select: (CacheHit) -> T?,
    ): DataResult<T> {
        val usable = hit?.takeIf { it.freshness == CacheFreshness.OFFLINE_FALLBACK } ?: return failure
        val value = select(usable) ?: return failure
        val served = cache.serveFallback(usable, operation, null, failure.failure)
        return DataResult.Success(
            select(served) ?: value,
            DataSource.DISK_CACHE,
            isStale = true,
            warnings = failure.warnings,
        )
    }
}

/** The cache key of a page load, so a test can assert the request identity without reaching inside. */
internal fun pageCacheKey(
    protocol: RemoteProtocol,
    filter: CharacterFilter,
    page: Int,
): CacheKey = CacheKeyBuilder.page(protocol, filter, page)
