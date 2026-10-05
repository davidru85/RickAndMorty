package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.feature.characterdetail.details
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `TEST-UNIT-080`, Detail half — the Detail carries the character's gender (`IC-019`, `DEC-131`,
 * `REQ-FUNC-002`, `CONF-87`, `TASK-112`).
 *
 * `REQ-FUNC-002` (Must) lists gender, and Figma shows it in the subtitle on both platforms, but the
 * state had no field for it, so neither platform could render it. The list card does not carry a
 * gender, so the field is known only once the detail answers.
 */
class DetailGenderTest {
    private fun render(result: DataResult<io.github.davidru85.multiverse.core.domain.model.CharacterDetails>?) =
        CharacterDetailReducer.render(
            header = null,
            result = result,
            isFavorite = false,
            enrichRequested = false,
            formatters = DefaultPresentationFormatters,
        )

    @Test
    fun `TEST-UNIT-080 given_a_detail_when_it_renders_then_the_gender_is_its_copy_key`() {
        assertEquals(
            CopyKeys.GENDER_FEMALE,
            render(DataResult.Success(details(gender = CharacterGender.Female), DataSource.NETWORK, isStale = false)).gender,
            "TEST-UNIT-080: the gender is the IC-017 label, resolved by the platform",
        )
        assertEquals(
            CopyKeys.VALUE_UNKNOWN,
            render(DataResult.Success(details(gender = CharacterGender.Unknown), DataSource.NETWORK, isStale = false)).gender,
            "TEST-UNIT-080: an unknown gender reads Unknown (AC-REQ-FUNC-002-2)",
        )
    }

    @Test
    fun `TEST-UNIT-080 given_no_detail_yet_or_a_failure_when_it_renders_then_the_gender_is_not_known`() {
        assertNull(render(null).gender, "TEST-UNIT-080: the list card carries no gender, so none renders before the detail")
        assertNull(render(DataResult.Failure(ApiFailure.Offline, DataSource.NETWORK)).gender, "TEST-UNIT-080: nor after a failed detail")
    }
}
