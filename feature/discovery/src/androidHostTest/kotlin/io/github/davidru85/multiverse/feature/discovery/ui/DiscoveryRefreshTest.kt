package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-024`, Android half — the manual refresh gesture (`REQ-FUNC-012`, `AC-REQ-FUNC-012-1`,
 * `DEC-134`, `TASK-112`).
 *
 * No screen ever dispatched `CharacterListIntent.Refresh`, so a manual revalidation was unreachable.
 * Pulling the grid down past the threshold now sends it; the indicator itself is bound to the state's
 * `isRefreshing`, so it ends when the shared refresh ends.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", manifest = "AndroidManifest.xml")
class DiscoveryRefreshTest {
    @get:Rule
    val compose = createComposeRule()

    private val cards =
        (1..6).map {
            CharacterCardUi(
                id = CharacterId("$it"),
                name = "Rick $it",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/$it.jpeg",
            )
        }

    @Test
    fun `TEST-UI-024 given_content_when_the_grid_is_pulled_down_then_a_refresh_is_requested`() {
        val sent = mutableListOf<CharacterListIntent>()
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(
                    state = CharacterListUiState(items = cards, totalCount = 6, loadState = LoadState.Content),
                    onIntent = { sent += it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(startY = top, endY = bottom, durationMillis = 600) }
        compose.waitForIdle()

        assertEquals(
            "TEST-UI-024: one pull is one refresh request",
            listOf<CharacterListIntent>(CharacterListIntent.Refresh),
            sent.filterIsInstance<CharacterListIntent.Refresh>(),
        )
    }
}
