package team.sakhi.android.feature.reports

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Registered by :app once the reports route stops using the placeholder shell. */
val reportsFeatureModule = module {
    single {
        ReportPdfExporter(context = get())
    }
    viewModel {
        ReportsViewModel(
            sessionManager = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            reportPdfExporter = get(),
            hapticManager = get(),
        )
    }
}
