package io.github.davidru85.multiverse.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.davidru85.multiverse.core.diagnostics.DiagnosticsRecorder
import org.koin.android.ext.android.inject

/**
 * The debug-only panel host (`TASK-044`, `OBSERVABILITY.md` §5): an activity that exists in the
 * debug variant alone, declared by `src/debug/AndroidManifest.xml`. It is not a launcher entry — the
 * app's one icon opens the shell — and a debug build reaches it through that icon's long-press
 * shortcut (`TASK-117`).
 *
 * It reads the one [DiagnosticsRecorder] the debug shell module registers and hands it to the panel.
 * There is no release counterpart: `TEST-UNIT-033` proves the class and this manifest entry are
 * absent from the release artifact.
 */
public class DiagnosticsActivity : ComponentActivity() {
    private val recorder: DiagnosticsRecorder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DiagnosticsPanel(recorder) }
    }
}
