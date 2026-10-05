package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TEST-UI-041` — a stat value too long for its tile keeps every word whole (`UI_SPEC.md` §4.1,
 * `DEC-151`, `GAP-036`, `TASK-124`).
 *
 * The row is laid out at the Detail's real width on a 411 dp phone — the screen less the 16 dp margins
 * — with the GraphQL dimension that broke as "Dimensi" / "on" on a device. The case reads the value's
 * own text layout, so it asserts where the lines actually end and the size the value was drawn at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class StatTileFitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TEST-UI-041 given_a_value_wider_than_its_tile_when_laid_out_then_no_word_is_broken`() {
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.width(DETAIL_WIDTH - MultiverseDimensions.spaceL * 2)) {
                    StatTileRow(
                        tiles =
                            listOf(
                                Triple("51", "Episodes", MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer),
                                Triple(LONG_VALUE, "Dimension", MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer),
                                Triple("Human", "Species", MultiverseColors.secondaryFixedDim to MultiverseColors.onSecondaryFixed),
                            ),
                    )
                }
            }
        }

        val layout = layoutOf(LONG_VALUE)
        val text = layout.layoutInput.text.text
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue(
                "TEST-UI-041: line $line ends inside a word (\"${text.substring(0, end)}\" | \"${text.substring(end)}\")",
                !(text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()),
            )
        }
        assertTrue(
            "TEST-UI-041: the value is never smaller than the Label Large Emphasized floor (TASK-133)",
            layout.layoutInput.style.fontSize.value >= MultiverseType.labelLargeEmphasizedSize.value,
        )
    }

    @Test
    fun `TEST-UI-041 given_a_value_that_fits_when_laid_out_then_it_keeps_the_value_size`() {
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.width(DETAIL_WIDTH - MultiverseDimensions.spaceL * 2)) {
                    StatTileRow(
                        tiles =
                            listOf(
                                Triple("51", "Episodes", MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer),
                                Triple("C-137", "Dimension", MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer),
                                Triple("Human", "Species", MultiverseColors.secondaryFixedDim to MultiverseColors.onSecondaryFixed),
                            ),
                    )
                }
            }
        }

        assertEquals(
            "TEST-UI-041: a value that fits is drawn at Title Medium Emphasized (TASK-133)",
            MultiverseType.titleMediumEmphasizedSize.value,
            layoutOf("C-137")
                .layoutInput.style.fontSize.value,
        )
    }

    /**
     * `TEST-UI-045` (`TASK-133`, `GAP-043`): the stats row is compact. At font scale 1.0 and the Detail's
     * width, the row of values that fit — Rick's "51", "C-137", "Human" — is at most 56 dp tall; the tiles
     * were 76 dp with 24 sp values, which the owner found too large.
     */
    @Test
    fun `TEST-UI-045 given_values_that_fit_when_the_row_is_laid_out_then_it_is_at_most_56_dp_tall`() {
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.width(DETAIL_WIDTH - MultiverseDimensions.spaceL * 2)) {
                    StatTileRow(
                        tiles =
                            listOf(
                                Triple("51", "Episodes", MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer),
                                Triple("C-137", "Dimension", MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer),
                                Triple("Human", "Species", MultiverseColors.secondaryFixedDim to MultiverseColors.onSecondaryFixed),
                            ),
                        modifier = Modifier.testTag(ROW),
                    )
                }
            }
        }

        val bounds = compose.onNodeWithTag(ROW).getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("TEST-UI-045: the stats row is at most 56 dp tall, but it is $height", height <= 56.dp)
    }

    private fun layoutOf(value: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(value)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single()
    }

    private companion object {
        const val LONG_VALUE = "Dimension C-137"
        val DETAIL_WIDTH = 411.dp
        const val ROW = "stat-tile-row"
    }
}
