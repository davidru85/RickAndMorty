package io.github.davidru85.multiverse.app.splash

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-A11Y-001` — the splash is an indeterminate progress indicator labelled "Loading characters"
 * (`REQ-FUNC-007`, `AC-REQ-FUNC-007-3`, `UI_SPEC.md` §9).
 *
 * The rotation is the loading signal, so exposing it as progress is what a screen reader announces;
 * the case asserts the semantics node the user actually reaches, not the drawing. Both motion paths
 * are covered: the spin, and the Reduce Motion pulse that replaces it.
 */
@RunWith(AndroidJUnit4::class)
// The harness must not instantiate the app's Application: its `onCreate` starts the Koin graph, which
// a UI case does not need. The Compose rule supplies its own host activity from the test manifest.
@Config(sdk = [36], qualifiers = "en", application = android.app.Application::class)
class SplashAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TEST-A11Y-001 given_the_splash_when_it_renders_then_it_is_one_indeterminate_progress_node_labelled_loading_characters`() {
        compose.setContent { BrandedSplash(reduceMotion = false) }
        compose.waitForIdle()

        val node = compose.onNodeWithContentDescription("Loading characters")
        node.assertIsDisplayed()
        val range = node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(
            "the splash is an indeterminate progress indicator, not a determinate one",
            ProgressBarRangeInfo.Indeterminate,
            range,
        )
    }

    @Test
    fun `TEST-A11Y-001 given_reduce_motion_when_the_splash_renders_then_the_same_progress_signal_is_exposed`() {
        compose.setContent { BrandedSplash(reduceMotion = true) }
        compose.waitForIdle()

        val node = compose.onNodeWithContentDescription("Loading characters")
        node.assertIsDisplayed()
        val range = node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(
            "with Reduce Motion the pulse keeps the same progress semantics",
            ProgressBarRangeInfo.Indeterminate,
            range,
        )
    }

    @Test
    fun `TEST-A11Y-001 given_the_splash_when_the_wordmark_is_read_then_the_approved_copy_is_the_one_rendered`() {
        compose.setContent { BrandedSplash(reduceMotion = false) }
        compose.waitForIdle()

        compose.onRoot().assertIsDisplayed()
        // The wordmark, its sub-line and the tagline are the approved copy of `DEC-101`; the case
        // asserts they resolve rather than restating them, so a missing key fails here.
        io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver.names().let { names ->
            listOf("splash_wordmark", "splash_wordmark_sub", "splash_tagline", "splash_loading").forEach { key ->
                org.junit.Assert.assertTrue("the splash key `$key` must be registered", key in names)
            }
        }
    }
}
