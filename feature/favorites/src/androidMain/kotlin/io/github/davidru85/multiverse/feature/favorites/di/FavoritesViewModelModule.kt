package io.github.davidru85.multiverse.feature.favorites.di

import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.favorites.ui.FavoritesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Android half of the Favorites module (`DESIGN.md` §5, `ADR-0006`): the `IC-020` state holder,
 * declared with Koin's `viewModel` DSL so `koinViewModel()` resolves it from the graph and the
 * framework owns its lifecycle.
 *
 * It is a separate module from [favoritesModule] because `androidx.lifecycle.ViewModel` and the
 * `viewModel` DSL exist only in an Android source set: the shared module must stay platform-free so the
 * same graph serves iOS, while this declaration cannot leave `androidMain`.
 *
 * A ViewModel is a **factory**, never a singleton: its scope is the one the framework gives it, so a
 * second entry into the section does not share the first one's state.
 */
public val favoritesViewModelModule: Module =
    module {
        viewModel {
            FavoritesViewModel(
                observeFavoriteIds = get<ObserveFavoriteIds>(),
                resolveFavoriteCards = get(),
                formatters = get<PresentationFormatters>(),
            )
        }
    }
