package io.github.davidru85.multiverse.app.splash

import android.app.Application
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-100` — Reduce Motion read as observed state (`REQ-UX-007`, `AC-REQ-UX-007-1`, `TASK-115`).
 *
 * `rememberReduceMotion` read the animator duration scale once and remembered it, so turning animations
 * off while the app ran changed nothing until the screen was rebuilt. The setting is now observed: a
 * change reaches the composition that reads it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class ReduceMotionObservationTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `TEST-UNIT-100 given_animations_on_when_they_are_turned_off_while_shown_then_reduce_motion_follows`() {
        setAnimatorScale(1f)
        compose.setContent { Text(if (rememberReduceMotion()) "reduced" else "full") }
        compose.onNodeWithText("full").assertExists()

        setAnimatorScale(0f)
        compose.waitForIdle()

        compose.onNodeWithText("reduced").assertExists()
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }
}
