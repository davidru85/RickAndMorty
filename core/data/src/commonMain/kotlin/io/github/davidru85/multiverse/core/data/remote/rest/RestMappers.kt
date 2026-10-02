package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
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
import io.ktor.http.parseUrl
import kotlin.time.Instant

// DTO → domain mapping (`API_SPECS.md` §3, §4.7). Ids become canonical strings exactly once, unknown
// status and gender strings are preserved, an empty `type` is absent, image URLs are untouched, and
// every relation or pagination URL is checked against the host allow-list before anything is taken
// from it (`REQ-SEC-001`).

/**
 * A payload the mapper refuses: a link outside the allow-list ([ApiFailure.InvalidRequest]) or an
 * allow-listed link that does not name what it should ([ApiFailure.MalformedResponse]). The data
 * source turns it into a `DataResult.Failure` at the seam; it never escapes `:core:data`.
 */
internal class RejectedPayload(
    val failure: ApiFailure,
) : Exception()

/** The detail an `InvalidRequest` carries for a link outside the allow-list; never the URL itself. */
internal const val FOREIGN_HOST: String = "foreign-host"

internal fun RestPageDto<RestCharacterDto>.toPage(page: Int): CharacterPage =
    CharacterPage(
        characters = results.map { it.toSummary() },
        page = page,
        pageCount = info.pages,
        totalCount = info.count,
        nextPage = pageNumber(info.next),
        previousPage = pageNumber(info.prev),
    )

internal fun RestCharacterDto.toSummary(): CharacterSummary =
    CharacterSummary(
        id = CharacterId(id.toString()),
        name = name,
        status = status.toStatus(),
        species = species,
        type = type.ifEmpty { null },
        gender = gender.toGender(),
        lastKnownLocation = location.toLocation(),
        imageUrl = image,
    )

internal fun RestCharacterDto.toDetails(): CharacterDetails =
    CharacterDetails(
        id = CharacterId(id.toString()),
        name = name,
        status = status.toStatus(),
        species = species,
        type = type.ifEmpty { null },
        gender = gender.toGender(),
        origin = origin.toLocation(),
        lastKnownLocation = location.toLocation(),
        imageUrl = image,
        episodeIds = episode.map { EpisodeId(relationId(it, RickAndMortyApi.EPISODE)) },
        episodeSummaries = null,
        createdAt = created.toInstantOrNull(),
    )

internal fun RestEpisodeDto.toSummary(): EpisodeSummary =
    EpisodeSummary(
        id = EpisodeId(id.toString()),
        name = name,
        code = episode,
        airDate = airDate,
    )

private fun RestResourceRefDto.toLocation(): LocationSummary =
    LocationSummary(
        id = url.takeIf { it.isNotEmpty() }?.let { LocationId(relationId(it, RickAndMortyApi.LOCATION)) },
        name = name,
    )

private fun String.toStatus(): CharacterStatus =
    when {
        equals("alive", ignoreCase = true) -> CharacterStatus.Alive
        equals("dead", ignoreCase = true) -> CharacterStatus.Dead
        equals("unknown", ignoreCase = true) -> CharacterStatus.Unknown
        else -> CharacterStatus.Unsupported(this)
    }

private fun String.toGender(): CharacterGender =
    when {
        equals("female", ignoreCase = true) -> CharacterGender.Female
        equals("male", ignoreCase = true) -> CharacterGender.Male
        equals("genderless", ignoreCase = true) -> CharacterGender.Genderless
        equals("unknown", ignoreCase = true) -> CharacterGender.Unknown
        else -> CharacterGender.Unsupported(this)
    }

/** `created` is contractually a string; an unparsable one is absent rather than a failure. */
private fun String.toInstantOrNull(): Instant? =
    try {
        Instant.parse(this)
    } catch (_: IllegalArgumentException) {
        null
    }

/** The id a relation URL names, once the URL passed the allow-list and names [resource]. */
private fun relationId(
    link: String,
    resource: String,
): String {
    val url = parseUrl(link) ?: throw RejectedPayload(ApiFailure.MalformedResponse)
    if (!RickAndMortyApi.isAllowListed(url)) throw RejectedPayload(ApiFailure.InvalidRequest(FOREIGN_HOST))
    val segments = url.segments
    val id = segments.getOrNull(2)
    if (segments.size != 3 || segments[0] != RickAndMortyApi.API || segments[1] != resource || id == null || !id.isRestId()) {
        throw RejectedPayload(ApiFailure.MalformedResponse)
    }
    return id
}

/** The page number a pagination link names; `null` stays `null`, the end of pagination. */
private fun pageNumber(link: String?): Int? {
    if (link == null) return null
    val url = parseUrl(link) ?: throw RejectedPayload(ApiFailure.MalformedResponse)
    if (!RickAndMortyApi.isAllowListed(url)) throw RejectedPayload(ApiFailure.InvalidRequest(FOREIGN_HOST))
    val page = url.parameters["page"]?.toIntOrNull()
    if (url.segments != listOf(RickAndMortyApi.API, RickAndMortyApi.CHARACTER) || page == null || page < 1) {
        throw RejectedPayload(ApiFailure.MalformedResponse)
    }
    return page
}

/** A REST id is a non-empty run of decimal digits; anything else cannot be routed safely. */
internal fun String.isRestId(): Boolean = isNotEmpty() && all { it in '0'..'9' }
