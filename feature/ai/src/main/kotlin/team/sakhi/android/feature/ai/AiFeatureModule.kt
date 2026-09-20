package team.sakhi.android.feature.ai

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the AI feature. */
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
            widgetSnapshotManager = get(),
            localStore = get(),
            appContext = get(),
            topicMemory = get(),
        )
    }
}
