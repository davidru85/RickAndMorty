package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `TEST-UI-020`'s Android half — the Detail's Back pops to the list and its Share opens the system
 * chooser with the character's line (`UI_SPEC.md` §6.3, `DEC-125`, `TASK-111`).
 *
 * Both controls are live: before this case the shell wired Share to `{}`, a control that looked
 * tappable and did nothing (`AGENTS.md` §3.5). The case runs the real shell over the production graph,
 * with the repository and the stores replaced by in-memory doubles so nothing reaches a network or a
 * disk (`REQ-REL-004`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = android.app.Application::class)
class DetailShareAndBackTest {
    @get:Rule
    val compose = createComposeRule()

    private val card =
        CharacterCardUi(
            id = CharacterId("1"),
            name = "Character 1",
            species = DisplayText.Data("Human"),
            status = CharacterStatus.Alive,
            statusLabel = CopyKeys.STATUS_ALIVE,
            imageUrl = "https://${RickAndMortyApi.HOST}/api/character/avatar/1.jpeg",
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

    /** Opens the Detail of [card] the way a tapped card does: the hand-off first, then the route. */
    private fun openDetail(): NavHostController {
        val handoff = DetailHandoff()
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            MultiverseApp(navController = controller, detailHandoff = handoff)
        }
        compose.runOnIdle {
            handoff.publish(card)
            controller.navigate(CharacterDetail(card.id.value))
        }
        compose.waitForIdle()
        return controller
    }

    @Test
    fun `TEST-UI-020 given_an_open_detail_when_share_is_tapped_then_the_chooser_carries_the_character_line`() {
        openDetail()

        compose.onNodeWithContentDescription("Share").performClick()
        compose.waitForIdle()

        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull("Share must start an activity rather than do nothing", started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals(
            "the shared line names the character and its API resource (DEC-125)",
            "Character 1 on Multiverse Explorer: https://${RickAndMortyApi.HOST}/api/character/1",
            send?.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test
    fun `TEST-UI-020 given_an_open_detail_when_back_is_tapped_then_the_list_is_shown_again`() {
        val controller = openDetail()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        assertEquals(
            "Back pops the Detail and returns to Characters",
            true,
            controller.currentBackStackEntry
                ?.destination
                ?.route
                .orEmpty()
                .contains("CharacterList"),
        )
    }
}
