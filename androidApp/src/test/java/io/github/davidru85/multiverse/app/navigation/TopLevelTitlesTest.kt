package io.github.davidru85.multiverse.app.navigation

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
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
 * `TEST-UI-031` — every top-level destination names itself (`UI_SPEC.md` §6.2, §6.4, §6.5, Figma
 * `101:499`, `101:637`, `TASK-113`).
 *
 * Episodes and Favorites rendered no title at all, where Figma shows "Episodes" and "Favorites" in
 * Display Small Emphasized like "Characters" and "Settings"; and no title was a heading, so a screen
 * reader could not jump to it. The real shell runs over the production graph with in-memory doubles.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = Application::class)
class TopLevelTitlesTest {
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

    private fun heading(title: String) = hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `TEST-UI-031 given_each_top_level_destination_when_selected_then_its_title_is_a_heading`() {
        compose.setContent { MultiverseApp() }
        compose.waitForIdle()

        listOf("Characters", "Episodes", "Favorites", "Settings").forEach { title ->
            // The bar's item carries the same word, so the case selects through it and then finds the
            // one node that is a heading.
            compose.onAllNodes(hasText(title)).onFirst().performClick()
            compose.waitForIdle()
            compose.onNode(heading(title)).assertExists("TEST-UI-031: $title names its screen as a heading")
        }
    }
}
