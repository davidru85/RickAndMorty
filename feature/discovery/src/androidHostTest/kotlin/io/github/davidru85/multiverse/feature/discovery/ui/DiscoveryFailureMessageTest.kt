package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-018` — Discovery renders every failure message with its placeholder resolved, in both
 * shipped locales (`ERROR_FLOW.md` §4.1, `AC-REQ-FUNC-022-1`, `TASK-111`).
 *
 * The rate-limit message is the one message with a placeholder. Its countdown is a number, so a
 * surface that substitutes it as text crashes (`%d` with a `String`), and a surface that renders the
 * template without advice shows a raw specifier. The cases render the real resources through the
 * real screen, so neither can pass by asserting a key.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryFailureMessageTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(failure: ApiFailure) {
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(state = CharacterListUiState(loadState = LoadState.Error(failure)), onIntent = {})
            }
        }
        compose.waitForIdle()
    }

    private fun assertNoUnresolvedPlaceholder() {
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `TEST-UI-018 given_a_rate_limit_with_advice_when_the_error_renders_then_the_countdown_is_substituted`() {
        render(ApiFailure.RateLimited(30))

        compose.onNodeWithText("Too many jumps. Try again in 30 s.").assertIsDisplayed()
        assertNoUnresolvedPlaceholder()
    }

    @Test
    fun `TEST-UI-018 given_a_rate_limit_without_advice_when_the_error_renders_then_no_countdown_is_invented`() {
        render(ApiFailure.RateLimited(null))

        compose.onNodeWithText("Too many jumps. Try again shortly.").assertIsDisplayed()
        compose.onAllNodesWithText("0 s", substring = true).assertCountEquals(0)
        assertNoUnresolvedPlaceholder()
    }

    @Test
    @Config(qualifiers = "es")
    fun `TEST-UI-018 given_the_spanish_locale_when_a_rate_limit_with_advice_renders_then_the_countdown_is_substituted`() {
        render(ApiFailure.RateLimited(30))

        compose.onNodeWithText("Demasiados saltos. Inténtalo de nuevo en 30 s.").assertIsDisplayed()
        assertNoUnresolvedPlaceholder()
    }

    @Test
    @Config(qualifiers = "es")
    fun `TEST-UI-018 given_the_spanish_locale_when_a_rate_limit_without_advice_renders_then_no_countdown_is_invented`() {
        render(ApiFailure.RateLimited(null))

        compose.onNodeWithText("Demasiados saltos. Inténtalo de nuevo en un momento.").assertIsDisplayed()
        assertNoUnresolvedPlaceholder()
    }
}
