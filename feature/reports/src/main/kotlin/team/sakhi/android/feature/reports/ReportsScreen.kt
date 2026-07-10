package team.sakhi.android.feature.reports

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SecondaryButton
import team.sakhi.date.DateConverter
import team.sakhi.report.CalendarMonth
import team.sakhi.report.InsightSeverity
import team.sakhi.report.ReportData
import team.sakhi.report.ReportInsight

/**
 * Android port of the iOS health-report flow: config sheet, generating overlay,
 * then preview carousel. PDF export/share is still a platform gap, so the flow
 * stops at preview with an explicit note instead of faking the last step.
 */
@Composable
fun ReportsScreen(
    onClose: (() -> Unit)? = null,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.sharePdfUri) {
        val shareUri = uiState.sharePdfUri ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.reports_share_health_report),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        viewModel.consumeSharePdf()
    }

    when (uiState.phase) {
        ReportsPhase.Preview -> ReportsPreviewScreen(
            uiState = uiState,
            onBack = viewModel::returnToConfig,
            onDownloadPdf = viewModel::exportPdf,
            onDismissExportError = viewModel::dismissExportError,
        )

        ReportsPhase.Config,
        ReportsPhase.Generating,
        ReportsPhase.Error,
        -> ReportsConfigScreen(
            uiState = uiState,
            onClose = onClose,
            onPresetSelected = viewModel::selectPreset,
            onToggleSection = viewModel::toggleSection,
            onGenerate = viewModel::generate,
            onDismissError = viewModel::dismissError,
        )
    }
}

private enum class PreviewPageType {
    Cover,
    CycleSummary,
    PeriodCalendar,
    SymptomsFlow,
    MoodPatterns,
    Medications,
    Insights,
}

private data class PreviewPage(
    val titleRes: Int,
    val type: PreviewPageType,
)

@Composable
private fun ReportsConfigScreen(
    uiState: ReportsUiState,
    onClose: (() -> Unit)?,
    onPresetSelected: (ReportDateRangePreset) -> Unit,
    onToggleSection: (ReportSection) -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            onClose?.let { close ->
                SecondaryButton(
                    text = stringResource(R.string.reports_back),
                    onClick = close,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = stringResource(R.string.reports_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.reports_config_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ReportsSectionLabel(stringResource(R.string.reports_section_date_range))
            DateRangeCard(
                config = uiState.config,
                onPresetSelected = onPresetSelected,
            )

            ReportsSectionLabel(stringResource(R.string.reports_section_include))
            GlassCard {
                ReportSection.entries.forEachIndexed { index, section ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    SectionToggleRow(
                        section = section,
                        selected = section in uiState.config.sections,
                        onToggle = { onToggleSection(section) },
                    )
                }
            }

            if (ReportSection.Medications in uiState.config.sections) {
                SakhiAlert(
                    title = stringResource(R.string.reports_shared_gap_title),
                    message = stringResource(R.string.reports_shared_gap_message),
                )
            }

            Spacer(modifier = Modifier.height(SakhiSpacing.space10))
        }

        FooterBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonText = stringResource(R.string.reports_generate_pdf),
            buttonEnabled = uiState.phase != ReportsPhase.Generating,
            note = null,
            onButtonClick = onGenerate,
        )

        if (uiState.phase == ReportsPhase.Generating) {
            FullscreenMessage(
                title = stringResource(R.string.reports_building_title),
                subtitle = stringResource(R.string.reports_building_subtitle),
            )
        }

        if (uiState.phase == ReportsPhase.Error && uiState.errorMessage != null) {
            ErrorOverlay(
                message = uiState.errorMessage,
                onDismiss = onDismissError,
            )
        }
    }
}

