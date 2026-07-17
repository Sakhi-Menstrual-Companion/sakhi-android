package team.sakhi.android.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the home feature. */
val homeFeatureModule = module {
    viewModel {
        HomeViewModel(
            sessionManager = get(),
            syncStore = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            appContext = get(),
        )
    }
    viewModel {
        PartnerChecklistViewModel(
            sessionManager = get(),
            aiRepository = get(),
            hapticManager = get(),
        )
    }
}
