package io.github.davidru85.multiverse.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.app.R
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.diagnostics.Diagnostic
import io.github.davidru85.multiverse.core.diagnostics.DiagnosticsRecorder

/**
 * The visible diagnostic panel (`TASK-044`, `OBSERVABILITY.md` §5, `DEC-085`): a read-only view of
 * the [DiagnosticsRecorder]'s snapshot, built from design-system components.
 *
 * It is **read-only**: there is no request trigger, no export, no share and no clear button, so
 * opening it changes nothing. Every value it cannot know names its deliverer rather than showing an
 * invented zero — the recorder's own `Unavailable(deliveredBy)` — and it is compiled into the debug
 * variant only, because the whole file lives in `src/debug` (`R11`, `TEST-UNIT-033`).
 */
@Composable
public fun DiagnosticsPanel(recorder: DiagnosticsRecorder) {
    val snapshot by recorder.snapshot.collectAsState()
    MultiverseTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MultiverseColors.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.debug_panel_title),
                style = MaterialTheme.typography.displaySmall,
                color = MultiverseColors.onSurface,
            )
            Text(
                text = stringResource(R.string.debug_panel_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MultiverseColors.onSurfaceVariant,
            )
            PanelRow("last failure", snapshot.lastFailure.render())
            PanelRow("data source", snapshot.currentSource.render())
            PanelRow("last request", snapshot.lastRequest.render())
            PanelRow("pager", snapshot.pager.render())
            PanelRow("stale indicator", snapshot.stale.render())
            PanelRow("cache", snapshot.cacheState.render())
            PanelRow("favourites", snapshot.favoritesCount.render())
            PanelRow("append in flight", APPEND_IN_FLIGHT)
            PanelRow("build envelope", snapshot.buildEnvelope.render())
        }
    }
}

/**
 * "Append in flight" is not carried by any log event (`HANDOFF.md` §1.4): the screen pager's state is
 * where it becomes observable, so the panel names that deliverer rather than guessing a value
 * (`DEC-099`, `OBSERVABILITY.md` §5 rule 3).
 */
private const val APPEND_IN_FLIGHT: String = "unavailable — delivered by the screen pager (TASK-001)"

/** One row of the panel: a fixed-width label and the value, in a monospaced face for scanning. */
@Composable
private fun PanelRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MultiverseColors.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MultiverseColors.onSurface,
        )
    }
}

/** Renders any diagnostic value, so the panel never shows a raw `null`. */
private fun Diagnostic<*>.render(): String =
    when (this) {
        is Diagnostic.Observed -> value.toString()
        is Diagnostic.NotYetObserved -> "not yet observed"
        is Diagnostic.Unavailable -> "unavailable — delivered by $deliveredBy"
    }
