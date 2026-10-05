package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.image.LocalPortalMark
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TEST-UI-029` — the card, the status badge and the portrait's failure mark (`UI_SPEC.md` §4.1, §5.3,
 * §9, `AC-REQ-UX-005-1`, `AC-REQ-FUNC-005-2`, `TASK-113`).
 *
 * The card's merged description ended in the English literal ", button" on top of `Role.Button`, so
 * TalkBack said "button" twice, in English even in Spanish; the badge announced "Alive" where §9 asks
 * for "Status: Alive"; and no caller supplied the portal mark, so every failed portrait was an empty box.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class CardBadgePortraitTest {
    @get:Rule
    val compose = createComposeRule()

    /** A painter that records whether it was drawn, so a case can see the mark without reading pixels. */
    private class RecordingPainter : Painter() {
        var drawn = false
        override val intrinsicSize: Size = Size(24f, 24f)

        override fun DrawScope.onDraw() {
            drawn = true
        }
    }

    @Test
    fun `TEST-UI-029 given_a_card_when_it_is_read_and_tapped_then_it_is_one_button_whose_description_names_no_role`() {
        var taps = 0
        compose.setContent {
            MultiverseTheme {
                CharacterCard(
                    name = "Rick Sanchez",
                    species = "Human",
                    statusTone = StatusTone.Alive,
                    statusLabel = "Alive",
                    imageUrl = "https://example.invalid/avatar/1.jpeg",
                    seam = RecordingSeam(ImageSeamResult.Loading),
                    onClick = { taps++ },
                )
            }
        }

        val buttons = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNodes()
        assertEquals("TEST-UI-029: the card is one button", 1, buttons.size)
        val description = buttons.single().config[SemanticsProperties.ContentDescription].joinToString()
        assertEquals("TEST-UI-029: name, status and species, with the role left to the role", "Rick Sanchez, Alive, Human", description)
        compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human").performClick()
        compose.runOnIdle { assertEquals("TEST-UI-029: a tap opens the card", 1, taps) }
    }

    @Test
    fun `TEST-UI-029 given_a_standalone_badge_when_it_is_read_then_it_announces_the_status_sentence`() {
        compose.setContent {
            MultiverseTheme {
                StatusBadge(tone = StatusTone.Alive, label = "Alive", announcement = "Status: Alive")
            }
        }

        compose.onNodeWithContentDescription("Status: Alive").assertIsDisplayed()
    }

    @Test
    fun `TEST-UI-029 given_a_failed_portrait_and_no_mark_of_its_own_when_it_renders_then_it_draws_the_shells_portal_mark`() {
        val mark = RecordingPainter()
        compose.setContent {
            MultiverseTheme {
                CompositionLocalProvider(LocalPortalMark provides mark) {
                    CharacterPortrait(
                        imageUrl = "https://example.invalid/avatar/1.jpeg",
                        seam = RecordingSeam(ImageSeamResult.Failure),
                    )
                }
            }
        }
        // Drawing happens when a frame is captured, so the case captures one rather than reading pixels.
        compose.onRoot().captureToImage()

        assertTrue("TEST-UI-029: a failed portrait shows the portal mark the shell provides (AC-REQ-FUNC-005-2)", mark.drawn)
    }
}
