package io.github.davidru85.multiverse.feature.settings.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-044` — the data-source picker marks its selection with a check, not with colour alone
 * (`TASK-132`, `GAP-042`, `AC-REQ-FUNC-034-1`, `REQ-UX-005`, `UI_SPEC.md` §4.1).
 *
 * On a fresh install the state is REST, but the selected segment rendered as a pale pill without a check
 * and the other as the saturated green the filter chips use for *their* selection, so GraphQL read as
 * the choice. The cases find the check glyph and the segment it sits in.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h2000dp", application = android.app.Application::class)
class SettingsProtocolSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun copy(key: String): String =
        org.robolectric.RuntimeEnvironment
            .getApplication()
            .getString(requireNotNull(CopyResolver.resourceId(key)))

    private fun render(state: SettingsUiState) {
        compose.setContent { MultiverseTheme { SettingsScreen(state = state, onIntent = {}) } }
        compose.waitForIdle()
    }

    /** The one check glyph's bounds; the case fails when there is none or more than one. */
    private fun checkBounds(): Rect {
        val checks = compose.onAllNodesWithTag(PROTOCOL_CHECK, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("TEST-UI-044: exactly one segment carries the check", 1, checks.size)
        return checks.single().boundsInRoot
    }

    private fun segmentBounds(label: String): Rect =
        compose
            .onNodeWithText(label)
            .fetchSemanticsNode()
            .boundsInRoot

    @Test
    fun `TEST-UI-044 given_a_fresh_install_when_the_picker_renders_then_REST_carries_the_check`() {
        render(SettingsUiState())

        val check = checkBounds()
        val rest = segmentBounds(copy(CopyKeys.SETTINGS_DATA_REST.value))
        assertTrue("TEST-UI-044: the check sits in the REST API segment, the default ($check in $rest)", rest.contains(check.center))
    }

    @Test
    fun `TEST-UI-044 given_GraphQL_chosen_when_the_picker_renders_then_GraphQL_carries_the_check`() {
        render(SettingsUiState(remoteProtocol = RemoteProtocol.GraphQl))

        val check = checkBounds()
        val graphQl = segmentBounds(copy(CopyKeys.SETTINGS_DATA_GRAPHQL.value))
        assertTrue("TEST-UI-044: the check follows the selection to GraphQL ($check in $graphQl)", graphQl.contains(check.center))
    }

    private companion object {
        const val PROTOCOL_CHECK = "settings.protocol.check"
    }
}
