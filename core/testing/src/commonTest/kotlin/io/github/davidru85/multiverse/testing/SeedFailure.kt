package io.github.davidru85.multiverse.testing

import kotlin.test.Test
import kotlin.test.assertEquals

/** TEMPORARY seed proving the gate blocks a failing test (TASK-025). Reverted immediately after. */
class SeedFailure {
    @Test
    fun `seeded failure`() {
        assertEquals(1, 2, "seeded failure: the gate must block this head")
    }
}
