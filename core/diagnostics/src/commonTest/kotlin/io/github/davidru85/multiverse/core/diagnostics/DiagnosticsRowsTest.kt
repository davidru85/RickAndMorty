package io.github.davidru85.multiverse.core.diagnostics

import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-103` — the diagnostics surface's rows, rendered once for both platforms (`REQ-OBS-002`,
 * `OBSERVABILITY.md` §5, `DEC-147`, `TASK-119`).
 *
 * The Android panel rendered each snapshot item itself; the iOS sheet needs the same rows, and a second
 * rendering in Swift would be the duplicate `CONTRACTS.md` §7 R2 forbids. The rows are the snapshot's
 * items in the order of §5, each value an enum name, a number or the deliverer of a value this build
 * cannot know — never a raw `null` or an invented zero.
 */
class DiagnosticsRowsTest {
    @Test
    fun `TEST-UNIT-103 given_a_fresh_snapshot_when_rendered_then_every_item_is_a_row_in_order`() {
        val rows = DiagnosticsSnapshot().rows()

        assertEquals(
            listOf(
                "last failure",
                "data source",
                "last request",
                "pager",
                "stale indicator",
                "cache",
                "favourites",
                "append in flight",
                "build envelope",
            ),
            rows.map { it.label },
        )
        assertEquals("not yet observed", rows.first { it.label == "last failure" }.value)
        assertEquals(
            "unavailable — delivered by the response cache (LOG-005…LOG-009, TASK-020)",
            rows.first { it.label == "cache" }.value,
        )
    }

    @Test
    fun `TEST-UNIT-103 given_observed_values_when_rendered_then_they_show_as_their_names`() {
        val snapshot =
            DiagnosticsSnapshot(
                lastFailure = Diagnostic.Observed(FailureDiagnostic(ErrorClass.OFFLINE, Diagnostic.NotYetObserved)),
                currentSource = Diagnostic.Observed(DataSource.DISK_CACHE),
            )

        val rows = snapshot.rows().associate { it.label to it.value }

        assertEquals("DISK_CACHE", rows["data source"])
        assertEquals(true, rows["last failure"]?.contains("OFFLINE"), "the failure shows its error class: ${rows["last failure"]}")
    }
}
