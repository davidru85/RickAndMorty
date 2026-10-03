package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesViewModelModule
import io.github.davidru85.multiverse.feature.settings.di.settingsModule
import io.github.davidru85.multiverse.feature.settings.di.settingsViewModelModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UI-007` — the four destinations and the "Browse characters" contract (`REQ-FUNC-008`,
 * `AC-REQ-FUNC-008-1`/`-2`, `TASK-008`).
 *
 * The case walks **every ordered pair** of destinations and asserts each is one tap from any other,
 * which is the criterion's wording rather than a sample of it. It then asserts the second half: from
 * either placeholder, "Browse characters" selects Characters **without pushing a route**.
 *
 * "Without pushing a route" is asserted the way the criterion means it: a push would leave the start
 * destination *and* a second Characters entry on the stack. Selecting Characters from a tab **pops**
 * the intermediate destination instead — the bar's `popUpTo(start) { saveState }` contract — so the
 * case asserts that Characters is the top destination **and that exactly one Characters entry exists**.
 *
 * A destination's label also appears as its own screen title, so a tap targets the node that is
 * clickable, which is the bar's item rather than the page heading.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = android.app.Application::class)
class DestinationNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * The graph the destinations resolve from. The real Discovery and Detail screens resolve their
     * ViewModels through Koin, so this case starts the same graph the application does; the
     * assertions below are about navigation and are untouched.
     */
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
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    private val labels = listOf("Characters", "Episodes", "Favorites", "Settings")

    private fun tapDestination(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    /** The destination the controller is showing, read from its route. */
    private fun selected(controller: NavHostController): String {
        val route = controller.currentBackStackEntry?.destination?.route.orEmpty()
        return when {
            route.contains("CharacterList") -> "Characters"
            route.contains("Episodes") -> "Episodes"
            route.contains("Favorites") -> "Favorites"
            route.contains("Settings") -> "Settings"
            else -> route
        }
    }

    private fun charactersEntries(controller: NavHostController): Int =
        controller.currentBackStack.value.count { it.destination.route?.contains("CharacterList") == true }

    @Test
    fun `TEST-UI-007 given_any_destination_when_every_other_is_tapped_then_it_is_reached_in_one_tap`() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, splashGate = null)
        }
        compose.waitForIdle()

        // The 4 x 3 ordered pairs: from each origin, every other destination is one tap away.
        labels.forEach { origin ->
            tapDestination(origin)
            labels.filterNot { it == origin }.forEach { target ->
                tapDestination(target)
                assertEquals(
                    "from $origin, $target must be selected by one tap on the bar",
                    target,
                    selected(controller),
                )
            }
        }
    }

    @Test
    fun `TEST-UI-007 given_a_placeholder_when_browse_characters_is_tapped_then_characters_is_selected_without_pushing_a_route`() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, splashGate = null)
        }
        compose.waitForIdle()

        listOf("Episodes", "Favorites").forEach { placeholder ->
            tapDestination(placeholder)
            assertEquals("the case starts from $placeholder", placeholder, selected(controller))

            compose.onNodeWithText("Browse characters").performClick()
            compose.waitForIdle()

            assertEquals(
                "from $placeholder, Browse characters selects the Characters destination",
                "Characters",
                selected(controller),
            )
            assertEquals(
                "Browse characters must not push a route, so Characters holds exactly one entry (AC-REQ-FUNC-008-2)",
                1,
                charactersEntries(controller),
            )
        }
    }

    @Test
    fun `TEST-UI-007 given_a_top_level_tap_when_it_repeats_then_the_back_stack_does_not_grow`() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, splashGate = null)
        }
        compose.waitForIdle()

        tapDestination("Episodes")
        val depth = controller.currentBackStack.value.size
        repeat(3) { tapDestination("Episodes") }
        assertEquals(
            "re-tapping the current destination keeps a single top entry (UI_SPEC.md §4.1)",
            depth,
            controller.currentBackStack.value.size,
        )
        assertEquals("Episodes holds exactly one entry", 1, controller.currentBackStack.value.count { it.destination.route?.contains("Episodes") == true })
    }

    @Test
    fun `TEST-UI-007 given_each_destination_when_it_is_shown_then_the_bar_marks_it_selected`() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, splashGate = null)
        }
        compose.waitForIdle()

        labels.forEach { label ->
            tapDestination(label)
            assertEquals("the bar's selection follows the destination", label, selected(controller))
        }
    }
}
