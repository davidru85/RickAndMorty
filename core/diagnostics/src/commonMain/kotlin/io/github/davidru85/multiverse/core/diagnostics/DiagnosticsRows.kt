package io.github.davidru85.multiverse.core.diagnostics

/**
 * One row of the debug diagnostics surface (`OBSERVABILITY.md` §5): a label and the rendered value.
 *
 * The labels and values are developer copy for a debug-only surface, never product copy, so they are
 * not part of the `CopyKeys` set and are not translated — as the Android panel's own debug strings.
 */
public data class DiagnosticsRow(
    public val label: String,
    public val value: String,
)

/**
 * The snapshot as the surface's rows, in the order of `OBSERVABILITY.md` §5 (`DEC-147`): rendered here
 * once, so the Android panel and the iOS sheet cannot show the same snapshot differently
 * (`CONTRACTS.md` §7 R2). A value nothing in this build produces names its deliverer.
 */
public fun DiagnosticsSnapshot.rows(): List<DiagnosticsRow> {
    val rows = mutableListOf<DiagnosticsRow>()
    rows += DiagnosticsRow("last failure", lastFailure.render())
    rows += DiagnosticsRow("data source", currentSource.render())
    rows += DiagnosticsRow("last request", lastRequest.render())
    rows += DiagnosticsRow("pager", pager.render())
    rows += DiagnosticsRow("stale indicator", stale.render())
    rows += DiagnosticsRow("cache", cacheState.render())
    rows += DiagnosticsRow("favourites", favoritesCount.render())
    rows += DiagnosticsRow("append in flight", APPEND_IN_FLIGHT)
    rows += DiagnosticsRow("build envelope", buildEnvelope.render())
    return rows
}

/** The surface's title and the line that says it is read-only (`OBSERVABILITY.md` §5). */
public object DiagnosticsCopy {
    public const val TITLE: String = "Diagnostics"
    public const val READ_ONLY: String = "Read-only: this panel triggers no request and exports nothing."
}

/**
 * "Append in flight" is carried by no log event: the screen pager's state is where it becomes
 * observable, so the surface names that deliverer rather than guessing a value (`DEC-099`).
 */
private const val APPEND_IN_FLIGHT: String = "unavailable — delivered by the screen pager (TASK-001)"

/** Renders any diagnostic value, so the surface never shows a raw `null`. */
private fun Diagnostic<*>.render(): String =
    when (this) {
        is Diagnostic.Observed -> value.toString()
        is Diagnostic.NotYetObserved -> "not yet observed"
        is Diagnostic.Unavailable -> "unavailable — delivered by $deliveredBy"
    }
