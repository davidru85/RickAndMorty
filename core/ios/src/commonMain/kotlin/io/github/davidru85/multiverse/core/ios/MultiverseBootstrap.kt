package io.github.davidru85.multiverse.core.ios

import io.github.davidru85.multiverse.core.data.cache.NsFileCacheStorage
import io.github.davidru85.multiverse.core.data.cache.iosResponseCacheDirectory
import io.github.davidru85.multiverse.core.data.di.CoreGraphInputs
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.favorites.UserDefaultsFavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.logging.OsLogSink
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.appleRickAndMortyHttpClient
import io.github.davidru85.multiverse.core.data.settings.UserDefaultsAppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.navigation.CharacterList
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
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSUserDefaults
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
 * 1. **It proves the exported surface in Kotlin.** The framework's `api` graph is a build
 *    declaration; a compile-time reference to a type from each exported module is what turns that
 *    declaration into evidence, and it fails the build — rather than the Xcode session — if an
 *    export regresses to an `implementation` edge.
 * 2. **It gives the Swift side one entry point** to the shared graph it must start
 *    (`DESIGN.md` §5): the shell resolves its state holders against `:core:domain` interfaces, so the
 *    bootstrapper is what names them without any feature naming an implementation.
 *
 * It carries no behaviour of its own beyond assembling that surface, and it is the only file in the
 * module.
 */
public object MultiverseBootstrap {
    /**
     * The exported route declarations, in the shell's tab order (`UI_SPEC.md` §6, `DESIGN.md` §4.2).
     *
     * Each is a type from a different `:feature:*` module, so referencing them together is what makes
     * the export list compile-checked: an export that regressed to `implementation` would make this
     * declaration unresolved.
     */
    public val routes: List<String> =
        listOf(
            CharacterList::class.qualifiedName.orEmpty(),
            CharacterDetail::class.qualifiedName.orEmpty(),
        )

    /**
     * The identity of one character card in the shared vocabulary (`IC-016`, `:core:presentation`),
     * so the Swift side can be handed a card without reconstructing it.
     */
    public fun card(
        id: String,
        name: String,
        species: String,
        statusLabelKey: String,
        imageUrl: String,
    ): CharacterCardUi =
        CharacterCardUi(
            id = CharacterId(id),
            name = name,
            species =
                io.github.davidru85.multiverse.core.presentation.DisplayText
                    .Data(species),
            status = io.github.davidru85.multiverse.core.domain.model.CharacterStatus.Unknown,
            statusLabel =
                io.github.davidru85.multiverse.core.presentation
                    .CopyKey(statusLabelKey),
            imageUrl = imageUrl,
        )

    /**
     * The type the composition root resolves (`:core:domain`), named here so the exported graph
     * carries the repository interface the Swift shell starts its graph around (`DEC-091`).
     */
    public fun repositoryType(): String = CharacterRepository::class.qualifiedName.orEmpty()

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
    private var started = false

    /** The graph, started on first use. */
    public val koin: Koin
        get() {
            if (!started) {
                startKoin {
                    modules(
                        coreModule,
                        discoveryModule,
                        characterDetailModule,
                        favoritesModule,
                        settingsModule,
                    )
                    modules(iOSPlatformInputs())
                }
                started = true
            }
            // Kotlin/Native has no `GlobalContext` object: the platform-neutral accessor is
            // `KoinPlatformTools.defaultContext()`, which is what `startKoin` populated.
            return KoinPlatformTools.defaultContext().get()
        }
}

/**
 * The iOS platform inputs (`CoreGraphInputs`).
 *
 * `NSUserDefaults` is the platform store for both favourites and preferences (`DEC-017`, `ADR-0007`),
 * and the response cache is the app's own caches directory ([`NsFileCacheStorage`]); the HTTP client
 * is the Darwin-engine one the shared data layer already builds for Apple targets.
 */
private fun iOSPlatformInputs(): Module {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val logger = ValidatingAppLogger.forRelease(OsLogSink)
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
