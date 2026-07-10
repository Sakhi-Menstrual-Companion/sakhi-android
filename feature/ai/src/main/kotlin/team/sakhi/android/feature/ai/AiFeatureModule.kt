package team.sakhi.android.feature.ai

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the AI route stops using the placeholder shell. */
val aiFeatureModule = module {
    viewModel {
        ChatViewModel(
            sessionManager = get(),
            aiRepository = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            reportPdfExporter = get(),
            safePlaceRanker = get(),
            locationProvider = get(),
            hapticManager = get(),
            appContext = get(),
        )
    }
}
