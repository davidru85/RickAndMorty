package io.github.davidru85.multiverse.core.ios

import io.github.davidru85.multiverse.core.data.cache.NsFileCacheStorage
import io.github.davidru85.multiverse.core.data.cache.iosResponseCacheDirectory
import io.github.davidru85.multiverse.core.data.di.CoreGraphInputs
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.UserDefaultsFavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.logging.OsLogSink
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.data.remote.appleRickAndMortyHttpClient
import io.github.davidru85.multiverse.core.data.settings.UserDefaultsAppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.diagnostics.DiagnosticsCopy
import io.github.davidru85.multiverse.core.diagnostics.DiagnosticsRecorder
import io.github.davidru85.multiverse.core.diagnostics.rows
import io.github.davidru85.multiverse.core.domain.logging.AppLogger
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.PresentationBindings
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.core.presentation.observation.StateObserver
import io.github.davidru85.multiverse.core.presentation.splash.SplashGate
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesModule
import io.github.davidru85.multiverse.feature.favorites.domain.ResolveFavoriteCards
import io.github.davidru85.multiverse.feature.settings.di.settingsModule
import io.github.davidru85.multiverse.feature.settings.domain.ClearFavorites
import io.github.davidru85.multiverse.feature.settings.domain.ObserveAppSettings
import io.github.davidru85.multiverse.feature.settings.domain.UpdateAppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.time.Clock

/**
 * The `:core:ios` bootstrap (`ADR-0012`, `TASK-078`).
 *
 * The module's reason to exist is the framework binary its build script declares, and a Kotlin
 * Multiplatform module with an empty source set does not produce one: the linker task is `NO-SOURCE`
 * and no `.framework` is written, so a module that exported everything and compiled nothing would
 * satisfy the boundary rules while producing nothing the iOS app could link. This file is therefore
 * the module's one type, and it earns its place by doing the two jobs the export exists to serve.
 *
 * 1. **It gives the Swift side one entry point** to the shared graph it must start
 *    (`DESIGN.md` §5): the shell resolves its state holders against `:core:domain` interfaces, so the
 *    bootstrapper is what names them without any feature naming an implementation.
 * 2. **It proves the exported surface in Kotlin.** Each screen's dependency bundle below names a type
 *    from that feature module, so an export that regressed to an `implementation` edge fails the
 *    build rather than the Xcode session.
 *
 * It carries no behaviour of its own beyond assembling that surface.
 */
public object MultiverseBootstrap {
    /**
     * A state-holder scope for one iOS screen (`TASK-053`).
     *
     * Kotlin/Native exports `CoroutineScope` as a **protocol**, not a constructor, so Swift cannot
     * build one; the shared holders therefore take the scope from here rather than each platform
     * inventing its own. The scope is a supervisor job on the platform main queue, so a failed load
     * cancels nothing else and every update lands where the view reads it, and closing it cancels the
     * screen's work exactly as `viewModelScope` does on Android.
     */
    public fun screenScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The dispatcher the shared holders run their work on: the main queue, so a state update is
     * already where SwiftUI reads it and no extra hop is needed.
     */
    public fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * Cancels [scope]. Kotlin/Native exports `CoroutineScope` as a protocol with no `cancel`, so a
     * Swift holder cannot close the scope it was given; this is the one operation it needs.
     */
    public fun cancelScope(scope: CoroutineScope) {
        scope.cancel()
    }

    /** Whether [scope] is still active: the Swift side's one way to see that a screen's work ended. */
    public fun isScopeActive(scope: CoroutineScope): Boolean = scope.isActive

    /**
     * Whether a portrait URL may be fetched at all (`REQ-SEC-001`, `DEC-126`): the one host rule of
     * `:core:data`, which the iOS image pipeline applies before its memory, disk and network layers,
     * so a payload cannot point the app at another host.
     */
    public fun isAllowedImageUrl(url: String): Boolean = RickAndMortyApi.isAllowedImageUrl(url)

    /**
     * The one logger the shared graph binds (`IC-024`, `OBSERVABILITY.md` §2.1): Swift code logs through
     * the same validating contract as Kotlin, never through `os.Logger` or `print`, and a case can read
     * which levels this build lets reach the sink.
     */
    public fun logger(): AppLogger = IosGraph.koin.get()

    /** The API resource URL of the character [id], which the Detail's Share sends (`DEC-125`). */
    public fun characterUrl(id: String): String = RickAndMortyApi.characterUrl(id)

