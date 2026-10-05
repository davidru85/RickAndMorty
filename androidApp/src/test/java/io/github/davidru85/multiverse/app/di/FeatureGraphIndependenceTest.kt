package io.github.davidru85.multiverse.app.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.characterdetail.ui.CharacterDetailViewModel
import io.github.davidru85.multiverse.feature.settings.di.settingsModule
import io.github.davidru85.multiverse.feature.settings.di.settingsViewModelModule
import io.github.davidru85.multiverse.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-101` — a feature's ViewModel resolves without another feature's module (`DEC-145`,
 * `TASK-115`).
 *
 * `discoveryViewModelModule` declared the app-wide `CoroutineDispatcher` and `PresentationFormatters`,
 * and the Detail ViewModel resolved its formatters from whichever feature module happened to bind
 * them, so removing or reordering Discovery's module changed the graph under another feature. The
 * composition root binds them once; each case loads the core, the shell and one feature only.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class FeatureGraphIndependenceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @After
    fun stopGraph() {
        stopKoin()
    }

    @Test
    fun `TEST-UNIT-101 given_only_the_detail_feature_when_its_view_model_resolves_then_the_shell_supplies_the_rest`() {
        val koin =
            startKoin {
                androidContext(context)
                modules(coreModule)
                modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
                modules(characterDetailModule, characterDetailViewModelModule)
            }.koin

        assertNotNull(koin.get<CharacterDetailViewModel> { parametersOf(CharacterId("1"), null) })
    }

    @Test
    fun `TEST-UNIT-101 given_only_the_settings_feature_when_its_view_model_resolves_then_the_shell_supplies_the_rest`() {
        val koin =
            startKoin {
                androidContext(context)
                modules(coreModule)
                modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
                modules(settingsModule, settingsViewModelModule)
            }.koin

        assertNotNull(koin.get<SettingsViewModel>())
    }
}
