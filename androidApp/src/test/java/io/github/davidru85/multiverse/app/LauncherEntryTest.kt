package io.github.davidru85.multiverse.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-057` — the app has exactly one launcher entry, and it is the shell (`TASK-117`).
 *
 * The launcher icon must open the app at its branded splash (`REQ-FUNC-007`), which only
 * `MainActivity` hosts. A second `MAIN`/`LAUNCHER` activity puts a second icon in the launcher; the
 * debug build used to do that for its diagnostics panel. The case reads the merged manifest of the
 * variant under test through the package manager, so it holds for debug and release alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class LauncherEntryTest {
    @Test
    fun `TEST-UNIT-057 given_the_installed_app_when_the_launcher_lists_its_entries_then_only_the_shell_is_listed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)

        val entries = context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.name }

        assertEquals(
            "the launcher must show one icon, and it must open the shell with its splash",
            listOf(MainActivity::class.java.name),
            entries,
        )
    }
}
