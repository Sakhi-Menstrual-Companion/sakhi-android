package team.sakhi.android.feature.logging

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the logging feature. */
val loggingFeatureModule = module {
    viewModel {
        LoggingViewModel(
            appContext = get(),
            sessionManager = get(),
            periodLogRepository = get(),
            hapticManager = get(),
            widgetSnapshotManager = get(),
        )
    }
}
