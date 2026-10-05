package io.github.davidru85.multiverse.feature.discovery.di

import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.presentation.PresentationBindings
import io.github.davidru85.multiverse.core.presentation.PresentationFormatters
import io.github.davidru85.multiverse.feature.discovery.ui.DiscoveryViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Discovery ViewModel, bound where `androidx.lifecycle.ViewModel` exists (`DEC-013`).
 *
 * `androidMain` rather than `commonMain` because the platform state holder is Android's. The pager is
 * built by the factory this binding receives with the **ViewModel's own scope**: `IC-014` gives one
 * pager to one state-holder scope, and no graph can supply a scope it does not own.
 */
public val discoveryViewModelModule: Module =
    module {
        viewModel {
            DiscoveryViewModel(
                pagerFor = { scope ->
                    get<CharacterPager> {
                        org.koin.core.parameter
                            .parametersOf(scope)
                    }
                },
                // Bound once by the composition root, by name (`DEC-145`).
                dispatcher = get<CoroutineDispatcher>(named(PresentationBindings.DEFAULT_DISPATCHER)),
                formatters = get<PresentationFormatters>(),
            )
        }
    }
