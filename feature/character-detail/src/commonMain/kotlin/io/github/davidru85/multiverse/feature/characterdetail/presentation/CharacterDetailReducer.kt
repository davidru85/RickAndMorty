package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKey
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters

/**
 * The shared half of the Detail state holder (`IC-019`, `DESIGN.md` §4.1): the mapping from a
 * `DataResult<CharacterDetails>` to [CharacterDetailUiState], so neither the Android
 * `CharacterDetailViewModel` nor the iOS `ObservableObject` computes a display value of its own
 * (`CONTRACTS.md` §7 R2).
 *
 * The one fact the mapper needs from the caller is [CharacterDetailUiState.header]: the
 * list-provided card the navigation hand-off carried in. It is passed in rather than derived here,
 * because `CharacterCardUi` is the payload of the hand-off (`IC-025`) and a deep link has none. When
 * the result succeeds, a header built from the detail response replaces a `null` one, so a deep link
 * still gets a hero; an existing header is never replaced, because the list data is what the
 * shared-element transition animated from and it is already correct.
 *
 * Every display value is produced by `IC-017`: [PresentationFormatters.dimensionText] for the
 * dimension tile and [PresentationFormatters.firstSeenText] for the last row. A `null` from either
 * means "hide", never a placeholder (`UI_SPEC.md` §6.3), and [CharacterDetails.episodeIds] is the
 * count's only source so it survives a missing enrichment (`AC-REQ-FUNC-023-2`).
 */
public object CharacterDetailReducer {
    /**
     * The state for one detail [result], or for the absence of one while [isLoading] holds.
     *
     * `null` [result] with [isLoading] is the pre-response state: the header renders from list data
     * and nothing else is known. `null` [result] without [isLoading] cannot be produced by the state
     * holder — a load always ends in a result — so it renders as [LoadState.Loading] rather than
     * inventing a failure.
     */
    public fun render(
        header: CharacterCardUi?,
        result: DataResult<CharacterDetails>?,
        isLoading: Boolean,
        isFavorite: Boolean,
        enrichRequested: Boolean,
        formatters: PresentationFormatters,
    ): CharacterDetailUiState =
        when (result) {
            null ->
                CharacterDetailUiState(
                    header = header,
                    isFavorite = isFavorite,
                    loadState = LoadState.Loading,
                )

            is DataResult.Failure ->
                // An error never clears a non-null header: the surface keeps the known fields and
                // shows the inline retry in place of the info list (AC-REQ-FUNC-002-3).
                CharacterDetailUiState(
                    header = header,
                    isFavorite = isFavorite,
                    loadState = LoadState.Error(result.failure),
                )

            is DataResult.Success ->
                content(
                    header = header,
                    details = result.value,
                    isFavorite = isFavorite,
                    enrichRequested = enrichRequested,
                    formatters = formatters,
                )
        }

    /** Renders [details] as content; the header prefers the list-provided card and falls back to the detail. */
    private fun content(
        header: CharacterCardUi?,
        details: CharacterDetails,
        isFavorite: Boolean,
        enrichRequested: Boolean,
        formatters: PresentationFormatters,
    ): CharacterDetailUiState =
        CharacterDetailUiState(
            header = header ?: headerOf(details, formatters),
            episodeCount = details.episodeIds.size,
            dimension = formatters.dimensionText(details.origin, enrichRequested),
            info = infoRows(details, enrichRequested, formatters),
            isFavorite = isFavorite,
            loadState = LoadState.Content,
        )

    /** The card the detail response itself can supply, for a deep link that carried none (`IC-016`). */
    private fun headerOf(
        details: CharacterDetails,
        formatters: PresentationFormatters,
    ): CharacterCardUi =
        CharacterCardUi(
            id = details.id,
            name = details.name,
            species = formatters.valueText(details.species),
            status = details.status,
            statusLabel = formatters.statusKey(details.status),
            imageUrl = details.imageUrl,
        )

    /**
     * The available rows in the fixed order `Origin`, `LastKnownLocation`, `FirstSeenIn`, filtered by
     * availability: a row whose value the formatters hid is **absent**, not empty
     * (`IC-019`, `AC-REQ-FUNC-023-2`).
     */
    private fun infoRows(
        details: CharacterDetails,
        enrichRequested: Boolean,
        formatters: PresentationFormatters,
    ): List<InfoRowUi> =
        buildList {
            (CopyKeys.DETAIL_INFO_ORIGIN to details.origin.name)
                .row(InfoRowKind.Origin, formatters)
                ?.let(::add)
            (CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION to details.lastKnownLocation.name)
                .row(InfoRowKind.LastKnownLocation, formatters)
                ?.let(::add)
            // First seen in requires the enrichment; `firstSeenText(null)` is null, so an absent
            // enrichment leaves the row out entirely rather than rendering an empty value.
            if (enrichRequested) {
                formatters
                    .firstSeenText(details.episodeSummaries)
                    ?.let { add(InfoRowUi(InfoRowKind.FirstSeenIn, CopyKeys.DETAIL_INFO_FIRST_SEEN_IN, it)) }
            }
        }

    /** The row for a raw location name, or `null` when the value is absent, blank or "unknown". */
    private fun Pair<CopyKey, String>.row(
        kind: InfoRowKind,
        formatters: PresentationFormatters,
    ): InfoRowUi? {
        val text = formatters.valueText(second)
        val value =
            when (text) {
                is io.github.davidru85.multiverse.core.presentation.DisplayText.Data -> text.value
                // A value that resolves to the one "Unknown" presentation is not a location name, so
                // the row is absent. The design system resolves the key at the platform seam.
                is io.github.davidru85.multiverse.core.presentation.DisplayText.Copy -> return null
            }
        return InfoRowUi(kind, first, value)
    }
}
