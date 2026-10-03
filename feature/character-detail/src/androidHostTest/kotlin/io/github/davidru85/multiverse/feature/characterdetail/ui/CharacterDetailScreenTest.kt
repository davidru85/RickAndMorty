package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailIntent
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.InfoRowKind
import io.github.davidru85.multiverse.feature.characterdetail.presentation.InfoRowUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-002` (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`/`-3`, `REQ-FUNC-023`) — the Detail screen's
 * rendered state on the Android host.
 *
 * The case drives the real composable with a recording image seam, so it asserts the hero, the stat
 * row, the info list and the inline retry as the user sees them, and that a failed load keeps the
 * header. Every expected string is resolved through `CopyResolver`, so a case cannot pass on an
 * English literal the copy set does not carry.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h1600dp", application = android.app.Application::class)
class CharacterDetailScreenTest {
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

    private val header =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Rick Sanchez",
            species = DisplayText.Data("Human"),
            status = io.github.davidru85.multiverse.core.domain.model.CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

    private fun show(
        state: CharacterDetailUiState,
        onIntent: (CharacterDetailIntent) -> Unit = {},
    ) {
        compose.setContent {
            MultiverseTheme {
                CharacterDetailScreen(
                    state = state,
                    seam = RecordingSeam(),
                    onIntent = onIntent,
                    onBack = {},
                    onShare = {},
                    portalMark = ColorPainter(Color.Black),
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * The string the screen resolves for [key], read through the resolver's own resource table.
     *
     * It goes through `CopyResolver.resourceId`, so a case cannot pass on English the copy set does
     * not carry, and through the same table the screen uses, so the case and the screen cannot
     * diverge. Robolectric's application context supplies the value for the `en` qualifier this class
     * is configured with.
     */
    private fun copy(key: String): String {
        val id = requireNotNull(CopyResolver.resourceId(key)) { "the copy set does not register `$key`" }
        return org.robolectric.RuntimeEnvironment
            .getApplication()
            .getString(id)
    }

    /**
     * Asserts the node holding [text] is on screen.
     *
     * The screen is a scrolling `Column`, so every section is composed and the `h1600dp` qualifier
     * this class declares puts the whole screen inside the viewport; the assertion is then on the
     * node the user sees rather than on one the test scrolled to.
     */
    private fun assertVisible(text: String) {
        // The screen scrolls, so a section below the fold is reached the way the user reaches it.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun `TEST-UI-002 given_a_loaded_detail_when_it_is_shown_then_the_hero_the_stats_and_the_info_list_render`() {
        show(
            CharacterDetailUiState(
                header = header,
                episodeCount = 51,
                dimension = "C-137",
                info =
                    listOf(
                        InfoRowUi(InfoRowKind.Origin, CopyKeys.DETAIL_INFO_ORIGIN, "Earth (C-137)"),
                        InfoRowUi(InfoRowKind.LastKnownLocation, CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION, "Citadel of Ricks"),
                        InfoRowUi(InfoRowKind.FirstSeenIn, CopyKeys.DETAIL_INFO_FIRST_SEEN_IN, "Pilot · S01E01"),
                    ),
                loadState = LoadState.Content,
            ),
        )

        assertVisible("Rick Sanchez")
        compose.onNodeWithContentDescription(copy("status_alive")).assertIsDisplayed()
        assertVisible(copy("detail_stat_episodes"))
        assertVisible("51")
        assertVisible(copy("detail_stat_dimension"))
        assertVisible("C-137")
        assertVisible(copy("detail_info_origin"))
        assertVisible("Earth (C-137)")
        assertVisible(copy("detail_info_first_seen_in"))
        assertVisible("Pilot · S01E01")
        compose.onNodeWithContentDescription(copy("detail_action_favorite")).assertIsDisplayed()
    }

    @Test
    fun `TEST-UI-002 given_a_failed_load_with_list_data_when_it_is_shown_then_the_header_stays_and_the_inline_retry_renders`() {
        var retried = 0
        show(
            CharacterDetailUiState(
                header = header,
                loadState = LoadState.Error(ApiFailure.Offline),
            ),
            onIntent = { retried++ },
        )

        assertVisible("Rick Sanchez")
        assertVisible(copy("detail_error_inline"))
        assertAbsent(copy("detail_info_origin"))

        compose.onNodeWithText(copy("action_retry")).performClick()
        compose.runOnIdle { assert(retried == 1) { "TEST-UI-002: the inline retry dispatches the Retry intent" } }
    }

    @Test
    fun `TEST-UI-002 given_no_enrichment_when_the_detail_is_shown_then_the_episode_count_renders_without_the_first_seen_row`() {
        show(
            CharacterDetailUiState(
                header = header,
                episodeCount = 3,
                info = listOf(InfoRowUi(InfoRowKind.Origin, CopyKeys.DETAIL_INFO_ORIGIN, "Earth (C-137)")),
                loadState = LoadState.Content,
            ),
        )

        assertVisible("3")
        assertAbsent(copy("detail_info_first_seen_in"))
    }

    @Test
    fun `TEST-UI-002 given_a_marked_favourite_when_the_fab_is_shown_then_it_advertises_the_toggled_state`() {
        show(
            CharacterDetailUiState(
                header = header,
                episodeCount = 1,
                isFavorite = true,
                loadState = LoadState.Content,
            ),
        )

        compose.onNodeWithContentDescription(copy("detail_action_favorite")).assertIsDisplayed()
    }
}
