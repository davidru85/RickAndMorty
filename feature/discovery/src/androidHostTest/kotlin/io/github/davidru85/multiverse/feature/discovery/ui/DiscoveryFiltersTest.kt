package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-030` — the filter chips during the initial load (`UI_SPEC.md` §8 "Initial loading: filters
 * disabled", Figma `20:1845`, `TASK-113`).
 *
 * The chips stayed enabled while the first page loaded, so a tap reset a load that had not answered yet.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(loadState: LoadState): MutableList<CharacterListIntent> {
        val sent = mutableListOf<CharacterListIntent>()
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(state = CharacterListUiState(loadState = loadState), onIntent = { sent += it })
            }
        }
        compose.waitForIdle()
        return sent
    }

    @Test
    fun `TEST-UI-030 given_the_initial_load_when_the_chips_render_then_they_are_disabled_and_a_tap_sends_nothing`() {
        val sent = render(LoadState.Loading)

        listOf("All", "Alive", "Dead", "Unknown").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        compose.onNodeWithText("Alive").performClick()
        compose.runOnIdle {
            assertEquals(
                "TEST-UI-030: no status change while the first page loads",
                emptyList<CharacterListIntent>(),
                sent.filterIsInstance<CharacterListIntent.StatusSelected>(),
            )
        }
    }

    @Test
    fun `TEST-UI-030 given_content_when_the_chips_render_then_they_are_enabled`() {
        render(LoadState.Empty)

        listOf("All", "Alive", "Dead", "Unknown").forEach { compose.onNodeWithText(it).assertIsEnabled() }
    }
}
