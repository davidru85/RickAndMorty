package io.github.davidru85.multiverse.core.data.remote.rest

import io.github.davidru85.multiverse.core.data.remote.RemoteJson
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.LocationId
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.testing.FixtureLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `TEST-UNIT-001` — DTO → domain mapping (`API_SPECS.md` §3, §4.7; `IC-001`, `IC-002`).
 *
 * The DTOs are decoded from committed fixtures with the production [RemoteJson], then varied one
 * field at a time with `copy`, so each case isolates one mapping rule.
 */
class RestMappersTest {
    private val detail: RestCharacterDto =
        RemoteJson.decodeFromString(RestCharacterDto.serializer(), FixtureLoader.text("character-detail.json"))

    @Test
    fun `TEST-UNIT-001 given_a_detail_dto_when_mapped_then_ids_names_and_urls_are_kept_verbatim`() {
        val details = detail.toDetails()

        assertEquals("1", details.id.value, "TEST-UNIT-001: the REST integer id becomes its string once")
        assertEquals(detail.name, details.name)
        assertEquals(detail.species, details.species, "TEST-UNIT-001: species is never replaced by type")
        assertEquals(detail.image, details.imageUrl, "TEST-UNIT-001: the image URL is the cache key and is never rewritten")
        assertEquals(LocationId("1"), details.origin.id, "TEST-UNIT-001: the location id comes from its URL")
        assertEquals(detail.origin.name, details.origin.name)
        assertEquals(
            detail.episode.map { EpisodeId(it.substringAfterLast('/')) },
            details.episodeIds,
            "TEST-UNIT-001: episode ids keep the server's order",
        )
        assertNull(details.episodeSummaries, "TEST-UNIT-001: summaries are never fabricated by the mapper")
        assertEquals(Instant.parse(detail.created), details.createdAt)
    }

    @Test
    fun `TEST-UNIT-001 given_each_status_and_gender_spelling_when_mapped_then_known_values_map_and_others_are_preserved`() {
        val statuses =
            mapOf(
                "Alive" to CharacterStatus.Alive,
                "Dead" to CharacterStatus.Dead,
                "unknown" to CharacterStatus.Unknown,
                "ALIVE" to CharacterStatus.Alive,
                "Zombie" to CharacterStatus.Unsupported("Zombie"),
                "" to CharacterStatus.Unsupported(""),
            )
        statuses.forEach { (raw, expected) ->
            assertEquals(expected, detail.copy(status = raw).toDetails().status, "TEST-UNIT-001: status `$raw`")
        }
        val genders =
            mapOf(
                "Female" to CharacterGender.Female,
                "Male" to CharacterGender.Male,
                "Genderless" to CharacterGender.Genderless,
                "unknown" to CharacterGender.Unknown,
                "Fluid" to CharacterGender.Unsupported("Fluid"),
            )
        genders.forEach { (raw, expected) ->
            assertEquals(expected, detail.copy(gender = raw).toDetails().gender, "TEST-UNIT-001: gender `$raw`")
        }
    }

    @Test
    fun `TEST-UNIT-001 given_an_empty_type_an_empty_reference_and_a_bad_date_when_mapped_then_each_is_absent_not_invented`() {
        val details =
            detail
                .copy(type = "", created = "yesterday", origin = RestResourceRefDto(name = "unknown", url = ""))
                .toDetails()

        assertNull(details.type, "TEST-UNIT-001: an empty type is absent in the domain")
        assertNull(details.origin.id, "TEST-UNIT-001: an empty reference URL is valid and has no id")
        assertEquals("unknown", details.origin.name)
        assertNull(details.createdAt, "TEST-UNIT-001: an unparsable date is null and does not fail the character")
        assertEquals("Parasite", detail.copy(type = "Parasite").toDetails().type)
    }

    @Test
    fun `TEST-UNIT-001 given_a_relation_url_outside_the_api_when_mapped_then_the_payload_is_rejected`() {
        val foreignHost = detail.copy(location = RestResourceRefDto("Citadel", "https://evil.example/api/location/3"))
        val cleartext = detail.copy(episode = listOf("http://" + detail.episode.first().substringAfter("://")))
        val wrongPath = detail.copy(episode = listOf(detail.episode.first().replace("/episode/", "/location/")))

        assertIs<ApiFailure.InvalidRequest>(assertFailsWith<RejectedPayload> { foreignHost.toDetails() }.failure)
        assertIs<ApiFailure.InvalidRequest>(assertFailsWith<RejectedPayload> { cleartext.toDetails() }.failure)
        assertEquals(
            ApiFailure.MalformedResponse,
            assertFailsWith<RejectedPayload> { wrongPath.toDetails() }.failure,
            "TEST-UNIT-001: an allow-listed URL that names the wrong resource is malformed",
        )
    }

    @Test
    fun `TEST-UNIT-001 given_an_episode_dto_when_mapped_then_the_code_and_display_date_are_kept`() {
        val episodes =
            RemoteJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(RestEpisodeDto.serializer()),
                FixtureLoader.text("episode-batch.json"),
            )

        val summary = episodes.first().toSummary()

        assertEquals(EpisodeId("1"), summary.id)
        assertEquals("S01E01", summary.code)
        assertEquals("December 2, 2013", summary.airDate, "TEST-UNIT-001: air_date is a display string, not ISO-8601")
    }
}
