package team.sakhi.android.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import team.sakhi.android.feature.home.inbox.NotificationInboxViewModel

/** Koin module for the home feature. */
val homeFeatureModule = module {
    viewModel {
        HomeViewModel(
            sessionManager = get(),
            syncStore = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            cycleDetectionCoordinator = get(),
            recommendationRepository = get(),
            appContext = get(),
        )
    }
    viewModel {
        PartnerChecklistViewModel(
            appContext = get(),
            sessionManager = get(),
            aiRepository = get(),
            hapticManager = get(),
        )
    }
    viewModel {
        NotificationInboxViewModel(
            store = get(),
            careStore = get(),
            sessionManager = get(),
        )
    }
}
