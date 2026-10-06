package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.app.sound.SelectionSound
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
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
import org.junit.Assert.assertEquals
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
 * `TEST-UI-051` — the shell plays the selection sound on the selections the user makes
 * (`REQ-FUNC-036`, `AC-REQ-FUNC-036-1`, `AC-REQ-FUNC-036-2`, `TASK-139`, `DEC-162`).
 *
 * The real shell runs over the production graph with in-memory doubles (`REQ-REL-004`) and an empty
 * catalogue, so Discovery settles on its empty-results state with the chips enabled. The sound is a
 * recording double: whether the preference lets it play is `TEST-UNIT-109`'s, so this case asserts when
 * the shell asks. It asks on a change of destination from the bar and on every status tap, the selected
 * chip included; a tap on the current destination, "Browse characters" and "Clear filters" are not
 * selections the user made on the bar or the selector, and ask nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = Application::class)
class SelectionSoundShellTest {
    @get:Rule
    val compose = createComposeRule()

    private var plays = 0
    private val sound = SelectionSound { plays++ }

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
                single<CharacterRepository> { FakeCharacterRepository(FakeCatalogue(emptyList())) }
            },
        )
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    private fun launch() {
        compose.setContent { MultiverseApp(splashGate = null, selectionSound = sound) }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-051 given_the_bar_when_the_destination_changes_then_the_sound_plays_and_otherwise_it_does_not`() {
        launch()

        tap("Episodes")
        assertEquals("TEST-UI-051: Characters to Episodes is a change of destination", 1, plays)
        tap("Episodes")
        assertEquals("TEST-UI-051: the current destination tapped again changes nothing", 1, plays)
        tap("Favorites")
        tap("Settings")
        tap("Characters")
        assertEquals("TEST-UI-051: each further change plays once", 4, plays)

        tap("Episodes")
        compose.onNodeWithText("Browse characters").performClick()
        compose.waitForIdle()
        assertEquals("TEST-UI-051: Browse characters is the app's selection, not the bar's", 5, plays)
    }

    @Test
    fun `TEST-UI-051 given_the_status_selector_when_a_chip_is_tapped_then_every_tap_plays_and_clear_filters_does_not`() {
        launch()
        // The empty catalogue settles Discovery on its empty-results state, where the chips are enabled.
        compose.waitUntilAtLeastOneExists(hasText("Clear filters"), timeoutMillis = 5_000)

        // `All` is the selected chip: tapping it requests nothing new, so the chips stay enabled.
        tap("All")
        tap("All")
        assertEquals("TEST-UI-051: a tap on the selected chip plays, every time", 2, plays)
        tap("Dead")
        assertEquals("TEST-UI-051: a tap that changes the status plays", 3, plays)

        // The new status reloads the list, which settles on the empty-results state again.
        compose.waitUntilAtLeastOneExists(hasText("Clear filters"), timeoutMillis = 5_000)
        tap("Clear filters")
        assertEquals("TEST-UI-051: Clear filters is the app's reset, not a tap on a chip", 3, plays)
    }
}
