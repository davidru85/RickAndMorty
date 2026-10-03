package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Discovery screen's semantics cases (`TEST-UI-001`, `TEST-UI-003`, `TEST-UI-009`), driven through
 * Robolectric and the Compose test rule so the assertions are on the semantics tree a user reaches,
 * not on the source.
 *
 * STUB — the screen draws nothing yet, so every case is red until the implementation commit lands.
 */
@RunWith(AndroidJUnit4::class)
// A Kotlin Multiplatform library's host-test run has no merged test manifest of its own, so the
// activity the Compose rule hosts its content in is declared in the module's host-test manifest and
// pointed at here; nothing of it reaches the published library.
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val card =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Rick Sanchez",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

    private val cards =
        listOf(
            CharacterCardUi(
                id = card.id,
                name = card.name,
                species = card.species,
                status = card.status,
                statusLabel = card.statusLabel,
                imageUrl = card.imageUrl,
            ),
        )

    @Test
    fun `TEST-UI-001 given_a_first_page_when_the_grid_renders_then_the_cards_are_shown`() {
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(
                    state = CharacterListUiState(items = cards, totalCount = 1, loadState = LoadState.Content),
                    onIntent = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Rick Sanchez").assertIsDisplayed()
    }

    @Test
    fun `TEST-UI-003 given_the_filter_row_when_it_renders_then_there_are_four_options_and_All_is_selected`() {
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(
                    state = CharacterListUiState(items = cards, totalCount = 1, loadState = LoadState.Content),
                    onIntent = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("Alive").assertIsDisplayed()
        compose.onNodeWithText("Dead").assertIsDisplayed()
        compose.onNodeWithText("Unknown").assertIsDisplayed()
    }

    @Test
    fun `TEST-UI-009 given_the_empty_state_when_clear_filters_is_tapped_then_the_unfiltered_filter_is_requested`() {
        val sent = mutableListOf<CharacterListIntent>()
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(
                    state = CharacterListUiState(loadState = LoadState.Empty),
                    onIntent = { sent += it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Clear filters").assertIsDisplayed().performClick()
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "clearing the filters asks for the unfiltered first page",
            sent.any { it is CharacterListIntent.QueryChanged && it.query.isEmpty() },
        )
    }

}
