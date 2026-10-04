package io.github.davidru85.multiverse.app.snapshots

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.app.navigation.MultiverseApp
import io.github.davidru85.multiverse.app.splash.BrandedSplash
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-012` and `UI_SPEC.md` §1.1 — the app-shell baselines (`TASK-045`).
 *
 * The shell owns two of the specification's screens: the Splash (§1.1 "01 · Splash") and the
 * four-destination shell, captured with the Characters destination selected. The splash is captured
 * on both motion paths — the portal spin, and the Reduce Motion pulse that replaces it
 * (`AC-REQ-UX-007-1`) — because the setting substitutes the drawn signal.
 *
 * Each subject is captured once with the system in light and once in dark; the two must be
 * byte-identical (`REQ-UX-001`, `AC-REQ-UX-001-1`), because the app renders one palette and never
 * reads the system setting (`UI_SPEC.md` §3.1, `android:forceDarkAllowed="false"`).
 *
 * Baselines are committed (`DEC-024`): a missing baseline fails `verifyRoborazziDebug` rather than
 * being accepted. They sit beside this source set, where `TESTING.md` §13.1 places them.
 *
 * The shell resolves its Discovery ViewModel through Koin, so the case starts the same graph the
 * application does and then substitutes four bindings for determinism: the character repository is
 * the shared `FakeCharacterRepository`, the two stores are the shared fakes, and the reducer's
 * dispatcher is the test's main dispatcher, so the first page is rendered synchronously and the
 * baseline never depends on a network, a disk or a scheduler (`TESTING.md` §7). Nothing of the
 * substitution reaches a production source; only this test's graph is affected.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class ShellSnapshotTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                },
        )

    private val baselineDir = File("src/test/snapshots")

    /**
     * The `splash-still`/`splash-spin` and `shell-characters` subjects, in capture order. `TEST-UI-012`
     * walks this list, so a subject can never be added without its byte-identity proof.
     */
    private val subjects = listOf("splash-still", "splash-spin", "shell-characters", "shell-episodes", "shell-maximum-text")

    /**
     * Starts the shell's graph with the deterministic substitutes. The real shell module still builds
     * the platform inputs (the Ktor client and the stores), as it does in production; the three
     * overrides below are the only difference, and they are applied after it so they win.
     */
    @Before
    fun startGraph() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val catalogue =
            FakeCatalogue(
                listOf(
                    FakeCatalogue.character("1", name = "Rick Sanchez"),
                    FakeCatalogue.character("2", name = "Morty Smith"),
                    FakeCatalogue.character("3", name = "Summer Smith"),
                ),
            )
        startKoin {
            androidContext(context)
            modules(coreModule)
            modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
            modules(discoveryModule, discoveryViewModelModule)
            modules(
                module {
                    // The first page the shell renders, with no loader and no network behind it.
                    single<CharacterRepository> { FakeCharacterRepository(catalogue) }
                    // Both stores are the shared fakes, so no DataStore file is opened or read.
                    single<AppSettingsLocalDataSource> { FakeAppSettingsStore() }
                    single<FavoritesLocalDataSource> { FakeFavoritesStore() }
                    // The reducer's dispatcher, so the first page is published before the capture.
                    single<CoroutineDispatcher> { Dispatchers.Main.immediate }
                },
            )
        }
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    /** Composes [content] and captures it to `baselineDir` under [name]. */
    private fun capture(
        name: String,
        selectEpisodes: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { content() }
        }
        compose.waitForIdle()
        if (selectEpisodes) {
            compose.onNodeWithText("Episodes").performClick()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(baselineDir, "$name.png"))
    }

    /** The splash on its Reduce Motion path: the pulse, at the cycle's start. */
    @Composable
    private fun stillSplash() {
        MultiverseTheme {
            BrandedSplash(modifier = Modifier.fillMaxSize(), reduceMotion = true)
        }
    }

    /** The splash on its spin path: the portal rotation, at the cycle's start. */
    @Composable
    private fun spinningSplash() {
        MultiverseTheme {
            BrandedSplash(modifier = Modifier.fillMaxSize(), reduceMotion = false)
        }
    }

    /** The four-destination shell with Characters selected; no gate, so the splash does not overlay it. */
    @Composable
    private fun shell() {
        MultiverseApp(navController = rememberNavController(), splashGate = null)
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-UI-012 given_the_system_in_light when the reduce motion splash renders then it is snapshotted`() {
        capture("splash-still-light") { stillSplash() }
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `TEST-UI-012 given_the_system_in_dark when the reduce motion splash renders then it is snapshotted`() {
        capture("splash-still-dark") { stillSplash() }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-UI-012 given_the_system_in_light when the spinning splash renders then it is snapshotted`() {
        capture("splash-spin-light") { spinningSplash() }
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `TEST-UI-012 given_the_system_in_dark when the spinning splash renders then it is snapshotted`() {
        capture("splash-spin-dark") { spinningSplash() }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-UI-012 given_the_system_in_light when the shell renders then the characters destination is snapshotted`() {
        capture("shell-characters-light") { shell() }
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `TEST-UI-012 given_the_system_in_dark when the shell renders then the characters destination is snapshotted`() {
        capture("shell-characters-dark") { shell() }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-UI-016 given_the_system_in_light_when_episodes_is_selected_then_the_destination_is_snapshotted`() {
        capture("shell-episodes-light", selectEpisodes = true) { shell() }
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_episodes_is_selected_then_the_destination_is_snapshotted`() {
        capture("shell-episodes-dark", selectEpisodes = true) { shell() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h891dp-notnight")
    fun `TEST-UI-014 given_maximum_text_in_light_when_the_shell_renders_then_navigation_is_snapshotted`() {
        capture("shell-maximum-text-light", fontScale = 2f) { shell() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h891dp-night")
    fun `TEST-UI-014 given_maximum_text_in_dark_when_the_shell_renders_then_navigation_is_snapshotted`() {
        capture("shell-maximum-text-dark", fontScale = 2f) { shell() }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-A11Y-006 given_reduce_motion_when_a_destination_changes_then_the_old_screen_leaves_after_the_crossfade`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = false
        compose.setContent { shell() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithText("Episodes").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.waitForIdle()
        assertEquals(
            "after the 300 ms reduced-motion crossfade only the navigation label remains",
            1,
            compose.onAllNodesWithText("Characters").fetchSemanticsNodes().size,
        )
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `TEST-UI-012 given_the_committed_baselines when light and dark are compared then they are byte identical`() {
        subjects.forEach { subject ->
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
