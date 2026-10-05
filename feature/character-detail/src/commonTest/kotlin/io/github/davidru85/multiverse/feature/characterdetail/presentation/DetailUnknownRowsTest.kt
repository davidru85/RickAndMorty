package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.feature.characterdetail.details
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-079` — an unknown origin or location keeps its row and reads "Unknown" (`IC-019`,
 * `DEC-131`, `AC-REQ-FUNC-002-2`, `CONF-88`, `TASK-112`).
 *
 * The reducer used to drop a row whose value was `unknown`, so a character with an unknown origin
 * showed no Origin row at all, where the acceptance criterion asks for "Unknown". Only `FirstSeenIn`
 * is availability-filtered: it depends on an enrichment, not on a value the API reports as unknown.
 */
class DetailUnknownRowsTest {
    private fun render(
        origin: String,
        location: String,
    ): CharacterDetailUiState =
        CharacterDetailReducer.render(
            header = null,
            result =
                DataResult.Success(
                    details(
                        origin = LocationSummary(id = null, name = origin),
                        lastKnownLocation = LocationSummary(id = null, name = location),
                        episodeIds = listOf("1"),
                        episodeSummaries = null,
                    ),
                    DataSource.NETWORK,
                    isStale = false,
                ),
            isFavorite = false,
            enrichRequested = false,
            formatters = DefaultPresentationFormatters,
        )

    @Test
    fun `TEST-UNIT-079 given_an_unknown_origin_and_location_when_the_detail_renders_then_both_rows_read_Unknown`() {
        val state = render(origin = "unknown", location = "unknown")

        assertEquals(
            listOf(InfoRowKind.Origin, InfoRowKind.LastKnownLocation),
            state.info.map { it.kind },
            "TEST-UNIT-079: the rows stay, in their fixed order (AC-REQ-FUNC-002-2)",
        )
        assertEquals(
            listOf<Any>(DisplayText.Copy(CopyKeys.VALUE_UNKNOWN), DisplayText.Copy(CopyKeys.VALUE_UNKNOWN)),
            state.info.map { it.value },
            "TEST-UNIT-079: and read the one Unknown presentation, resolved by the platform (IC-017)",
        )
    }

    @Test
    fun `TEST-UNIT-079 given_a_known_origin_and_a_blank_location_when_the_detail_renders_then_the_name_is_data_and_the_blank_is_Unknown`() {
        val state = render(origin = "Earth (C-137)", location = " ")

        assertEquals(
            listOf<Any>(DisplayText.Data("Earth (C-137)"), DisplayText.Copy(CopyKeys.VALUE_UNKNOWN)),
            state.info.map { it.value },
            "TEST-UNIT-079: a location name is shown unchanged; a blank one is Unknown, not an empty row",
        )
    }
}
