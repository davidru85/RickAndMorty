package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `TEST-UI-016` (the Settings half) and `TEST-UI-012` — the committed screen baselines of
 * `TESTING.md` §8.2.
 *
 * `UI_SPEC.md` §6.5 fixes three sections in one order, and `IC-023` fixes the three states this
 * suite captures: the shipping defaults (Sounds off, `Rest`, no favourites to delete), the same
 * screen with both controls moved and the delete action enabled, and the Delete-favorites
 * confirmation open (`AC-REQ-FUNC-035-1`).
 *
 * Each subject is captured once with the system in light and once in dark; the two must be
 * byte-identical (`AC-REQ-UX-001-1`), because the app renders one palette and never reads the system
 * setting (`UI_SPEC.md` §3.1). Baselines are committed (`DEC-024`): a missing baseline fails
 * `verifyRoborazziDebug` rather than being accepted.
 *
 * The capture runs on Robolectric in native graphics mode, through the same host-test manifest and
 * `test_config.properties` the semantics cases use, so the copy set resolves (`TEST-UI-017`).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same `@Config` shape `SettingsScreenTest` declares — the host run has no merged test manifest
// of its own — with light/dark `qualifiers` chosen per case.
@Config(sdk = [36], qualifiers = "en-w412dp-h2000dp-notnight", application = android.app.Application::class)
class SettingsSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val baselineDir = File("src/androidHostTest/snapshots")

    /** Composes [state] through the real screen and captures the root to `baselineDir`. */
    private fun capture(
        name: String,
        state: SettingsUiState,
    ) {
        compose.setContent {
            MultiverseTheme {
                SettingsScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            file = File(baselineDir, "$name.png"),
            roborazziOptions = RoborazziOptions(),
        )
    }

    /** Captures the open confirmation, which Compose draws in a window of its own. */
    private fun captureDialog(
        name: String,
        state: SettingsUiState,
    ) {
        compose.setContent {
            MultiverseTheme {
                SettingsScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onNode(hasAnyAncestor(isDialog())).captureRoboImage(
            file = File(baselineDir, "$name.png"),
            roborazziOptions = RoborazziOptions(),
        )
    }

    @Test
    fun `TEST-UI-016 given_the_system_in_light_when_the_default_settings_render_then_they_are_snapshotted`() {
        capture("settings-default-light", SettingsUiState())
    }

    @Test
    @Config(qualifiers = "en-w412dp-h2000dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_default_settings_render_then_they_are_snapshotted`() {
        capture("settings-default-dark", SettingsUiState())
    }

    @Test
    fun `TEST-UI-016 given_the_system_in_light_when_the_moved_settings_render_then_they_are_snapshotted`() {
        capture(
            "settings-changed-light",
            SettingsUiState(
                soundsEnabled = true,
                remoteProtocol = RemoteProtocol.GraphQl,
                canDeleteFavorites = true,
            ),
        )
    }

    @Test
    @Config(qualifiers = "en-w412dp-h2000dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_moved_settings_render_then_they_are_snapshotted`() {
        capture(
            "settings-changed-dark",
            SettingsUiState(
                soundsEnabled = true,
                remoteProtocol = RemoteProtocol.GraphQl,
                canDeleteFavorites = true,
            ),
        )
    }

    @Test
    fun `TEST-UI-016 given_the_system_in_light_when_the_delete_confirmation_opens_then_it_is_snapshotted`() {
        captureDialog(
            "settings-confirm-light",
            SettingsUiState(canDeleteFavorites = true, isConfirmingDelete = true),
        )
    }

    @Test
    @Config(qualifiers = "en-w412dp-h2000dp-night")
    fun `TEST-UI-016 given_the_system_in_dark_when_the_delete_confirmation_opens_then_it_is_snapshotted`() {
        captureDialog(
            "settings-confirm-dark",
            SettingsUiState(canDeleteFavorites = true, isConfirmingDelete = true),
        )
    }

    @Test
    fun `TEST-UI-012 given_the_committed_settings_baselines_when_light_and_dark_are_compared_then_they_are_byte_identical`() {
        listOf("settings-default", "settings-changed", "settings-confirm").forEach { subject ->
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
