package team.sakhi.android.feature.logging

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import team.sakhi.android.common.CycleDetectionCoordinator

/** Koin module for the logging feature. */
val loggingFeatureModule = module {
    // `single`, not `factory`: it holds no state, and every caller must go through
    // the same recompute path so two concurrent log saves can't build competing
    // cycle sets. Registered here because logging is what triggers detection today;
    // it moves to a shared module the moment a second feature needs it.
    single { CycleDetectionCoordinator(periodLogRepository = get(), cycleDataRepository = get()) }
    viewModel {
        LoggingViewModel(
            appContext = get(),
            sessionManager = get(),
            periodLogRepository = get(),
            hapticManager = get(),
            widgetSnapshotManager = get(),
            cycleDetectionCoordinator = get(),
            cycleDataRepository = get(),
        )
    }
}
