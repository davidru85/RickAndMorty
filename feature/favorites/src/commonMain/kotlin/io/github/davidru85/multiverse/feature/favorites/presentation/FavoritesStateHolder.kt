package io.github.davidru85.multiverse.feature.favorites.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.favorites.domain.FavoriteCards
import io.github.davidru85.multiverse.feature.favorites.domain.ResolveFavoriteCards
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The shared half of the Favorites state holder (`IC-020`, `DESIGN.md` §4.1): every rule a platform
 * holder must not re-derive lives here, so the Android `FavoritesViewModel` and the iOS
 * `ObservableObject` are thin adapters over one implementation (`CONTRACTS.md` §7 R1–R5).
 *
 * It owns three things, and nothing else:
 *
 * - the **observed id set**. `ObserveFavoriteIds` is hot and never completes; the state is `Loading`
 *   exactly until its first emission, which is the one condition `IC-020` permits `Loading` in;
 * - the **resolution of that set**, through [ResolveFavoriteCards] on the cached path (`CONF-79`,
 *   `DESIGN.md` §4.5), re-run when the set changes and re-attempted on [FavoritesIntent.Retry];
 * - the **[FavoritesUiState]** those two produce, through [FavoritesReducer], so no platform computes
 *   a display value of its own.
 *
 * A new store emission supersedes the resolution in flight: each run carries the generation it started
 * in, so a superseded run that still returns cannot publish over a newer set. The resolution is cleared
 * when a run starts, so the state never shows a previous set's cards or a previous read's failure while
 * the new one is in flight. Closing [scope] cancels the observation and the resolution together
 * (`GUIDELINES.md` §2.7).
 */
public class FavoritesStateHolder(
    private val observeFavoriteIds: ObserveFavoriteIds,
    private val resolveFavoriteCards: ResolveFavoriteCards,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val formatters: PresentationFormatters = DefaultPresentationFormatters,
) {
    /** The newest store emission, or `null` until the first one: "nothing is known about the set yet". */
    private val ids = MutableStateFlow<Set<CharacterId>?>(null)

    /** The newest resolution, or `null` while none has landed for the set currently known. */
    private val resolution = MutableStateFlow<FavoriteCards?>(null)

    /** The generation of the newest resolution attempt, so a superseded run cannot publish. */
    private var generation: Int = 0

    /** The run in flight, so a new set cancels it rather than racing it. */
    private var run: Job? = null

    /** What the section renders, always up to date; it starts `Loading` for the reason `IC-020` states. */
    public val state: StateFlow<FavoritesUiState> =
        combine(ids, resolution) { known, resolved -> known to resolved }
            .map { (known, resolved) ->
                FavoritesReducer.render(
                    ids = known,
                    summaries = resolved?.summaries.orEmpty(),
                    failure = resolved?.failure,
                    formatters = formatters,
                )
            }.stateIn(scope, SharingStarted.Eagerly, FavoritesUiState())

    /**
     * Observes the stored set and resolves its first emission, until [scope] is cancelled. A platform
     * holder invokes it once, from its own initialisation.
     */
    public fun start() {
        observeFavoriteIds()
            .onEach { emitted ->
                ids.value = emitted
                resolve(emitted)
            }.launchIn(scope)
    }

    /** Dispatches [intent]; a view never reaches a repository or a use case directly. */
    public fun onIntent(intent: FavoritesIntent) {
        when (intent) {
            // Retry re-attempts the read with a fresh attempt budget: the resolution runs again for the
            // set currently known, so the repository gives it a new attempt rather than replaying an
            // outcome.
            FavoritesIntent.Retry -> ids.value?.let(::resolve)
        }
    }

    /**
     * Starts one resolution of [target] in [scope], superseding any run in flight.
     *
     * The previous run is cancelled and the generation advanced before the new one starts, so a
     * superseded resolution that still returns cannot publish over the newer set. An empty set costs
     * **no read at all** — the designed empty state shows then, and it is the one state that is a
     * statement about the store rather than about a read (`AC-REQ-FUNC-006-3`).
     *
     * A non-empty [target] **inherits the summaries already resolved** for it and clears only the
     * failure. That is `IC-020`'s precedence at the holder's level: a set that gains an id whose read has
     * not landed yet is still `Content` with the cards a user can already see, rather than blanking the
     * section to `Loading` on every store emission. The summaries that came from ids no longer in
     * [target] are dropped, so the map never accumulates stale cards.
     */
    private fun resolve(target: Set<CharacterId>) {
        run?.cancel()
        val mine = ++generation
        if (target.isEmpty()) {
            resolution.value = FavoriteCards()
            return
        }
        val carried =
            resolution.value
                ?.summaries
                .orEmpty()
                .filterKeys { it in target }
        resolution.value = FavoriteCards(summaries = carried)
        run =
            scope.launch(dispatcher) {
                val resolved = resolveFavoriteCards(target)
                // A superseded run publishes nothing, so the newest set's state is never overwritten by
                // an older read's outcome.
                if (mine == generation) resolution.value = resolved
            }
    }
}
