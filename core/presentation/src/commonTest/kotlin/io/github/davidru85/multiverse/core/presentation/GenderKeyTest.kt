package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-080`, formatter half — the gender label (`IC-017`, `DEC-131`, `REQ-FUNC-002`,
 * `AC-REQ-FUNC-002-2`, `CONF-87`, `TASK-112`).
 *
 * `REQ-FUNC-002` lists gender among the detail fields, and no formatter produced one. Each supported
 * gender is a registered copy key, so the label is localised like the status; an unknown or
 * unrecognised gender is the one "Unknown" presentation, never the raw API value.
 */
class GenderKeyTest {
    private val formatters: PresentationFormatters = DefaultPresentationFormatters

    @Test
    fun `TEST-UNIT-080 given_each_supported_gender_when_labelled_then_it_has_its_own_registered_key`() {
        val keys =
            listOf(CharacterGender.Female, CharacterGender.Male, CharacterGender.Genderless)
                .map(formatters::genderKey)

        assertEquals(
            listOf("gender_female", "gender_male", "gender_genderless"),
            keys.map { it.value },
            "TEST-UNIT-080: Female, Male and Genderless each read their own copy",
        )
        assertTrue(CopyKeys.all.containsAll(keys), "TEST-UNIT-080: and every key is in the canonical list the parity test runs")
    }

    @Test
    fun `TEST-UNIT-080 given_an_unknown_or_unrecognised_gender_when_labelled_then_it_is_the_unknown_key`() {
        assertEquals(formatters.unknownKey(), formatters.genderKey(CharacterGender.Unknown), "TEST-UNIT-080: AC-REQ-FUNC-002-2")
        assertEquals(
            formatters.unknownKey(),
            formatters.genderKey(CharacterGender.Unsupported("Fluid")),
            "TEST-UNIT-080: an unrecognised gender never renders raw (AC-REQ-NFR-004-2)",
        )
    }
}
