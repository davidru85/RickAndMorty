package io.github.davidru85.multiverse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.davidru85.multiverse.app.navigation.MultiverseApp
import io.github.davidru85.multiverse.app.sound.SelectionSound
import io.github.davidru85.multiverse.core.designsystem.image.CharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.presentation.DetailHandoff
import io.github.davidru85.multiverse.core.presentation.splash.SplashGate
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.android.inject

/**
 * The one activity (`TASK-044`, `TASK-007`): it installs the system splash, draws edge to edge with
 * light system bars, and composes [MultiverseApp] inside the design system's theme.
 *
 * The system phase is handed over here: `installSplashScreen()` shows the splash theme's Surface
 * background, with no icon, for as long as the process takes to start (`TASK-127`); Android 12+ cannot
 * skip that window, so it is left empty and the user sees one splash. The **branded** phase is the
 * app's own, and it is driven by the [SplashGate] this activity supplies from the graph, so the splash
 * is a real loading indicator rather than decoration (`UI_SPEC.md` §6.1, `DEC-098`).
 */
public class MainActivity : ComponentActivity() {
    private val characterRepository: CharacterRepository by inject()

    /** The one image seam (`DEC-097`): the app's Coil loader, adapted to the design system's port. */
    private val imageSeam: ImageSeam by inject()

    /** The one card-accent policy (`UI_SPEC.md` §5.4): every card in the app asks it for its colour. */
    private val accentPolicy: CharacterAccentPolicy by inject()

    /**
     * The one selection sound (`REQ-FUNC-036`). It is resolved in [onCreate], so its decode starts at
     * launch and is done long before the splash lets the first tap through (`AC-REQ-FUNC-036-4`).
     */
    private val selectionSound: SelectionSound by inject()

    /** The one hand-off (`IC-025`); the shell owns it because it knows both destinations exist. */
    private val detailHandoff: DetailHandoff = DetailHandoff()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before `super.onCreate`, as the SplashScreen API requires.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
        )
        val sound = selectionSound
        setContent {
            // The gate is built once per composition from the graph's repository, so the splash waits on
            // the same first page the Discovery screen will render.
            val gate = remember { SplashGate(characterRepository, Dispatchers.IO) }
            MultiverseApp(
                splashGate = gate,
                imageSeam = imageSeam,
                detailHandoff = detailHandoff,
                accentPolicy = accentPolicy,
                selectionSound = sound,
            )
        }
    }
}
