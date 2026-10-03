package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared presentation formatters (`IC-016`, `IC-017`, `UI_SPEC.md` §6.2–§6.3): `TEST-UNIT-001`
 * for the card, `TEST-UNIT-002` for the detail rows. Localisable prose is always a canonical copy key;
 * only data-derived text is a string, and nothing is invented when the source data is absent.
 */
class PresentationFormattersTest {
    private val formatters: PresentationFormatters = DefaultPresentationFormatters

    private fun summary(
        species: String = "Human",
        type: String? = null,
        status: CharacterStatus = CharacterStatus.Alive,
    ) = CharacterSummary(
        id = CharacterId("1"),
        name = "Rick Sanchez of Dimension C-137, the very long-named",
        status = status,
        species = species,
        type = type,
        gender = CharacterGender.Male,
        lastKnownLocation = LocationSummary(id = null, name = "Citadel of Ricks"),
        imageUrl = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
    )

    @Test
    fun `TEST-UNIT-001 given_each_status_when_labelled_then_unknown_and_unsupported_share_the_unknown_key`() {
        assertEquals(CopyKeys.STATUS_ALIVE, formatters.statusKey(CharacterStatus.Alive))
        assertEquals(CopyKeys.STATUS_DEAD, formatters.statusKey(CharacterStatus.Dead))
        assertEquals(formatters.unknownKey(), formatters.statusKey(CharacterStatus.Unknown), "TEST-UNIT-001: AC-REQ-FUNC-002-2")
        assertEquals(
            formatters.unknownKey(),
            formatters.statusKey(CharacterStatus.Unsupported("Time-Traveller")),
            "TEST-UNIT-001: an unrecognised status never renders raw (AC-REQ-NFR-004-2)",
        )
        assertEquals(CopyKeys.VALUE_UNKNOWN, formatters.unknownKey())
    }

    @Test
    fun `TEST-UNIT-001 given_raw_values_when_displayed_then_absent_blank_and_unknown_resolve_to_the_unknown_key`() {
        listOf(null, "", "   ", "unknown", "Unknown").forEach { raw ->
            assertEquals(DisplayText.Copy(CopyKeys.VALUE_UNKNOWN), formatters.valueText(raw), "TEST-UNIT-001: `$raw` is never shown raw")
        }
        assertEquals(DisplayText.Data("Human"), formatters.valueText("Human"), "TEST-UNIT-001: a known value is data, unchanged")
    }

    @Test
    fun `TEST-UNIT-001 given_a_summary_when_mapped_to_a_card_then_the_four_items_come_from_their_own_fields`() {
        val source = summary(species = "Alien", type = "Parasite", status = CharacterStatus.Dead)

        val card = CharacterCardUi.from(source, formatters)

        assertEquals(source.id, card.id)
        assertEquals(source.name, card.name, "TEST-UNIT-001: the full name, never cut short (UI_SPEC.md 6.2)")
        assertEquals(DisplayText.Data("Alien"), card.species, "TEST-UNIT-001: species, never type")
        assertEquals(CharacterStatus.Dead, card.status)
        assertEquals(CopyKeys.STATUS_DEAD, card.statusLabel)
        assertEquals(source.imageUrl, card.imageUrl, "TEST-UNIT-001: the image URL verbatim; it is the image cache key (IC-016)")
    }

    @Test
    fun `TEST-UNIT-001 given_an_unknown_species_when_mapped_to_a_card_then_it_is_the_unknown_key_not_the_type`() {
        val card = CharacterCardUi.from(summary(species = "unknown", type = "Robot"), formatters)

        assertEquals(DisplayText.Copy(CopyKeys.VALUE_UNKNOWN), card.species, "TEST-UNIT-001: AC-REQ-FUNC-002-2")
    }

    @Test
    fun `TEST-UNIT-002 given_an_origin_when_its_dimension_is_derived_then_the_enriched_value_wins_then_the_designation_then_nothing`() {
        val enriched = LocationSummary(id = null, name = "Earth (C-137)", dimension = "Dimension C-137")
        val named = LocationSummary(id = null, name = "Earth (Replacement Dimension)")
        val plain = LocationSummary(id = null, name = "Citadel of Ricks")
        val unknown = LocationSummary(id = null, name = "unknown", dimension = "unknown")

        assertEquals("Dimension C-137", formatters.dimensionText(enriched, enrichRequested = true))
        assertEquals(
            "C-137",
            formatters.dimensionText(enriched, enrichRequested = false),
            "TEST-UNIT-002: the designation without enrichment",
        )
        assertEquals("Replacement Dimension", formatters.dimensionText(named, enrichRequested = true))
        assertNull(formatters.dimensionText(plain, enrichRequested = true), "TEST-UNIT-002: nothing to derive hides the tile")
        assertNull(formatters.dimensionText(unknown, enrichRequested = true), "TEST-UNIT-002: an unknown value is not a dimension")
    }

    @Test
    fun `TEST-UNIT-002 given_episode_summaries_when_first_seen_is_derived_then_it_is_the_first_name_and_code_or_hidden`() {
        val pilot = EpisodeSummary(EpisodeId("1"), name = "Pilot", code = "S01E01", airDate = "December 2, 2013")
        val second = EpisodeSummary(EpisodeId("2"), name = "Lawnmower Dog", code = "S01E02", airDate = "December 9, 2013")

        assertEquals("Pilot · S01E01", formatters.firstSeenText(listOf(pilot, second)))
        assertNull(formatters.firstSeenText(null), "TEST-UNIT-002: no enrichment requested hides the row (AC-REQ-FUNC-023-2)")
        assertNull(formatters.firstSeenText(emptyList()), "TEST-UNIT-002: no appearance is not invented")
    }
}
