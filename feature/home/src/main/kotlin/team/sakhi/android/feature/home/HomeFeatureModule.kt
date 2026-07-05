package team.sakhi.android.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the root home placeholder is swapped to the real feature. */
val homeFeatureModule = module {
    viewModel {
        HomeViewModel(
            sessionManager = get(),
            syncStore = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
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