    /**
     * The pager one iOS screen's state holder runs on (`IC-014`, `TASK-055`).
     *
     * The graph binds `factory<CharacterPager>` **with a `CoroutineScope` parameter**, and Kotlin/Native
     * exports a parameterised Koin factory as a lambda a Swift caller cannot invoke. Rather than
     * teaching Swift Koin's parameter protocol, the one place that owns the graph — the composition
     * root — exposes the resolved value: `:core:ios` starts the graph from the same modules the
     * Android shell loads, and this function is how the Swift side receives a dependency.
     */
    public fun characterPager(scope: CoroutineScope): CharacterPager = IosGraph.koin.get { parametersOf(scope) }

    /**
     * Awaits the shared splash gate (`IC-026`, `DEC-136`) and then calls [onReady] on the main thread:
     * at least 1.2 s, at most 3 s, ended by the first page's outcome — the policy the Android shell
     * awaits, over the graph's own repository, so the splash warms the page Discovery loads first.
     *
     * A callback rather than a suspending function, like every other Swift entry here: the Swift
     * caller resumes its own continuation, and no Kotlin coroutine is driven from Swift.
     */
    public fun awaitSplashReady(onReady: () -> Unit) {
        val gate = SplashGate(IosGraph.koin.get<CharacterRepository>(), Dispatchers.Default)
        CoroutineScope(Dispatchers.Main).launch {
            gate.awaitReady()
            onReady()
        }
    }

    /** The debug diagnostics sheet's title and its read-only line (`OBSERVABILITY.md` §5). */
    public val diagnosticsTitle: String = DiagnosticsCopy.TITLE
    public val diagnosticsReadOnly: String = DiagnosticsCopy.READ_ONLY

    /**
     * Observes the debug diagnostics as the sheet's rows (`REQ-OBS-002`, `DEC-147`): [onEach] receives
     * the rows `:core:diagnostics` renders — the same rows the Android panel draws — on the main queue,
     * now and on every new record, until the returned observation is closed.
     *
     * It returns `null` in the release framework, which attaches no recorder (`AC-REQ-OBS-002-1`): the
     * recorder's class is linked in both binaries, but only the debug binary ever creates one.
     */
    public fun observeDiagnostics(onEach: (List<DiagnosticsLine>) -> Unit): DiagnosticsObservation? {
        val recorder = IosGraph.diagnostics ?: return null
        val observer =
            StateObserver(recorder.snapshot, Dispatchers.Main) { snapshot ->
                onEach(snapshot.rows().map { DiagnosticsLine(it.label, it.value) })
            }
        return DiagnosticsObservation(observer)
    }

    /**
     * The Detail screen's dependencies (`IC-019`, `TASK-055`).
     *
     * Koin's Swift surface cannot name a generic resolution, so each screen's dependency set is
     * bundled here: the Swift host receives concrete values and passes them to its state holder, and
     * no Swift file resolves from the graph itself.
     */
    public fun characterDetailDependencies(): CharacterDetailDependencies =
        CharacterDetailDependencies(
            getDetails = IosGraph.koin.get(),
            toggleFavorite = IosGraph.koin.get(),
            observeFavoriteIds = IosGraph.koin.get(),
        )

    /** The Favorites section's dependencies (`IC-020`, `TASK-055`). */
    public fun favoritesDependencies(): FavoritesDependencies =
        FavoritesDependencies(
            observeFavoriteIds = IosGraph.koin.get(),
            resolveFavoriteCards = IosGraph.koin.get(),
        )

    /**
     * The Settings screen's dependencies (`IC-023`, `TASK-077`).
     *
     * `observeFavoriteIds` is included because the screen derives `canDeleteFavorites` from the
     * stored set (`AC-REQ-FUNC-035-3`), which is `:core:domain`'s use case rather than a settings one.
     */
    public fun settingsDependencies(): SettingsDependencies =
        SettingsDependencies(
            observeAppSettings = IosGraph.koin.get(),
            updateAppSettings = IosGraph.koin.get(),
            clearFavorites = IosGraph.koin.get(),
            observeFavoriteIds = IosGraph.koin.get(),
        )
}

/** One row of the debug diagnostics sheet (`DEC-147`): a label and its rendered value. */
public class DiagnosticsLine(
    public val label: String,
    public val value: String,
)

/** A running diagnostics observation; closing it stops the deliveries. */
public class DiagnosticsObservation internal constructor(
    private val observer: StateObserver<*>,
) {
    public fun close() {
        observer.close()
    }
}

/** The Detail screen's resolved dependencies (`IC-019`). */
public class CharacterDetailDependencies(
    public val getDetails: GetCharacterDetails,
    public val toggleFavorite: ToggleFavorite,
    public val observeFavoriteIds: ObserveFavoriteIds,
)

/** The Favorites section's resolved dependencies (`IC-020`). */
public class FavoritesDependencies(
    public val observeFavoriteIds: ObserveFavoriteIds,
    public val resolveFavoriteCards: ResolveFavoriteCards,
)

