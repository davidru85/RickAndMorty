package io.github.davidru85.multiverse.core.data.di

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.CacheStorage
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.favorites.LocalFavoritesRepository
import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.CharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource
import io.github.davidru85.multiverse.core.domain.logging.AppLogger as DomainAppLogger

/**
 * The Koin module of `:core:data` (`GAP-026`, `DEC-091`, ADR-0014): every B3 implementation bound
 * behind its `:core:domain` interface, so no feature and no shell names an implementation.
 *
 * **Platform inputs come from the composition root** (`DESIGN.md` §5): the module resolves the Ktor
 * client, the decoding [CoroutineDispatcher], a [Clock], the [FavoritesLocalDataSource], the
 * [AppLogger] and the application [CoroutineScope] from the graph, and the shell registers them in
 * its own module. That is why this file declares no `expect/actual` and no platform type: `:core:ios`
 * (`TASK-078`) registers the Darwin client and `NSUserDefaults.standardUserDefaults` and loads the
 * same `coreModule`.
 *
 * Two declarations are this module's own, because they are platform-free: the [Random] source the
 * retry policy jitters with and the [TimeSource] the pager measures a load with. A shell may override
 * either.
 *
 * The pager is a **factory**, not a singleton: `IC-014` belongs to one state-holder scope, so the
 * caller passes that scope's [CoroutineScope] as a parameter and two screens cannot share a pager.
 */
public val coreModule: Module =
    module {
        single<Random> { Random.Default }
        single<TimeSource> { TimeSource.Monotonic }

        single<CharacterRemoteDataSource> {
            RestCharacterRemoteDataSource(
                client = get(),
                decodingDispatcher = get(),
                clock = get(),
                logger = get(),
            )
        }

        single {
            ResponseCache(
                storage = get(),
                clock = get(),
                policy = get(),
                logger = get(),
            )
        }

        single<CachePolicy> { CachePolicy() }

        single<CharacterRepository> {
            RemoteCharacterRepository(
                remote = get(),
                scope = get(),
                random = get(),
                logger = get(),
                cache = get(),
            )
        }

        single<FavoritesLocalDataSource> { get() }

        single<FavoritesRepository> {
            LocalFavoritesRepository(local = get(), logger = get())
        }

        single { ObserveFavoriteIds(repository = get()) }

        /** One pager per state-holder scope; the caller supplies its scope (`IC-014`). */
        factory<CharacterPager> { (scope: CoroutineScope) ->
            RepositoryCharacterPager(
                repository = get(),
                scope = scope,
                logger = get(),
                timeSource = get(),
            )
        }
    }

/**
 * The platform inputs `coreModule` resolves from the graph. A shell registers one of each; the
 * `:core:data` graph test registers a test double for each, so the whole graph resolves without a
 * device, a network or a filesystem.
 *
 * It is a parameter object rather than a Koin module so a reader sees the complete input set in one
 * place, and so a shell cannot forget one silently: `TEST-UNIT-056` resolves the graph the same way.
 */
public class CoreGraphInputs(
    public val client: io.ktor.client.HttpClient,
    public val decodingDispatcher: CoroutineDispatcher,
    public val clock: Clock,
    public val applicationScope: CoroutineScope,
    public val favoritesStore: FavoritesLocalDataSource,
    public val cacheStorage: CacheStorage,
    public val logger: DomainAppLogger,
) {
    /**
     * The shell module that registers exactly these inputs, beside `coreModule`.
     *
     * The inputs are captured in locals first: inside `module { }` the name `logger` is Koin's own
     * `Module.logger`, so an unqualified read would register the framework's logger instead of the
     * shell's (a defect `TEST-UNIT-056` reproduced as a `ClassCastException`).
     */
    public fun asModule(): Module {
        val httpClient = client
        val dispatcher = decodingDispatcher
        val wallClock = clock
        val scope = applicationScope
        val store = favoritesStore
        val cache = cacheStorage
        val appLogger = logger
        return module {
            single { httpClient }
            single<CoroutineDispatcher> { dispatcher }
            single { wallClock }
            single<CoroutineScope> { scope }
            single<FavoritesLocalDataSource> { store }
            single<CacheStorage> { cache }
            single<io.github.davidru85.multiverse.core.domain.logging.AppLogger> { appLogger }
        }
    }
}
