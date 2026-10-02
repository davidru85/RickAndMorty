package io.github.davidru85.multiverse.core.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.LocationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** `IC-001`: an id is the canonical server string, compared without any normalisation. */
class IdentityTest {
    @Test
    fun `TEST-UNIT-052 given_ids_that_differ_only_by_padding_case_or_leading_zeros_when_compared_then_they_stay_distinct`() {
        assertEquals(CharacterId("1"), CharacterId("1"), "TEST-UNIT-052: equal strings are one identity")
        assertNotEquals(CharacterId("1"), CharacterId(" 1"), "TEST-UNIT-052: no trimming")
        assertNotEquals(CharacterId("01"), CharacterId("1"), "TEST-UNIT-052: no numeric coercion")
        assertNotEquals(LocationId("abc"), LocationId("ABC"), "TEST-UNIT-052: no case folding")
        assertEquals("21", EpisodeId("21").value, "TEST-UNIT-052: the server string is kept verbatim")
    }
}
