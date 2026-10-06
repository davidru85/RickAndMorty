package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DetailHandoff
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import io.github.davidru85.multiverse.feature.discovery.navigation.CharacterList
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
 * `TEST-UI-050` — the predictive back gesture from the Detail runs the Back button's transition
 * (`TASK-138`, `GAP-048`, `REQ-FUNC-009`, `UI_SPEC.md` §7).
 *
 * The shell's `NavHost` named the pop transitions but not the predictive-pop pair, so the gesture ran
 * Navigation Compose's defaults: the Detail shrank toward the centre of the screen while its portrait
 * flew back to the card, and the two came apart. The case drives the gesture through the activity's
 * back dispatcher, as the system does, and holds it half-way: the list is being revealed underneath,
 * and the Detail's own controls are neither scaled nor moved — the Detail fades in place, as it does
 * when Back is tapped. Releasing the gesture still returns to Characters. The real shell runs over the
 * production graph with in-memory doubles (`REQ-REL-004`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h915dp", application = Application::class)
class PredictiveBackTest {
    @get:Rule
    val compose = createComposeRule()

    private val card =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Character 1",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://example.invalid/avatar/1.jpeg",
        )

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

    private lateinit var controller: NavHostController
    private lateinit var dispatcher: OnBackPressedDispatcher

    /** Shows the shell on Characters, waits for the card, then opens its Detail the way a tap does. */
    private fun openDetail() {
        val handoff = DetailHandoff()
        compose.setContent {
            controller = rememberNavController()
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MultiverseApp(navController = controller, detailHandoff = handoff)
        }
        compose.waitUntil(timeoutMillis = 5_000) { cardsShown() > 0 }
        compose.runOnIdle {
            handoff.publish(card)
            controller.navigate(CharacterDetail(card.id.value))
        }
        compose.waitForIdle()
        assertEquals("the list is covered once the Detail is open", 0, cardsShown())
    }

    /** The grid's card for Character 1, which only the list draws; the hero names the character alone. */
    private fun cardsShown(): Int =
        compose
            .onAllNodesWithContentDescription("Character 1, Alive", substring = true)
            .fetchSemanticsNodes()
            .size

    /** Where the Detail's Share control is drawn, after every layer transform above it. */
    private fun shareBounds(): Rect = compose.onNodeWithContentDescription("Share").fetchSemanticsNode().boundsInRoot

    private fun swipe(progress: Float): BackEventCompat =
        BackEventCompat(touchX = SWIPE_TRAVEL_PX * progress, touchY = SWIPE_Y_PX, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private fun holdGestureHalfWay() {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(swipe(0f))
            dispatcher.dispatchOnBackProgressed(swipe(HALF_WAY))
        }
        compose.waitForIdle()
    }

    private fun assertDetailInPlace(resting: Rect) {
        assertTrue("TEST-UI-050: the gesture reveals the list under the Detail", cardsShown() > 0)
        val held = shareBounds()
        assertEquals("TEST-UI-050: the Detail is not scaled by the gesture (width)", resting.width, held.width, 0.5f)
        assertEquals("TEST-UI-050: the Detail is not moved by the gesture (left)", resting.left, held.left, 0.5f)
        assertEquals("TEST-UI-050: the Detail is not moved by the gesture (top)", resting.top, held.top, 0.5f)
    }

    @Test
    fun `TEST-UI-050 given_an_open_detail_when_the_back_gesture_is_half_way_then_the_detail_fades_in_place`() {
        openDetail()
        val resting = shareBounds()

        holdGestureHalfWay()

        assertDetailInPlace(resting)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(
            "TEST-UI-050: releasing the gesture returns to Characters",
            controller.currentBackStackEntry?.destination?.hasRoute(CharacterList::class) == true,
        )
    }

    @Test
    fun `TEST-UI-050 given_reduce_motion_when_the_back_gesture_is_half_way_then_the_detail_cross_fades_in_place`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        openDetail()
        val resting = shareBounds()

        holdGestureHalfWay()

        assertDetailInPlace(resting)
    }

    private companion object {
        const val HALF_WAY = 0.5f
        const val SWIPE_TRAVEL_PX = 400f
        const val SWIPE_Y_PX = 900f
    }
}
