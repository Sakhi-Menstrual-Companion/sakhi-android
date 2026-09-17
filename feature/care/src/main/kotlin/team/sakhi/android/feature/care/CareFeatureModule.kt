package team.sakhi.android.feature.care

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the care feature. */
val careFeatureModule = module {
    viewModel {
        LogPermissionViewModel(
            appContext = get(),
            sessionManager = get(),
            careStore = get(),
        )
    }
    viewModel {
        StayWithMeViewModel(
            appContext = get(),
            sessionManager = get(),
            store = get(),
            realtime = get(),
            placesProvider = get(),
            hapticManager = get(),
            alarmPreference = get(),
            kvStore = get(),
            biometrics = get(),
        )
    }
    viewModel {
        CareViewModel(
            appContext = get(),
            sessionManager = get(),
            careStore = get(),
            hapticManager = get(),
        )
    }
}
