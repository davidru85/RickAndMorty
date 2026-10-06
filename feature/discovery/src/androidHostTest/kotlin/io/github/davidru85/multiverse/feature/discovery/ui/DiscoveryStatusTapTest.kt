package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-052` — every tap on a status chip reaches the caller (`REQ-FUNC-036`, `AC-REQ-FUNC-036-2`,
 * `TASK-139`, `DEC-162`).
 *
 * The shell plays the selection sound from this report, on every tap and for the chip already selected
 * too, so the screen reports the tap rather than a change of filter; it knows nothing of sound. "Clear
 * filters" resets the status without a tap on a chip, so it reports nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryStatusTapTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<CharacterListIntent>()
    private val reported = mutableListOf<StatusFilter>()

    private fun render(state: CharacterListUiState) {
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(state = state, onIntent = { sent += it }, onStatusSelected = { reported += it })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-052 given_the_chips_when_each_is_tapped_then_every_tap_is_reported_the_selected_chip_included`() {
        // `All` is the selected chip: the state's filter is the default one.
        render(CharacterListUiState(loadState = LoadState.Empty))

        listOf("All", "Alive", "Alive", "Dead", "Unknown", "All").forEach {
            compose.onNodeWithText(it).performClick()
        }

        compose.runOnIdle {
            assertEquals(
                "TEST-UI-052: one report per tap, in order, the selected chip and a repeated tap included",
                listOf(
                    StatusFilter.All,
                    StatusFilter.Alive,
                    StatusFilter.Alive,
                    StatusFilter.Dead,
                    StatusFilter.Unknown,
                    StatusFilter.All,
                ),
                reported,
            )
            assertEquals(
                "TEST-UI-052: each tap still sends its intent",
                reported.map { CharacterListIntent.StatusSelected(it) },
                sent.filterIsInstance<CharacterListIntent.StatusSelected>(),
            )
        }
    }

    @Test
    fun `TEST-UI-052 given_empty_results_when_clear_filters_is_tapped_then_no_status_tap_is_reported`() {
        render(
            CharacterListUiState(
                filter = CharacterFilter(query = "zzz", status = StatusFilter.Dead),
                loadState = LoadState.Empty,
            ),
        )

        compose.onNodeWithText("Clear filters").performClick()

        compose.runOnIdle {
            assertEquals(
                "TEST-UI-052: the reset is the app's, not a tap on a chip",
                emptyList<StatusFilter>(),
                reported,
            )
            assertEquals(listOf(CharacterListIntent.ClearFilters), sent)
        }
    }
}
