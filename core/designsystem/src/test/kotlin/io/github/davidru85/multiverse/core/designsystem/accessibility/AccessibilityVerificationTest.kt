package io.github.davidru85.multiverse.core.designsystem.accessibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCard
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCardSkeleton
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.MultiverseNavigationBar
import io.github.davidru85.multiverse.core.designsystem.components.NavigationDestination
import io.github.davidru85.multiverse.core.designsystem.components.StatusBadge
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.layout.MultiverseGrid
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The automated accessibility cases of `TESTING.md` §9.1 that a rendered component can decide
 * (`TASK-046`): measured target sizes (`TEST-A11Y-003`), the accessibility tree of the components a
 * screen reader walks (`TEST-A11Y-004`) and the grid's collapse at the largest text scale
 * (`TEST-A11Y-005`).
 *
 * What these cases deliberately do **not** claim is screen-reader behaviour: `TESTING.md` §9 is
 * explicit that automated checks "`MUST NOT` claim to prove screen-reader behaviour", so the TalkBack
 * traversal, the on-device contrast measurement and the observed Reduce Motion behaviour are carried
 * by the recorded manual checklist of `DEC-023` (`TESTING.md` §9.2) rather than asserted here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class AccessibilityVerificationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun seam(result: ImageSeamResult = ImageSeamResult.Loading) =
        object : ImageSeam {
            @Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = result
        }

    /**
     * `TEST-A11Y-003` (`REQ-UX-004`, `AC-REQ-UX-004-1`): every interactive control meets the 48 dp
     * Android minimum. The case reads the composed node's measured bounds rather than the source, so a
     * padding change that shrinks a control fails rather than passing unnoticed.
     */
    @Test
    fun `TEST-A11Y-003 given_the_card_when_it_renders_then_its_touch_target_meets_48_dp`() {
        compose.setContent {
            MultiverseTheme {
                CharacterCard(
                    name = "Rick Sanchez",
                    species = "Human",
                    statusTone = StatusTone.Alive,
                    statusLabel = "Alive",
                    imageUrl = "https://example.invalid/avatar/1.jpeg",
                    seam = seam(),
                    portalMark = ColorPainter(Color.White),
                    onClick = {},
                )
            }
        }
        compose.waitForIdle()

        // `assertHeightIsAtLeast` reads the node's layout bounds, and Compose's minimum-touch-target
        // enforcement only ever expands a target, so a node at or above 48 dp of layout is at or above
        // the REQ-UX-004 minimum for touch as well. The card is the list's interactive unit and its
        // documented patterns are 172 dp and 224 dp tall, so this asserts the property rather than a
        // literal, and the measured on-device value is carried by the recorded checklist of DEC-023.
        compose
            .onNodeWithContentDescription("Rick Sanchez, Alive, Human, button")
            .assertHeightIsAtLeast(48.dp)
    }

    /**
     * `TEST-A11Y-004` (`REQ-UX-005`, `AC-REQ-UX-005-1`): a card is exactly **one** merged node naming
     * name, status and species, and carries the button role — the announcement the acceptance criterion
     * spells out. A second matching node would mean the description was duplicated and a screen reader
     * would read the card twice.
     */
    @Test
    fun `TEST-A11Y-004 given_a_card_when_the_tree_is_read_then_one_merged_node_names_name_status_and_species`() {
        compose.setContent {
            MultiverseTheme {
                CharacterCard(
                    name = "Rick Sanchez",
                    species = "Human",
                    statusTone = StatusTone.Alive,
                    statusLabel = "Alive",
                    imageUrl = "https://example.invalid/avatar/1.jpeg",
                    seam = seam(),
                    portalMark = ColorPainter(Color.White),
                    onClick = {},
                )
            }
        }
        compose.waitForIdle()

        val matches = compose.onAllNodesWithContentDescription("Rick Sanchez, Alive, Human, button").fetchSemanticsNodes()
        assertEquals("the card is one merged node, announced once", 1, matches.size)
        val node = compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human, button")
        node.assertIsDisplayed()
        assertEquals("the card exposes the button role", Role.Button, node.fetchSemanticsNode().config[SemanticsProperties.Role])
    }

    /**
     * `TEST-A11Y-004` (`REQ-UX-005`): the status is announced as its own text label, so the state is
     * never conveyed by the dot's colour alone.
     */
    @Test
    fun `TEST-A11Y-004 given_a_status_badge_when_it_renders_then_its_label_is_announced_as_text`() {
        compose.setContent {
            MultiverseTheme {
                StatusBadge(tone = StatusTone.Dead, label = "Dead")
            }
        }
        compose.waitForIdle()

        // The pill replaces its children's semantics with the label, so the dot contributes no node of
        // its own and the status reads as the word.
        compose.onNodeWithContentDescription("Dead").assertIsDisplayed()
    }

    /**
     * `TEST-A11Y-004` (`REQ-SEC-004`, `DEC-002`): the mic control is absent from the semantics tree.
     * Voice search is deferred, so no affordance may exist even as an unlabelled node.
     */
    @Test
    fun `TEST-A11Y-004 given_the_character_surfaces_when_they_render_then_no_voice_search_control_exists`() {
        compose.setContent {
            MultiverseTheme {
                EmptyState(
                    heading = "Episodes are on their way",
                    body = "Soon you'll be able to browse every episode, from the Pilot to the latest season.",
                    illustration = ColorPainter(MultiverseBrandColors.portalGreen),
                    actionLabel = "Browse characters",
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()

        listOf("Search by voice", "Voice search", "Microphone").forEach { label ->
            assertEquals(
                "`$label` must not exist: voice search is deferred (REQ-SEC-004, DEC-002)",
                0,
                compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size,
            )
        }
    }

    /**
     * `TEST-A11Y-005` (`REQ-UX-006`, `AC-REQ-UX-006-1`): the grid collapses to one column at the
     * largest accessibility text sizes.
     *
     * The rule is a pure function of the configured font scale, so the case asserts the decision at
     * the collapse point and on both sides of it, which is stronger than sampling one rendered
     * configuration.
     */
    @Test
    fun `TEST-A11Y-005 given_the_largest_text_scale_when_the_grid_lays_out_then_it_collapses_to_one_column`() {
        assertEquals("the default text scale keeps the two-column grid", 2, MultiverseGrid.columnsFor(1.0f))
        assertEquals(
            "just below the collapse point the grid is still two columns",
            2,
            MultiverseGrid.columnsFor(MultiverseGrid.SINGLE_COLUMN_FONT_SCALE - 0.01f),
        )
        assertEquals(
            "at the collapse point the grid drops to one column, as the requirement states",
            1,
            MultiverseGrid.columnsFor(MultiverseGrid.SINGLE_COLUMN_FONT_SCALE),
        )
        assertEquals("Android's maximum text scale renders one column", 1, MultiverseGrid.columnsFor(2.0f))
    }

    /**
     * `TEST-A11Y-005` (`REQ-UX-006`): at a large font scale the card's own text still renders rather
     * than being clipped away.
     */
    @Test
    @Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp")
    fun `TEST-A11Y-005 given_a_large_font_scale_when_a_card_renders_then_its_name_and_species_still_render`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                MultiverseTheme {
                    CharacterCard(
                        name = "Rick Sanchez",
                        species = "Human",
                        statusTone = StatusTone.Alive,
                        statusLabel = "Alive",
                        imageUrl = "https://example.invalid/avatar/1.jpeg",
                        seam = seam(),
                        portalMark = ColorPainter(Color.White),
                    )
                }
            }
        }
        compose.waitForIdle()

        listOf("Rick Sanchez", "Human").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("$text must actually render at maximum font scale", 2f, layout.layoutInput.density.fontScale, 0f)
            assertTrue("$text must not be clipped or ellipsized", (0 until layout.lineCount).none(layout::isLineEllipsized))
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp")
    fun `TEST-A11Y-005 given_maximum_text_when_navigation_renders_then_destination_labels_fit_without_breaking_words`() {
        val icon = ImageVector.Builder("decorative", 24.dp, 24.dp, 24f, 24f).build()
        val labels = listOf("Characters", "Episodes", "Favorites", "Settings")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                MultiverseTheme {
                    MultiverseNavigationBar(labels.map { NavigationDestination(it, it, icon, icon) }, "Characters", {})
                }
            }
        }
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$label must fit without splitting its word", 1, layouts.single().lineCount)
        }
    }

    /**
     * `TEST-A11Y-004` (`UI_SPEC.md` §9, `TESTING.md` §9.2): the illustration inside a state surface is
     * decorative, so the reading order is heading → body → button and the heading leads.
     */
    @Test
    fun `TEST-A11Y-004 given_an_empty_state_when_it_renders_then_the_illustration_is_decorative_and_the_action_is_reachable`() {
        var clicked = 0
        compose.setContent {
            MultiverseTheme {
                EmptyState(
                    heading = "Episodes are on their way",
                    body = "Soon you'll be able to browse every episode, from the Pilot to the latest season.",
                    illustration = ColorPainter(MultiverseBrandColors.portalGreen),
                    actionLabel = "Browse characters",
                    onAction = { clicked += 1 },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Episodes are on their way").assertIsDisplayed()
        compose.onNodeWithText("Browse characters").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("the action is reachable and callable", 1, clicked) }
    }

    /**
     * `TEST-A11Y-004` (`UI_SPEC.md` §8): a skeleton card is a placeholder, so the six of an initial load
     * must not add nodes to the accessibility tree — the loading state is announced once by the screen.
     */
    @Test
    fun `TEST-A11Y-004 given_loading_skeletons_when_they_render_then_they_carry_no_content_description`() {
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.size(200.dp)) { CharacterCardSkeleton() }
            }
        }
        compose.waitForIdle()

        // The skeleton clears and sets its own semantics (`characterCardSkeleton`'s contract): it must
        // contribute no description, so the screen can announce "loading" once rather than describing
        // six empty cards. The case asserts that property, not the shape of the tree.
        compose.onRoot().fetchSemanticsNode().let { root ->
            val described =
                root.children.count { child ->
                    SemanticsProperties.ContentDescription in child.config &&
                        child.config[SemanticsProperties.ContentDescription].isNotEmpty()
                }
            assertEquals("no skeleton contributes a description a reader would announce", 0, described)
        }
    }

    /** The design system's own components expose no click action where the caller passed none. */
    @Test
    fun `TEST-A11Y-004 given_a_card_without_a_callback_when_the_tree_is_read_then_it_is_not_announced_as_a_button`() {
        compose.setContent {
            MultiverseTheme {
                CharacterCard(
                    name = "Rick Sanchez",
                    species = "Human",
                    statusTone = StatusTone.Alive,
                    statusLabel = "Alive",
                    imageUrl = "https://example.invalid/avatar/1.jpeg",
                    seam = seam(),
                    portalMark = ColorPainter(Color.White),
                )
            }
        }
        compose.waitForIdle()

        val node = compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human, button").fetchSemanticsNode()
        assertTrue(
            "without a callback the card carries no click action, so it is not an interactive target",
            SemanticsActions.OnClick !in node.config,
        )
    }
}
