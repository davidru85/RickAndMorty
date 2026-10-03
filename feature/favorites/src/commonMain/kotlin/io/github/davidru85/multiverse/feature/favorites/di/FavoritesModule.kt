package io.github.davidru85.multiverse.feature.favorites.di

import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.FavoritesRepository
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.favorites.domain.ResolveFavoriteCards
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Favorites module's own Koin bindings (`DESIGN.md` §5, `DEC-091`).
 *
 * It binds against `:core:domain` interfaces only — `CharacterRepository` and `FavoritesRepository` —
 * so no implementation type of `:core:data` is named here and the composition root stays the one place
 * that supplies them (`ADR-0014`). Both collaborators are stateless, so both are singletons.
 *
 * **`IC-020` and `CONF-79`.** [ResolveFavoriteCards] is bound over `CharacterRepository` because
 * `DESIGN.md` §4.5 — the section that settles `CONF-79` — has the section resolve its cards through the
 * normal cached path. No repository and no data source is declared here: a feature module consumes
 * `:core:domain` contracts and owns no data layer (`ADR-0001`).
 *
 * `ObserveFavoriteIds` is `:core:domain`'s and `coreModule` registers it, so it is resolved here rather
 * than re-declared: one observation instance is shared by every feature that reads it.
 *
 * [PresentationFormatters] is `IC-017`'s shared implementation, bound here with the module so the graph
 * is resolvable without a shell-side binding; a shell may override it.
 */
public val favoritesModule: Module =
    module {
        single { ResolveFavoriteCards(repository = get<CharacterRepository>()) }
        single { ObserveFavoriteIds(repository = get<FavoritesRepository>()) }
        single<PresentationFormatters> { DefaultPresentationFormatters }
    }
