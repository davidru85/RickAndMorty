package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
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
import org.junit.Assert.assertTrue
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
 * `TEST-UI-028` — the shell's chrome (`UI_SPEC.md` §4.1, §6.3, Figma `21:1217`, `117:887`, `TASK-113`).
 *
 * The navigation bar rendered on the Detail destination, which Figma draws with no bar, only the
 * gesture handle; and the bar's container declared `Role.Tab` for the whole bar, so TalkBack announced
 * the bar itself as a tab before its four tabs. The real shell runs over the production graph with
 * in-memory doubles (`REQ-REL-004`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = Application::class)
class ShellChromeTest {
    @get:Rule
    val compose = createComposeRule()

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
                single<CharacterRepository> { FakeCharacterRepository(FakeCatalogue(listOf(FakeCatalogue.character("1")))) }
            },
        )
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    private fun shell(): NavHostController {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller)
        }
        compose.waitForIdle()
        return controller
    }

    @Test
    fun `TEST-UI-028 given_a_top_level_destination_when_shown_then_the_bar_shows_and_only_its_items_are_tabs`() {
        shell()

        // "Settings" is a bar label only; the Characters screen shows no other text with it.
        compose.onNodeWithText("Settings").assertExists()
        assertEquals(
            "TEST-UI-028: the four items are the tabs; the bar is a selectable group, not a fifth tab",
            4,
            compose
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `TEST-UI-028 given_the_detail_destination_when_shown_then_no_navigation_bar_is_drawn`() {
        val controller = shell()

        compose.runOnIdle { controller.navigate(CharacterDetail("1")) }
        compose.waitForIdle()

        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `TEST-UI-028 given_the_detail_when_popped_then_the_bar_grows_in_instead_of_jumping`() {
        val controller = shell()
        val bar = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        val fullHeight = compose.onNode(bar).fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { controller.navigate(CharacterDetail("1")) }
        compose.waitForIdle()

        // Two frames into the pop: the bar has started to show, but has not pushed the leaving Detail
        // up by its whole height in one frame.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { controller.popBackStack() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val earlyHeight = compose.onNode(bar).fetchSemanticsNode().boundsInRoot.height
        assertTrue("TEST-UI-028: the bar grows in over the transition (was $earlyHeight of $fullHeight)", earlyHeight < fullHeight)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(fullHeight, compose.onNode(bar).fetchSemanticsNode().boundsInRoot.height)
    }
}
