package team.sakhi.android.feature.reports

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the reports feature. */
val reportsFeatureModule = module {
    single {
        ReportPdfExporter(context = get())
    }
    viewModel {
        ReportsViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            userProfileRepository = get(),
            reportPdfExporter = get(),
            hapticManager = get(),
            appContext = get(),
        )
    }
}
