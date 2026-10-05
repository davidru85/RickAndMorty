package io.github.davidru85.multiverse.app.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TEST-UNIT-105` — the system splash draws no icon, so the branded splash is the only one seen
 * (`TASK-127`, `GAP-037`, `REQ-FUNC-007`, `UI_SPEC.md` §6.1).
 *
 * Android 12+ always shows a starting window before the first frame, and it cannot be turned off; what
 * the theme decides is what that window draws. The case resolves the icon the activity's splash theme
 * hands the platform and draws it with the native renderer, so an icon that paints anything — the
 * launcher foreground did — fails, whatever drawable type carries it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain `Application`: the case reads a theme and needs no graph.
@Config(sdk = [36], application = android.app.Application::class)
class SystemSplashIconTest {
    @Test
    fun `TEST-UNIT-105 given_the_splash_theme_when_its_icon_is_drawn_then_no_pixel_is_visible`() {
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Multiverse_Splash)
        val attributes = themed.obtainStyledAttributes(intArrayOf(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon))
        val icon =
            try {
                attributes.getDrawable(0)
            } finally {
                attributes.recycle()
            }
        assertNotNull("the splash theme names the icon the platform draws in the starting window", icon)

        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        requireNotNull(icon).setBounds(0, 0, ICON_PX, ICON_PX)
        icon.draw(Canvas(bitmap))
        val pixels = IntArray(ICON_PX * ICON_PX)
        bitmap.getPixels(pixels, 0, ICON_PX, 0, 0, ICON_PX, ICON_PX)
        val visible = pixels.count { (it ushr 24) != 0 }

        assertEquals(
            "the starting window must show only the Surface background; its icon painted $visible of ${pixels.size} pixels",
            0,
            visible,
        )
    }

    private companion object {
        /** The splash icon's 108 dp canvas at 4×, enough pixels for any painted shape to show. */
        const val ICON_PX = 432
    }
}
