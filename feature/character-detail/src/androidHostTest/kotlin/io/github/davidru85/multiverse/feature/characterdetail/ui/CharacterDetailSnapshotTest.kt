package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.CharacterDetailUiState
import io.github.davidru85.multiverse.feature.characterdetail.presentation.InfoRowKind
import io.github.davidru85.multiverse.feature.characterdetail.presentation.InfoRowUi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-016` (the detail half) and `TEST-UI-012` — the committed screen baselines of
 * `TESTING.md` §8.2.
 *
 * One case per state the detail surface can render: `UI_SPEC.md` §6.3's loaded screen, the
 * `Loading` state with the list-provided header already in the first frame (`AC-REQ-FUNC-002-1`),
 * and the two failure shapes of `ERROR_FLOW.md` §4 — the inline retry with the header retained
 * (`AC-REQ-FUNC-002-3`) and the cold failure with nothing cached.
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
class CharacterDetailSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val baselineDir = File("src/androidHostTest/snapshots")

    /** A seam that never touches a loader: the hero draws the same flat painter in every case. */
    private fun seam(): ImageSeam =
        object : ImageSeam {
            @Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = ImageSeamResult.Success(ColorPainter(Color(0xFF2A2A2A)))
        }

    /** The list-provided card the navigation hand-off carries in (`IC-019`). */
    private val header =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Rick Sanchez",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

    /** Composes [state] through the real screen and captures it to `baselineDir`. */
    private fun capture(
        name: String,
        state: CharacterDetailUiState,
        portalMark: Painter = ColorPainter(Color.Black),
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MultiverseTheme {
                    CharacterDetailScreen(
                        state = state,
                        seam = seam(),
                        onIntent = {},
                        onBack = {},
                        onShare = {},
                        portalMark = portalMark,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            file = File(baselineDir, "$name.png"),
            roborazziOptions = RoborazziOptions(),
        )
    }

    /** The `Loading` state: the header is present in the first frame, the list data is pre-filled. */
    private fun loading(): CharacterDetailUiState =
        CharacterDetailUiState(
            header = header,
            episodeCount = 51,
            dimension = "C-137",
            info =
                listOf(
                    InfoRowUi(InfoRowKind.Origin, CopyKeys.DETAIL_INFO_ORIGIN, DisplayText.Data("Earth (C-137)")),
                    InfoRowUi(
                        InfoRowKind.LastKnownLocation,
                        CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION,
                        DisplayText.Data("Citadel of Ricks"),
                    ),
                    InfoRowUi(InfoRowKind.FirstSeenIn, CopyKeys.DETAIL_INFO_FIRST_SEEN_IN, DisplayText.Data("Pilot · S01E01")),
                ),
            loadState = LoadState.Loading,
        )

    /** The loaded screen (`UI_SPEC.md` §6.3): the hero, the three tiles and the three info rows. */
    private fun content(): CharacterDetailUiState =
        CharacterDetailUiState(
            header = header,
            episodeCount = 51,
            dimension = "C-137",
            info =
                listOf(
                    InfoRowUi(InfoRowKind.Origin, CopyKeys.DETAIL_INFO_ORIGIN, DisplayText.Data("Earth (C-137)")),
                    InfoRowUi(
                        InfoRowKind.LastKnownLocation,
                        CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION,
                        DisplayText.Data("Citadel of Ricks"),
                    ),
                    InfoRowUi(InfoRowKind.FirstSeenIn, CopyKeys.DETAIL_INFO_FIRST_SEEN_IN, DisplayText.Data("Pilot · S01E01")),
                ),
            loadState = LoadState.Content,
        )

    /** The failure with the header retained: the inline retry replaces the info list only. */
    private fun errorWithHeader(): CharacterDetailUiState =
        CharacterDetailUiState(
            header = header,
            loadState = LoadState.Error(ApiFailure.Offline),
        )

    /** The cold failure: nothing was cached, so only the inline retry and the FAB render. */
    private fun errorWithoutHeader(): CharacterDetailUiState =
        CharacterDetailUiState(
            header = null,
            loadState = LoadState.Error(ApiFailure.Offline),
        )

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_loading_state_renders_then_it_is_snapshotted`() {
        capture("detail-loading-light", loading())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_loading_state_renders_then_it_is_snapshotted`() {
        capture("detail-loading-dark", loading())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_content_state_renders_then_it_is_snapshotted`() {
        capture("detail-content-light", content())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_content_state_renders_then_it_is_snapshotted`() {
        capture("detail-content-dark", content())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_error_with_header_renders_then_it_is_snapshotted`() {
        capture("detail-error-with-header-light", errorWithHeader())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_error_with_header_renders_then_it_is_snapshotted`() {
        capture("detail-error-with-header-dark", errorWithHeader())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_the_error_without_header_renders_then_it_is_snapshotted`() {
        capture("detail-error-without-header-light", errorWithoutHeader())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_error_without_header_renders_then_it_is_snapshotted`() {
        capture("detail-error-without-header-dark", errorWithoutHeader())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-notnight")
    fun `TEST-A11Y-005 given_maximum_text_in_light_when_detail_renders_then_it_is_snapshotted`() {
        capture("detail-maximum-text-light", content(), fontScale = 2f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h2400dp-night")
    fun `TEST-A11Y-005 given_maximum_text_in_dark_when_detail_renders_then_it_is_snapshotted`() {
        capture("detail-maximum-text-dark", content(), fontScale = 2f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h1600dp-notnight")
    fun `TEST-UI-012 given_the_committed_detail_baselines_when_light_and_dark_are_compared_then_they_are_byte_identical`() {
        listOf(
            "detail-loading",
            "detail-content",
            "detail-maximum-text",
            "detail-error-with-header",
            "detail-error-without-header",
        ).forEach { subject ->
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
