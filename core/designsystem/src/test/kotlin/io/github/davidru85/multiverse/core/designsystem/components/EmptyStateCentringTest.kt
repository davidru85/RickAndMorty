package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseComponentDimensions
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-043` — the designed empty state sits in the middle of the area it is given (`TASK-131`,
 * `GAP-041`, `UI_SPEC.md` §6.4): the Episodes placeholder and the Favorites empty state give it the
 * space below their title.
 *
 * The component scrolls so that long copy at the largest text sizes stays reachable; inside the scroll
 * its column wrapped its content and stacked it from the top. The case measures the space above the
 * illustration — the heading's top minus the illustration and its gap, from the tokens — against the
 * space below the button.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class EmptyStateCentringTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TEST-UI-043 given_a_tall_area_when_the_empty_state_renders_then_its_content_is_centred_in_it`() {
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.size(width = 412.dp, height = 760.dp).testTag(AREA)) {
                    EmptyState(
                        heading = "Episodes are on their way",
                        body = "Soon you'll be able to browse every episode, from the Pilot to the latest season.",
                        illustration = ColorPainter(Color.White),
                        actionLabel = "Browse characters",
                        onAction = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val area = compose.onNodeWithTag(AREA).getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText("Episodes are on their way").getUnclippedBoundsInRoot()
        val button = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        val illustrationTop = heading.top - MultiverseDimensions.spaceM - MultiverseComponentDimensions.emptyStateIllustration
        val above = (illustrationTop - area.top).value
        val below = (area.bottom - button.bottom).value

        assertEquals("TEST-UI-043: the space above the illustration equals the space below the button", above, below, 1f)
    }

    private companion object {
        const val AREA = "empty-state-area"
    }
}
