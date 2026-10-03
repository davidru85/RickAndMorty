package io.github.davidru85.multiverse.core.data.cache

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a cached payload (`IC-012`).
 *
 * The domain types of `:core:domain` are platform-free and carry no serialization annotation
 * (`DEC-066`), so the cache stores its own record and maps it back in both directions
 * ([CachedPayloadMapper]). A record that cannot be decoded, or that does not map, is a miss — never a
 * user-visible failure.
 *
 * The record is a flat envelope with a [kind] tag rather than a polymorphic hierarchy: an unknown tag
 * fails deserialization like any other malformed record, which is what the seam promises, and it needs
 * no serializers module.
 */
@Serializable
public data class CachedPayloadRecord(
    public val kind: String,
    public val page: CachedPage? = null,
    public val details: CachedDetails? = null,
) {
    init {
        require(kind == PAGE || kind == DETAILS) { "a cached record is a page or a detail" }
        require((kind == PAGE) == (page != null)) { "a page record carries its page" }
        require((kind == DETAILS) == (details != null)) { "a detail record carries its detail" }
    }

    public companion object {
        public const val PAGE: String = "page"
        public const val DETAILS: String = "details"
    }
}

@Serializable
public data class CachedPage(
    public val characters: List<CachedCharacter>,
    public val page: Int,
    public val pageCount: Int? = null,
    public val totalCount: Int? = null,
    public val nextPage: Int? = null,
    public val previousPage: Int? = null,
)

@Serializable
public data class CachedDetails(
    public val character: CachedCharacter,
    public val origin: CachedLocation,
    public val lastKnownLocation: CachedLocation,
    public val episodeIds: List<String>,
    public val episodeSummaries: List<CachedEpisode>? = null,
    public val createdAt: String? = null,
)

@Serializable
public data class CachedCharacter(
    public val id: String,
    public val name: String,
    public val status: String,
    public val species: String,
    public val type: String? = null,
    public val gender: String,
    public val location: CachedLocation,
    public val imageUrl: String,
)

@Serializable
public data class CachedLocation(
    public val id: String? = null,
    public val name: String,
    public val type: String? = null,
    public val dimension: String? = null,
)

@Serializable
public data class CachedEpisode(
    public val id: String,
    public val name: String,
    public val code: String,
    public val airDate: String,
)
