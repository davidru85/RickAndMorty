package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesIntent
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-005` and `TEST-UI-013` (`REQ-FUNC-006`, `AC-REQ-FUNC-006-3`, `REQ-UX-005`, `REQ-SEC-004`) —
 * the Favorites section's rendered states on the Android host.
 *
 * The case drives the real composable with a recording image seam, so it asserts what the user sees:
 * the designed empty state with its copy and a working "Browse characters", the grid of the same cards
 * Discovery renders, the full-surface error with a working Retry, and — the accessibility half — a card
 * that is one merged node naming the name, the status, the species and the button, with a status that is
 * never colour-only.
 *
 * Every expected string is resolved through `CopyResolver`, so a case cannot pass on an English literal
 * the one Android copy set does not carry.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h1600dp", application = android.app.Application::class)
class FavoritesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** The seam a test drives; it records every request and performs no I/O. */
    private class RecordingSeam : ImageSeam {
        val requests = mutableListOf<Triple<String, Int, Int>>()

        @Composable
        override fun rememberPainter(
            url: String,
            widthPx: Int,
            heightPx: Int,
        ): ImageSeamResult {
            requests += Triple(url, widthPx, heightPx)
            return ImageSeamResult.Success(ColorPainter(Color.Magenta))
        }
    }

    private fun card(
        id: String,
        name: String,
        status: CharacterStatus = CharacterStatus.Alive,
        species: DisplayText = DisplayText.Data("Human"),
        label: io.github.davidru85.multiverse.core.presentation.CopyKey = CopyKeys.STATUS_ALIVE,
    ) = CharacterCardUi(
        id = CharacterId(id),
        name = name,
        species = species,
        status = status,
        statusLabel = label,
        imageUrl = "https://example.invalid/avatar/$id.jpeg",
    )

    private fun show(
        state: FavoritesUiState,
        onIntent: (FavoritesIntent) -> Unit = {},
        onCharacterSelected: (CharacterCardUi) -> Unit = {},
        onBrowseCharacters: () -> Unit = {},
    ) {
        compose.setContent {
            MultiverseTheme {
                FavoritesScreen(
                    state = state,
                    seam = RecordingSeam(),
                    onCharacterSelected = onCharacterSelected,
                    onIntent = onIntent,
                    onBrowseCharacters = onBrowseCharacters,
                    illustration = ColorPainter(Color.White),
                    portalMark = ColorPainter(Color.Black),
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * The string the screen resolves for [key], read through the resolver's own resource table, so a
     * case cannot pass on English the copy set does not carry.
     */
    private fun copy(key: String): String {
        val id = requireNotNull(CopyResolver.resourceId(key)) { "the copy set does not register `$key`" }
        return org.robolectric.RuntimeEnvironment
            .getApplication()
            .getString(id)
    }

    @Test
    fun `TEST-UI-005 given_the_empty_state_when_it_is_shown_then_its_copy_renders_and_browse_characters_selects_characters`() {
        var browsed = 0
        show(FavoritesUiState(loadState = LoadState.Empty), onBrowseCharacters = { browsed++ })

        compose.onNodeWithText(copy("favorites_heading")).assertIsDisplayed()
        compose.onNodeWithText(copy("favorites_body")).assertIsDisplayed()

        compose.onNodeWithText(copy("browse_characters")).performClick()
        compose.runOnIdle {
            assert(browsed == 1) { "TEST-UI-005: Browse characters selects the Characters destination without pushing a route" }
        }
    }

    @Test
    fun `TEST-UI-005 given_stored_favourites_when_the_grid_is_shown_then_the_cards_render_and_a_tap_selects_that_card`() {
        var selected: CharacterCardUi? = null
        val rick = card("1", "Rick Sanchez")
        val morty = card("2", "Morty Smith", CharacterStatus.Dead, label = CopyKeys.STATUS_DEAD)
        show(
            FavoritesUiState(items = listOf(rick, morty), loadState = LoadState.Content),
            onCharacterSelected = { selected = it },
        )

        // Each card is one node whose sentence names the character, its status and its species, so the
        // status is announced as words, never by colour alone (REQ-UX-005, TEST-UI-029).
        compose.onNodeWithContentDescription("Rick Sanchez", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Morty Smith, ${copy("status_dead")}", substring = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("Rick Sanchez, ${copy("status_alive")}, Human").performClick()
        compose.runOnIdle {
            assert(selected == rick) { "TEST-UI-005: tapping a card hands that card to the shell's navigation callback" }
        }
    }

    @Test
    fun `TEST-UI-005 given_a_read_failure_when_the_error_state_is_shown_then_it_renders_the_copy_and_a_working_retry`() {
        var retried = 0
        show(
            FavoritesUiState(items = emptyList(), loadState = LoadState.Error(ApiFailure.Offline)),
            onIntent = { retried++ },
        )

        compose.onNodeWithText(copy("error_title")).assertIsDisplayed()
        compose.onNodeWithText(copy("error_message_offline")).assertIsDisplayed()

        compose.onNodeWithText(copy("action_retry")).performClick()
        compose.runOnIdle { assert(retried == 1) { "TEST-UI-005: the error's Retry dispatches the Retry intent (IC-020)" } }
    }

    @Test
    fun `TEST-UI-013 given_a_favourite_card_when_it_renders_then_it_is_one_merged_node_naming_name_status_species_and_button`() {
        show(
            FavoritesUiState(
                items =
                    listOf(
                        card("1", "Rick Sanchez", species = DisplayText.Copy(CopyKeys.VALUE_UNKNOWN), label = CopyKeys.STATUS_ALIVE),
                    ),
                loadState = LoadState.Content,
            ),
        )

        compose
            .onNodeWithContentDescription("Rick Sanchez, ${copy("status_alive")}, ${copy("value_unknown")}")
            .assertIsDisplayed()
    }

    @Test
    fun `TEST-UI-013 given_the_favorites_section_when_it_is_shown_then_the_voice_search_control_is_absent`() {
        show(FavoritesUiState(items = listOf(card("1", "Rick Sanchez")), loadState = LoadState.Content))

        // Voice search is deferred (DEC-002, REQ-FUNC-030): the mic's absence is the assertion, not a
        // disabled control's presence.
        compose
            .onNode(
                hasText("Search by voice") or
                    androidx.compose.ui.test
                        .hasContentDescription("Search by voice"),
            ).assertDoesNotExist()
    }
}
