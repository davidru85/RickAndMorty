package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary

/**
 * A character card's four displayed items — photo, name, status, species — complete in one immutable
 * value (`IC-016`), so a card component needs no fetch and no second model. [status] drives the
 * badge's colour and semantics and [statusLabel] its text; [species] is the API `species`, never
 * `type`, with an unknown value as the unknown key; [imageUrl] is the API URL verbatim, because it is
 * also the image cache key.
 */
public data class CharacterCardUi(
    public val id: CharacterId,
    public val name: String,
    public val species: DisplayText,
    public val status: CharacterStatus,
    public val statusLabel: CopyKey,
    public val imageUrl: String,
) {
    public companion object {
        /** The card for [summary], with its copy keys from [formatters]. */
        public fun from(
            summary: CharacterSummary,
            formatters: PresentationFormatters,
        ): CharacterCardUi =
            CharacterCardUi(
                id = summary.id,
                name = summary.name,
                species = formatters.valueText(summary.species),
                status = summary.status,
                statusLabel = formatters.statusKey(summary.status),
                imageUrl = summary.imageUrl,
            )
    }
}
