package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Discovery state holder's shared half (`IC-018`, `DESIGN.md` §4.1): every rule a platform holder
 * must not re-derive lives here, so the Android `DiscoveryViewModel` and the iOS `ObservableObject`
 * are thin adapters over one implementation.
 *
 * It owns the **300 ms debounce** (`API_SPECS.md` §8), the request-identity check the spec calls
 * `distinctUntilChanged`, the cancellation of a superseded request so no stale state is emitted, the
 * reset to page 1 on a query or status change, and the mapping from the observed `PagerState`
 * (`IC-014`) to [CharacterListUiState].
 *
 * Two facts feed the screen, and they are deliberately separate:
 *
 * - the pager's `PagerState` carries everything a load can prove, [PagerState.filter] included — the
 *   filter the pager holds the items for — so "a state whose filter has changed but whose items still
 *   belong to the previous filter" cannot be expressed, let alone emitted;
 * - [loading] carries the one fact a `PagerState` cannot: whether a load has **completed** for the
 *   current filter. It is the session flag `IC-018`'s precedence reads, and it is this class's only
 *   mutable state.
 *
 * A blank query flows into [CharacterFilter] unchanged, because the filter retains the raw user text
 * and the adapter trims and drops it (`IC-010`, `AC-REQ-FUNC-003-3`); nothing here inspects the query.
 */
