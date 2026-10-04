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
import org.koin.core.logger.Level as KoinLevel
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

    @Test
    fun `TEST-UNIT-057 given_the_base_application_when_koin_is_started_then_the_default_koin_level_is_error`() {
        // Dec-039 forbids any log line below ERROR in a release build — Koin's included. The base
        // class derives Koin's level from the same variant seam; the debug subclass is covered by
        // its own threshold behaviour. A regression to the framework default (INFO) fails here.
        org.junit.Assert.assertEquals(
            "the release variant must not let Koin log below ERROR (DEC-039)",
            KoinLevel.ERROR,
            MultiverseApplication().koinLevel(),
        )
    }

    @Test
    fun `TEST-UNIT-057 given_the_shell_graph_when_every_type_the_activity_injects_is_resolved_then_none_is_missing`() {
        // `TASK-070`'s handover run launched the app and it died in `onCreate`: the activity injected
        // `CoilImageSeam`, the concrete class, while the graph registers only the `ImageSeam` port, so
        // Koin threw `NoDefinitionFoundException` before the first frame drew. The cases above resolve a
        // hand-written list of bindings and happen not to name the seam, so the graph was green while the
        // app could not start.
        //
        // This case reads the activity's **actual** injected properties reflectively, so a `by inject()`
        // whose declared type the graph cannot serve fails here rather than on a device. A Kotlin
        // delegate compiles to a `Lazy` field plus an accessor, so the accessor's return type — not the
        // field type — is what Koin resolves; `detailHandoff` is a plain property with no injection and
        // is excluded by name.
        val koin = graph()
        val injectedTypes =
            MainActivity::class.java.declaredMethods
                .filter { it.name.startsWith("get") && it.parameterCount == 0 }
                .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) to it.returnType }
                .filter { (name, _) -> name != "detailHandoff" }
        assertTrue("the activity must declare its injections", injectedTypes.isNotEmpty())
        injectedTypes.forEach { (name, type) ->
            assertNotNull(
                "MainActivity injects `$name` as $type and the graph must serve it",
                koin.getOrNull<Any>(type.kotlin),
            )
        }
    }
}
