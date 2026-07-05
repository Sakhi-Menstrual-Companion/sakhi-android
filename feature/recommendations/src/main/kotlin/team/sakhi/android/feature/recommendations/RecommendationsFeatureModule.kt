package team.sakhi.android.feature.recommendations

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the recommendations route stops using the placeholder shell. */
val recommendationsFeatureModule = module {
    viewModel {
        RecommendationsViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            userProfileRepository = get(),
            recommendationRepository = get(),
        )
    }
}
