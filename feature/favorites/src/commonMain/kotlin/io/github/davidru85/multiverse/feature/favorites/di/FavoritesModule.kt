package io.github.davidru85.multiverse.feature.favorites.di

import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
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
 * `PresentationFormatters`, `IC-017`'s shared implementation, is bound once by the composition root
 * (`DEC-145`), not here.
 */
public val favoritesModule: Module =
    module {
        single { ResolveFavoriteCards(repository = get<CharacterRepository>()) }
    }
