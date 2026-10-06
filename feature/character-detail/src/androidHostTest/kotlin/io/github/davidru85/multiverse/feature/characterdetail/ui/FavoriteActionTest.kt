package io.github.davidru85.multiverse.feature.characterdetail.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import io.github.davidru85.multiverse.feature.characterdetail.R
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * `TEST-UI-046` — the Detail's favourite action is the small extended FAB and its hearts are whole
 * (`TASK-134`, `GAP-044`, `AC-REQ-FUNC-006-1`, `UI_SPEC.md` §4.1, §6.3).
 *
 * The owner found the 80 dp Medium extended FAB too large, and its outline heart lopsided: the bundled
 * path was a corrupted copy of `favorite_border` whose inner contour sat one unit off, so the stroke was
 * thin on one side and thick on the other. A heart is mirror-symmetric, so each glyph is drawn and its
 * two halves compared.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w412dp-h1600dp", application = android.app.Application::class)
class FavoriteActionTest {
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

    @Test
    fun `TEST-UI-046 given_a_detail_when_the_favourite_action_renders_then_it_is_56_dp_tall`() {
        compose.setContent {
            MultiverseTheme {
                CharacterDetailScreen(
                    state = CharacterDetailUiState(header = header, episodeCount = 51, loadState = LoadState.Content),
                    seam = seam,
                    onIntent = {},
                    onBack = {},
                    onShare = {},
                    portalMark = ColorPainter(Color.Black),
                )
            }
        }
        compose.waitForIdle()

        val label = RuntimeEnvironment.getApplication().getString(requireNotNull(CopyResolver.resourceId(CopyKeys.DETAIL_ACTION_FAVORITE.value)))
        val fab = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
        assertEquals("TEST-UI-046: the favourite action is the 56 dp small extended FAB", 56f, (fab.bottom - fab.top).value, 0.5f)
    }

    @Test
    fun `TEST-UI-046 given_the_favourite_hearts_when_drawn_then_each_is_mirror_symmetric`() {
        val lopsided =
            listOf(R.drawable.ic_heart_outline to "outline", R.drawable.ic_heart_filled to "filled").mapNotNull { (id, name) ->
                val asymmetric = asymmetricPixels(id)
                if (asymmetric <= HEART_PX * HEART_PX / 100) null else "the $name heart differs from its mirror image in $asymmetric of ${HEART_PX * HEART_PX} pixels"
            }

        assertTrue("TEST-UI-046: every heart is mirror-symmetric:\n" + lopsided.joinToString("\n"), lopsided.isEmpty())
    }

    /** The pixels whose alpha differs visibly from the pixel mirrored across the vertical axis. */
    private fun asymmetricPixels(drawable: Int): Int {
        val heart = requireNotNull(RuntimeEnvironment.getApplication().getDrawable(drawable)).mutate()
        val bitmap = Bitmap.createBitmap(HEART_PX, HEART_PX, Bitmap.Config.ARGB_8888)
        heart.setBounds(0, 0, HEART_PX, HEART_PX)
        heart.draw(Canvas(bitmap))
        var asymmetric = 0
        for (y in 0 until HEART_PX) {
            for (x in 0 until HEART_PX / 2) {
                val left = bitmap.getPixel(x, y) ushr 24
                val right = bitmap.getPixel(HEART_PX - 1 - x, y) ushr 24
                if (abs(left - right) > ALPHA_TOLERANCE) asymmetric += 2
            }
        }
        return asymmetric
    }

    private companion object {
        /** Four times the 24 dp grid, enough pixels for a one-unit offset to show. */
        const val HEART_PX = 96

        /** Anti-aliasing noise allowed between mirrored pixels, out of 255. */
        const val ALPHA_TOLERANCE = 64
    }
}
