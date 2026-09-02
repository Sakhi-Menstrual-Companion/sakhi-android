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
import team.sakhi.report.ReportDataBuilder
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.android.common.toSafeUserMessage

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
    val document: ReportDocument? = null,
    val errorMessage: String? = null,
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
    private val userProfileRepository: UserProfileRepository,
    private val reportPdfExporter: ReportPdfExporter,
    private val reportHtmlPdfExporter: ReportHtmlPdfExporter,
    private val hapticManager: AndroidHapticManager,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(defaultUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    private var preparedSharePdfUri: Uri? = null

    fun selectPreset(preset: ReportDateRangePreset) {
        val (startDate, endDate) = preset.dateRange()
        clearPreparedPdf()
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Config,
            errorMessage = null,
            exportErrorMessage = null,
            sharePdfUri = null,
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
        clearPreparedPdf()
        _uiState.value = _uiState.value.copy(
            exportErrorMessage = null,
            sharePdfUri = null,
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
        clearPreparedPdf()
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Config,
            exportErrorMessage = null,
            sharePdfUri = null,
        )
    }

    fun generate() {
        val requestedSession = sessionManager.current
        val userId = requestedSession?.targetUserId
        if (userId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Error,
                errorMessage = appContext.getString(R.string.reports_current_account_error),
            )
            return
        }
        // Real security fix (2026-07-15): partner-mode report generation is a
        // legitimate feature, but per Karan's explicit direction it must depend
        // entirely on the primary user having granted this specific permission --
        // same authorization model as every granular Logging view permission.
        // Before this fix there was NO gate here at all: any connected partner
        // could generate/export a full report regardless of what was actually
        // granted, and `SakhiDeepLink.OpenReport` reaches this screen without
        // even the `!isPartnerRole` nav-item hiding the main Profile route relies
        // on -- a real, live unauthorized-access path, the same class as the four
        // `targetUserId` instances fixed earlier today, just needing a permission
        // check added instead of a self-only block, since this one is meant to
        // work for an authorized partner.
        if (!requestedSession.isViewingOwnData && !requestedSession.can(Permission.GENERATE_REPORTS)) {
            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Error,
                errorMessage = appContext.getString(R.string.reports_permission_denied_error),
            )
            return
        }
        val activeSession = requestedSession ?: return

        val config = _uiState.value.config
        clearPreparedPdf()
        _uiState.value = _uiState.value.copy(
            phase = ReportsPhase.Generating,
            errorMessage = null,
            exportErrorMessage = null,
            sharePdfUri = null,
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
            val profileDeferred = async { userProfileRepository.get(userId) }

            val cyclesResult = cyclesDeferred.await()
            val logsResult = logsDeferred.await()

            if (discardStaleGeneration(activeSession)) return@launch

            val cycles = cyclesResult.getOrNull()
            val logs = logsResult.getOrNull()

            if (cycles == null || logs == null) {
                if (discardStaleGeneration(activeSession)) return@launch
                val errorMessage = (cyclesResult.exceptionOrNull() ?: logsResult.exceptionOrNull())
                    ?.toSafeUserMessage(appContext, R.string.reports_load_failed)
                    ?: appContext.getString(R.string.reports_load_failed)
                _uiState.value = _uiState.value.copy(
                    phase = ReportsPhase.Error,
                    errorMessage = errorMessage,
                )
                return@launch
            }

            val cyclesForReport = cycles.filteredForReportWindow(
                from = config.startDate,
                to = config.endDate,
            )
            val report = ReportDataBuilder.build(
                userId = userId,
                cycles = cyclesForReport,
                logs = logs,
                from = config.startDate,
                to = config.endDate,
            )
            val profile = profileDeferred.await().getOrNull()
            val document = ReportDocument(
                report = report,
                subjectName = activeSession.resolveSubjectName(profile?.name),
                generatedOn = DateConverter.today(),
                nextPredictedPeriod = profile?.cycleStatistics?.predictedNextPeriod
                    ?: cyclesForReport.maxByOrNull { it.cycleStartDate }?.cycleEndDate?.let { cycleEndDate ->
                        DateConverter.addDays(cycleEndDate, 1)
                    },
                trackedCyclesCount = cyclesForReport.count { it.isComplete && it.cycleLength != null },
            )
            if (discardStaleGeneration(activeSession)) return@launch

            val preparedUri = runCatching {
                // Renders SakhiCore's shared `ReportHtml`, so iOS and Android produce
                // the same document. `ReportHtmlPdfExporter` switches to the main
                // thread itself (WebView is main-thread-only), which is why this is
                // no longer wrapped in `Dispatchers.IO`.
                val file = reportHtmlPdfExporter.export(
                    document = document,
                    selectedSections = config.sections,
                )
                reportHtmlPdfExporter.buildShareUri(file)
            }.getOrElse { throwable ->
                android.util.Log.e("SakhiReport", "PDF export failed", throwable)
                if (discardStaleGeneration(activeSession)) return@launch
                _uiState.value = _uiState.value.copy(
                    phase = ReportsPhase.Error,
                    errorMessage = throwable.toSafeUserMessage(appContext, R.string.reports_export_failed),
                )
                return@launch
            }

            if (discardStaleGeneration(activeSession)) return@launch
            preparedSharePdfUri = preparedUri

            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Preview,
                document = document,
                errorMessage = null,
                exportErrorMessage = null,
            )
        }
    }

    private fun discardStaleGeneration(requestedSession: SessionContext?): Boolean {
        if (isStillCurrent(requestedSession)) {
            return false
        }

        if (_uiState.value.phase == ReportsPhase.Generating) {
            clearPreparedPdf()
            _uiState.value = _uiState.value.copy(
                phase = ReportsPhase.Config,
                document = null,
                errorMessage = null,
                exportErrorMessage = null,
                sharePdfUri = null,
            )
        }
        return true
    }

    fun exportPdf() {
        val preparedUri = preparedSharePdfUri ?: run {
            _uiState.value = _uiState.value.copy(
                exportErrorMessage = appContext.getString(
                    if (_uiState.value.document == null) {
                        R.string.reports_export_before_preview
                    } else {
                        R.string.reports_export_failed
                    },
                ),
            )
            return
        }
        hapticManager.impact(HapticImpact.MEDIUM)
        _uiState.value = _uiState.value.copy(
            exportErrorMessage = null,
            sharePdfUri = preparedUri,
        )
    }

    // isSameSubjectAs, not ==: see SessionContext.isSameSubjectAs.
    private fun isStillCurrent(session: SessionContext?): Boolean =
        session?.isSameSubjectAs(sessionManager.current) ?: (sessionManager.current == null)

    private fun clearPreparedPdf() {
        preparedSharePdfUri = null
    }

    private fun SessionContext.resolveSubjectName(profileName: String?): String {
        return when {
            profileName.isMeaningfulPersonName() -> profileName.orEmpty()
            !isViewingOwnData -> activePartnership?.partnerName
                ?.takeIfMeaningfulPersonName()
                ?: appContext.getString(R.string.reports_subject_default)
            userName.isMeaningfulPersonName() -> userName
            else -> appContext.getString(R.string.reports_subject_default)
        }
    }

    private fun String?.isMeaningfulPersonName(): Boolean = !takeIfMeaningfulPersonName().isNullOrEmpty()

    private fun String?.takeIfMeaningfulPersonName(): String? {
        val trimmed = this?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.lowercase()
        if (normalized.contains("partner") || normalized.contains("sakhi") || normalized == "unknown") {
            return null
        }
        return trimmed
    }

    private fun List<team.sakhi.models.CycleData>.filteredForReportWindow(
        from: LocalDate,
        to: LocalDate,
    ): List<team.sakhi.models.CycleData> {
        val earliestCycleStart = from.minus(2, DateTimeUnit.YEAR)
        return filter { cycle ->
            cycle.cycleStartDate >= earliestCycleStart && cycle.cycleStartDate <= to
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
