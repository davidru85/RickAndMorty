package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailIntent
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailStateHolder
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The Android state holder of the Character detail screen (`IC-019`, `DESIGN.md` §5, ADR-0006).
 *
 * It is a **thin adapter**: every rule lives in the shared `CharacterDetailStateHolder`, so this class
 * exposes its [state] and forwards intents to it, and computes no display value of its own
 * (`CONTRACTS.md` §7 R2). Its own concerns are the ones only a platform has — the `viewModelScope`
 * the loads run in, and nothing else.
 *
 * [header] is the list-provided card the navigation hand-off carried in (`IC-025`); a deep link or a
 * process restart passes `null`, and the screen renders from its own load state instead of treating
 * the missing payload as a failure.
 */
public class CharacterDetailViewModel(
    id: CharacterId,
    header: CharacterCardUi?,
    getDetails: GetCharacterDetails,
    toggleFavorite: ToggleFavorite,
    observeFavoriteIds: ObserveFavoriteIds,
    formatters: PresentationFormatters,
    enrich: Boolean = true,
) : ViewModel() {
    private val holder =
        CharacterDetailStateHolder(
            id = id,
            header = header,
            getDetails = getDetails,
            toggleFavorite = toggleFavorite,
            observeFavoriteIds = observeFavoriteIds,
            scope = viewModelScope,
            dispatcher = Dispatchers.Main.immediate,
            formatters = formatters,
            enrich = enrich,
        )

    /** What the screen renders, owned by the shared holder and only projected here. */
    public val state: StateFlow<CharacterDetailUiState> = holder.state

    init {
        holder.start()
    }

    /** The one write path into the state holder (`ERROR_FLOW.md` §3 invariant 4). */
    public fun onIntent(intent: CharacterDetailIntent) {
        holder.onIntent(intent)
    }
}
