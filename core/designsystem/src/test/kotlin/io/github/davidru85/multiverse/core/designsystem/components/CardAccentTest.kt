package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.image.CharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.image.LocalCharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * `TEST-UI-034` — the card's container is its portrait's accent (`UI_SPEC.md` §5.4, Figma `20:1735`,
 * `TASK-113`).
 *
 * `accentFor()` had no production caller, so every card stayed Surface Container High where Figma tints
 * each one with its portrait's tone-30 colour. The card now asks the policy the shell provides, shows
 * Surface Container High until it answers, and then animates to the accent.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class CardAccentTest {
    @get:Rule
    val compose = createComposeRule()

    /** A portrait of one saturated colour, so its accent is a distinct, deterministic tint. */
    private val portrait = IntArray(64 * 64) { 0xFFB0302A.toInt() }

    private fun containerColorOfCard(policy: CharacterAccentPolicy?): Color {
        compose.setContent {
            MultiverseTheme {
                CompositionLocalProvider(LocalCharacterAccentPolicy provides policy) {
                    Box(modifier = Modifier.width(200.dp)) {
                        CharacterCard(
                            name = "Rick Sanchez",
                            species = "Human",
                            statusTone = StatusTone.Alive,
                            statusLabel = "Alive",
                            imageUrl = "https://example.invalid/avatar/1.jpeg",
                            seam = RecordingSeam(ImageSeamResult.Loading),
                            onClick = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val image = compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human").captureToImage().toPixelMap()
        // A point inside the card's bottom corner region, below the text and clear of the 28 corner.
        return image[image.width / 2, image.height - 4]
    }

    private fun assertClose(
        message: String,
        expected: Color,
        actual: Color,
    ) {
        val close =
            abs(expected.red - actual.red) < 0.03f && abs(expected.green - actual.green) < 0.03f && abs(expected.blue - actual.blue) < 0.03f
        assertEquals(message, true to Integer.toHexString(expected.toArgb()), close to Integer.toHexString(actual.toArgb()))
    }

    @Test
    fun `TEST-UI-034 given_the_shells_accent_policy_when_a_card_renders_then_its_container_is_the_portraits_accent`() {
        val policy = CharacterAccentPolicy(pixels = { portrait }, dispatcher = Dispatchers.Unconfined)
        val expected = runBlocking { CharacterAccentPolicy(pixels = { portrait }, dispatcher = Dispatchers.Unconfined).accentFor("x") }

        assertClose("TEST-UI-034: the card is tinted with its portrait's tone-30 accent", expected, containerColorOfCard(policy))
    }

    @Test
    fun `TEST-UI-034 given_no_accent_policy_when_a_card_renders_then_it_stays_surface_container_high`() {
        assertClose(
            "TEST-UI-034: without a policy the card keeps its neutral container",
            MultiverseColors.surfaceContainerHigh,
            containerColorOfCard(null),
        )
    }
}
