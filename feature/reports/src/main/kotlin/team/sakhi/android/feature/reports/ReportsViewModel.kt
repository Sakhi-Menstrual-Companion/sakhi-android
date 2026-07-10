package team.sakhi.android.feature.reports

import android.content.Context
import androidx.annotation.StringRes
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.date.DateConverter
import team.sakhi.report.ReportData
import team.sakhi.report.ReportDataBuilder
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionManager

enum class ReportsPhase {
    Config,
    Generating,
    Preview,
    Error,
}

enum class ReportDateRangePreset(
    @StringRes val shortLabelRes: Int,
    @StringRes val rowLabelRes: Int,
) {
    LastMonth(
        shortLabelRes = R.string.reports_preset_last_month_short,
        rowLabelRes = R.string.reports_preset_last_month_row,
    ),
    ThreeMonths(
        shortLabelRes = R.string.reports_preset_three_months_short,
        rowLabelRes = R.string.reports_preset_three_months_row,
    ),
    SixMonths(
        shortLabelRes = R.string.reports_preset_six_months_short,
        rowLabelRes = R.string.reports_preset_six_months_row,
    ),
    OneYear(
        shortLabelRes = R.string.reports_preset_one_year_short,
        rowLabelRes = R.string.reports_preset_one_year_row,
    ),
    Lifetime(
        shortLabelRes = R.string.reports_preset_lifetime_short,
        rowLabelRes = R.string.reports_preset_lifetime_row,
    ),
    ;

    fun dateRange(referenceDate: LocalDate = DateConverter.today()): Pair<LocalDate, LocalDate> =
        when (this) {
            LastMonth -> referenceDate.minus(1, DateTimeUnit.MONTH) to referenceDate
            ThreeMonths -> referenceDate.minus(3, DateTimeUnit.MONTH) to referenceDate
            SixMonths -> referenceDate.minus(6, DateTimeUnit.MONTH) to referenceDate
            OneYear -> referenceDate.minus(1, DateTimeUnit.YEAR) to referenceDate
            Lifetime -> LocalDate(1900, 1, 1) to referenceDate
        }
}

enum class ReportSection(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    CycleOverview(
        titleRes = R.string.reports_section_cycle_overview_title,
        subtitleRes = R.string.reports_section_cycle_overview_subtitle,
    ),
    PeriodCalendar(
        titleRes = R.string.reports_section_period_calendar_title,
        subtitleRes = R.string.reports_section_period_calendar_subtitle,
    ),
    Symptoms(
        titleRes = R.string.reports_section_symptoms_title,
        subtitleRes = R.string.reports_section_symptoms_subtitle,
    ),
    MoodPatterns(
        titleRes = R.string.reports_section_mood_title,
        subtitleRes = R.string.reports_section_mood_subtitle,
    ),
    Medications(
        titleRes = R.string.reports_section_medications_title,
        subtitleRes = R.string.reports_section_medications_subtitle,
    ),
    Insights(
        titleRes = R.string.reports_section_insights_title,
        subtitleRes = R.string.reports_section_insights_subtitle,
    ),
    ;
}

data class ReportConfigUiState(
    val preset: ReportDateRangePreset,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val sections: Set<ReportSection>,
)

data class ReportsUiState(
    val phase: ReportsPhase,
    val config: ReportConfigUiState,
    val report: ReportData? = null,
    val errorMessage: String? = null,
    val isExportingPdf: Boolean = false,
    val exportErrorMessage: String? = null,
    val sharePdfUri: Uri? = null,
)

/**
 * Android owns only the config sheet and flow phase. The report payload itself
 * still comes straight from shared `ReportDataBuilder`.
 */
