package io.github.davidru85.multiverse.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.davidru85.multiverse.app.debug.DiagnosticsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * `TEST-UNIT-033`'s debug half — the diagnostics panel stays reachable in a debug build without a
 * launcher icon of its own (`REQ-OBS-002`, `OBSERVABILITY.md` §5, `TASK-117`).
 *
 * The panel is reached through a static shortcut on the app's one launcher entry (long-press the
 * icon), declared by the debug manifest overlay alone, so a release artifact carries neither the
 * shortcut nor its target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DiagnosticsEntryTest {
    @Test
    fun `TEST-UNIT-033 given_a_debug_build_when_the_shell_entry_is_read_then_a_shortcut_opens_the_diagnostics_panel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shell =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.GET_META_DATA,
            )

        val shortcuts = shell.metaData?.getInt("android.app.shortcuts") ?: 0
        assertNotEquals("the shell entry must declare the debug shortcuts", 0, shortcuts)
        assertEquals(
            "the shortcut must open the diagnostics panel",
            listOf(DiagnosticsActivity::class.java.name),
            shortcutTargets(context, shortcuts),
        )
    }

    @Test
    fun `TEST-UNIT-033 given_a_debug_build_when_the_panel_is_read_then_it_is_reachable_by_its_component_name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val panel = context.packageManager.getActivityInfo(ComponentName(context, DiagnosticsActivity::class.java), 0)

        assertTrue("the panel must stay startable by its explicit name (adb am start -n)", panel.exported)
    }

    /** The `android:targetClass` of every `<intent>` in the shortcuts resource [id]. */
    private fun shortcutTargets(
        context: Context,
        id: Int,
    ): List<String> {
        val targets = mutableListOf<String>()
        val parser = context.resources.getXml(id)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                parser.getAttributeValue(ANDROID_NAMESPACE, "targetClass")?.let(targets::add)
            }
        }
        return targets
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
