package io.github.davidru85.multiverse.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogLevel
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-057` — the full Android graph resolves (`TASK-044`, `DEC-091`, ADR-0014).
 *
 * `coreModule` is proved on its own in `:core:data` (`TEST-UNIT-056`); this case is the shell's half:
 * the graph starts with the **real** platform module — the OkHttp-backed Ktor client and the DataStore
 * favourites store the app uses — and every binding the shell loads resolves. Nothing here performs a
 * request: the client is built, not driven, and the store is opened, not written, so the case needs no
 * network and no device.
 *
 * Each case builds its own [koinApplication], so no global Koin state is shared between tests.
 */
@RunWith(RobolectricTestRunner::class)
// The harness must not instantiate the real Application: its `onCreate` starts the Koin graph, and
// this case starts its own so the assertions decide on the graph rather than on global state.
@Config(sdk = [36], application = android.app.Application::class)
class ShellGraphTest {
    private fun graph(): Koin {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return koinApplication {
            androidContext(context)
            modules(coreModule)
            modules(shellModules(scope, context))
        }.koin
    }

    @Test
    fun `TEST-UNIT-057 given_the_shell_platform_module_when_the_graph_starts_then_every_binding_resolves`() =
        runTest {
            val koin = graph()
            assertNotNull("the REST adapter must resolve", koin.get<CharacterRemoteDataSource>())
            assertNotNull("the repository must resolve", koin.get<CharacterRepository>())
            assertNotNull("the favourites repository must resolve", koin.get<FavoritesRepository>())
            assertNotNull("the variant logger must resolve", koin.get<AppLogger>())
        }

    @Test
    fun `TEST-UNIT-057 given_a_release_variant_when_the_logger_is_read_then_its_threshold_is_the_release_one`() =
        runTest {
            val logger = graph().get<AppLogger>()
            // The release threshold enables ERROR and nothing below it (DEC-039); the case asserts the
            // behaviour rather than a type name, so a refactor of the logger cannot pass it vacuously.
            assertTrue("a release build must not enable DEBUG (DEC-039)", !logger.isEnabled(LogLevel.DEBUG))
            assertTrue("a release build must not enable INFO", !logger.isEnabled(LogLevel.INFO))
            assertTrue("a release build must enable ERROR", logger.isEnabled(LogLevel.ERROR))
        }
}
