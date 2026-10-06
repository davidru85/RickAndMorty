package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TEST-UI-049` — the Discovery app bar keeps its colour while the grid scrolls (`TASK-137`, `GAP-047`,
 * `UI_SPEC.md` §6.2).
 *
 * The bar took Surface Container once the grid had scrolled, although the grid scrolls in its own area
 * below the headline and the chips and never passes under the bar, so the band around the search field
 * changed colour for no reason the user could see. The case reads the bar's gutter beside the field, and
 * the field itself, before and after a scroll.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w412dp-h915dp", manifest = "AndroidManifest.xml")
class DiscoveryAppBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val cards =
        (1..20).map { id ->
            CharacterCardUi(
                id = CharacterId("$id"),
                name = "Character $id",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/$id.jpeg",
            )
        }

    private fun capture(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The bar's 16 dp gutter left of the field, at the bar's vertical centre. */
    private fun PixelMap.barGutter(): Color = this[px(BAR_GUTTER_X), px(BAR_CENTRE_Y)]

    /** Inside the search field, right of its placeholder, at the bar's vertical centre. */
    private fun PixelMap.field(): Color = this[width - px(FIELD_INSET_FROM_END), px(BAR_CENTRE_Y)]

    private fun px(dp: Int): Int = with(compose.density) { dp.dp.roundToPx() }

    private fun hex(color: Color): String = "#%08X".format(color.toArgb())

    @Test
    fun `TEST-UI-049 given_a_scrolled_grid_when_the_app_bar_renders_then_it_keeps_the_surface_colour`() {
        compose.setContent {
            MultiverseTheme {
                DiscoveryScreen(
                    state = CharacterListUiState(items = cards, totalCount = 826, loadState = LoadState.Content),
                    onIntent = {},
                )
            }
        }
        compose.waitForIdle()
        val atRest = capture()
        assertEquals(
            "TEST-UI-049: the app bar is Surface at rest",
            hex(MultiverseColors.surface),
            hex(atRest.barGutter()),
        )

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_INDEX)
        compose.waitForIdle()
        val scrolled = capture()

        assertEquals(
            "TEST-UI-049: the app bar stays Surface while the grid scrolls",
            hex(MultiverseColors.surface),
            hex(scrolled.barGutter()),
        )
        assertEquals(
            "TEST-UI-049: the search field keeps its own container",
            hex(atRest.field()),
            hex(scrolled.field()),
        )
    }

    private companion object {
        const val BAR_GUTTER_X = 4
        const val BAR_CENTRE_Y = 32
        const val FIELD_INSET_FROM_END = 40
        const val SCROLLED_INDEX = 6
    }
}
