package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKey
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.favorites.presentation.FavoritesUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-016` (the Favorites half) and `TEST-UI-012` — the committed section baselines of
 * `TESTING.md` §8.2.
 *
 * One case per state `IC-020` can hold: `UI_SPEC.md` §6.4's designed empty state, the `Loading`
 * hold, the full-surface error of `ERROR_FLOW.md` §4, and the populated two-column grid of three
 * favourite cards — the same card contract Discovery's grid uses (`REQ-FUNC-006`).
 *
 * Each subject is captured once with the system in light and once in dark; the two must be
 * byte-identical (`AC-REQ-UX-001-1`), because the app renders one palette and never reads the system
 * setting (`UI_SPEC.md` §3.1). Baselines are committed (`DEC-024`): a missing baseline fails
 * `verifyRoborazziDebug` rather than being accepted.
 *
 * The capture runs on Robolectric in native graphics mode and the image seam is a stub, so no case
 * touches a loader or a network (`TESTING.md` §7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val baselineDir = File("src/androidHostTest/snapshots")

    /** A seam that never touches a loader: every card draws the same flat painter. */
    private fun seam(): ImageSeam =
        object : ImageSeam {
            @Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = ImageSeamResult.Success(ColorPainter(Color(0xFF2A2A2A)))
        }

    /** The empty state's illustration, which the shell supplies (`DEC-097`). */
    private val illustration: Painter = ColorPainter(Color(0xFF3DDC84))

    private fun card(
        id: String,
        name: String,
        status: CharacterStatus,
        species: String,
        label: CopyKey,
    ) = CharacterCardUi(
        id = CharacterId(id),
        name = name,
        species = DisplayText.Data(species),
        status = status,
        statusLabel = label,
        imageUrl = "https://example.invalid/avatar/$id.jpeg",
    )

    /** Composes [state] through the real section and captures it to `baselineDir`. */
    private fun capture(
        name: String,
        state: FavoritesUiState,
    ) {
        compose.setContent {
            MultiverseTheme {
                FavoritesScreen(
                    state = state,
                    seam = seam(),
                    onCharacterSelected = {},
                    onIntent = {},
                    onBrowseCharacters = {},
                    illustration = illustration,
                    portalMark = ColorPainter(Color.Black),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            file = File(baselineDir, "$name.png"),
            roborazziOptions = RoborazziOptions(),
        )
    }

    /** The three favourite cards the populated state carries (`IC-020`, `IC-016`). */
    private fun threeCards(): List<CharacterCardUi> =
        listOf(
            card("1", "Rick Sanchez", CharacterStatus.Alive, "Human", CopyKeys.STATUS_ALIVE),
            card("2", "Morty Smith", CharacterStatus.Alive, "Human", CopyKeys.STATUS_ALIVE),
            card("3", "Birdperson", CharacterStatus.Dead, "Bird Person", CopyKeys.STATUS_DEAD),
        )

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_empty_state_renders_then_it_is_snapshotted`() {
        capture("favorites-empty-light", FavoritesUiState(loadState = LoadState.Empty))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_empty_state_renders_then_it_is_snapshotted`() {
        capture("favorites-empty-dark", FavoritesUiState(loadState = LoadState.Empty))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_loading_state_renders_then_it_is_snapshotted`() {
        capture("favorites-loading-light", FavoritesUiState(loadState = LoadState.Loading))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_loading_state_renders_then_it_is_snapshotted`() {
        capture("favorites-loading-dark", FavoritesUiState(loadState = LoadState.Loading))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_error_state_renders_then_it_is_snapshotted`() {
        capture("favorites-error-light", FavoritesUiState(loadState = LoadState.Error(ApiFailure.Offline)))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_error_state_renders_then_it_is_snapshotted`() {
        capture("favorites-error-dark", FavoritesUiState(loadState = LoadState.Error(ApiFailure.Offline)))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_content_grid_renders_then_it_is_snapshotted`() {
        capture("favorites-content-light", FavoritesUiState(items = threeCards(), loadState = LoadState.Content))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_content_grid_renders_then_it_is_snapshotted`() {
        capture("favorites-content-dark", FavoritesUiState(items = threeCards(), loadState = LoadState.Content))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-012 given_the_committed_favorites_baselines_when_light_and_dark_are_compared_then_they_are_byte_identical`() {
        listOf("favorites-empty", "favorites-loading", "favorites-error", "favorites-content").forEach { subject ->
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
