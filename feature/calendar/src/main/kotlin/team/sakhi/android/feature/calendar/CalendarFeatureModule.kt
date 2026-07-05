package team.sakhi.android.feature.calendar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the calendar route stops using the placeholder shell. */
val calendarFeatureModule = module {
    viewModel {
        CalendarViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            hapticManager = get(),
        )
    }
}