/** The Settings screen's resolved dependencies (`IC-023`). */
public class SettingsDependencies(
    public val observeAppSettings: ObserveAppSettings,
    public val updateAppSettings: UpdateAppSettings,
    public val clearFavorites: ClearFavorites,
    public val observeFavoriteIds: ObserveFavoriteIds,
)

/**
 * The iOS composition root (`DESIGN.md` §5, `DEC-091`, ADR-0014, `TASK-055`).
 *
 * It starts the **same** Koin graph the Android shell starts — `coreModule` plus each feature's own
 * module — so no feature names an implementation and the two platforms cannot diverge on which
 * implementation serves an interface. The Android peer is `:androidApp`'s `MultiverseApplication`.
 *
 * It lives in `:core:ios` rather than in the Swift app because starting a graph is Kotlin work: Koin's
 * `startKoin` is a Kotlin DSL, and a Kotlin/Native caller is the only kind that can invoke it. The
 * Swift side reaches the graph through [MultiverseBootstrap]'s functions instead of resolving
 * dependencies itself.
 */
public object IosGraph {
    /**
     * The debug diagnostics recorder (`DEC-147`, `OBSERVABILITY.md` §5): created in the debug binary
     * only, where the logger writes every validated record to it as well as to the unified log; the
     * release binary has none, so nothing records and nothing can show it (`AC-REQ-OBS-002-1`).
     */
    @OptIn(ExperimentalNativeApi::class)
    internal val diagnostics: DiagnosticsRecorder? by lazy {
        if (Platform.isDebugBinary) DiagnosticsRecorder() else null
    }

    /**
     * The graph, started on first use. `lazy` is synchronised on Kotlin/Native, so two first callers
     * on different threads start it once rather than both calling `startKoin`.
     */
    public val koin: Koin by lazy {
        startKoin {
            modules(
                coreModule,
                discoveryModule,
                characterDetailModule,
                favoritesModule,
                settingsModule,
            )
            modules(iOSPlatformInputs(), presentationModule())
        }.koin
    }
}

/**
 * The logger for this framework's variant (`DEC-039`, `DEC-127`, `OBSERVABILITY.md` §5): every level
 * in the debug framework a Debug build links, so the REST and GraphQL request events reach the unified
 * log as the Android debug variant's reach Logcat; `ERROR` only in the release framework. The choice
 * is a property of the compiled binary — the app's Debug and Release configurations link different
 * frameworks — never a runtime flag a release build could carry.
 */
private fun platformLogger(debugBinary: Boolean): ValidatingAppLogger {
    val recorder = IosGraph.diagnostics
    return if (debugBinary) {
        // The debug binary also folds every validated record into the diagnostics recorder (`DEC-147`).
        ValidatingAppLogger.forDebug(
            LogSink { record ->
                OsLogSink.write(record)
                recorder?.write(record)
            },
        )
    } else {
        ValidatingAppLogger.forRelease(OsLogSink)
    }
}

/**
 * The iOS platform inputs (`CoreGraphInputs`).
 *
 * `NSUserDefaults` is the platform store for both favourites and preferences (`DEC-017`, `ADR-0007`),
 * and the response cache is the app's own caches directory ([`NsFileCacheStorage`]); the HTTP client
 * is the Darwin-engine one the shared data layer already builds for Apple targets.
 */
@OptIn(ExperimentalNativeApi::class)
private fun iOSPlatformInputs(): Module {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val logger = platformLogger(debugBinary = Platform.isDebugBinary)
    return CoreGraphInputs(
        client = appleRickAndMortyHttpClient(),
        decodingDispatcher = Dispatchers.Default,
        clock = Clock.System,
        applicationScope = scope,
        favoritesStore = UserDefaultsFavoritesLocalDataSource(NSUserDefaults.standardUserDefaults, logger),
        cacheStorage = NsFileCacheStorage(iosResponseCacheDirectory()),
        settingsStore = UserDefaultsAppSettingsLocalDataSource(),
        logger = logger,
    ).asModule()
}

/**
 * The app-wide presentation dependencies, bound once by the iOS root as the Android shell binds them
 * (`DEC-145`): the two named dispatchers and the shared formatters.
 */
private fun presentationModule(): Module =
    module {
        single<CoroutineDispatcher>(named(PresentationBindings.DEFAULT_DISPATCHER)) { Dispatchers.Default }
        single<CoroutineDispatcher>(named(PresentationBindings.MAIN_DISPATCHER)) { Dispatchers.Main }
        single<PresentationFormatters> { DefaultPresentationFormatters }
    }
