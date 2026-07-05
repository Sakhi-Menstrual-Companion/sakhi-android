package team.sakhi.android.feature.care

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the care route stops using the placeholder shell. */
val careFeatureModule = module {
    viewModel {
        CareViewModel(
            sessionManager = get(),
            careStore = get(),
            hapticManager = get(),
        )
    }
}
