package io.github.davidru85.multiverse.feature.settings.di

import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.core.presentation.PresentationBindings
import io.github.davidru85.multiverse.feature.settings.domain.ClearFavorites
import io.github.davidru85.multiverse.feature.settings.domain.ObserveAppSettings
import io.github.davidru85.multiverse.feature.settings.domain.UpdateAppSettings
import io.github.davidru85.multiverse.feature.settings.presentation.SettingsStateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Settings module's own Koin bindings (`DESIGN.md` §5, `DEC-091`): the three feature-local use
 * cases and the state holder its screen consumes.
 *
 * It binds against `:core:domain` interfaces only — `AppSettingsRepository`, `FavoritesRepository` —
 * so no implementation type of `:core:data` is named here and the composition root stays the one
 * place that supplies them (ADR-0014). The use cases are singletons because they are stateless;
 * `ObserveFavoriteIds` is `:core:domain`'s, registered by `coreModule`, and is resolved rather than
 * re-declared.
 *
 * The state holder is a **factory**, not a singleton: `IC-023` belongs to one screen's scope, so the
 * caller passes that scope as a parameter and two screens cannot share one confirmation state.
 */
public val settingsModule: Module =
    module {
        single { ObserveAppSettings(repository = get<AppSettingsRepository>()) }
        single { UpdateAppSettings(repository = get<AppSettingsRepository>()) }
        single { ClearFavorites(repository = get<FavoritesRepository>()) }

        /** One holder per screen; the caller supplies its scope (`IC-023`). */
        factory { (scope: CoroutineScope) ->
            SettingsStateHolder(
                observeAppSettings = get(),
                updateAppSettings = get(),
                clearFavorites = get(),
                observeFavoriteIds = get(),
                scope = scope,
                dispatcher = get<CoroutineDispatcher>(named(PresentationBindings.DEFAULT_DISPATCHER)),
            )
        }
    }
