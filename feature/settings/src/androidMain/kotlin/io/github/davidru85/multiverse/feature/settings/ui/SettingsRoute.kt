package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * The Settings destination's route (`TASK-074`, `TASK-076`, `UI_SPEC.md` §6.5).
 *
 * It resolves its own ViewModel from the graph, so the shell composes a destination without naming a
 * use case or a store — the shape `IC-023` and `DESIGN.md` §5 give every feature screen. The screen
 * below it renders immutable state and emits intents; nothing here reaches the repository.
 */
@Composable
public fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}
