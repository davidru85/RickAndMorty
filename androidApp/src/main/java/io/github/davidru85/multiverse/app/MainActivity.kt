package io.github.davidru85.multiverse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.davidru85.multiverse.app.navigation.MultiverseApp

/**
 * The one activity (`TASK-044`): it installs the system splash, draws edge to edge with light system
 * bars, and composes [MultiverseApp] inside the design system's theme.
 *
 * The system splash is handed over here and nothing more: `installSplashScreen()` shows the launcher
 * icon's foreground over the splash theme's background, and the branded rotation, the readiness gate
 * and the crossfade to Characters are `TASK-007`'s (`UI_SPEC.md` §6.1). Keeping the handoff here is
 * what lets phase 4.3 replace the in-app surface without touching the activity.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before `super.onCreate`, as the SplashScreen API requires.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MultiverseApp()
        }
    }
}
