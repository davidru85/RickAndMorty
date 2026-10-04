package io.github.davidru85.multiverse.core.designsystem.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.davidru85.multiverse.core.designsystem.components.CardHeight
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCard
import io.github.davidru85.multiverse.core.designsystem.components.CharacterCardSkeleton
import io.github.davidru85.multiverse.core.designsystem.components.CharacterPortrait
import io.github.davidru85.multiverse.core.designsystem.components.EmptyState
import io.github.davidru85.multiverse.core.designsystem.components.InfoListGroup
import io.github.davidru85.multiverse.core.designsystem.components.InfoListItem
import io.github.davidru85.multiverse.core.designsystem.components.StatTileRow
import io.github.davidru85.multiverse.core.designsystem.components.StatusBadge
import io.github.davidru85.multiverse.core.designsystem.components.StatusTone
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-016` (component half) and `TEST-UI-012` — the committed component baselines of
 * `TESTING.md` §8.2 and the single-appearance proof of `REQ-UX-001`.
 *
 * Coverage is owned by `TESTING.md` §8.2: `CharacterCard` (both heights), `StatusBadge` (all three
 * tones), `StatTile`, `InfoListItem`, the skeleton cards, the portrait states and the empty state.
 * Each subject is captured once with the system in light and once in dark; the two must be
 * byte-identical (`AC-REQ-UX-001-1`), because the app renders one palette and never reads the
 * system setting (`UI_SPEC.md` §3.1, `android:forceDarkAllowed="false"`).
 *
 * Baselines are committed (`DEC-024`): a missing baseline fails `verifyRoborazziDebug` rather than
 * being accepted. They sit beside this source set, where `TESTING.md` §13.1 places them.
 *
 * The capture runs on Robolectric in native graphics mode and the image seam is a stub, so no case
 * touches a loader or a network (`TESTING.md` §7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val baselineDir = File("src/test/snapshots")

    /** A seam that never touches a loader: the portrait draws whatever the case says it should. */
    private fun seam(result: ImageSeamResult = ImageSeamResult.Loading) =
        object : ImageSeam {
            @Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = result
        }

    /** Composes [content] on the theme at a fixed inset and captures it to `baselineDir`. */
    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            MultiverseTheme {
                Column(
                    modifier = Modifier.background(MultiverseColors.surface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        if (name.startsWith("components")) {
            val regular = compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human, button").fetchSemanticsNode().boundsInRoot
            val tall = compose.onNodeWithContentDescription("Morty Smith, Unknown, Human, button").fetchSemanticsNode().boundsInRoot
            assertTrue("both cards must be visible without covering each other in the catalogue", !regular.overlaps(tall))
        }
        compose.onRoot().captureRoboImage(
            file = File(baselineDir, "$name.png"),
            roborazziOptions = RoborazziOptions(),
        )
    }

    /** The component catalogue of `TESTING.md` §8.2, on the neutral container. */
    @Composable
    private fun components() {
        val seam = seam()
        val glyph: Painter = ColorPainter(MultiverseColors.onSurface)

        CharacterCard(
            name = "Rick Sanchez",
            species = "Human",
            statusTone = StatusTone.Alive,
            statusLabel = "Alive",
            imageUrl = "https://example.invalid/avatar/1.jpeg",
            seam = seam,
        )
        CharacterCard(
            name = "Morty Smith",
            species = "Human",
            statusTone = StatusTone.Unknown,
            statusLabel = "Unknown",
            imageUrl = "https://example.invalid/avatar/2.jpeg",
            seam = seam,
            height = CardHeight.Tall,
        )
        CharacterCardSkeleton()
        StatusBadge(tone = StatusTone.Alive, label = "Alive")
        StatusBadge(tone = StatusTone.Dead, label = "Dead")
        StatusBadge(tone = StatusTone.Unknown, label = "Unknown")
        StatTileRow(
            tiles =
                listOf(
                    Triple("42", "Episodes", MultiverseColors.primaryContainer to MultiverseColors.onPrimaryContainer),
                    Triple(
                        "Human",
                        "Species",
                        MultiverseColors.secondaryContainer to MultiverseColors.onSecondaryContainer,
                    ),
                    Triple(
                        "Earth",
                        "Origin",
                        MultiverseColors.tertiaryContainer to MultiverseColors.onTertiaryContainer,
                    ),
                ),
        )
        InfoListGroup {
            InfoListItem(label = "Status", value = "Alive", icon = glyph)
            InfoListItem(label = "Species", value = "Human", icon = glyph)
        }
        EmptyState(
            heading = "Episodes are on their way",
            body = "Soon you'll be able to browse every episode, from the Pilot to the latest season.",
            illustration = ColorPainter(MultiverseBrandColors.portalGreen),
            actionLabel = "Browse characters",
            onAction = {},
        )
    }

    /**
     * The skeleton cards of the "Initial loading" state (`UI_SPEC.md` §8) and the portrait's three
     * states (`UI_SPEC.md` §5.1): placeholder, content, portal-mark error.
     */
    @Composable
    private fun loadingAndImageStates() {
        CharacterCardSkeleton(height = CardHeight.Tall)
        CharacterPortrait(
            imageUrl = "https://example.invalid/avatar/1.jpeg",
            seam = seam(ImageSeamResult.Loading),
            portalMark = ColorPainter(Color.White),
            modifier = Modifier.size(200.dp),
        )
        CharacterPortrait(
            imageUrl = "https://example.invalid/avatar/2.jpeg",
            seam = seam(ImageSeamResult.Success(ColorPainter(MultiverseBrandColors.portalGreen))),
            portalMark = ColorPainter(Color.White),
            modifier = Modifier.size(200.dp),
        )
        CharacterPortrait(
            imageUrl = "https://example.invalid/avatar/3.jpeg",
            seam = seam(ImageSeamResult.Failure),
            portalMark = ColorPainter(Color.White),
            modifier = Modifier.size(200.dp),
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_every_component_renders_then_the_catalogue_is_snapshotted`() {
        capture("components-light") { components() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_every_component_renders_then_the_catalogue_is_snapshotted`() {
        capture("components-dark") { components() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_loading_and_image_states_render_then_they_are_snapshotted`() {
        capture("states-light") { loadingAndImageStates() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_loading_and_image_states_render_then_they_are_snapshotted`() {
        capture("states-dark") { loadingAndImageStates() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-notnight")
    fun `TEST-UI-012 given_the_committed_baselines_when_light_and_dark_are_compared_then_they_are_byte_identical`() {
        listOf("components", "states").forEach { subject ->
            val light = File(baselineDir, "$subject-light.png")
            val dark = File(baselineDir, "$subject-dark.png")
            assertTrue(
                "the `$subject` baselines must both be committed (`TESTING.md` §8.2); " +
                    "missing: ${listOf(light, dark).filterNot { it.isFile }}",
                light.isFile && dark.isFile,
            )
            assertArrayEquals(
                "the `$subject` render must not vary with the system light/dark setting (AC-REQ-UX-001-1): " +
                    "the app renders one palette and never reads the system setting",
                light.readBytes(),
                dark.readBytes(),
            )
        }
    }
}
