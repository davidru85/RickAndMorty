package io.github.davidru85.multiverse.core.data.remote.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The REST wire types of `API_SPECS.md` §4.7 (`IC-005`). They are internal to `:core:data`, every
// field the API documents as present is required, and no default papers over a missing one: a
// missing field fails decoding and maps to `MalformedResponse`.

@Serializable
internal data class RestPageDto<T>(
    val info: RestPageInfoDto,
    val results: List<T>,
)

@Serializable
internal data class RestPageInfoDto(
    val count: Int,
    val pages: Int,
    val next: String?,
    val prev: String?,
)

@Serializable
internal data class RestResourceRefDto(
    val name: String,
    val url: String,
)

@Serializable
internal data class RestCharacterDto(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val type: String,
    val gender: String,
    val origin: RestResourceRefDto,
    val location: RestResourceRefDto,
    val image: String,
    val episode: List<String>,
    val url: String,
    val created: String,
)

@Serializable
internal data class RestEpisodeDto(
    val id: Int,
    val name: String,
    @SerialName("air_date") val airDate: String,
    val episode: String,
    val characters: List<String>,
    val url: String,
    val created: String,
)
