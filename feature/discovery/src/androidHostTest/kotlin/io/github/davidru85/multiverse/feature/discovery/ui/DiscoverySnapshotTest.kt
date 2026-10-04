package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.discovery.presentation.CharacterListUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-016` (the Discovery list-surface half) and `TEST-UI-012` — the committed screen baselines
 * of `TESTING.md` §8.2 (`TASK-045`).
 *
 * `TEST-UI-016` renders every `ERROR_FLOW.md` list-surface state of `AC-REQ-UX-009-1`: the initial
 * loading skeleton, the paging indicator (`isAppending`), the stale banner over `Content`, the
 * designed empty search whose message names the query, and the full-surface `Error` without cache
 * for a transport failure and for a server failure. Each state is captured once with the system in
 * light and once in dark; the two must be byte-identical (`AC-REQ-UX-001-1`, `TEST-UI-012`), because
 * the app renders one palette and never reads the system setting (`UI_SPEC.md` §3.1,
 * `android:forceDarkAllowed="false"`).
 *
 * Baselines are committed (`DEC-024`, `DEC-034`): a missing baseline fails `verifyRoborazziDebug`
 * rather than being accepted. They sit beside this source set, where `TESTING.md` §13.1 places them.
 *
 * The capture runs on Robolectric in native graphics mode and both seams are stubs — the card seam
 * reports the placeholder state and the illustration is transparent — so no case touches a loader or
 * a network (`TESTING.md` §7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverySnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val baselineDir = File("src/androidHostTest/snapshots")

    /** The seam the cards draw with: it never touches a loader, so the portrait stays a placeholder. */
    private val seam =
        object : ImageSeam {
            @Composable
            override fun rememberPainter(
                url: String,
                widthPx: Int,
                heightPx: Int,
            ): ImageSeamResult = ImageSeamResult.Loading
        }

    /** The illustration the empty and error surfaces draw; transparent, so it performs no I/O. */
    private val illustration: Painter = ColorPainter(Color.Transparent)

    /** The three cards the `Content` variants render, matching the fixture shapes of the module. */
    private val cards =
        listOf(
            CharacterCardUi(
                id = CharacterId("1"),
                name = "Rick Sanchez",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/1.jpeg",
            ),
            CharacterCardUi(
                id = CharacterId("2"),
                name = "Morty Smith",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/2.jpeg",
            ),
            CharacterCardUi(
                id = CharacterId("3"),
                name = "Summer Smith",
                species = DisplayText.Data("Human"),
                status = CharacterStatus.Alive,
                statusLabel = CopyKeys.STATUS_ALIVE,
                imageUrl = "https://example.invalid/avatar/3.jpeg",
            ),
        )

    /**
     * The state per `ERROR_FLOW.md`: the initial loading skeleton, the first page, the paging
     * indicator, the stale banner over content, and the two cacheless error variants. It is an
     * ordered list, because the committed baselines and their verification order follow it.
     *
     * [SnapshotCase.firstVisibleItemIndex] positions the grid before capture. Every case but the
     * paging indicator starts at the top; the indicator follows the third card in the grid, so the
     * `appending` case scrolls to it — otherwise the contained indicator would sit below the fold and
     * the baseline would not evidence the state (`AC-REQ-UX-009-1`).
     */
    private val states: List<SnapshotCase> =
        listOf(
            SnapshotCase("loading", CharacterListUiState(loadState = LoadState.Loading)),
            SnapshotCase(
                "content",
                CharacterListUiState(items = cards, totalCount = 3, loadState = LoadState.Content),
            ),
            SnapshotCase(
                "appending",
                CharacterListUiState(
                    items = cards,
                    totalCount = 3,
                    loadState = LoadState.Content,
                    isAppending = true,
                ),
                firstVisibleItemIndex = cards.size,
            ),
            SnapshotCase(
                "stale",
                CharacterListUiState(
                    items = cards,
                    totalCount = 3,
                    loadState = LoadState.Content,
                    isStale = true,
                ),
            ),
            SnapshotCase(
                "empty",
                CharacterListUiState(
                    filter = CharacterFilter(query = "rick"),
                    loadState = LoadState.Empty,
                ),
            ),
            SnapshotCase("error-offline", CharacterListUiState(loadState = LoadState.Error(ApiFailure.Offline))),
            SnapshotCase("error-server", CharacterListUiState(loadState = LoadState.Error(ApiFailure.Server(503)))),
        )

    /**
     * Composes the screen once on the theme with a state and grid holder, captures the first case,
     * then swaps both holders and captures each remaining one. The Compose test rule allows a single
     * `setContent` per case, so every state of a case is rendered through these two holders.
     */
    private fun capture(
        first: SnapshotCase,
        rest: List<SnapshotCase>,
    ) {
        val holder = mutableStateOf(first.state)
        val gridHolder = mutableStateOf(LazyStaggeredGridState(first.firstVisibleItemIndex))
        compose.setContent {
            MultiverseTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DiscoveryScreen(
                        state = holder.value,
                        onIntent = {},
                        seam = seam,
                        illustration = illustration,
                        gridState = gridHolder.value,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            file = File(baselineDir, "${first.name}.png"),
            roborazziOptions = RoborazziOptions(),
        )
        rest.forEach { case ->
            holder.value = case.state
            gridHolder.value = LazyStaggeredGridState(case.firstVisibleItemIndex)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(
                file = File(baselineDir, "${case.name}.png"),
                roborazziOptions = RoborazziOptions(),
            )
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-notnight", manifest = "AndroidManifest.xml")
    fun `TEST-UI-016 given_the_system_in_light when every list state renders then they are snapshotted`() {
        captureLight()
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-night", manifest = "AndroidManifest.xml")
    fun `TEST-UI-016 given_the_system_in_dark when every list state renders then they are snapshotted`() {
        captureDark()
    }

    /** Captures every state of [states] with the `-light` suffix, through one composition. */
    private fun captureLight() {
        capture(
            first = states.first().withSuffix("light"),
            rest = states.drop(1).map { it.withSuffix("light") },
        )
    }

    /** Captures every state of [states] with the `-dark` suffix, through one composition. */
    private fun captureDark() {
        capture(
            first = states.first().withSuffix("dark"),
            rest = states.drop(1).map { it.withSuffix("dark") },
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-notnight", manifest = "AndroidManifest.xml")
    fun `TEST-UI-012 given_the_committed_baselines when light and dark are compared then they are byte identical`() {
        states.map { it.name }.forEach { state ->
            val subject = "discovery-$state"
            val light = File(baselineDir, "$subject-light.png")
            val dark = File(baselineDir, "$subject-dark.png")
            assertTrue(
                "the `$subject` baselines must both be committed (`TESTING.md` §8.2); " +
                    "missing: ${listOf(light, dark).filterNot { it.isFile }}",
                light.isFile && dark.isFile,
            )
            assertArrayEquals(
                "the `$subject` render must not vary with the system light/dark setting " +
                    "(AC-REQ-UX-001-1): the app renders one palette and never reads the system setting",
                light.readBytes(),
                dark.readBytes(),
            )
        }
    }

    /** One committed baseline: the state to render and the grid position it is captured at. */
    private data class SnapshotCase(
        val name: String,
        val state: CharacterListUiState,
        val firstVisibleItemIndex: Int = 0,
    ) {
        /** This case with the light/dark qualifier folded into its committed baseline name. */
        fun withSuffix(suffix: String): SnapshotCase = copy(name = "discovery-$name-$suffix")
    }
}
