package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailIntent
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TEST-UI-032` — the Detail's layout and its favourite action (`UI_SPEC.md` §4.1, §6.3, §9, Figma
 * `21:1217`, `21:1251`, `21:1295`, `AC-REQ-FUNC-006-1`, `TASK-113`).
 *
 * The title block sat below the hero, where Figma overlays it inside the hero; and the favourite FAB's
 * icon and label both read "Favorite" with no toggled state, so TalkBack said "Favorite" twice and
 * never said whether the character was one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w412dp-h915dp", application = android.app.Application::class)
class DetailLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val seam =
        object : ImageSeam {
            @androidx.compose.runtime.Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = ImageSeamResult.Success(ColorPainter(Color.Magenta))
        }

    private val header =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Rick Sanchez",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

    private fun copy(key: String): String {
        val id = requireNotNull(CopyResolver.resourceId(key))
        return org.robolectric.RuntimeEnvironment
            .getApplication()
            .getString(id)
    }

    private fun show(
        isFavorite: Boolean,
        onIntent: (CharacterDetailIntent) -> Unit = {},
    ) {
        compose.setContent {
            MultiverseTheme {
                CharacterDetailScreen(
                    state =
                        CharacterDetailUiState(
                            header = header,
                            episodeCount = 51,
                            isFavorite = isFavorite,
                            loadState = LoadState.Content,
                        ),
                    seam = seam,
                    onIntent = onIntent,
                    onBack = {},
                    onShare = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** The favourite action: the one node named "Favorite". */
    private fun favoriteAction() = compose.onNodeWithContentDescription(copy("detail_action_favorite"))

    @Test
    fun `TEST-UI-032 given_a_character_that_is_not_a_favourite_when_the_action_is_read_then_it_is_an_unchecked_toggle_named_once`() {
        val sent = mutableListOf<CharacterDetailIntent>()
        show(isFavorite = false, onIntent = { sent += it })

        val node = favoriteAction().fetchSemanticsNode()
        assertEquals(
            "TEST-UI-032: the action exposes its toggled state",
            ToggleableState.Off,
            node.config[SemanticsProperties.ToggleableState],
        )
        // Named once: no node also carries the label as text, which TalkBack would read a second time.
        compose.onNode(hasText(copy("detail_action_favorite"))).assertDoesNotExist()
        favoriteAction().performClick()
        compose.runOnIdle { assertEquals(listOf<CharacterDetailIntent>(CharacterDetailIntent.ToggleFavorite), sent) }
    }

    @Test
    fun `TEST-UI-032 given_a_favourite_when_the_action_is_read_then_it_is_a_checked_toggle`() {
        show(isFavorite = true)

        assertEquals(ToggleableState.On, favoriteAction().fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    @Test
    fun `TEST-UI-032 given_a_loaded_detail_when_laid_out_then_the_name_sits_inside_the_hero`() {
        show(isFavorite = false)

        val hero = compose.onNodeWithContentDescription("Rick Sanchez").fetchSemanticsNode().boundsInRoot
        val name =
            compose
                .onNode(hasText("Rick Sanchez") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "TEST-UI-032: the name is overlaid on the hero (Figma 21:1251), hero=$hero name=$name",
            name.top >= hero.top && name.bottom <= hero.bottom,
        )
    }
}
