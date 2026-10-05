package io.github.davidru85.multiverse.app.splash

import android.app.Application
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.app.navigation.MultiverseApp
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.presentation.splash.SplashGate
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesViewModelModule
import io.github.davidru85.multiverse.feature.settings.di.settingsModule
import io.github.davidru85.multiverse.feature.settings.di.settingsViewModelModule
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

/**
 * `TEST-UI-026`, Android half — the splash plays once per launch (`REQ-FUNC-007`, `DEC-136`,
 * `TASK-112`).
 *
 * The shell kept its "ready" flag in `remember`, so a configuration change — a rotation, a locale or
 * a font-scale change — recreated the activity and replayed the whole splash over a list that was
 * already on screen. The flag now survives in saved state. The case runs the real shell over the
 * production graph with in-memory doubles, so nothing reaches a network or a disk (`REQ-REL-004`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = Application::class)
class SplashRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    private val repository = FakeCharacterRepository(FakeCatalogue(listOf(FakeCatalogue.character("1"))))

    @Before
    fun startGraph() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        startKoin {
            androidContext(context)
            modules(coreModule)
            modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
            modules(
                discoveryModule,
                discoveryViewModelModule,
                characterDetailModule,
                characterDetailViewModelModule,
                favoritesModule,
                favoritesViewModelModule,
                settingsModule,
                settingsViewModelModule,
            )
        }
        loadKoinModules(
            module {
                single<FavoritesLocalDataSource> { FakeFavoritesStore() }
                single<AppSettingsLocalDataSource> { FakeAppSettingsStore() }
                single<CharacterRepository> { repository }
            },
        )
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    @Test
    fun `TEST-UI-026 given_a_completed_splash_when_the_saved_state_is_restored_then_the_splash_does_not_replay`() {
        val restoration = StateRestorationTester(compose)
        val gate = SplashGate(repository, Dispatchers.Unconfined)
        restoration.setContent { MultiverseApp(splashGate = gate) }

        compose.onNodeWithContentDescription("Loading characters").assertExists()
        compose.mainClock.advanceTimeBy(SplashGate.MINIMUM.inWholeMilliseconds + SPLASH_EXIT_MILLIS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading characters").assertDoesNotExist()

        // A configuration change destroys the composition and restores it from saved state.
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Loading characters").assertDoesNotExist()
    }

    private companion object {
        /** Past the splash's exit cross-fade, so the overlay has left the tree. */
        const val SPLASH_EXIT_MILLIS = 1_000L
    }
}
