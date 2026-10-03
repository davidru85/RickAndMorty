package io.github.davidru85.multiverse.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

/** Proves the feature modules load beside `coreModule` without a definition clash. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en", application = android.app.Application::class)
class FeatureModulesGraphTest {
    @After
    fun stop() {
        stopKoin()
    }

    @Test
    fun `the discovery and detail modules load beside coreModule`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        startKoin {
            androidContext(context)
            modules(coreModule)
            modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
            modules(discoveryModule, characterDetailModule)
        }
    }
}
