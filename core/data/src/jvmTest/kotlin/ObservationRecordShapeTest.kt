package io.github.davidru85.multiverse.data.live

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TEST-CONTRACT-006` — the observation record shape (`TASK-027`, `DEC-074`; `TESTING.md` §11.1).
 *
 * The probes themselves run only from the scheduled workflow. What this case pins is what must hold
 * regardless of what the service answers today: the record carries its date and the request it
 * made, and its status is a real one (or a failure, as `0`) rather than an invented guarantee.
 */
class ObservationRecordShapeTest {
    @Test
    fun `TEST-CONTRACT-006 given_a_probe_when_it_runs_then_the_record_carries_its_date_and_request`() {
        val records =
            ObservationProbes.run(
                outputDirectory =
                    kotlin.io.path
                        .createTempDirectory("obs")
                        .toFile(),
                probeSet = listOf(ObservationProbes.Probe("shape", "/api/character", "totals")),
            )
        assertTrue(records.size == 1, "one probe produces one record")
        val record = records.single()
        assertTrue(record.observedAt.isNotBlank(), "every observation carries its date")
        assertTrue(
            record.request.startsWith(ObservationProbes.HOST),
            "the record names the host it asked; observed ${record.request}",
        )
        assertTrue(
            record.status == 0 || record.status in 200..599,
            "the status is a real one or 0 for a transport failure; observed ${record.status}",
        )
    }
}
