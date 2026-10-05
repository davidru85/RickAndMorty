package io.github.davidru85.multiverse.feature.characterdetail.di

import io.github.davidru85.multiverse.core.presentation.PresentationBindings
import io.github.davidru85.multiverse.feature.characterdetail.ui.CharacterDetailViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Detail ViewModel, bound where `androidx.lifecycle.ViewModel` exists (`DEC-013`).
 *
 * `androidMain` rather than `commonMain` because the platform state holder is Android's. The id and
 * the list-provided header are **not** constructor dependencies here: they are inputs the route
 * supplies, so the binding stays a function of the graph alone.
 */
public val characterDetailViewModelModule: Module =
    module {
        viewModel { parameters ->
            CharacterDetailViewModel(
                id = parameters.get(),
                header = parameters.getOrNull(),
                getDetails = get(),
                toggleFavorite = get(),
                observeFavoriteIds = get(),
                formatters = get(),
                dispatcher = get(named(PresentationBindings.MAIN_DISPATCHER)),
            )
        }
    }
