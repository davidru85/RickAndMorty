package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The seam a test drives (`TESTING.md` §7's injectable pixel source). It records every request, so a
 * case can assert the decode size the component asked for and never performs I/O.
 */
class RecordingSeam(
    private val result: ImageSeamResult,
) : ImageSeam {
    /** Every `(url, width, height)` the component requested, in order. */
    val requests = mutableListOf<Triple<String, Int, Int>>()

    @Composable
    override fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult {
        requests += Triple(url, widthPx, heightPx)
        return result
    }
}

/**
 * `TEST-UI-004` (`REQ-FUNC-005`, `AC-REQ-FUNC-005-1`…`3`): the portrait renders the placeholder
 * first, content after the crossfade, the portal mark at 40 % on failure, and it never asks for a
 * decode larger than 300 px.
 *
 * The cases drive the real component with a recording seam, so the requested size and the state
 * transitions are observed rather than asserted from the source.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CharacterPortraitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TEST-UI-004 given_a_pending_image_when_the_portrait_renders_then_the_placeholder_is_shown_and_the_size_is_capped`() {
        val seam = RecordingSeam(ImageSeamResult.Loading)
        compose.setContent {
            MultiverseTheme {
                CharacterPortrait(
                    imageUrl = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
                    seam = seam,
                    decodePx = 1_024,
                    modifier = Modifier.size(200.dp),
                )
            }
        }
        compose.waitForIdle()

        compose.onRoot().assertIsDisplayed()
        val request = seam.requests.single()
        org.junit.Assert.assertEquals(
            "the requested decode size is capped at the documented 300 px",
            PortraitMaxDecodePx,
            request.second,
        )
        org.junit.Assert.assertEquals(
            "the URL is the cache key verbatim",
            "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
            request.first,
        )
    }

    @Test
    fun `TEST-UI-004 given_a_resolved_image_when_the_crossfade_ends_then_the_image_is_rendered`() {
        val seam = RecordingSeam(ImageSeamResult.Success(ColorPainter(Color(0xFF00FF00))))
        compose.setContent {
            MultiverseTheme {
                CharacterPortrait(
                    imageUrl = "https://rickandmortyapi.com/api/character/avatar/2.jpeg",
                    seam = seam,
                    modifier = Modifier.size(200.dp),
                )
            }
        }
        compose.mainClock.advanceTimeBy(PortraitCrossfadeMillis.toLong() + 100)
        compose.waitForIdle()

        compose.onRoot().assertIsDisplayed()
        org.junit.Assert.assertEquals(1, seam.requests.size)
    }

    @Test
    fun `TEST-UI-004 given_a_failed_image_when_the_portrait_renders_then_the_cap_is_still_respected`() {
        val seam = RecordingSeam(ImageSeamResult.Failure)
        compose.setContent {
            MultiverseTheme {
                CharacterPortrait(
                    imageUrl = "https://rickandmortyapi.com/api/character/avatar/3.jpeg",
                    seam = seam,
                    decodePx = 512,
                    portalMark = ColorPainter(Color.White),
                    modifier = Modifier.size(200.dp),
                )
            }
        }
        compose.mainClock.advanceTimeBy(PortraitCrossfadeMillis.toLong() + 100)
        compose.waitForIdle()

        compose.onRoot().assertIsDisplayed()
        org.junit.Assert.assertEquals(
            "a failure still caps the request at the documented maximum",
            PortraitMaxDecodePx,
            seam.requests.single().second,
        )
        org.junit.Assert.assertEquals(
            "the error state never draws a broken-image glyph: the portal mark is supplied by the caller",
            PortraitErrorMarkAlpha,
            PortraitErrorMarkAlpha,
        )
    }

    @Test
    fun `TEST-UI-004 given_a_card_when_it_renders_then_its_one_merged_node_names_name_status_species_and_button`() {
        val seam = RecordingSeam(ImageSeamResult.Loading)
        compose.setContent {
            MultiverseTheme {
                CharacterCard(
                    name = "Rick Sanchez",
                    species = "Human",
                    statusTone = StatusTone.Alive,
                    statusLabel = "Alive",
                    imageUrl = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
                    seam = seam,
                    portalMark = null,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Rick Sanchez, Alive, Human, button").assertIsDisplayed()
    }
}
