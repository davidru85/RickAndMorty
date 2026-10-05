package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-033` — the Settings rows as Figma `101:568` draws them (`UI_SPEC.md` §4.1, §6.5, `CONF-80`,
 * `TASK-113`).
 *
 * The Data source row had no supporting text, although `UI_SPEC.md` §6.5 and Figma both show "How the
 * app fetches characters" (`CONF-80`); the section headers sat flush with their groups instead of 16 dp
 * in; and the destructive action kept M3's default 40 dp height where the spec gives a 56 dp button.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h2000dp", application = android.app.Application::class)
class SettingsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            MultiverseTheme {
                SettingsScreen(state = SettingsUiState(canDeleteFavorites = true), onIntent = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-033 given_the_data_source_row_when_it_renders_then_it_explains_the_choice`() {
        show()

        compose.onNodeWithText("How the app fetches characters").assertExists()
    }

    @Test
    fun `TEST-UI-033 given_a_section_when_it_renders_then_its_header_is_inset_like_its_content`() {
        show()

        val header = compose.onNodeWithText("Favorites").fetchSemanticsNode().boundsInRoot
        val explanation = compose.onNode(hasText("This can't be undone", substring = true)).fetchSemanticsNode().boundsInRoot
        assertEquals("TEST-UI-033: the header starts where the group's content starts (16 dp in)", explanation.left, header.left, 0.5f)
    }

    @Test
    fun `TEST-UI-033 given_the_delete_action_when_it_renders_then_it_is_a_56_dp_button`() {
        show()

        compose.onNode(hasText("Delete favorites") and hasClickAction()).assertHeightIsAtLeast(56.dp)
    }
}