class ReportsViewModel(
    private val sessionManager: SessionManager,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val reportPdfExporter: ReportPdfExporter,
    private val hapticManager: AndroidHapticManager,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(defaultUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    fun selectPreset(preset: ReportDateRangePreset) {
        val (startDate, endDate) = preset.dateRange()
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Config,
            errorMessage = null,
            config = _uiState.value.config.copy(
                preset = preset,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    fun toggleSection(section: ReportSection) {
        hapticManager.selection()
        val updatedSections = _uiState.value.config.sections.toMutableSet()
        if (!updatedSections.add(section)) {
            updatedSections.remove(section)
        }
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(sections = updatedSections),
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Config,
            errorMessage = null,
        )
    }

    fun dismissExportError() {
        _uiState.value = _uiState.value.copy(exportErrorMessage = null)
    }

    fun consumeSharePdf() {
        _uiState.value = _uiState.value.copy(sharePdfUri = null)
    }

    fun returnToConfig() {
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Config,
            exportErrorMessage = null,
            sharePdfUri = null,
            isExportingPdf = false,
        )
    }

    fun generate() {
        val userId = sessionManager.current?.targetUserId
        if (userId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Error,
                errorMessage = appContext.getString(R.string.reports_current_account_error),
            )
            return
        }

        val config = _uiState.value.config
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Generating,
            errorMessage = null,
        )

        viewModelScope.launch {
            val cyclesDeferred = async { cycleDataRepository.getAll(userId) }
            val logsDeferred = async {
                periodLogRepository.getForDateRange(
                    userId = userId,
                    from = config.startDate,
                    to = config.endDate,
                )
            }

            val cyclesResult = cyclesDeferred.await()
            val logsResult = logsDeferred.await()

            if (discardStaleGeneration(userId)) return@launch

            val cycles = cyclesResult.getOrNull()
            val logs = logsResult.getOrNull()

            if (cycles == null || logs == null) {
                val errorMessage = cyclesResult.exceptionOrNull()?.message
                    ?: logsResult.exceptionOrNull()?.message
                    ?: appContext.getString(R.string.reports_load_failed)
                _uiState.value = _uiState.value.copy(
                    phase = ReportsPhase.Error,
                    errorMessage = errorMessage,
                )
                return@launch
            }

            val report = ReportDataBuilder.build(
                userId = userId,
                cycles = cycles,
                logs = logs,
                from = config.startDate,
                to = config.endDate,
            )

            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Preview,
                report = report,
                errorMessage = null,
                exportErrorMessage = null,
            )
        }
    }

    private fun discardStaleGeneration(requestedTargetUserId: String): Boolean {
        if (sessionManager.current?.targetUserId == requestedTargetUserId) {
            return false
        }

        if (_uiState.value.phase == ReportsPhase.Generating) {
            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Config,
                errorMessage = null,
            )
        }
        return true
    }

    fun exportPdf() {
        val report = _uiState.value.report ?: run {
            _uiState.value = _uiState.value.copy(
                exportErrorMessage = appContext.getString(R.string.reports_export_before_preview),
            )
            return
        }
        hapticManager.impact(HapticImpact.MEDIUM)

        val selectedSections = _uiState.value.config.sections
        _uiState.value = _uiState.value.copy(
            isExportingPdf = true,
            exportErrorMessage = null,
            sharePdfUri = null,
        )

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = reportPdfExporter.export(
                        report = report,
                        selectedSections = selectedSections,
                    )
                    reportPdfExporter.buildShareUri(file)
                }
            }.onSuccess { uri ->
                _uiState.value = _uiState.value.copy(
                    isExportingPdf = false,
                    sharePdfUri = uri,
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isExportingPdf = false,
                    exportErrorMessage = throwable.message ?: appContext.getString(R.string.reports_export_failed),
                )
            }
        }
    }

    companion object {
        fun defaultUiState(): ReportsUiState {
            val (startDate, endDate) = ReportDateRangePreset.ThreeMonths.dateRange()
            return ReportsUiState(
                phase = ReportsPhase.Config,
                config = ReportConfigUiState(
                    preset = ReportDateRangePreset.ThreeMonths,
                    startDate = startDate,
                    endDate = endDate,
                    sections = ReportSection.entries.toSet(),
                ),
            )
        }
    }
}
