package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListIntent
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-019` — over content, a failed load and a stale result each show the Android snackbar of
 * `UI_SPEC.md` §8 with a Retry that dispatches `CharacterListIntent.Retry` (`ERROR_FLOW.md` §4, §9,
 * `DEC-124`, `TASK-111`).
 *
 * The failure is the class-specific message of `ERROR_FLOW.md` §4.1, never a generic one, and the
 * content stays on screen beneath it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryBannerTest {
    @get:Rule
    val compose = createComposeRule()

    private val cards =
        listOf(
            CharacterCardUi(
                id = CharacterId("1"),
                name = "Rick Sanchez",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/1.jpeg",
            ),
        )

    private fun render(state: CharacterListUiState): List<CharacterListIntent> {
        val intents = mutableListOf<CharacterListIntent>()
        compose.setContent {
            MultiverseTheme { DiscoveryScreen(state = state, onIntent = { intents += it }) }
        }
        compose.waitForIdle()
        return intents
    }

    private fun content(
        contentFailure: ApiFailure? = null,
        isStale: Boolean = false,
        isRefreshing: Boolean = false,
    ) = CharacterListUiState(
        items = cards,
        totalCount = 1,
        loadState = LoadState.Content,
        isStale = isStale,
        contentFailure = contentFailure,
        isRefreshing = isRefreshing,
    )

    @Test
    fun `TEST-UI-019 given_a_failed_append_over_content_when_rendered_then_its_message_and_a_working_retry_show`() {
        val intents = render(content(contentFailure = ApiFailure.Offline))

        compose.onNodeWithContentDescription("Rick Sanchez", substring = true).assertIsDisplayed()
        compose.onNodeWithText("You're offline. Reconnect to continue exploring the multiverse.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitForIdle()

        assertEquals(listOf<CharacterListIntent>(CharacterListIntent.Retry), intents.filterIsInstance<CharacterListIntent.Retry>())
    }

    @Test
    fun `TEST-UI-019 given_stale_content_when_rendered_then_the_stale_snackbar_offers_a_working_retry`() {
        val intents = render(content(isStale = true))

        compose.onNodeWithText("Showing saved results").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitForIdle()

        assertEquals(listOf<CharacterListIntent>(CharacterListIntent.Retry), intents.filterIsInstance<CharacterListIntent.Retry>())
    }

    @Test
    fun `TEST-UI-019 given_a_refresh_in_flight_over_stale_content_when_rendered_then_no_retry_is_offered`() {
        render(content(isStale = true, isRefreshing = true))

        compose.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    @Test
    fun `TEST-UI-019 given_fresh_content_without_a_failure_when_rendered_then_no_snackbar_shows`() {
        render(content())

        compose.onAllNodesWithText("Showing saved results").assertCountEquals(0)
        compose.onAllNodesWithText("Retry").assertCountEquals(0)
    }
}
