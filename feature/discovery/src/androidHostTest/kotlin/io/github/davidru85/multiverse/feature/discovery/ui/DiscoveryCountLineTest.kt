package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-042` — the count line keeps its space while the count is unknown (`TASK-129`, `GAP-039`,
 * `AC-REQ-FUNC-001-3`, `UI_SPEC.md` §6.2).
 *
 * A filter change resets the total until the new page answers. The screen removed the line for that
 * time, so the chips and the grid moved up one line and back down. The cases watch the chips — the
 * first row under the line — and what a screen reader can reach.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryCountLineTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(CharacterListUiState(totalCount = 826, loadState = LoadState.Loading))

    private fun chipsTop(): Float =
        compose
            .onNodeWithText("All")
            .getUnclippedBoundsInRoot()
            .top.value

    @Test
    fun `TEST-UI-042 given_a_known_count_when_it_becomes_unknown_then_the_chips_keep_their_place`() {
        compose.setContent { MultiverseTheme { DiscoveryScreen(state = state, onIntent = {}) } }
        compose.waitForIdle()
        val withCount = chipsTop()

        compose.runOnIdle { state = state.copy(totalCount = null) }
        compose.waitForIdle()

        assertEquals("TEST-UI-042: the chips stay where they were while the count is unknown", withCount, chipsTop(), 0.5f)
    }

    @Test
    fun `TEST-UI-042 given_no_count_yet_when_the_header_renders_then_the_chips_sit_where_a_count_puts_them`() {
        state = CharacterListUiState(totalCount = null, loadState = LoadState.Loading)
        compose.setContent { MultiverseTheme { DiscoveryScreen(state = state, onIntent = {}) } }
        compose.waitForIdle()
        val withoutCount = chipsTop()

        compose.runOnIdle { state = state.copy(totalCount = 826) }
        compose.waitForIdle()

        assertEquals("TEST-UI-042: the first count arrives without moving the chips", withoutCount, chipsTop(), 0.5f)
    }

    @Test
    fun `TEST-UI-042 given_an_unknown_count_when_the_header_renders_then_no_count_is_announced`() {
        compose.setContent { MultiverseTheme { DiscoveryScreen(state = state, onIntent = {}) } }
        compose.waitForIdle()
        compose.runOnIdle { state = state.copy(totalCount = null) }
        compose.waitForIdle()

        assertTrue(
            "TEST-UI-042: a hidden count line exposes no text to a screen reader",
            compose.onAllNodesWithText("characters across the multiverse", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
