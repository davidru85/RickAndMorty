package io.github.davidru85.multiverse.core.data.remote.graphql

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The hand-written GraphQL envelope and DTOs of `API_SPECS.md` §5 (`IC-005`). Apollo and any code
// generator are not used (`DEC-056`, ADR-0011): the envelope shape of §5.1 is declared here and the
// root fields' nullability of §5.2 is kept, because every root type the published schema exposes is
// nullable.
//
// A field the operation does not request is absent, not null: `explicitNulls = true` makes a missing
// key and an explicit `null` different only where the schema allows null, and every field the screen
// renders is required so a missing one fails decoding instead of defaulting.

/**
 * The response envelope of `API_SPECS.md` §5.1, with `data` left as the raw element.
 *
 * The envelope is read in two steps on purpose: this shape decides whether the outcome is an
 * errors-only failure, a null root or usable data **before** any typed decode runs, so `§6.2`'s
 * evaluation order is a property of the code rather than of a serializer's leniency. `data` is
 * required to be present, so a body that carries neither `data` nor a decodable envelope fails
 * decoding into `MalformedResponse`.
 */
@Serializable
internal data class GraphQlRawEnvelopeDto(
    val data: JsonElement? = null,
    val errors: List<GraphQlErrorDto>? = null,
)

@Serializable
internal data class GraphQlErrorDto(
    val message: String,
    val path: List<JsonElement>? = null,
    val extensions: GraphQlErrorExtensionsDto? = null,
)

/** Only the extension the mapping reads: the error code `API-ERR-012`/`013` name. */
@Serializable
internal data class GraphQlErrorExtensionsDto(
    val code: String? = null,
)

@Serializable
internal data class GraphQlPageDataDto(
    val characters: GraphQlCharactersDto? = null,
)

@Serializable
internal data class GraphQlCharactersDto(
    val info: GraphQlInfoDto? = null,
    val results: List<GraphQlCharacterDto?>? = null,
)

/** Every `Info` field is nullable (`API_SPECS.md` §5.3); `next` and `prev` are page numbers. */
@Serializable
internal data class GraphQlInfoDto(
    val count: Int? = null,
    val pages: Int? = null,
    val next: Int? = null,
    val prev: Int? = null,
)

/**
 * The list shape of `API_SPECS.md` §5.5: only the fields the list renders. `origin` and `episode` are
 * deliberately absent — the list operation does not request them (`§5.6`).
 */
@Serializable
internal data class GraphQlCharacterDto(
    val id: String,
    val name: String,
    val status: String,
    val species: String,
    val type: String? = null,
    val gender: String,
    val location: GraphQlLocationDto? = null,
    val image: String,
)

@Serializable
internal data class GraphQlDetailDataDto(
    val character: GraphQlCharacterDetailDto? = null,
)

@Serializable
internal data class GraphQlCharacterDetailDto(
    val id: String,
    val name: String,
    val status: String,
    val species: String,
    val type: String? = null,
    val gender: String,
    val image: String,
    val created: String? = null,
    val origin: GraphQlLocationDto? = null,
    val location: GraphQlLocationDto? = null,
    val episode: List<GraphQlEpisodeRefDto?>? = null,
)

@Serializable
internal data class GraphQlLocationDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val dimension: String? = null,
)

/** The relationship shape: the detail needs the episode's id and nothing else. */
@Serializable
internal data class GraphQlEpisodeRefDto(
    val id: String? = null,
)

@Serializable
internal data class GraphQlEpisodesDataDto(
    @SerialName("episodesByIds") val episodes: List<GraphQlEpisodeDto?>? = null,
)

@Serializable
internal data class GraphQlEpisodeDto(
    val id: String? = null,
    val name: String? = null,
    val episode: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)
