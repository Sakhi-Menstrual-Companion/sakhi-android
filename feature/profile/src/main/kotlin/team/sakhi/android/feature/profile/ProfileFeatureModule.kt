package team.sakhi.android.feature.profile

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the root profile placeholder is swapped to the real feature. */
val profileFeatureModule = module {
    viewModel {
        ProfileViewModel(
            sessionManager = get(),
            userProfileRepository = get(),
            cycleDataRepository = get(),
            authRepository = get(),
            appStateInputBridge = get(),
            featureAccessState = get(),
            hapticManager = get(),
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
}
