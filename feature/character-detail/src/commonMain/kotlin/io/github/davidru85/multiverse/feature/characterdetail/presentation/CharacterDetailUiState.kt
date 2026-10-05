package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKey
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState

/**
 * What the Character detail screen renders (`IC-019`, `CONTRACTS.md` §6).
 *
 * Both platforms construct this one type — the Android `CharacterDetailViewModel` and the iOS
 * `ObservableObject` — so the two screens cannot diverge.
 *
 * [header] is the **list-provided** card the navigation hand-off carries in, so the hero and the
 * known fields render before the detail response arrives and the shared-element transition has a
 * source (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`). An `Error` never clears a non-null [header]: a detail
 * failure with list data keeps the known fields and offers the inline retry
 * (`AC-REQ-FUNC-002-3`). [gender] is the `IC-017.genderKey` label, `null` until the detail answers,
 * because the list card carries no gender (`REQ-FUNC-002`, `DEC-131`). [episodeCount] comes from
 * `CharacterDetails.episodeIds.size`, never from the
 * enrichment, so it renders even when `episodeSummaries` is `null` (`AC-REQ-FUNC-023-2`).
 * [dimension] is produced by `IC-017.dimensionText`; `null` means "hide the tile". [info] holds the
 * rows in the fixed order `Origin`, `LastKnownLocation`, `FirstSeenIn`: the first two are always
 * present once the detail answered, reading "Unknown" when the API reports no value
 * (`AC-REQ-FUNC-002-2`, `DEC-131`), and the `FirstSeenIn` row is **absent** rather than empty when
 * enrichment was not requested or found no episode (`AC-REQ-FUNC-023-2`). [isFavorite]
 * flips immediately on a toggle and is then reconciled with `ObserveFavoriteIds` emissions.
 */
public data class CharacterDetailUiState(
    public val header: CharacterCardUi? = null,
    public val gender: CopyKey? = null,
    public val episodeCount: Int? = null,
    public val dimension: String? = null,
    public val info: List<InfoRowUi> = emptyList(),
    public val isFavorite: Boolean = false,
    public val loadState: LoadState = LoadState.Loading,
)

/** The fixed identity of an info row (`IC-019`); the order in [CharacterDetailUiState.info] is [Origin], [LastKnownLocation], [FirstSeenIn]. */
public enum class InfoRowKind { Origin, LastKnownLocation, FirstSeenIn }

/**
 * One info row (`IC-019`): its [kind], the [copyKey] of its label — never an English literal
 * (`REQ-FUNC-013`, `REQ-UX-008`) — and its [value]: the data-derived name, or the one "Unknown"
 * presentation the platform resolves (`IC-017`, `DEC-131`).
 */
public data class InfoRowUi(
    public val kind: InfoRowKind,
    public val copyKey: CopyKey,
    public val value: DisplayText,
)

/**
 * The Detail intents (`IC-019`), the only write path into the state holder: a view never calls a
 * repository or a use case directly (`ERROR_FLOW.md` §3 invariant 4).
 */
public sealed interface CharacterDetailIntent {
    /** The user toggled the favourite; the flag flips immediately, then reconciles with the store. */
    public data object ToggleFavorite : CharacterDetailIntent

    /** The user asked for a retry after a failure; a fresh attempt budget is granted. */
    public data object Retry : CharacterDetailIntent
}
