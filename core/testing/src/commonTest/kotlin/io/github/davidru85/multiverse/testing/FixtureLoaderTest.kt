package io.github.davidru85.multiverse.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The harness's own contract (`TASK-024`).
 *
 * These tests pin the seam a consuming suite relies on, not the fixtures' content: a fixture that
 * changes shape is the consuming test's business, while "the loader returns the committed bytes and
 * refuses an unknown name" is this module's. `TESTING.md` §13.2 asks for the `TEST-UNIT-###` id in
 * every failure line, which the assertions below carry in their messages.
 */
class FixtureLoaderTest {
    @Test
    fun `every committed fixture is loadable and matches its sidecar`() {
        val names = FixtureLoader.names()
        assertTrue(names.isNotEmpty(), "TEST-UNIT-024: no fixture was embedded; check generateFixtureSources")
        names.forEach { name ->
            val meta = FixtureCatalog.meta(name)
            assertEquals(name, meta.fixture, "TEST-UNIT-024: sidecar of `$name` names a different fixture")
            assertTrue(meta.capturedAt.endsWith("Z"), "TEST-UNIT-024: `$name` sidecar has no ISO-8601 capture date")
            assertEquals(
                meta.bytes,
                FixtureLoader.bytes(name).size,
                "TEST-UNIT-024: `$name` byte count disagrees with its sidecar — the fixture and sidecar drifted",
            )
            assertTrue(meta.status in 100..599, "TEST-UNIT-024: `$name` sidecar has an impossible status")
        }
    }

    @Test
    fun `an unknown fixture fails loudly rather than serving an empty body`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                FixtureLoader.text("this-fixture-does-not-exist.json")
            }
        assertTrue(
            failure.message!!.contains("this-fixture-does-not-exist.json"),
            "TEST-UNIT-024: the failure must name the missing fixture: ${failure.message}",
        )
    }

    @Test
    fun `the loader preserves the committed bytes exactly`() {
        // The generated source escapes the body; a fixture containing quotes, backslashes or a `$`
        // must survive the round trip, which is what makes the embedded copy trustworthy.
        val name = "character-page-01.json"
        val text = FixtureLoader.text(name)
        assertTrue(text.startsWith("{"), "TEST-UNIT-024: `$name` must round-trip as JSON: ${text.take(40)}")
        assertTrue(text.contains("\"info\""), "TEST-UNIT-024: `$name` lost its `info` object in the round trip")
        assertTrue(text.contains("Rick"), "TEST-UNIT-024: `$name` lost its captured content")
    }

    @Test
    fun `a derived fixture records its provenance in the sidecar`() {
        val derived =
            FixtureLoader.names().filter { name ->
                FixtureLoader.text("$name.meta.json").contains("\"kind\": \"derived\"")
            }
        assertTrue(
            derived.isNotEmpty(),
            "TEST-UNIT-024: the synthesised fixtures must record that they are derived, not captured",
        )
    }
}

/** The time and dispatcher helpers (`TESTING.md` §5, `REQ-REL-004`). */
class TestTimeTest {
    @Test
    fun `the fake clock only moves when a test says so`() {
        val clock = MutableFakeClock(1_000L)
        assertEquals(1_000L, clock.nowMillis(), "TEST-UNIT-024: a fresh fake clock must not read the wall clock")
        assertEquals(1_500L, clock.advanceBy(500L).nowMillis(), "TEST-UNIT-024: advanceBy must be observable")
        assertEquals(2_000L, clock.setTo(2_000L).nowMillis(), "TEST-UNIT-024: setTo must move forward")
    }

    @Test
    fun `the fake clock refuses to move backwards`() {
        val clock = MutableFakeClock(1_000L)
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-024") { clock.advanceBy(-1L) }
        assertFailsWith<IllegalArgumentException>("TEST-UNIT-024") { clock.setTo(999L) }
    }

    @Test
    fun `a suspending body waits in virtual time rather than real time`() =
        TestTime.run { _ ->
            val started = testScheduler.currentTime
            kotlinx.coroutines.delay(3_000L)
            val elapsed = testScheduler.currentTime - started
            assertEquals(
                3_000L,
                elapsed,
                "TEST-UNIT-024: a 3 s debounce must cost 3 s of virtual time and no wall-clock wait",
            )
        }
}
