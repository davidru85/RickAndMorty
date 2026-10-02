package io.github.davidru85.multiverse.core.domain.model

import kotlin.time.Instant

// The domain models of `API_SPECS.md` §3 (`IC-002`). Both remote adapters map to these types, and
// no wire type ever appears in them (`IC-005`).

/**
 * One page of the character list.
 *
 * [page] is the requested page number. [pageCount], [totalCount] and [nextPage] stay `null` until
 * the server establishes them; a consumer never substitutes `0` for a null count
 * (`AC-REQ-FUNC-001-3`).
 */
public data class CharacterPage(
    public val characters: List<CharacterSummary>,
    public val page: Int,
    public val pageCount: Int?,
    public val totalCount: Int?,
    public val nextPage: Int?,
    public val previousPage: Int?,
)

/** A character as the list renders it. An empty server `type` is `null` here. */
public data class CharacterSummary(
    public val id: CharacterId,
    public val name: String,
    public val status: CharacterStatus,
    public val species: String,
    public val type: String?,
    public val gender: CharacterGender,
    public val lastKnownLocation: LocationSummary,
    public val imageUrl: String,
)

/**
 * A character with its detail fields.
 *
 * [episodeSummaries] is `null` when enrichment was not requested and an empty list when enrichment
 * completed with no appearances; a consumer never reads `null` as "no episodes". A missing or
 * unparsable creation date is `null` and does not prevent the rest from rendering.
 */
public data class CharacterDetails(
    public val id: CharacterId,
    public val name: String,
    public val status: CharacterStatus,
    public val species: String,
    public val type: String?,
    public val gender: CharacterGender,
    public val origin: LocationSummary,
    public val lastKnownLocation: LocationSummary,
    public val imageUrl: String,
    public val episodeIds: List<EpisodeId>,
    public val episodeSummaries: List<EpisodeSummary>?,
    public val createdAt: Instant?,
)

/** A location reference; one with an empty server URL is valid and has no [id]. */
public data class LocationSummary(
    public val id: LocationId?,
    public val name: String,
    public val type: String? = null,
    public val dimension: String? = null,
)

/** An episode as the detail enrichment renders it; [airDate] is the server's display string. */
public data class EpisodeSummary(
    public val id: EpisodeId,
    public val name: String,
    public val code: String,
    public val airDate: String,
)

/**
 * A character's life status. The API sends a string, not a closed enum, so an unrecognised value
 * is preserved as [Unsupported] rather than dropped or thrown on (`AC-REQ-NFR-004-2`).
 */
public sealed interface CharacterStatus {
    public data object Alive : CharacterStatus

    public data object Dead : CharacterStatus

    public data object Unknown : CharacterStatus

    public data class Unsupported(
        public val raw: String,
    ) : CharacterStatus
}

/** A character's gender, with the same forward-compatible shape as [CharacterStatus]. */
public sealed interface CharacterGender {
    public data object Female : CharacterGender

    public data object Male : CharacterGender

    public data object Genderless : CharacterGender

    public data object Unknown : CharacterGender

    public data class Unsupported(
        public val raw: String,
    ) : CharacterGender
}
