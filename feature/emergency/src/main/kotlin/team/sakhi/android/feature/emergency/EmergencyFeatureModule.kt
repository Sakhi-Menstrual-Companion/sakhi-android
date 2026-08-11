package team.sakhi.android.feature.emergency

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Emergency Assistance.
 *
 * `EmergencyStore` and `EmergencyRealtimeCoordinator` are already registered as
 * process-lifetime singletons in SakhiCore's `appModule()`, so this only wires the
 * Android-side ViewModel over them. That is deliberate: the store has to outlive any one
 * screen, otherwise a live request would be dropped the moment she navigated away.
 */
val emergencyFeatureModule = module {
    viewModel {
        EmergencyViewModel(
            store = get(),
            realtime = get(),
            locationProvider = get(),
            hapticManager = get(),
            sessionManager = get(),
        )
    }
}
