package io.github.davidru85.multiverse.feature.characterdetail.di

import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Character detail module's own Koin bindings (`DESIGN.md` §5, `DEC-091`).
 *
 * It binds against `:core:domain` interfaces only — `CharacterRepository`, `FavoritesRepository`,
 * `ObserveFavoriteIds` — so no implementation type of `:core:data` is named here and the composition
 * root stays the one place that supplies them (ADR-0014). The feature's own use cases are singletons
 * because they are stateless; `ObserveFavoriteIds` is `:core:domain`'s, registered by `coreModule`,
 * and is resolved rather than re-declared.
 */
public val characterDetailModule: Module =
    module {
        single { GetCharacterDetails(repository = get<CharacterRepository>()) }
        single { ToggleFavorite(repository = get<FavoritesRepository>()) }
    }
