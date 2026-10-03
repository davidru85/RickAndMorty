package io.github.davidru85.multiverse.core.data.cache

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
import kotlin.time.Instant

/**
 * Domain ⇄ cache-record mapping (`IC-012`).
 *
 * Both directions are total: every value the data layer admitted is representable, and a record that
 * does not map is discarded rather than approximated. Unknown status and gender values keep their raw
 * string, so a value a future server adds survives a cache round trip exactly as the adapter preserved
 * it (`AC-REQ-NFR-004-2`).
 */
internal object CachedPayloadMapper {
    fun page(value: CharacterPage): CachedPayloadRecord =
        CachedPayloadRecord(
            kind = CachedPayloadRecord.PAGE,
            page =
                CachedPage(
                    characters = value.characters.map { it.record() },
                    page = value.page,
                    pageCount = value.pageCount,
                    totalCount = value.totalCount,
                    nextPage = value.nextPage,
                    previousPage = value.previousPage,
                ),
        )

    fun details(value: CharacterDetails): CachedPayloadRecord =
        CachedPayloadRecord(
            kind = CachedPayloadRecord.DETAILS,
            details =
                CachedDetails(
                    character = value.record(),
                    origin = value.origin.record(),
                    lastKnownLocation = value.lastKnownLocation.record(),
                    episodeIds = value.episodeIds.map { it.value },
                    episodeSummaries = value.episodeSummaries?.map { it.record() },
                    createdAt = value.createdAt?.toString(),
                ),
        )

    /** The page a record holds, or `null` when the record is not one. */
    fun page(record: CachedPayloadRecord): CharacterPage? =
        record.page?.let { cached ->
            CharacterPage(
                characters = cached.characters.map { it.domain() },
                page = cached.page,
                pageCount = cached.pageCount,
                totalCount = cached.totalCount,
                nextPage = cached.nextPage,
                previousPage = cached.previousPage,
            )
        }

    /** The detail a record holds, or `null` when the record is not one. */
    fun details(record: CachedPayloadRecord): CharacterDetails? =
        record.details?.let { cached ->
            CharacterDetails(
                id = CharacterId(cached.character.id),
                name = cached.character.name,
                status = cached.character.status.toStatus(),
                species = cached.character.species,
                type = cached.character.type,
                gender = cached.character.gender.toGender(),
                origin = cached.origin.domain(),
                lastKnownLocation = cached.lastKnownLocation.domain(),
                imageUrl = cached.character.imageUrl,
                episodeIds = cached.episodeIds.map { EpisodeId(it) },
                episodeSummaries = cached.episodeSummaries?.map { it.domain() },
                createdAt = cached.createdAt?.toInstantOrNull(),
            )
        }

    private fun CharacterSummary.record(): CachedCharacter =
        CachedCharacter(
            id = id.value,
            name = name,
            status = status.wireValue(),
            species = species,
            type = type,
            gender = gender.wireValue(),
            location = lastKnownLocation.record(),
            imageUrl = imageUrl,
        )

    private fun CharacterDetails.record(): CachedCharacter =
        CachedCharacter(
            id = id.value,
            name = name,
            status = status.wireValue(),
            species = species,
            type = type,
            gender = gender.wireValue(),
            location = lastKnownLocation.record(),
            imageUrl = imageUrl,
        )

    private fun LocationSummary.record(): CachedLocation = CachedLocation(id = id?.value, name = name, type = type, dimension = dimension)

    private fun EpisodeSummary.record(): CachedEpisode = CachedEpisode(id = id.value, name = name, code = code, airDate = airDate)

    private fun CachedCharacter.domain(): CharacterSummary =
        CharacterSummary(
            id = CharacterId(id),
            name = name,
            status = status.toStatus(),
            species = species,
            type = type,
            gender = gender.toGender(),
            lastKnownLocation = location.domain(),
            imageUrl = imageUrl,
        )

    private fun CachedLocation.domain(): LocationSummary =
        LocationSummary(id = id?.let { LocationId(it) }, name = name, type = type, dimension = dimension)

    private fun CachedEpisode.domain(): EpisodeSummary = EpisodeSummary(id = EpisodeId(id), name = name, code = code, airDate = airDate)

    /** The server's own string, so an unrecognised value survives the round trip unchanged. */
    private fun CharacterStatus.wireValue(): String =
        when (this) {
            CharacterStatus.Alive -> ALIVE
            CharacterStatus.Dead -> DEAD
            CharacterStatus.Unknown -> UNKNOWN
            is CharacterStatus.Unsupported -> raw
        }

    private fun CharacterGender.wireValue(): String =
        when (this) {
            CharacterGender.Female -> FEMALE
            CharacterGender.Male -> MALE
            CharacterGender.Genderless -> GENDERLESS
            CharacterGender.Unknown -> UNKNOWN
            is CharacterGender.Unsupported -> raw
        }

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

    private fun String.toInstantOrNull(): Instant? =
        try {
            Instant.parse(this)
        } catch (_: IllegalArgumentException) {
            null
        }

    private const val ALIVE = "alive"
    private const val DEAD = "dead"
    private const val UNKNOWN = "unknown"
    private const val FEMALE = "female"
    private const val MALE = "male"
    private const val GENDERLESS = "genderless"
}
