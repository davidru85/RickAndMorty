package io.github.davidru85.multiverse.app

import android.app.Application
import io.github.davidru85.multiverse.app.di.ShellLogging
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesModule
import io.github.davidru85.multiverse.feature.favorites.di.favoritesViewModelModule
import io.github.davidru85.multiverse.feature.settings.di.settingsModule
import io.github.davidru85.multiverse.feature.settings.di.settingsViewModelModule
import io.github.davidru85.multiverse.feature.characterdetail.di.characterDetailViewModelModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryModule
import io.github.davidru85.multiverse.feature.discovery.di.discoveryViewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level as KoinLevel

/**
 * The Android composition root (`TASK-044`, `DEC-091`, ADR-0014): it starts the one Koin graph and
 * owns the application [CoroutineScope] the shared work runs in.
 *
 * The graph is `coreModule` (the implementations behind the domain interfaces) plus the shell's
 * platform module (the Ktor client, the DataStore favourites store, the dispatchers and the logger).
 * No feature contributes a module yet — a feature module is created by the B5 task that gives it a
 * binding (`DEC-099`) — so the shell loads exactly what exists.
 *
 * [logging] is the variant seam: a release build uses the base implementation, while the debug
 * variant's Application subclass (`io.github.davidru85.multiverse.app.debug.DebugMultiverseApplication`,
 * selected by `src/debug/AndroidManifest.xml`) supplies the DEBUG threshold and the diagnostic
 * recorder. The variant is a build-time property, never a runtime flag (`OBSERVABILITY.md` §5).
 */
public open class MultiverseApplication : Application() {
    /** The application-wide scope the graph's shared work runs in. */
    public val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The variant's logging seams; the base implementation is the release behaviour. */
    protected open fun logging(): ShellLogging = ShellLogging()

    /**
     * What an unregistered copy key renders as in this variant (`DEC-144`): the key itself in the base
     * (release) class, a loud failure in the debug subclass.
     */
    protected open fun missingCopyKeyPolicy(): CopyResolver.MissingKeyPolicy = CopyResolver.MissingKeyPolicy.SHOW_KEY

    override fun onCreate() {
        super.onCreate()
        CopyResolver.missingKeyPolicy = missingCopyKeyPolicy()
        startKoin {
            // Koin's own log lines follow the variant threshold DEC-039 fixes: the base (release)
            // class passes ERROR, and the debug subclass carries the DEBUG threshold of its own
            // logger. The level is a build-time property, so a release artifact logs nothing
            // below ERROR (`OBSERVABILITY.md` §5, `TEST-UNIT-057`).
            androidLogger(koinLevel())
            androidContext(this@MultiverseApplication)
            modules(coreModule)
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
            modules(shellModules(applicationScope, this@MultiverseApplication, logging()))
        }
    }

    /** Koin's own logger level for this variant, derived from the same variant seam as the sink. */
    internal open fun koinLevel(): KoinLevel = KoinLevel.ERROR
}
