package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-018`'s Favorites half — the Favorites full-surface error renders the rate-limit message with
 * its countdown substituted, as Discovery does (`ERROR_FLOW.md` §4.1, `AC-REQ-FUNC-022-1`, `TASK-111`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h1600dp", application = android.app.Application::class)
class FavoritesFailureMessageTest {
    @get:Rule
    val compose = createComposeRule()

    private object NoImageSeam : ImageSeam {
        @Composable
        override fun rememberPainter(
            url: String,
            widthPx: Int,
            heightPx: Int,
        ): ImageSeamResult = ImageSeamResult.Loading
    }

    private fun render(failure: ApiFailure) {
        compose.setContent {
            MultiverseTheme {
                FavoritesScreen(
                    state = FavoritesUiState(loadState = LoadState.Error(failure)),
                    seam = NoImageSeam,
                    onCharacterSelected = {},
                    onIntent = {},
                    onBrowseCharacters = {},
                    illustration = ColorPainter(Color.Transparent),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-018 given_a_rate_limit_with_advice_when_the_favorites_error_renders_then_the_countdown_is_substituted`() {
        render(ApiFailure.RateLimited(30))

        compose.onNodeWithText("Too many jumps. Try again in 30 s.").assertIsDisplayed()
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `TEST-UI-018 given_a_rate_limit_without_advice_when_the_favorites_error_renders_then_no_placeholder_shows`() {
        render(ApiFailure.RateLimited(null))

        compose.onNodeWithText("Too many jumps. Try again shortly.").assertIsDisplayed()
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }
}
