package team.sakhi.android.feature.profile

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the profile feature. */
val profileFeatureModule = module {
    viewModel {
        OfflineModeViewModel(
            sessionManager = get(),
            careStore = get(),
            careRealtimeCoordinator = get(),
            syncEngine = get(),
            featureAccessState = get(),
        )
    }
    viewModel {
        ProfileViewModel(
            sessionManager = get(),
            userProfileRepository = get(),
            cycleDataRepository = get(),
            authRepository = get(),
            appStateInputBridge = get(),
            featureAccessState = get(),
            hapticManager = get(),
            appContext = get(),
        )
    }
    viewModel {
        AppIntegrationViewModel(
            sessionManager = get(),
            healthConnectManager = get(),
            appContext = get(),
        )
    }
    viewModel {
        SanityContentViewModel(
            sanityRepository = get(),
        )
    }
    viewModel {
        MyDataViewModel(
            sessionManager = get(),
            localStore = get(),
            userProfileRepository = get(),
            periodLogRepository = get(),
            cycleDataRepository = get(),
            partnerCareRepository = get(),
            aiRepository = get(),
            appContext = get(),
        )
    }
}
