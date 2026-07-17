package team.sakhi.android.feature.calendar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the calendar feature. */
val calendarFeatureModule = module {
    viewModel {
        CalendarViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            hapticManager = get(),
            appContext = get(),
        )
    }
}
