package team.sakhi.android.feature.onboarding

import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Registered by :app once onboarding replaces the shell placeholder route. */
val onboardingFeatureModule = module {
    viewModel { params ->
        val flowId: String = params.get()
        OnboardingViewModel(
            flowId = flowId,
            flowStore = get { parametersOf(flowId) },
            authRepository = get(),
            careStore = get(),
            periodLogRepository = get(),
            cycleDataRepository = get(),
            userProfileRepository = get(),
            healthConnectManager = get(),
            hapticManager = get(),
        )
    }
}
