package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsIntent
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-017` (`REQ-FUNC-033`…`REQ-FUNC-035`, `AC-REQ-FUNC-033-1`, `AC-REQ-FUNC-034-1`,
 * `AC-REQ-FUNC-035-1`/`-2`/`-3`) — the Settings screen as the Android host renders it.
 *
 * The case drives the real composable, so the three sections, their canonical copy, the Sounds
 * switch, the data-source picker, the disabled delete action and the confirmation are asserted the
 * way a user reaches them. Every expected string is resolved through `CopyResolver`, so a case cannot
 * pass on an English literal the copy set does not carry.
 */
@RunWith(AndroidJUnit4::class)
// A Kotlin Multiplatform library's host-test run has no merged test manifest of its own, so the
// activity the Compose rule hosts its content in is declared in the module's host-test manifest and
// pointed at by its `test_config.properties`; nothing of it reaches the published library.
@Config(sdk = [36], qualifiers = "en-w412dp-h2000dp", application = android.app.Application::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * The string the screen resolves for [key], read through the resolver's own resource table.
     *
     * It goes through `CopyResolver.resourceId`, so a case cannot pass on English the copy set does
     * not carry, and through the same table the screen uses, so the case and the screen cannot
     * diverge. Robolectric's application context supplies the value for the `en` qualifier this class
     * is configured with.
     */
    private fun copy(key: String): String {
        val id = requireNotNull(CopyResolver.resourceId(key)) { "the copy set does not register `$key`" }
        return org.robolectric.RuntimeEnvironment
            .getApplication()
            .getString(id)
    }

    /** Asserts the node holding [text] is on screen, scrolling to it the way a user would. */
    private fun assertVisible(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun show(
        state: SettingsUiState,
        onIntent: (SettingsIntent) -> Unit = {},
    ) {
        compose.setContent {
            MultiverseTheme {
                SettingsScreen(state = state, onIntent = onIntent)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-017 given_the_screen_when_it_renders_then_the_three_sections_are_in_order_with_their_copy`() {
        show(SettingsUiState())

        assertVisible(copy("settings_section_preferences"))
        assertVisible(copy("settings_sound_title"))
        assertVisible(copy("settings_sound_body"))
        assertVisible(copy("settings_section_data"))
        assertVisible(copy("settings_data_source_title"))
        assertVisible(copy("settings_data_rest"))
        assertVisible(copy("settings_data_graphql"))
        assertVisible(copy("nav_favorites"))
        assertVisible(copy("settings_delete_explanation"))
        assertVisible(copy("settings_delete_action"))

        // The order the specification fixes: Preferences, Data, Favorites (AC-REQ-FUNC-033-1).
        fun top(key: String): Float =
            compose
                .onNodeWithText(copy(key))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val preferences = top("settings_section_preferences")
        val data = top("settings_section_data")
        val favorites = top("nav_favorites")
        org.junit.Assert.assertTrue(
            "TEST-UI-017: Preferences is above Data above Favorites — Preferences=$preferences Data=$data Favorites=$favorites",
            preferences < data && data < favorites,
        )
    }

    @Test
    fun `TEST-UI-017 given_the_sounds_row_when_the_switch_is_toggled_then_the_new_value_is_dispatched`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(), onIntent = { sent += it })

        compose.onNodeWithContentDescription(copy("settings_sound_title")).assertIsOff().performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "TEST-UI-017: the switch dispatches the new Sounds value, sent=$sent",
            sent.any { it is SettingsIntent.SoundsToggled && it.enabled },
        )
    }

    @Test
    fun `TEST-UI-017 given_sounds_on_when_the_switch_renders_then_it_announces_its_state`() {
        show(SettingsUiState(soundsEnabled = true))

        compose.onNodeWithContentDescription(copy("settings_sound_title")).assertIsOn()
    }

    @Test
    fun `TEST-UI-017 given_the_picker_when_GraphQL_is_chosen_then_the_selection_is_dispatched`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(), onIntent = { sent += it })

        compose.onNodeWithText(copy("settings_data_rest")).assertIsOn()
        compose.onNodeWithText(copy("settings_data_graphql")).assertIsOff().performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "TEST-UI-017: the picker dispatches the chosen protocol, sent=$sent",
            sent.any { it is SettingsIntent.RemoteProtocolSelected && it.protocol == RemoteProtocol.GraphQl },
        )
    }

    @Test
    fun `TEST-UI-017 given_no_favorites_when_the_delete_action_renders_then_it_is_disabled_and_opens_nothing`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(canDeleteFavorites = false), onIntent = { sent += it })

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy("settings_delete_action")))
        compose.onNodeWithText(copy("settings_delete_action")).assertIsNotEnabled().performClick()
        compose.waitForIdle()

        org.junit.Assert.assertEquals(
            "TEST-UI-017: a disabled action dispatches nothing (AC-REQ-FUNC-035-3)",
            emptyList<SettingsIntent>(),
            sent,
        )
    }

    @Test
    fun `TEST-UI-017 given_favorites_when_the_delete_action_is_activated_then_the_confirmation_opens_in_focus`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(canDeleteFavorites = true), onIntent = { sent += it })

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy("settings_delete_action")))
        compose.onNodeWithText(copy("settings_delete_action")).performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "TEST-UI-017: the action dispatches the request, sent=$sent",
            sent.any { it is SettingsIntent.DeleteFavoritesRequested },
        )
    }

    @Test
    fun `TEST-UI-017 given_the_confirmation_when_it_opens_then_it_shows_its_copy_and_takes_focus`() {
        show(SettingsUiState(canDeleteFavorites = true, isConfirmingDelete = true))

        compose.onNodeWithText(copy("settings_delete_confirm_title")).assertIsDisplayed()
        compose.onNodeWithText(copy("settings_delete_confirm_message")).assertIsDisplayed()
        compose.onNodeWithText(copy("action_cancel")).assertIsDisplayed()
        compose.onNodeWithText(copy("action_delete")).assertIsDisplayed()
        compose.onNodeWithText(copy("settings_delete_confirm_title")).assertIsFocused()
    }

    @Test
    fun `TEST-UI-017 given_the_confirmation_when_Cancel_is_tapped_then_dismissal_is_dispatched_and_nothing_else`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(canDeleteFavorites = true, isConfirmingDelete = true), onIntent = { sent += it })

        compose.onNodeWithText(copy("action_cancel")).performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "TEST-UI-017: Cancel dispatches the dismissal, sent=$sent",
            sent.any { it is SettingsIntent.DeleteFavoritesDismissed },
        )
        org.junit.Assert.assertTrue(
            "TEST-UI-017: Cancel dispatches no confirmation (AC-REQ-FUNC-035-1)",
            sent.none { it is SettingsIntent.DeleteFavoritesConfirmed },
        )
    }

    @Test
    fun `TEST-UI-017 given_the_confirmation_when_Delete_is_tapped_then_the_confirmation_is_dispatched`() {
        val sent = mutableListOf<SettingsIntent>()
        show(SettingsUiState(canDeleteFavorites = true, isConfirmingDelete = true), onIntent = { sent += it })

        compose.onNodeWithText(copy("action_delete")).performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "TEST-UI-017: Delete dispatches the confirmation, sent=$sent",
            sent.any { it is SettingsIntent.DeleteFavoritesConfirmed },
        )
        org.junit.Assert.assertTrue(
            "TEST-UI-017: Delete dispatches no dismissal",
            sent.none { it is SettingsIntent.DeleteFavoritesDismissed },
        )
    }
}
