package io.github.davidru85.multiverse.core.data.paging

import io.github.davidru85.multiverse.core.data.logging.CorrelationId
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogEvent
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.logging.LogOutcome
import io.github.davidru85.multiverse.core.domain.logging.log
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * The shared pager (`IC-014`, ADR-0009) over the `IC-007` repository, whose coalescing and bounded
 * retry it reuses rather than repeating (ADR-0009 rule 7).
 *
 * Every load runs in [scope], which the owner supplies and closes (`GUIDELINES.md` §2.7); the
 * suspending methods return when the load they started or joined ends. State transitions happen under
 * one lock, and each load carries the generation it was started in: `setFilter` and `refresh` start a
 * new generation, so a superseded load that still returns cannot publish. The next page comes from the
 * server's metadata only.
 *
 * A failed refresh is retried as a refresh; every other retry re-attempts the page that failed
 * (`DEC-092`). The repository gives each call a fresh attempt budget.
 *
 * Each load opens a correlation scope its request inherits, and a published load is logged through
 * [logger]: `LOG-010` with its outcome, source and duration on [timeSource], and `LOG-011` when it
 * ends pagination (`OBSERVABILITY.md` §3). A superseded load publishes nothing and logs nothing.
 */
public class RepositoryCharacterPager(
    private val repository: CharacterRepository,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
    initialFilter: CharacterFilter = CharacterFilter(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
    protocolChanges: Flow<RemoteProtocol> = emptyFlow(),
) : CharacterPager {
    private val mutableState =
        MutableStateFlow(
            PagerState(
                filter = initialFilter,
                items = emptyList(),
                totalCount = null,
                isAppending = false,
                isEndReached = false,
                isStale = false,
                failure = null,
            ),
        )
    override val state: StateFlow<PagerState> = mutableState.asStateFlow()

    private val lock = Mutex()
    private var generation = 0L
    private var nextPage: Int? = FIRST_PAGE
    private var inFlight: Job? = null
    private var failedLoad: Load? = null

    /**
     * A change of the active protocol is a change of identity (`AC-REQ-FUNC-034-2`): the load in flight
     * is cancelled, the pager resets to page 1 and reloads through the newly selected adapter, and no
     * item of the previous protocol is ever published. The reset is the one a filter change performs,
     * because it is the same kind of change (`adr/0011-runtime-remote-protocol.md`, ADR-0009 rule 4).
     *
     * The collector runs in [scope], so it ends with the owner of the pager like every load it starts.
     * A source that never changes — the default — emits nothing and costs one suspended collector.
     */
    private val protocolChanges: Job =
        scope.launch {
            protocolChanges.collect { switchProtocol() }
        }

    /** Resets to page 1 of the current filter and reloads, cancelling the superseded load. */
    private suspend fun switchProtocol() {
        val load =
            lock.withLock {
                startGeneration()
                nextPage = FIRST_PAGE
                start(Load(FIRST_PAGE, PageLoadPolicy.Default))
            }
        load.join()
    }

    /** One page request: page 1 replaces the collection, any later page appends to it. */
    private data class Load(
        val page: Int,
        val policy: PageLoadPolicy,
    ) {
        val replaces: Boolean get() = page == FIRST_PAGE
    }

    override suspend fun setFilter(filter: CharacterFilter) {
        val load =
            lock.withLock {
                startGeneration()
                nextPage = FIRST_PAGE
                mutableState.value =
                    PagerState(
                        filter = filter,
                        items = emptyList(),
                        totalCount = null,
                        isAppending = false,
                        isEndReached = false,
                        isStale = false,
                        failure = null,
                    )
                start(Load(FIRST_PAGE, PageLoadPolicy.Default))
            }
        load.join()
    }

    override suspend fun next() {
        val load =
            lock.withLock {
                inFlight?.takeIf { it.isActive }
                    ?: nextPage
                        ?.takeIf { mutableState.value.failure == null }
                        ?.let { start(Load(it, PageLoadPolicy.Default)) }
            }
        load?.join()
    }

    override suspend fun refresh() {
        val load =
            lock.withLock {
                startGeneration()
                start(Load(FIRST_PAGE, PageLoadPolicy.ForceNetwork))
            }
        load.join()
    }

    override suspend fun retry() {
        val load =
            lock.withLock {
                val failed = failedLoad?.takeIf { mutableState.value.failure != null } ?: return@withLock null
                inFlight?.takeIf { it.isActive } ?: start(failed)
            }
        load?.join()
    }

    /** Supersedes the load in flight, whose indicator ends with it. Called under [lock]. */
    private fun startGeneration() {
        inFlight?.cancel()
        generation++
        mutableState.update { it.copy(isAppending = false) }
    }

    /** Starts [load] in the owner's scope. Called under [lock]. */
    private fun start(load: Load): Job {
        val loadGeneration = generation
        val filter = mutableState.value.filter
        if (!load.replaces) mutableState.update { it.copy(isAppending = it.items.isNotEmpty()) }
        val correlation = CorrelationId.next()
        return scope
            .launch(correlation) {
                val started = timeSource.markNow()
                try {
                    val result = repository.page(filter, load.page, load.policy)
                    val durationMs = started.elapsedNow().inWholeMilliseconds
                    lock.withLock { if (loadGeneration == generation) publish(load, result, durationMs, correlation.value) }
                } catch (cancellation: CancellationException) {
                    withContext(NonCancellable) {
                        lock.withLock {
                            if (loadGeneration == generation) mutableState.update { it.copy(isAppending = false) }
                        }
                    }
                    throw cancellation
                }
            }.also { inFlight = it }
    }

    /** Applies and logs the outcome of a current-generation [load]. Called under [lock]. */
    private fun publish(
        load: Load,
        result: DataResult<CharacterPage>,
        durationMs: Long,
        correlationId: String,
    ) {
        when (result) {
            is DataResult.Success -> {
                val page = result.value
                val outcome = if (page.characters.isEmpty()) LogOutcome.EMPTY else LogOutcome.SUCCESS
                logger.log(LogLevel.DEBUG) { LogEvent.PageLoaded(load.page, outcome, durationMs, result.source, correlationId) }
                if (page.nextPage == null) logger.log(LogLevel.DEBUG) { LogEvent.PaginationExhausted(load.page) }
                failedLoad = null
                nextPage = page.nextPage
                mutableState.update { current ->
                    current.copy(
                        items = (if (load.replaces) page.characters else current.items + page.characters).distinctBy { it.id },
                        totalCount = page.totalCount ?: current.totalCount,
                        isAppending = false,
                        isEndReached = page.nextPage == null,
                        isStale = result.isStale || (!load.replaces && current.isStale),
                        failure = null,
                    )
                }
            }
            is DataResult.Failure ->
                if (!load.replaces && result.failure is ApiFailure.NotFound) {
                    // A paging 404 reached through a valid sequence is the end (`ERROR_FLOW.md` §5.2): the
                    // last page is the one before it.
                    logger.log(LogLevel.DEBUG) { LogEvent.PaginationExhausted(load.page - 1) }
                    failedLoad = null
                    nextPage = null
                    mutableState.update { it.copy(isAppending = false, isEndReached = true, failure = null) }
                } else {
                    logger.log(LogLevel.DEBUG) {
                        LogEvent.PageLoaded(load.page, LogOutcome.FAILURE, durationMs, result.source, correlationId)
                    }
                    failedLoad = load
                    mutableState.update { it.copy(isAppending = false, failure = result.failure) }
                }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
