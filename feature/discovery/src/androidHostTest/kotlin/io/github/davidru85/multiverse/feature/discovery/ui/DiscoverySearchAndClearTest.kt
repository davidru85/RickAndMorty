package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
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
 * `TEST-UI-021`'s Android half — "Clear filters" sends the one intent that clears both dimensions, and
 * the search field follows a query the state changed, without echoing it back (`IC-018`, `DEC-129`,
 * `AC-REQ-FUNC-010-2`, `TASK-112`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoverySearchAndClearTest {
    @get:Rule
    val compose = createComposeRule()

    private val emptyForQuery =
        CharacterListUiState(filter = CharacterFilter(query = "zzz", status = StatusFilter.Unknown), loadState = LoadState.Empty)

    private fun fieldText(): String =
        compose
            .onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    @Test
    fun `TEST-UI-021 given_an_empty_search_when_clear_filters_is_tapped_then_the_one_clearing_intent_is_sent`() {
        val intents = mutableListOf<CharacterListIntent>()
        compose.setContent { MultiverseTheme { DiscoveryScreen(state = emptyForQuery, onIntent = { intents += it }) } }
        compose.waitForIdle()

        compose.onNodeWithText("Clear filters").performClick()
        compose.waitForIdle()

        assertEquals(listOf<CharacterListIntent>(CharacterListIntent.ClearFilters), intents.filter { it !is CharacterListIntent.LoadNextPage })
    }

    @Test
    fun `TEST-UI-021 given_a_query_the_state_cleared_when_the_screen_recomposes_then_the_field_follows_without_echoing_it`() {
        val intents = mutableListOf<CharacterListIntent>()
        val state = mutableStateOf(emptyForQuery)
        compose.setContent { MultiverseTheme { DiscoveryScreen(state = state.value, onIntent = { intents += it }) } }
        compose.waitForIdle()
        assertEquals("zzz", fieldText())

        state.value = CharacterListUiState(filter = CharacterFilter(), loadState = LoadState.Loading)
        compose.waitForIdle()

        assertEquals("the field shows the query the state now holds", "", fieldText())
        assertEquals(
            "a query the state set is not sent back as a typed one",
            emptyList<CharacterListIntent>(),
            intents.filterIsInstance<CharacterListIntent.QueryChanged>(),
        )
    }
}