@Composable
private fun DateRangeCard(
    config: ReportConfigUiState,
    onPresetSelected: (ReportDateRangePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = stringResource(config.preset.rowLabelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.reports_date_range_span,
                        DateConverter.formatShort(config.startDate),
                        DateConverter.formatShort(config.endDate),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(stringResource(config.preset.shortLabelRes))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ReportDateRangePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(stringResource(preset.shortLabelRes)) },
                            onClick = {
                                expanded = false
                                onPresetSelected(preset)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionToggleRow(
    section: ReportSection,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SakhiSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(section.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = selected,
            onCheckedChange = { onToggle() },
        )
    }
}

@Composable
private fun ReportsPreviewScreen(
    uiState: ReportsUiState,
    onBack: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDismissExportError: () -> Unit,
) {
    val report = uiState.report ?: run {
        EmptyState(
            title = stringResource(R.string.reports_preview_unavailable),
            subtitle = stringResource(R.string.reports_preview_unavailable_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val pages = remember(report, uiState.config.sections) {
        buildPreviewPages(
            report = report,
            selectedSections = uiState.config.sections,
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty() && pagerState.currentPage >= pages.size) {
            pagerState.scrollToPage(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(
                text = stringResource(R.string.reports_back),
                onClick = onBack,
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.reports_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = pluralStringResource(R.plurals.reports_page_count, pages.size, pages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(88.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = SakhiSpacing.space3,
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SakhiSpacing.space5),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xxl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(595.28f / 841.89f),
                ) {
                    ReportPreviewPageContent(
                        page = page,
                        report = report,
                    )
                }

                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PageDots(
                    count = pages.size,
                    currentPage = pagerState.currentPage,
                )
            }
        }

        FooterBar(
            buttonText = stringResource(R.string.reports_download_pdf),
            buttonEnabled = !uiState.isExportingPdf,
            note = stringResource(R.string.reports_download_note),
            onButtonClick = onDownloadPdf,
        )

        if (uiState.isExportingPdf) {
            FullscreenMessage(
                title = stringResource(R.string.reports_preparing_pdf_title),
                subtitle = stringResource(R.string.reports_preparing_pdf_subtitle),
            )
        }

        if (uiState.exportErrorMessage != null) {
            ErrorOverlay(
                message = uiState.exportErrorMessage,
                onDismiss = onDismissExportError,
            )
        }
    }
}

@Composable
private fun ReportPreviewPageContent(
    page: PreviewPage,
    report: ReportData,
) {
    when (page.type) {
        PreviewPageType.Cover -> CoverPage(report)
        PreviewPageType.CycleSummary -> CycleSummaryPage(report)
        PreviewPageType.PeriodCalendar -> PeriodCalendarPage(report)
        PreviewPageType.SymptomsFlow -> SymptomsFlowPage(report)
        PreviewPageType.MoodPatterns -> MoodPatternsPage(report)
        PreviewPageType.Medications -> MedicationsGapPage()
        PreviewPageType.Insights -> InsightsPage(report)
    }
}

@Composable
private fun CoverPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            Text(
                text = stringResource(R.string.reports_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.reports_date_range_span,
                    DateConverter.formatShort(report.periodFrom),
                    DateConverter.formatShort(report.periodTo),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reports_cover_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassCard {
            StatRow(stringResource(R.string.reports_generated_on), report.generatedAt)
            StatRow(stringResource(R.string.reports_cycles_analyzed), report.cyclesAnalyzed.toString())
            StatRow(
                stringResource(R.string.reports_prediction_confidence),
                stringResource(R.string.reports_percent_value, (report.predictionConfidence * 100).toInt()),
            )
        }
    }
}

@Composable
private fun CycleSummaryPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_cycle_summary),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        StatGrid(report)
    }
}

@Composable
private fun PeriodCalendarPage(report: ReportData) {
    val previewMonths = report.calendarMonths.take(2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_period_calendar),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = pluralStringResource(
                R.plurals.reports_months_included,
                report.calendarMonths.size,
                report.calendarMonths.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        previewMonths.forEach { month ->
            CalendarMonthPreview(month)
        }
    }
}

@Composable
private fun SymptomsFlowPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_symptoms_flow),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (report.topSymptoms.isNotEmpty()) {
            FrequencyList(report.topSymptoms.map { it.name to it.percentage })
        }
        if (report.flowTimeline.isNotEmpty()) {
            GlassCard {
                report.flowTimeline.takeLast(4).forEach { point ->
                    StatRow(
                        label = DateConverter.formatShort(point.date),
                        value = point.intensity,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodPatternsPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_mood_patterns),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        FrequencyList(report.topMoods.map { it.name to it.percentage })
    }
}

@Composable
private fun MedicationsGapPage() {
    EmptyState(
        title = stringResource(R.string.reports_page_medications),
        subtitle = stringResource(R.string.reports_medications_gap_subtitle),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun InsightsPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_insights),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (report.insights.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.reports_no_insights),
                subtitle = stringResource(R.string.reports_no_insights_subtitle),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                report.insights.take(3).forEach { insight ->
                    InsightCard(insight)
                }
            }
        }
    }
}

