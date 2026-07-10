package team.sakhi.android.feature.logging

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the logging sheet route stops using the placeholder shell. */
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
