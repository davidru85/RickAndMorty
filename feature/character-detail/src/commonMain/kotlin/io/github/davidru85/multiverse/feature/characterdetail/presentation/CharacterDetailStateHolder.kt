package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The shared half of the Detail state holder (`IC-019`, `DESIGN.md` §4.1): every rule a platform
 * holder must not re-derive lives here, so the Android `CharacterDetailViewModel` and the iOS
 * `ObservableObject` are thin adapters over one implementation (`CONTRACTS.md` §7 R1–R5).
 *
 * It owns four things, and nothing else:
 *
 * - the **list-provided header**. It is a constructor parameter, taken from the navigation hand-off
 *   (`IC-025`) before any request exists, so the hero and the known fields render in the first
 *   composed frame and the shared-element transition has a source (`AC-REQ-FUNC-002-1`);
 * - the **load**. One detail request, retried on [CharacterDetailIntent.Retry] with a fresh attempt
 *   budget, whose outcome is mapped by [CharacterDetailReducer] — so no platform computes a display
 *   value;
 * - the **favourite flag**. [CharacterDetailIntent.ToggleFavorite] flips it immediately, before the
 *   write completes, and every `ObserveFavoriteIds` emission then reconciles it, so the control never
 *   shows a stale toggled state (`AC-REQ-FUNC-006-1`);
 * - the [enrich] switch, which the caller fixes when it constructs the holder: it is passed straight
 *   through to the use case, so `episodeSummaries` is `null` exactly when enrichment was not
 *   requested (`AC-REQ-FUNC-023-2`).
 *
 * [scope] belongs to the owner and is closed by it (`GUIDELINES.md` §2.7), so closing the holder
 * cancels the load and the observation together.
 */
public class CharacterDetailStateHolder(
    private val id: CharacterId,
    private val header: CharacterCardUi?,
    private val getDetails: GetCharacterDetails,
    private val toggleFavorite: ToggleFavorite,
    private val observeFavoriteIds: ObserveFavoriteIds,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val formatters: PresentationFormatters,
    private val enrich: Boolean = true,
) {
    /** The newest load's outcome, or `null` while no load has ended: the pre-response state. */
    private val result = MutableStateFlow<DataResult<CharacterDetails>?>(null)

    /** The last known favourite state: flipped optimistically, then reconciled by the store. */
    private val favorite = MutableStateFlow(false)

    /**
     * What the surface renders, always up to date. It starts at the [CharacterDetailUiState] the
     * hand-off implies — the header, and a load that has not ended — so nothing has to be composed
     * before the first emission to see the list data.
     */
    public val state: StateFlow<CharacterDetailUiState> =
        combine(result, favorite) { details, isFavorite ->
            CharacterDetailReducer.render(
                header = header,
                result = details,
                isFavorite = isFavorite,
                enrichRequested = enrich,
                formatters = formatters,
            )
        }.stateIn(scope, SharingStarted.Eagerly, CharacterDetailUiState(header = header))

    /** Dispatches [intent]; a view never reaches a repository or a use case directly. */
    public fun onIntent(intent: CharacterDetailIntent) {
        when (intent) {
            // Optimistic: the flag flips now so the control responds, and the store's next emission
            // either confirms or corrects it (AC-REQ-FUNC-006-1).
            CharacterDetailIntent.ToggleFavorite -> {
                favorite.value = !favorite.value
                scope.launch(dispatcher) { toggleFavorite(id) }
            }

            CharacterDetailIntent.Retry -> load()
        }
    }

    /**
     * Loads the detail once and then keeps the favourite flag reconciled with the store, until
     * [scope] is cancelled. A platform holder invokes it once, from its own initialisation.
     */
    public fun start(): Job {
        observeFavoriteIds()
            .onEach { ids -> favorite.value = id in ids }
            .launchIn(scope)
        return load()
    }

    /** The load in flight; the next load cancels it, so an older attempt can never publish late. */
    private var inFlight: Job? = null

    /**
     * Starts one detail request in [scope], superseding the one in flight: two concurrent loads would
     * race, and the one that finished last — possibly an older failure — would overwrite a newer
     * success. The repository gives each call a fresh attempt budget.
     */
    private fun load(): Job {
        inFlight?.cancel()
        return scope
            .launch(dispatcher) {
                result.value = getDetails(id, enrich)
            }.also { inFlight = it }
    }
}
