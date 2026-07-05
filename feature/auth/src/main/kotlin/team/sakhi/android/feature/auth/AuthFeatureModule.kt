package team.sakhi.android.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered into the app-wide Koin graph alongside SakhiCore's shared modules. */
val authFeatureModule = module {
    viewModel {
        AuthViewModel(
            authRepository = get(),
            accountClassifier = get(),
            appStateInputBridge = get(),
            hapticManager = get(),
        )
    }
}