public class DiscoveryReducer(
    private val pager: CharacterPager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val formatters: PresentationFormatters,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
) {
    private val intents =
        MutableSharedFlow<CharacterListIntent>(
            // An intent sent before [start] runs is buffered rather than dropped; the buffer is
            // bounded, so a storm cannot grow it without limit.
            replay = 0,
            extraBufferCapacity = INTENT_BUFFER,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    /** `true` while no load has completed for the current filter: the `Loading` arm of the precedence. */
    private val loading = MutableStateFlow(true)

    /** `true` while a user refresh is in flight (`DEC-124`). */
    private val refreshing = MutableStateFlow(false)

    /** Which refresh is the newest, so only that one's end clears [refreshing]. */
    private val refreshGeneration = MutableStateFlow(0L)

    /**
     * The pager's last published state. `IC-014` exposes `Flow`, not `StateFlow` (`DEC-091` keeps the
     * contract free of presentation types), so the last observed value is kept here for the places
     * that need it synchronously: an intent arriving before any filter choice, and `Retry`, whose
     * meaning depends on whether a failure or stale content is on screen.
     */
    private val observed = MutableStateFlow<PagerState?>(null)

    /** The filter the user has chosen; only a settled query or an immediate status change replaces it. */
    private val desired = MutableStateFlow<CharacterFilter?>(null)

    /** The filter the pager was last asked for, so a settled query that changes nothing is no request. */
    private var requestedFilter: CharacterFilter? = null

    /** The debounce window's job; a newer query cancels it, so the superseded one never becomes a request. */
    private var pendingQuery: Job? = null

    /**
     * What the surface renders, always up to date: the `IC-018` state over the observed `PagerState`
     * and the session flag. It starts at the [CharacterListUiState] default — `Loading`, because no
     * load has completed before [start] runs.
     */
    public val state: StateFlow<CharacterListUiState> =
        combine(
            pager.state.onEach { observed.value = it },
            loading,
            refreshing,
            ::render,
        ).stateIn(scope, SharingStarted.Eagerly, CharacterListUiState())

    /** Dispatches [intent]; a view never reaches a repository or a use case directly. */
    public fun onIntent(intent: CharacterListIntent) {
        intents.tryEmit(intent)
    }

    /**
     * Loads page 1 of [initialFilter] — the screen's first request — and then serves intents until the
     * scope is cancelled. A platform holder invokes it once, from its own initialisation.
     */
    public fun start(initialFilter: CharacterFilter = CharacterFilter()): Job =
        scope.launch(dispatcher) {
            desired.value = initialFilter
            // The first load is started, not awaited: an intent that arrives while it runs must be able
            // to supersede it, which it cannot do behind a suspended intent loop.
            request(initialFilter)
            intents.collect(::handle)
        }

    private fun handle(intent: CharacterListIntent) {
        when (intent) {
            is CharacterListIntent.QueryChanged -> {
                // The active status is preserved because only the query dimension is replaced
                // (`AC-REQ-FUNC-004-1`). The settled query waits; a newer one cancels the wait, so only
                // the last of a burst is asked for (`AC-REQ-FUNC-003-1`). The load already in flight is
                // left alone until then, because blanking the list on every keystroke is a worse
                // screen; when the settled query replaces the filter, `IC-014` cancels the superseded
                // load, which is the cancellation `AC-REQ-FUNC-003-2` asks for.
                val chosen = activeFilter().copy(query = intent.query)
                desired.value = chosen
                pendingQuery?.cancel()
                pendingQuery =
                    scope.launch(dispatcher) {
                        delay(debounce)
                        request(chosen)
                    }
            }

            is CharacterListIntent.StatusSelected -> {
                // A status change is immediate and supersedes a query that has not settled; it
                // preserves the active query because `desired` already carries it, and it resets
                // paging to page 1 because `setFilter` does.
                pendingQuery?.cancel()
                val chosen = activeFilter().copy(status = intent.status)
                desired.value = chosen
                request(chosen)
            }

            // A load is started, never awaited, here: the loop must stay free to take the next intent,
            // so a query or status change made during a load supersedes it at once through `IC-014`'s
            // generation guard instead of waiting behind it (`DEC-124`).
            CharacterListIntent.LoadNextPage -> scope.launch(dispatcher) { pager.next() }
            CharacterListIntent.Refresh -> refresh()
            // The failed load is what a Retry re-attempts; with none, stale content is what it
            // revalidates — the stale banner's action (`ERROR_FLOW.md` §9) — and otherwise there is
            // nothing to recover (`DEC-124`).
            CharacterListIntent.Retry -> {
                val current = observed.value
                when {
                    current?.failure != null -> retry()
                    current?.isStale == true -> refresh()
                    else -> Unit
                }
            }
        }
    }

    /** Revalidates page 1 over the network; [refreshing] holds until the newest refresh ends. */
    private fun refresh() {
        val generation = refreshGeneration.updateAndGet { it + 1 }
        refreshing.value = true
        scope.launch(dispatcher) {
            try {
                pager.refresh()
            } finally {
                if (refreshGeneration.value == generation) refreshing.value = false
            }
        }
    }

    /**
     * Re-attempts the failed load. The session flag reads `Loading` while it runs, and only a retry that
     * ends while its filter is still the requested one clears it, so a filter change made meanwhile
     * keeps its own `Loading` until its own load ends.
     */
    private fun retry() {
        val filter = requestedFilter
        loading.value = true
        scope.launch(dispatcher) {
            pager.retry()
            if (requestedFilter == filter) loading.value = false
        }
    }

    /**
     * Asks the pager for page 1 of [filter] unless it already holds the same request, and keeps the
     * session flag accurate across the load: a filter the pager has not loaded for is `Loading` until
     * its first load ends, which is precedence arm (1).
     *
     * The request runs in its own child of [scope] rather than in the caller's frame, so the intent
     * loop stays free to cancel it: a superseded request therefore emits nothing, which is the
     * cancellation `AC-REQ-FUNC-003-2` asks for. The pager's own generation guard is the second line
     * of defence, because a load already in the repository cannot always be interrupted.
     */
    private fun request(filter: CharacterFilter) {
        if (filter == requestedFilter) return
        requestedFilter = filter
        loading.value = true
        scope.launch(dispatcher) {
            pager.setFilter(filter)
            // Only a request that ends while it is still the current one clears the flag, so a
            // superseded request cannot report a completion that belongs to the one that replaced it.
            if (requestedFilter == filter) loading.value = false
        }
    }

    /** The filter the user has settled on, or the pager's own before any choice was recorded. */
    private fun activeFilter(): CharacterFilter = desired.value ?: observed.value?.filter ?: CharacterFilter()

    /**
     * `IC-018`'s precedence over one [PagerState] and the session flag, evaluated in order:
     *
     * (1) `Loading` while no load has completed for the current filter; (2) `Error` when the newest
     * attempt failed and nothing is displayable; (3) `Empty` when a load completed with no failure and
     * zero items; (4) `Content` otherwise. `isAppending` is true only with `Content`, and `isStale`
     * implies `Content`, so both are forced off in every other arm. `contentFailure` is the pager's
     * failure beside displayable content, withheld while a load re-attempts it (`DEC-124`).
     */
    public fun render(
        state: PagerState,
        isLoading: Boolean,
        isRefreshing: Boolean = false,
    ): CharacterListUiState {
        val displayable = state.items.isNotEmpty()
        val loadState =
            when {
                !displayable && isLoading -> LoadState.Loading
                !displayable && state.failure != null -> LoadState.Error(requireNotNull(state.failure))
                !displayable -> LoadState.Empty
                else -> LoadState.Content
            }
        val content = loadState == LoadState.Content
        return CharacterListUiState(
            filter = state.filter,
            items = state.items.map { CharacterCardUi.from(it, formatters) },
            totalCount = state.totalCount,
            loadState = loadState,
            isAppending = content && state.isAppending,
            isStale = content && state.isStale,
            contentFailure = state.failure?.takeIf { content && !state.isAppending && !isRefreshing },
            isRefreshing = isRefreshing,
        )
    }

    public companion object {
        /** The settled-query window of `API_SPECS.md` §8 and `REQ-FUNC-003`. */
        public val DEFAULT_DEBOUNCE: Duration = 300.milliseconds
        private const val INTENT_BUFFER = 64
    }
}
