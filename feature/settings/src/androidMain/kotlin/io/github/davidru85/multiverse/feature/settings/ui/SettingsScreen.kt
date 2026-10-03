package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsIntent
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState

/**
 * The Settings screen (`UI_SPEC.md` §6.5, Android column; `REQ-FUNC-033`…`REQ-FUNC-035`).
 *
 * STUB behind the declared surface, so the UI cases (`TEST-UI-017`) are red until the implementation
 * commit lands.
 */
@Composable
public fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // not yet implemented
}
