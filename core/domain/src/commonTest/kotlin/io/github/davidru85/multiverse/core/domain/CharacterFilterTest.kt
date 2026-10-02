package io.github.davidru85.multiverse.core.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** `IC-010`: the filter is a value with four status options and keeps the raw query text. */
class CharacterFilterTest {
    @Test
    fun `TEST-UNIT-052 given_no_arguments_when_a_filter_is_built_then_it_is_unfiltered_with_status_all`() {
        val filter = CharacterFilter()
        assertEquals("", filter.query, "TEST-UNIT-052: a blank query is the default")
        assertEquals(StatusFilter.All, filter.status, "TEST-UNIT-052: All is the default status")
        assertEquals(
            listOf(StatusFilter.All, StatusFilter.Alive, StatusFilter.Dead, StatusFilter.Unknown),
            StatusFilter.entries,
            "TEST-UNIT-052: exactly four status options (AC-REQ-FUNC-004-2)",
        )
    }

    @Test
    fun `TEST-UNIT-052 given_user_text_when_a_filter_is_built_then_the_raw_text_is_kept_and_equality_is_by_value`() {
        val padded = CharacterFilter(query = "  Rick ", status = StatusFilter.Alive)
        assertEquals("  Rick ", padded.query, "TEST-UNIT-052: trimming is the mapper's job, not the filter's")
        assertEquals(padded, CharacterFilter(query = "  Rick ", status = StatusFilter.Alive), "TEST-UNIT-052")
        assertNotEquals(padded, CharacterFilter(query = "  Rick ", status = StatusFilter.Dead), "TEST-UNIT-052")
    }
}
