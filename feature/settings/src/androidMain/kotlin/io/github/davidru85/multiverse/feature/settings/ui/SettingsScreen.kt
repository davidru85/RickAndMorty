package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsIntent
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState

/**
 * The Settings screen (`UI_SPEC.md` §6.5, the Android column; `REQ-FUNC-033`…`REQ-FUNC-035`) — the
 * third of the three sections the screen holds, in the specified order: Sounds, Data source and
 * Delete favorites.
 *
 * Every string comes from [CopyResolver], so the shipped copy set is the single source and a missing
 * key fails loudly rather than rendering a literal; every colour comes from [MultiverseColors], so
 * the palette is the one token export. The screen is **stateless**: it renders the `IC-023` state it
 * is handed and reports every interaction through [onIntent], which is what keeps the Android
 * `SettingsViewModel` and the iOS `ObservableObject` from diverging (`CONTRACTS.md` §7).
 *
 * Accessibility (`UI_SPEC.md` §9): the Sounds switch is labelled by its row title and announces its
 * on/off state, each data-source segment announces whether it is the selected option, and the
 * confirmation takes focus when it opens and hands it back to the action when it closes.
 */
@Composable
public fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MultiverseColors.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(MultiverseDimensions.spaceL),
            verticalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceXl),
        ) {
            Text(
                text = CopyResolver.copy("nav_settings"),
                style = MaterialTheme.typography.displaySmall,
                color = MultiverseColors.onSurface,
            )

            PreferencesSection(state = state, onIntent = onIntent)
            DataSection(state = state, onIntent = onIntent)
            FavoritesSection(state = state, onIntent = onIntent)
        }
    }

    // The confirmation is drawn only while it is open *and* favorites exist: a state that claims both
    // otherwise is reconciled here rather than rendered, so the dialog can never offer a Delete that
    // has nothing to delete (`AC-REQ-FUNC-035-3`).
    if (state.isConfirmingDelete && state.canDeleteFavorites) {
        DeleteConfirmation(onIntent = onIntent)
    }
}

/** Preferences: the two-line Sounds row with the switch as its trailing content (`UI_SPEC.md` §6.5). */
@Composable
private fun PreferencesSection(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SettingsSection(
        header = CopyResolver.copy("settings_section_preferences"),
        content = {
            val soundTitle = CopyResolver.copy("settings_sound_title")
            ListItem(
                supportingContent = { Text(CopyResolver.copy("settings_sound_body")) },
                trailingContent = {
                    Switch(
                        // The switch carries its row's title as its label, so a screen reader
                        // announces "Sounds" with the on/off state the switch itself exposes.
                        modifier = Modifier.semantics { contentDescription = soundTitle },
                        checked = state.soundsEnabled,
                        onCheckedChange = { enabled -> onIntent(SettingsIntent.SoundsToggled(enabled)) },
                    )
                },
            ) {
                Text(soundTitle)
            }
        },
    )
}

/**
 * Data: the "Data source" row with the two-segment connected picker below it, inside the same group
 * (`UI_SPEC.md` §6.5).
 *
 * The group is the M3 Expressive connected `ButtonGroup` in single-select mode: each segment is a
 * `ToggleButton` sharing the connected shapes, and the selected segment is the one whose
 * `remoteProtocol` matches. The overflow affordance is empty because the two segments are weighted to
 * share the row's width by construction, so the group has nothing to overflow into.
 */
@Composable
private fun DataSection(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SettingsSection(
        header = CopyResolver.copy("settings_section_data"),
        content = {
            ListItem { Text(CopyResolver.copy("settings_data_source_title")) }
            val restLabel = CopyResolver.copy("settings_data_rest")
            val graphQlLabel = CopyResolver.copy("settings_data_graphql")
            ButtonGroup(
                overflowIndicator = { },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MultiverseDimensions.spaceL,
                            end = MultiverseDimensions.spaceL,
                            bottom = MultiverseDimensions.spaceL,
                        ),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                toggleableItem(
                    checked = state.remoteProtocol == RemoteProtocol.Rest,
                    label = restLabel,
                    onCheckedChange = { onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.Rest)) },
                    weight = 1f,
                )
                toggleableItem(
                    checked = state.remoteProtocol == RemoteProtocol.GraphQl,
                    label = graphQlLabel,
                    onCheckedChange = { onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.GraphQl)) },
                    weight = 1f,
                )
            }
        },
    )
}

/**
 * Favorites: the explanation above the full-width destructive action (`UI_SPEC.md` §6.5).
 *
 * The action is disabled while there are no favorites, and the M3 disabled colours follow from the
 * button's own colours (`AC-REQ-FUNC-035-3`). Activating it dispatches the request; the confirmation
 * itself is drawn by [SettingsScreen].
 */
@Composable
private fun FavoritesSection(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SettingsSection(
        header = CopyResolver.copy("nav_favorites"),
        content = {
            Text(
                text = CopyResolver.copy("settings_delete_explanation"),
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
                modifier = Modifier.padding(MultiverseDimensions.spaceL),
            )
            Button(
                onClick = { onIntent(SettingsIntent.DeleteFavoritesRequested) },
                enabled = state.canDeleteFavorites,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MultiverseColors.errorContainer,
                        contentColor = MultiverseColors.onErrorContainer,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MultiverseDimensions.spaceL,
                            end = MultiverseDimensions.spaceL,
                            bottom = MultiverseDimensions.spaceL,
                        ),
            ) {
                Text(CopyResolver.copy("settings_delete_action"))
            }
        },
    )
}

/**
 * The delete confirmation (`UI_SPEC.md` §6.5, `AC-REQ-FUNC-035-1`).
 *
 * Its two outcomes are the only ones the contract states: "Delete" dispatches the confirmation and
 * "Cancel" — as a tap outside or the system dismiss gesture does, which `onDismissRequest` reports —
 * dispatches the dismissal, which invokes nothing. The title takes focus when the dialog opens, so
 * the confirmation is announced rather than the screen behind it.
 */
@Composable
private fun DeleteConfirmation(onIntent: (SettingsIntent) -> Unit) {
    val titleFocus = remember { FocusRequester() }
    val title = CopyResolver.copy("settings_delete_confirm_title")
    AlertDialog(
        onDismissRequest = { onIntent(SettingsIntent.DeleteFavoritesDismissed) },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.focusRequester(titleFocus).focusable(),
            )
        },
        text = {
            Text(
                text = CopyResolver.copy("settings_delete_confirm_message"),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(SettingsIntent.DeleteFavoritesConfirmed) },
                colors = ButtonDefaults.textButtonColors(contentColor = MultiverseColors.error),
            ) {
                Text(CopyResolver.copy("action_delete"))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SettingsIntent.DeleteFavoritesDismissed) }) {
                Text(CopyResolver.copy("action_cancel"))
            }
        },
    )
    LaunchedEffect(title) { titleFocus.requestFocus() }
}

/**
 * One settings section (`UI_SPEC.md` §4.1): a Primary section header, then a Surface Container group
 * with the extra-large corner holding the rows.
 *
 * It is private on purpose — the specification names three sections and a caller that composed a
 * fourth would be a screen change, not a usage.
 */
@Composable
private fun SettingsSection(
    header: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MultiverseDimensions.spaceS),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MultiverseColors.primary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape =
                androidx.compose.foundation.shape.RoundedCornerShape(
                    MultiverseDimensions.cornerExtraLarge,
                ),
            color = MultiverseColors.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}
