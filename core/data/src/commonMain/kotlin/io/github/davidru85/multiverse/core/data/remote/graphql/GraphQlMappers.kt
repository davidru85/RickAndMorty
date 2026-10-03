package io.github.davidru85.multiverse.core.data.remote.graphql

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationId
import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import kotlin.time.Instant

// GraphQL DTO → domain mapping (`API_SPECS.md` §3, §5.4, §5.5). It is deliberately field-for-field
// equivalent to `RestMappers.kt`: a GraphQL `ID` is already the canonical string, an unknown status or
// gender string is preserved verbatim, an empty `type` is absent, and a relation exposes its id
// directly instead of through a URL — which is why this mapper needs no host allow-list at all.
//
// A field the screen renders is required by the DTO, so a missing one fails decoding before mapping;
// what is nullable in the published schema (`location`, `episode`, `created`, `info`) is mapped
// defensively here.

internal fun GraphQlPageDataDto.toPage(page: Int): CharacterPage {
    val characters = characters ?: throw RejectedGraphQlPayload()
    // `§6.2` step 4: a list root is `MalformedResponse` unless `results` explicitly indicates an empty
    // set. `results: []` is that empty page; an absent or null `results` is not.
    val results = characters.results ?: throw RejectedGraphQlPayload()
    return CharacterPage(
        characters = results.map { it?.toSummary() ?: throw RejectedGraphQlPayload() },
        page = page,
        pageCount = characters.info?.pages,
        totalCount = characters.info?.count,
        nextPage = characters.info?.next,
        previousPage = characters.info?.prev,
    )
}

internal fun GraphQlCharacterDto.toSummary(): CharacterSummary =
    CharacterSummary(
        id = CharacterId(id),
        name = name,
        status = status.toStatus(),
        species = species,
        type = type?.ifEmpty { null },
        gender = gender.toGender(),
        lastKnownLocation = location.toLocation(),
        imageUrl = image,
    )

internal fun GraphQlCharacterDetailDto.toDetails(): CharacterDetails =
    CharacterDetails(
        id = CharacterId(id),
        name = name,
        status = status.toStatus(),
        species = species,
        type = type?.ifEmpty { null },
        gender = gender.toGender(),
        origin = origin.toLocation(),
        lastKnownLocation = location.toLocation(),
        imageUrl = image,
        episodeIds = episode?.mapNotNull { it?.id?.let(::EpisodeId) } ?: emptyList(),
        episodeSummaries = null,
        createdAt = created.orEmpty().toInstantOrNull(),
    )

/** A batch element whose fields the screen renders are absent is a payload the mapper refuses. */
internal fun GraphQlEpisodeDto.toSummary(): EpisodeSummary =
    EpisodeSummary(
        id = EpisodeId(id ?: throw RejectedGraphQlPayload()),
        name = name ?: throw RejectedGraphQlPayload(),
        code = episode ?: throw RejectedGraphQlPayload(),
        airDate = airDate ?: throw RejectedGraphQlPayload(),
    )

/**
 * A GraphQL object that is null where the screen needs it. The envelope is well-formed, so this is
 * `MalformedResponse` — never a defaulted value (`API_SPECS.md` §6.2 step 6).
 */
internal class RejectedGraphQlPayload : Exception()

/**
 * A null required root of a single-resource query (`API_SPECS.md` §6.2 step 4): a 200 answered with
 * `{"data":{"character":null}}` and no GraphQL error is `NotFound`, and it carries the resource and
 * id the domain failure names.
 */
internal class GraphQlRootNull(
    val failure: ApiFailure,
) : Exception()

private fun GraphQlLocationDto?.toLocation(): LocationSummary =
    LocationSummary(
        id = this?.id?.takeIf { it.isNotEmpty() }?.let(::LocationId),
        name = this?.name.orEmpty(),
        type = this?.type?.ifEmpty { null },
        dimension = this?.dimension?.ifEmpty { null },
    )

private fun String.toStatus(): CharacterStatus =
    when {
        equals(ALIVE, ignoreCase = true) -> CharacterStatus.Alive
        equals(DEAD, ignoreCase = true) -> CharacterStatus.Dead
        equals(UNKNOWN, ignoreCase = true) -> CharacterStatus.Unknown
        else -> CharacterStatus.Unsupported(this)
    }

private fun String.toGender(): CharacterGender =
    when {
        equals(FEMALE, ignoreCase = true) -> CharacterGender.Female
        equals(MALE, ignoreCase = true) -> CharacterGender.Male
        equals(GENDERLESS, ignoreCase = true) -> CharacterGender.Genderless
        equals(UNKNOWN, ignoreCase = true) -> CharacterGender.Unknown
        else -> CharacterGender.Unsupported(this)
    }

/** `created` is nullable in the schema; an absent or unparsable one is absent, never a failure. */
private fun String.toInstantOrNull(): Instant? =
    try {
        Instant.parse(this)
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * A GraphQL id is any non-empty server string (`API_SPECS.md` §3: the `ID` is opaque). Whitespace-only
 * and empty values cannot address a resource, so they are rejected before a request exists.
 */
internal fun String.isGraphQlId(): Boolean = isNotBlank()

private const val ALIVE = "alive"
private const val DEAD = "dead"
private const val UNKNOWN = "unknown"
private const val FEMALE = "female"
private const val MALE = "male"
private const val GENDERLESS = "genderless"