@Composable
private fun StatGrid(report: ReportData) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatRow(stringResource(R.string.reports_cycles_analyzed), report.cyclesAnalyzed.toString())
        StatRow(
            stringResource(R.string.reports_average_cycle_length),
            stringResource(R.string.reports_days_value, report.averageCycleLength.toInt()),
        )
        // iOS's own ReportViewModel gates shortest/longest behind cyclesAnalyzed > 0
        // (nil when there's no real cycle data, omitted from its PDF export note
        // entirely rather than shown as a fabricated range) -- shortestCycleDays/
        // longestCycleDays both default to CycleStatistics.default's placeholder
        // (28) when zero cycles are analyzed, which would otherwise render as a
        // meaningless "28 / 28 days" range presented as if it were the user's own
        // data. Matching iOS's real gating here, not inventing a new one.
        if (report.cyclesAnalyzed > 0) {
            StatRow(
                stringResource(R.string.reports_shortest_longest),
                stringResource(
                    R.string.reports_shortest_longest_value,
                    report.shortestCycleDays,
                    report.longestCycleDays,
                ),
            )
        }
        StatRow(
            stringResource(R.string.reports_average_period_length),
            stringResource(R.string.reports_days_value, report.averagePeriodLength.toInt()),
        )
        StatRow(
            stringResource(R.string.reports_regularity),
            stringResource(R.string.reports_percent_value, (report.regularityScore * 100).toInt()),
        )
        StatRow(
            stringResource(R.string.reports_prediction_confidence),
            stringResource(R.string.reports_percent_value, (report.predictionConfidence * 100).toInt()),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = SakhiSpacing.space1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CalendarMonthPreview(month: CalendarMonth) {
    val context = LocalContext.current
    val sortedDays = remember(month) { month.days.keys.sortedBy { it.toEpochDays() } }
    val firstDay = sortedDays.firstOrNull()
    val monthTitle = if (firstDay == null) {
        context.getString(R.string.reports_fallback_month, month.month)
    } else {
        val monthName = Month.of(firstDay.monthNumber)
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
        "$monthName ${month.year}"
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            WeekdayHeader()
            sortedDays.take(14).chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    week.forEach { date ->
                        val markerKey = month.days[date]?.toString()?.lowercase().orEmpty()
                        val markerDescription = reportMarkerDescription(context, markerKey)
                        val markerColor = when {
                            "ovulation" in markerKey || "fertile" in markerKey -> MaterialTheme.colorScheme.tertiary
                            "predicted" in markerKey -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            "pms" in markerKey -> MaterialTheme.colorScheme.secondary
                            "period" in markerKey -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(markerColor)
                                .semantics {
                                    contentDescription = buildString {
                                        append(date.dayOfMonth)
                                        append(" ")
                                        append(monthTitle)
                                        markerDescription?.let {
                                            append(", ")
                                            append(it)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyList(items: List<Pair<String, Double>>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.take(5).forEach { (name, percentage) ->
            StatRow(name, stringResource(R.string.reports_percent_value, percentage.toInt()))
        }
    }
}

@Composable
private fun InsightCard(insight: ReportInsight) {
    val accent = when (insight.severity) {
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
        InsightSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
        InsightSeverity.ALERT -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = accent.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                text = insight.body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun reportMarkerDescription(context: android.content.Context, markerKey: String): String? = when {
    "ovulation" in markerKey -> context.getString(R.string.reports_marker_ovulation)
    "fertile" in markerKey -> context.getString(R.string.reports_marker_fertile)
    "predicted" in markerKey -> context.getString(R.string.reports_marker_predicted)
    "pms" in markerKey -> context.getString(R.string.reports_marker_pms)
    "period" in markerKey -> context.getString(R.string.reports_marker_period)
    else -> null
}

@Composable
private fun FooterBar(
    buttonText: String,
    buttonEnabled: Boolean,
    note: String?,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = SakhiSpacing.space2,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            PrimaryButton(
                text = buttonText,
                onClick = onButtonClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FullscreenMessage(
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakhiSpacing.space6),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                CircularProgressIndicator()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        SakhiAlert(
            title = stringResource(R.string.reports_error_title),
            message = message,
            tone = SakhiAlertTone.Error,
            dismissLabel = stringResource(R.string.reports_error_retry),
            onDismiss = onDismiss,
            modifier = Modifier.padding(SakhiSpacing.space6),
        )
    }
}

@Composable
private fun ReportsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = SakhiSpacing.space1),
    )
}

@Composable
private fun PageDots(
    count: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(width = if (index == currentPage) 18.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(SakhiRadius.full))
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val weekdays = remember {
        listOf(
            java.time.DayOfWeek.SUNDAY,
            java.time.DayOfWeek.MONDAY,
            java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
            java.time.DayOfWeek.SATURDAY,
        ).map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        weekdays.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildPreviewPages(
    report: ReportData,
    selectedSections: Set<ReportSection>,
): List<PreviewPage> {
    val pages = mutableListOf(
        PreviewPage(
            titleRes = R.string.reports_page_cover,
            type = PreviewPageType.Cover,
        ),
        PreviewPage(
            titleRes = R.string.reports_page_cycle_summary,
            type = PreviewPageType.CycleSummary,
        ),
    )

    if (ReportSection.PeriodCalendar in selectedSections && report.calendarMonths.isNotEmpty()) {
        pages += PreviewPage(
            titleRes = R.string.reports_page_period_calendar,
            type = PreviewPageType.PeriodCalendar,
        )
    }
    if (ReportSection.Symptoms in selectedSections &&
        (report.topSymptoms.isNotEmpty() || report.flowTimeline.isNotEmpty())
    ) {
        pages += PreviewPage(
            titleRes = R.string.reports_page_symptoms_flow,
            type = PreviewPageType.SymptomsFlow,
        )
    }
    if (ReportSection.MoodPatterns in selectedSections && report.topMoods.isNotEmpty()) {
        pages += PreviewPage(
            titleRes = R.string.reports_page_mood_patterns,
            type = PreviewPageType.MoodPatterns,
        )
    }
    if (ReportSection.Medications in selectedSections) {
        pages += PreviewPage(
            titleRes = R.string.reports_page_medications,
            type = PreviewPageType.Medications,
        )
    }
    if (ReportSection.Insights in selectedSections && report.insights.isNotEmpty()) {
        pages += PreviewPage(
            titleRes = R.string.reports_page_insights,
            type = PreviewPageType.Insights,
        )
    }

    return pages
}
