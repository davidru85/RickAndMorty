package io.github.davidru85.multiverse.app.splash

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w412dp-h891dp", application = android.app.Application::class)
class ReducedMotionSplashTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale { override val scaleFactor = 0f })

    @Test
    fun `TEST-A11Y-006 given_system_animations_disabled_when_splash_waits_then_the_reduced_motion_indicator_still_pulses`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { BrandedSplash(reduceMotion = true) }
        compose.mainClock.advanceTimeBy(100)
        val dim = pixels()
        compose.mainClock.advanceTimeBy(600)
        val bright = pixels()
        assertFalse("the stationary progress indicator must change opacity rather than freeze", dim.contentEquals(bright))
    }

    private fun pixels(): IntArray {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }
}
