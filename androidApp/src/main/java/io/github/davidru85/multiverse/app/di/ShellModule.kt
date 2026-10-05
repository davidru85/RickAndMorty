package io.github.davidru85.multiverse.app.di

import android.content.Context
import io.github.davidru85.multiverse.app.image.CoilImageSeam
import io.github.davidru85.multiverse.app.image.CoilPortraitPixels
import io.github.davidru85.multiverse.app.image.imageCacheDirectory
import io.github.davidru85.multiverse.app.image.imageLoader
import io.github.davidru85.multiverse.core.data.cache.FileCacheStorage
import io.github.davidru85.multiverse.core.data.di.CoreGraphInputs
import io.github.davidru85.multiverse.core.data.favorites.DataStoreFavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.favorites.preferencesDataStore
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.androidRickAndMortyHttpClient
import io.github.davidru85.multiverse.core.data.settings.DataStoreAppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.designsystem.image.CharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.PresentationBindings
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.ktor.client.HttpClient
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The DataStore file `SECURITY.md` §3 classifies: favourites only, in the app's private storage.
 */
public const val FAVORITES_STORE_FILE: String = "favorites.preferences_pb"

/**
 * The response cache's directory, under the app's private **cache** storage (`TASK-020`).
 *
 * It is the cache directory rather than the files directory on purpose: the OS may evict it, and a
 * missing entry degrades to the offline error state, which is acceptable here and would not be for
 * favourites (`adr/0005-caching-strategy.md`, `DEC-004`).
 */
public const val RESPONSE_CACHE_DIRECTORY: String = "responses"

/**
 * The DataStore file the app-settings store owns (`IC-022`, `TASK-074`, `SECURITY.md` §3).
 *
 * A file of its own, beside the favourites one: the two stores share the store technology and no
 * key, so clearing either cannot clear the other, and the settings survive a favourites reset.
 */
public const val SETTINGS_STORE_FILE: String = "settings.preferences_pb"

/**
 * The shell's platform modules (`TASK-044`, `DESIGN.md` §5): every input `coreModule` resolves from
 * the graph is built here, because only the shell may name a platform type.
 *
 * The shell owns the **Ktor client** over `androidRickAndMortyHttpClient()` (host allow-list, no
 * redirects, no engine cache) and the **favourites DataStore** in `filesDir` over the application
 * scope, so a write survives the process and a corrupt file logs rather than throws.
 *
 * The **logger's variant is chosen by the source set, not by a runtime flag** (`DESIGN.md` §5,
 * `OBSERVABILITY.md` §5): [shellLogger] is the one seam, and the debug source set replaces that file
 * rather than the whole module, so nothing here can be turned on at runtime.
 */
public fun shellModules(
    applicationScope: CoroutineScope,
    context: Context,
    logging: ShellLogging = ShellLogging(),
): List<Module> {
    val appContext = context.applicationContext
    val logger = logging.logger()
    val client = androidRickAndMortyHttpClient()
    return listOf(
        platformInputs(applicationScope, appContext, logger, client),
        imageLoaderModule(appContext, client),
        presentationModule(),
    ) + logging.extraModules(logger)
}

/**
 * The app-wide presentation dependencies, bound once here rather than by a feature (`DEC-145`): the two
 * named dispatchers and the formatters every state holder renders through.
 */
public fun presentationModule(): Module =
    module {
        single<CoroutineDispatcher>(named(PresentationBindings.DEFAULT_DISPATCHER)) { Dispatchers.Default }
        single<CoroutineDispatcher>(named(PresentationBindings.MAIN_DISPATCHER)) { Dispatchers.Main.immediate }
        single<PresentationFormatters> { DefaultPresentationFormatters }
    }

/** The graph inputs, built once so both variants share every value except the logger. */
public fun platformInputs(
    applicationScope: CoroutineScope,
    appContext: Context,
    logger: ValidatingAppLogger,
    client: HttpClient = androidRickAndMortyHttpClient(),
): Module =
    CoreGraphInputs(
        client = client,
        decodingDispatcher = Dispatchers.IO,
        clock = Clock.System,
        applicationScope = applicationScope,
        favoritesStore =
            DataStoreFavoritesLocalDataSource(
                preferencesDataStore(
                    file = File(appContext.filesDir, FAVORITES_STORE_FILE),
                    scope = applicationScope,
                    logger = logger,
                ),
            ),
        cacheStorage = FileCacheStorage(File(appContext.cacheDir, RESPONSE_CACHE_DIRECTORY)),
        settingsStore =
            DataStoreAppSettingsLocalDataSource(
                preferencesDataStore(
                    file = File(appContext.filesDir, SETTINGS_STORE_FILE),
                    scope = applicationScope,
                    logger = logger,
                ),
            ),
        logger = logger,
    ).asModule()

/**
 * The one Coil image loader (`TASK-021`) and the seam that exposes it to the design system
 * (`DEC-097`): the same allow-listed client, so no image bypasses the host rule, and the one
 * `ImageSeam` every surface draws through.
 *
 * The loader is a process-lifetime singleton; the seam wraps it, so a screen resolves the port rather
 * than building a second loader.
 */
public fun imageLoaderModule(
    context: Context,
    client: HttpClient,
): Module =
    module {
        single {
            imageLoader(
                context = context,
                client = client,
                diskCacheDirectory = imageCacheDirectory(context),
            )
        }
        single<ImageSeam> {
            CoilImageSeam(
                context = context,
                imageLoader = get(),
            )
        }
        // The card accents of `UI_SPEC.md` §5.4: the pixels come through the same loader, and the
        // colour work runs off the main thread, memoised per URL for the process.
        single {
            CharacterAccentPolicy(
                pixels = CoilPortraitPixels(context = context, imageLoader = get()),
                dispatcher = Dispatchers.Default,
            )
        }
    }
