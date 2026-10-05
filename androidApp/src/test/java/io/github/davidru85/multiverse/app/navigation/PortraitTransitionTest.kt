package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitSharedKey
import io.github.davidru85.multiverse.core.designsystem.motion.PortraitTransition
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
 * `TEST-UI-025`, Android half — the card→Detail shared element (`REQ-FUNC-009`, `AC-REQ-FUNC-009-1`,
 * `-2`, `UI_SPEC.md` §7, `DEC-135`, `TASK-112`).
 *
 * There was no `SharedTransitionLayout` and no `sharedElement` anywhere: every destination change was
 * a fade. Frame-level motion is not asserted reliably on Robolectric (`TESTING.md` §14.2), so the case
 * pins the structure the transform needs: the grid card's portrait and the Detail hero's portrait
 * carry the **same** shared key, and with Reduce Motion neither does, so the change is the
 * cross-fade. The real shell runs over the production graph with in-memory doubles (`REQ-REL-004`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-w412dp-h915dp", application = Application::class)
class PortraitTransitionTest {
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

    private fun sharedKeyed(key: String) = SemanticsMatcher.expectValue(PortraitSharedKey, key)

    private fun portraitsKeyed(key: String): Int = compose.onAllNodes(sharedKeyed(key), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Shows the shell on Characters, waits for the card, then opens its Detail the way a tap does. */
    private fun openDetailFromTheGrid(onGrid: () -> Unit) {
        val handoff = DetailHandoff()
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, detailHandoff = handoff)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
                .fetchSemanticsNodes()
                .any { node ->
                    node.config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }
                        .any { it.startsWith("Character 1") }
                }
        }
        onGrid()
        compose.runOnIdle {
            handoff.publish(card)
            controller.navigate(CharacterDetail(card.id.value))
        }
        compose.waitForIdle()
    }

    @Test
    fun `TEST-UI-025 given_a_card_when_its_detail_opens_then_the_card_and_the_hero_share_one_portrait_key`() {
        val key = PortraitTransition.key("1")
        openDetailFromTheGrid {
            assertEquals("TEST-UI-025: the grid card's portrait is the transition's source", 1, portraitsKeyed(key))
        }

        assertEquals("TEST-UI-025: the Detail hero is its target, under the same key", 1, portraitsKeyed(key))
    }

    @Test
    fun `TEST-UI-025 given_reduce_motion_when_a_detail_opens_then_no_portrait_is_shared_and_the_change_cross_fades`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val key = PortraitTransition.key("1")

        openDetailFromTheGrid {
            assertEquals("TEST-UI-025: with Reduce Motion the card is not a transition source", 0, portraitsKeyed(key))
        }

        assertEquals("TEST-UI-025: nor is the hero a target (AC-REQ-FUNC-009-2)", 0, portraitsKeyed(key))
    }
}
