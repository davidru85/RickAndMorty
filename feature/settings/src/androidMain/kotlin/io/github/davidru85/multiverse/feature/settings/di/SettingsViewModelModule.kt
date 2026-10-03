package io.github.davidru85.multiverse.feature.settings.di

import io.github.davidru85.multiverse.feature.settings.ui.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Settings ViewModel, bound where `androidx.lifecycle.ViewModel` exists (`DEC-013`).
 *
 * `androidMain` rather than `commonMain` because the platform state holder is Android's: the iOS
 * `ObservableObject` is resolved by the app target, not by the graph. This is the counterpart of
 * `settingsModule`, which declares the use cases both platforms share.
 */
public val settingsViewModelModule: Module =
    module {
        viewModel { SettingsViewModel(get(), get(), get(), get()) }
    }
