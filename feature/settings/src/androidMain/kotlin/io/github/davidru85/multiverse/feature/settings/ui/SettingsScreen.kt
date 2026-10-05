package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.davidru85.multiverse.core.designsystem.components.ScreenTitle
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.feature.settings.R
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
            ScreenTitle(text = CopyResolver.copy(CopyKeys.NAV_SETTINGS.value))

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
        header = CopyResolver.copy(CopyKeys.SETTINGS_SECTION_PREFERENCES.value),
        content = {
            val soundTitle = CopyResolver.copy(CopyKeys.SETTINGS_SOUND_TITLE.value)
            ListItem(
                colors = TransparentRow,
                leadingContent = { RowGlyph(R.drawable.ic_volume_up) },
                supportingContent = { Text(CopyResolver.copy(CopyKeys.SETTINGS_SOUND_BODY.value)) },
                trailingContent = {
                    Switch(
                        // The switch carries its row's title as its label, so a screen reader
                        // announces "Sounds" with the on/off state the switch itself exposes.
                        modifier = Modifier.semantics { contentDescription = soundTitle },
                        checked = state.soundsEnabled,
                        onCheckedChange = { enabled -> onIntent(SettingsIntent.SoundsToggled(enabled)) },
                        // A check on the thumb when on (`UI_SPEC.md` §4.1).
                        thumbContent =
                            if (state.soundsEnabled) {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
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
 * The picker is the M3 Expressive connected group in single-select mode: two `ToggleButton`s sharing
 * the row's width with the connected shapes, the selected one the segment whose `remoteProtocol`
 * matches. Each segment is a radio button to a screen reader, because exactly one is ever selected.
 */
@Composable
private fun DataSection(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SettingsSection(
        header = CopyResolver.copy(CopyKeys.SETTINGS_SECTION_DATA.value),
        content = {
            ListItem(
                colors = TransparentRow,
                leadingContent = { RowGlyph(R.drawable.ic_swap_horiz) },
                supportingContent = { Text(CopyResolver.copy(CopyKeys.SETTINGS_DATA_SOURCE_BODY.value)) },
            ) {
                Text(CopyResolver.copy(CopyKeys.SETTINGS_DATA_SOURCE_TITLE.value))
            }
            // The connected single-select picker (`UI_SPEC.md` §4.1): two equal segments, the selected
            // one Secondary / On Secondary, the other Secondary Container / On Secondary Container.
            Row(
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
                ProtocolSegment(
                    label = CopyResolver.copy(CopyKeys.SETTINGS_DATA_REST.value),
                    checked = state.remoteProtocol == RemoteProtocol.Rest,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    onSelect = { onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.Rest)) },
                )
                ProtocolSegment(
                    label = CopyResolver.copy(CopyKeys.SETTINGS_DATA_GRAPHQL.value),
                    checked = state.remoteProtocol == RemoteProtocol.GraphQl,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    onSelect = { onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.GraphQl)) },
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
        header = CopyResolver.copy(CopyKeys.NAV_FAVORITES.value),
        content = {
            Text(
                text = CopyResolver.copy(CopyKeys.SETTINGS_DELETE_EXPLANATION.value),
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
                modifier = Modifier.padding(MultiverseDimensions.spaceL),
            )
            // A filled Medium (56 dp) destructive button with its leading glyph (`UI_SPEC.md` §4.1, §6.5).
            Button(
                onClick = { onIntent(SettingsIntent.DeleteFavoritesRequested) },
                enabled = state.canDeleteFavorites,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MultiverseColors.errorContainer,
                        contentColor = MultiverseColors.onErrorContainer,
                    ),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MultiverseDimensions.spaceL,
                            end = MultiverseDimensions.spaceL,
                            bottom = MultiverseDimensions.spaceL,
                        ).heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
                )
                Spacer(modifier = Modifier.width(MultiverseDimensions.spaceS))
                Text(
                    text = CopyResolver.copy(CopyKeys.SETTINGS_DELETE_ACTION.value),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        },
    )
}

/** One segment of the data-source picker: a connected `ToggleButton` that is a radio button to TalkBack. */
@Composable
private fun RowScope.ProtocolSegment(
    label: String,
    checked: Boolean,
    shapes: ToggleButtonShapes,
    onSelect: () -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onSelect() },
        shapes = shapes,
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MultiverseColors.secondaryContainer,
                contentColor = MultiverseColors.onSecondaryContainer,
                checkedContainerColor = MultiverseColors.secondary,
                checkedContentColor = MultiverseColors.onSecondary,
            ),
        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
    ) {
        Text(label)
    }
}

/** A row's 24 dp leading glyph in On Surface Variant (`UI_SPEC.md` §6.5); decorative, as the title names the row. */
@Composable
private fun RowGlyph(glyph: Int) {
    Icon(painter = painterResource(glyph), contentDescription = null, tint = MultiverseColors.onSurfaceVariant)
}

/** The rows are transparent on their group, so no row paints a darker band over the Surface Container. */
private val TransparentRow: ListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

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
    val title = CopyResolver.copy(CopyKeys.SETTINGS_DELETE_CONFIRM_TITLE.value)
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
                text = CopyResolver.copy(CopyKeys.SETTINGS_DELETE_CONFIRM_MESSAGE.value),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(SettingsIntent.DeleteFavoritesConfirmed) },
                colors = ButtonDefaults.textButtonColors(contentColor = MultiverseColors.error),
            ) {
                Text(CopyResolver.copy(CopyKeys.ACTION_DELETE.value))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SettingsIntent.DeleteFavoritesDismissed) }) {
                Text(CopyResolver.copy(CopyKeys.ACTION_CANCEL.value))
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
        // Title Small in Primary, 16 dp in, where the group's content starts (Figma `101:568`).
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MultiverseColors.primary,
            modifier = Modifier.padding(start = MultiverseDimensions.spaceL),
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
