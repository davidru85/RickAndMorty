package io.github.davidru85.multiverse.core.data.di

import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.NoCacheStorage
import io.github.davidru85.multiverse.testing.RecordingLogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * `TEST-UNIT-056` — the `coreModule` graph resolves (`GAP-026`, `TASK-044`, `DEC-091`).
 *
 * The test starts a real Koin application over `coreModule` plus the platform module a shell builds
 * from [CoreGraphInputs], so every binding is exercised the way the shell loads it: no device, no
 * network (the client is `MockHttp`'s) and no filesystem (the favourites store is
 * [FakeFavoritesStore]). It runs from `commonTest`, so the Android host and `iosSimulatorArm64` both
 * prove the module is platform-free.
 *
 * A binding that is missing, mistyped or has a broken constructor fails here rather than at first
 * launch.
 */
class CoreModuleGraphTest {
    /** Starts the graph, runs [block], and always closes Koin again. */
    private fun withGraph(
        scope: TestScope,
        block: (CoreGraphInputs, Koin) -> Unit,
    ) {
        val inputs =
            CoreGraphInputs(
                client = MockHttp.client().first,
                decodingDispatcher = StandardTestDispatcher(scope.testScheduler),
                clock = Clock.System,
                applicationScope = CoroutineScope(StandardTestDispatcher(scope.testScheduler)),
                favoritesStore = FakeFavoritesStore(),
                cacheStorage = NoCacheStorage,
                settingsStore = FakeAppSettingsStore(),
                logger = ValidatingAppLogger.forDebug(RecordingLogSink()),
            )
        val koin = startKoin { modules(coreModule, inputs.asModule()) }.koin
        try {
            block(inputs, koin)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `TEST-UNIT-056 given_core_module_when_the_graph_starts_then_every_binding_resolves`() {
        val scope = TestScope()
        withGraph(scope) { _, koin ->
            assertNotNull(koin.get<CharacterRemoteDataSource>(), "the REST adapter must resolve")
            assertNotNull(koin.get<CharacterRepository>(), "the repository must resolve")
            assertNotNull(koin.get<FavoritesRepository>(), "the favourites repository must resolve")
            assertNotNull(koin.get<ObserveFavoriteIds>(), "the cross-feature use case must resolve")
            assertNotNull(koin.get<FavoritesLocalDataSource>(), "the store the shell supplies must resolve")
        }
    }

    @Test
    fun `TEST-UNIT-056 given_a_state_holder_scope_when_the_pager_resolves_then_each_call_returns_its_own_instance`() {
        val scope = TestScope()
        withGraph(scope) { _, koin ->
            val first = koin.get<CharacterPager> { parametersOf(scope) }
            val second = koin.get<CharacterPager> { parametersOf(scope) }
            assertNotNull(first)
            assertTrue(first !== second, "the pager is a factory: two state-holder scopes never share one instance")
        }
    }

    @Test
    fun `TEST-UNIT-056 given_the_graph_when_the_logger_resolves_then_it_is_the_shell_supplied_one`() {
        val scope = TestScope()
        withGraph(scope) { inputs, koin ->
            assertSame(inputs.logger, koin.get<AppLogger>(), "the shell owns the logger variant and the threshold")
        }
    }

    @Test
    fun `TEST-UNIT-056 given_the_graph_when_the_favorites_store_resolves_then_it_is_the_shell_supplied_one`() {
        val scope = TestScope()
        withGraph(scope) { inputs, koin ->
            assertSame(inputs.favoritesStore, koin.get<FavoritesLocalDataSource>(), "the platform store is the shell's")
        }
    }
}
