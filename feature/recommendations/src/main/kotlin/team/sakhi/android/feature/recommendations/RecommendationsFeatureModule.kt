package team.sakhi.android.feature.recommendations

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the recommendations feature. */
val recommendationsFeatureModule = module {
    viewModel {
        RecommendationsViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            userProfileRepository = get(),
            recommendationRepository = get(),
            periodLogRepository = get(),
            recommendationInsightService = get(),
            appContext = get(),
        )
    }
}
