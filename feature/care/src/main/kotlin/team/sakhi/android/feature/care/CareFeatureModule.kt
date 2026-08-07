package team.sakhi.android.feature.care

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the care feature. */
val careFeatureModule = module {
    viewModel {
        LogPermissionViewModel(
            appContext = get(),
            sessionManager = get(),
            careStore = get(),
        )
    }
    viewModel {
        CareViewModel(
            appContext = get(),
            sessionManager = get(),
            careStore = get(),
            hapticManager = get(),
        )
    }
}
