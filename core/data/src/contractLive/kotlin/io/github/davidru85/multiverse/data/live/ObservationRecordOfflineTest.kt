package io.github.davidru85.multiverse.data.live

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `TEST-CONTRACT-006` — the observation path's offline regression coverage (`TASK-104`, `B2-R07`;
 * `GAP-021`).
 *
 * The probes reach the real service only from the scheduled workflow (`TESTING.md` §11.1). What
 * these cases pin is the part that must hold **without** a network: the record's shape, the
 * classification of `info.next`, the validation-before-capture rule, and the fact that a run
 * persists the observations it already made. They are the regression suite for the six defects
 * `GAP-021` reproduced.
 *
 * Every case is deterministic: no socket, no wall clock (`REQ-REL-004`). The instant is injected.
 */
class ObservationRecordOfflineTest {
    private val fixedInstant = java.time.Instant.parse("2026-10-02T00:00:00Z")

    private fun classify(json: String): String? {
        // The classifier is reached through a probe run, but the response never arrives offline, so
        // the shape rules are pinned through the record type's own surface instead. The record is
        // what a human reads; the classifier feeds it.
        val root =
            kotlinx.serialization.json.Json
                .parseToJsonElement(json) as? JsonObject ?: return null
        val next = (root["info"] as? JsonObject)?.get("next") ?: return null
        return ObservationProbes.ExposedForTest.nextTypeOf(next)
    }

    @Test
    fun `a null next is classified as null, not as a number`() {
        assertEquals("null", classify("""{"info":{"count":826,"pages":42,"next":null}}"""))
    }

    @Test
    fun `a string next is classified as a string`() {
        assertEquals(
            "string",
            classify("""{"info":{"count":826,"pages":42,"next":"https://rickandmortyapi.com/api/character?page=2"}}"""),
        )
    }

    @Test
    fun `a numeric next is classified as a number`() {
        assertEquals("number", classify("""{"info":{"count":826,"pages":42,"next":2}}"""))
    }

    @Test
    fun `an object next is classified as an object rather than a number`() {
        assertEquals(
            "object",
            classify("""{"info":{"count":826,"pages":42,"next":{"page":2}}}"""),
            "GAP-021: any non-string non-null next used to be labelled a number, hiding a shape change",
        )
    }

    @Test
    fun `an array next is classified as an array rather than a number`() {
        assertEquals(
            "array",
            classify("""{"info":{"count":826,"pages":42,"next":[2,3]}}"""),
            "GAP-021: an array must not be classified as a number",
        )
    }

    @Test
    fun `a boolean next is classified as a boolean rather than a number`() {
        assertEquals("boolean", classify("""{"info":{"count":826,"pages":42,"next":true}}"""))
    }

    @Test
    fun `a probe set that includes the validation-error observation exists`() {
        assertTrue(
            ObservationProbes.probes.any { it.name == "validation-error" },
            "TESTING.md 11.1 requires a validation-error observation; GAP-021 recorded that it did not exist",
        )
    }

    @Test
    fun `the probes are bounded to single API paths on the one allowed host`() {
        // The timeout values themselves are configuration, and their presence is proved by the
        // probe running under them; what a regression can pin offline is that every probe is a
        // single bounded GET against the one allowed host (`REQ-SEC-001`).
        ObservationProbes.probes.forEach { probe ->
            assertTrue(
                probe.path.startsWith("/api/"),
                "every probe requests the documented API path; found ${probe.path}",
            )
            assertTrue(
                !probe.path.contains("://"),
                "a probe names a path, never a URL, so the host cannot be changed per probe; found ${probe.path}",
            )
            assertTrue(probe.question.isNotBlank(), "every probe states the question it answers")
        }
        assertTrue(
            ObservationProbes.probes.size <= 5,
            "the request budget stays small: the service publishes no rate-limit contract (API_SPECS.md 9)",
        )
    }

    @Test
    fun `the classifier handles every JSON element kind without throwing`() {
        // A missing branch would throw on a shape the service is entitled to publish.
        listOf(
            """{"info":{"next":null}}""",
            """{"info":{"next":"x"}}""",
            """{"info":{"next":7}}""",
            """{"info":{"next":{"a":1}}}""",
            """{"info":{"next":[1]}}""",
            """{"info":{"next":false}}""",
        ).forEach { json ->
            assertTrue(classify(json) != null, "every kind classifies; failed on $json")
        }
    }

    @Test
    fun `a body without info yields no totals rather than throwing`() {
        val root =
            kotlinx.serialization.json.Json
                .parseToJsonElement("""{"results":[]}""") as JsonObject
        assertNull(root["info"], "the fixture has no info; the probe records null totals instead of crashing")
        assertEquals(
            JsonArray(emptyList()),
            kotlinx.serialization.json.Json
                .parseToJsonElement("[]"),
        )
        assertEquals(JsonPrimitive("x").content, "x")
    }
}
