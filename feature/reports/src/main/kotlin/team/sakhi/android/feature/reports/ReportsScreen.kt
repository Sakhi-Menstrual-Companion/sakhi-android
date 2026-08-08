package team.sakhi.android.feature.reports

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth as FilledCalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth as OutlinedCalendarMonth
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.TextStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiColors
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.CyclePhase
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.date.DateConverter
import team.sakhi.report.CalendarMonth
import team.sakhi.report.InsightSeverity
import team.sakhi.report.ReportData
import team.sakhi.report.ReportInsight
import team.sakhi.android.ui.SakhiSwitch

/** Android port of the iOS health-report flow: config sheet, real PDF generation, then preview/share. */
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

    // Same on-screen-back-button-only gap as elsewhere in the app: system back from
    // Preview used to skip straight past Config and close the whole sheet.
    BackHandler(enabled = uiState.phase == ReportsPhase.Preview) { viewModel.returnToConfig() }

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
    Box(modifier = Modifier.fillMaxSize()) {
        DetailSheetScaffold(
            title = stringResource(R.string.reports_title),
            subtitle = stringResource(R.string.reports_config_subtitle),
            headerIcon = Icons.Filled.Description,
            onBack = onClose ?: {},
            contentPadding = PaddingValues(
                start = SakhiSpacing.space5,
                top = SakhiSpacing.space5,
                end = SakhiSpacing.space5,
                // Must clear the floating `FooterBar` below, which is a sibling in the
                // outer Box rather than part of this scroll. At `space16` (64dp) the
                // footer still covered the last rows -- measured on device: the footer's
                // top edge sits at y2189 of 2400, i.e. ~77dp of screen, so the toggles
                // behind it could never be scrolled into view.
                bottom = ReportsFooterClearance,
            ),
        ) {
            ReportsSectionLabel(stringResource(R.string.reports_section_date_range))
            DateRangeCard(
                config = uiState.config,
                onPresetSelected = onPresetSelected,
            )

            ReportsSectionLabel(stringResource(R.string.reports_section_include))
            GlassCard {
                ReportSection.entries.forEachIndexed { index, section ->
                    if (index > 0) {
                        // Shared hairline, not a raw 1dp `outlineVariant` box -- the
                        // same treatment the profile screens use.
                        SakhiListDivider()
                    }
                    SectionToggleRow(
                        section = section,
                        selected = section in uiState.config.sections,
                        onToggle = { onToggleSection(section) },
                    )
                }
            }

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
            Icon(
                imageVector = Icons.Filled.FilledCalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = stringResource(config.preset.rowLabelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    // iOS: `Capsule().fill(DS.Colors.groupedBackground)`. `surfaceVariant`
                    // rendered this as a lavender pill on a pink sheet.
                    color = sakhiGroupedBackground(),
                ) {
                    TextButton(onClick = { expanded = true }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // iOS: lato(13, .bold) in secondaryLabel.
                            Text(
                                text = stringResource(config.preset.shortLabelRes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = sakhiSecondaryLabel(),
                            )
                            // `UnfoldMore` is the Material twin of iOS's
                            // `chevron.up.chevron.down`. A single down-chevron reads as
                            // "expands downward"; the double arrow is the pick-one-from-a-
                            // list affordance iOS chose here, and this is a menu, not an
                            // expander. Sized down from Material's 24dp default because
                            // iOS draws the glyph at 10pt beside 13pt text.
                            Icon(
                                imageVector = Icons.Rounded.UnfoldMore,
                                contentDescription = null,
                                tint = sakhiSecondaryLabel(),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
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
        Icon(
            imageVector = reportSectionIcon(section),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
                color = sakhiSecondaryLabel(),
            )
        }
        SakhiSwitch(
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
    val document = uiState.document ?: run {
        EmptyState(
            title = stringResource(R.string.reports_preview_unavailable),
            subtitle = stringResource(R.string.reports_preview_unavailable_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val pages = remember(document.report, uiState.config.sections) {
        buildPreviewPages(
            report = document.report,
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
                .padding(
                    start = SakhiSpacing.space6,
                    top = SakhiSpacing.space5,
                    end = SakhiSpacing.space6,
                    bottom = SakhiSpacing.space3,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.reports_preview_title),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = pluralStringResource(R.plurals.reports_page_count, pages.size, pages.size),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 11.sp * 1.4f,
                    ),
                    color = sakhiSecondaryLabel(),
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(40.dp))
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
                    .padding(horizontal = SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.lg),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(595.28f / 841.89f),
                ) {
                    ReportPreviewPageContent(
                        page = page,
                        document = document,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = sakhiSecondaryLabel(),
                    )
                    PageDots(
                        count = pages.size,
                        currentPage = pagerState.currentPage,
                    )
                }
            }
        }

        FooterBar(
            buttonText = stringResource(R.string.reports_download_pdf),
            buttonEnabled = true,
            note = stringResource(R.string.reports_download_note),
            onButtonClick = onDownloadPdf,
        )

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
    document: ReportDocument,
) {
    when (page.type) {
        PreviewPageType.Cover -> CoverPage(document)
        PreviewPageType.CycleSummary -> CycleSummaryPage(document)
        PreviewPageType.PeriodCalendar -> PeriodCalendarPage(document.report)
        PreviewPageType.SymptomsFlow -> SymptomsFlowPage(document.report)
        PreviewPageType.MoodPatterns -> MoodPatternsPage(document.report)
        PreviewPageType.Insights -> InsightsPage(document.report)
    }
}

@Composable
private fun CoverPage(document: ReportDocument) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = SakhiSpacing.space4),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )

            Column(
                modifier = Modifier.padding(SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1)) {
                    Text(
                        text = stringResource(R.string.reports_cover_title_menstrual),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.reports_cover_title_health_report),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                SakhiListDivider()

                GlassCard {
                    PreviewInfoRow(
                        label = stringResource(R.string.reports_prepared_for),
                        value = document.subjectName,
                    )
                    PreviewInfoRow(
                        label = stringResource(R.string.reports_report_period),
                        value = stringResource(
                            R.string.reports_date_range_span,
                            DateConverter.formatShort(document.report.periodFrom),
                            DateConverter.formatShort(document.report.periodTo),
                        ),
                    )
                    PreviewInfoRow(
                        label = stringResource(R.string.reports_generated_on),
                        value = fullDate(document.generatedOn),
                    )
                    PreviewInfoRow(
                        label = stringResource(R.string.reports_data_source),
                        value = stringResource(R.string.reports_source_app_name),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.reports_pdf_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space6),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reports_brand_wordmark),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.reports_confidential),
                style = MaterialTheme.typography.labelSmall,
                color = sakhiSecondaryLabel(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CycleSummaryPage(document: ReportDocument) {
    val report = document.report
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_cycle_summary),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.reports_key_statistics),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            StatRow(
                label = stringResource(R.string.reports_average_cycle_length),
                value = stringResource(R.string.reports_days_value, report.averageCycleLength.toInt()),
                valueColor = MaterialTheme.colorScheme.primary,
                note = if (report.cyclesAnalyzed > 0) {
                    stringResource(R.string.reports_range_value, report.shortestCycleDays, report.longestCycleDays)
                } else {
                    null
                },
            )
            StatRow(
                label = stringResource(R.string.reports_average_period_length),
                value = stringResource(R.string.reports_days_value_decimal, report.averagePeriodLength),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            StatRow(
                label = stringResource(R.string.reports_cycles_tracked),
                value = document.trackedCyclesCount.toString(),
                note = stringResource(R.string.reports_cycles_tracked_note),
            )
            StatRow(
                label = stringResource(R.string.reports_regularity),
                value = stringResource(
                    R.string.reports_regularity_value,
                    report.regularityPercent(),
                    report.regularityLabel(),
                ),
            )
            StatRow(
                label = stringResource(R.string.reports_period_days_logged),
                value = stringResource(R.string.reports_days_value, report.totalPeriodDays()),
            )
        }
        if (document.nextPredictedPeriod != null) {
            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                ) {
                    Text(
                        text = stringResource(R.string.reports_next_period_prediction),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = fullDate(document.nextPredictedPeriod),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.reports_expected_start_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }
        }
        LoggedActivityGrid(report)
    }
}

// Matches iOS's real `ReportCyclePage`'s always-shown "Logged Activity" table
// (`SakhiReportPDFGenerator.swift`) -- was missing entirely from Android's Cycle
// Summary page and PDF, since the shared `ReportData` had no fields for
// painkiller/doctor-visit/notes days at all until this fix.
@Composable
private fun LoggedActivityGrid(report: ReportData) {
    Text(
        text = stringResource(R.string.reports_logged_activity),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatRow(
            stringResource(R.string.reports_total_symptoms_logged),
            stringResource(R.string.reports_entries_value, report.topSymptoms.sumOf { it.count }),
        )
        StatRow(
            stringResource(R.string.reports_total_mood_entries),
            stringResource(R.string.reports_entries_value, report.topMoods.sumOf { it.count }),
        )
        StatRow(
            stringResource(R.string.reports_days_with_notes),
            stringResource(R.string.reports_days_value, report.daysWithNotes),
        )
        StatRow(
            stringResource(R.string.reports_medication_days),
            stringResource(R.string.reports_days_value, report.painkillerDays),
        )
        StatRow(
            stringResource(R.string.reports_doctor_visits),
            stringResource(R.string.reports_count_value, report.doctorVisitDays),
        )
    }
}

@Composable
private fun PeriodCalendarPage(report: ReportData) {
    val previewMonths = report.calendarMonths.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            color = sakhiSecondaryLabel(),
        )
        CalendarLegend()

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
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_symptoms_flow),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.reports_symptom_frequency),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        FrequencyTableCard(
            headers = Triple(
                stringResource(R.string.reports_table_symptom),
                stringResource(R.string.reports_table_days),
                stringResource(R.string.reports_table_percent_tracked),
            ),
            rows = report.topSymptoms.take(8).map { symptom ->
                Triple(
                    symptom.name,
                    symptom.count.toString(),
                    contextPercent(symptom.percentage),
                )
            },
        )
        val flowDistribution = report.flowDistribution()
        if (flowDistribution.isNotEmpty()) {
            Text(
                text = stringResource(R.string.reports_flow_distribution),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            FrequencyTableCard(
                headers = Triple(
                    stringResource(R.string.reports_table_flow_level),
                    "",
                    stringResource(R.string.reports_table_days),
                ),
                rows = flowDistribution.map { entry ->
                    Triple(entry.label, "", entry.count.toString())
                },
            )
        }
    }
}

@Composable
private fun MoodPatternsPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_mood_patterns),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.reports_mood_frequency),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        FrequencyTableCard(
            headers = Triple(
                stringResource(R.string.reports_table_mood),
                stringResource(R.string.reports_table_days),
                stringResource(R.string.reports_table_percent_logged),
            ),
            rows = report.topMoods.take(8).map { mood ->
                Triple(mood.name, mood.count.toString(), contextPercent(mood.percentage))
            },
        )
        Surface(
            shape = RoundedCornerShape(SakhiRadius.lg),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        ) {
            Text(
                text = stringResource(R.string.reports_mood_note),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(SakhiSpacing.space4),
            )
        }
    }
}

@Composable
private fun InsightsPage(report: ReportData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.reports_page_insights),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.reports_insights_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            report.insights.take(4).forEach { insight ->
                InsightCard(insight)
            }
        }
        Surface(
            shape = RoundedCornerShape(SakhiRadius.lg),
            // iOS tints its doctor section with `DS.Colors.pdfDoctorTeal.opacity(0.05)`
            // (`SakhiReportPDFGenerator`). Android used `colorScheme.tertiary`, which this
            // theme never sets, so the doctor note sat on Material's default purple-brown.
            color = SakhiUIColors.PDF_DOCTOR_TEAL.toComposeColor().copy(alpha = 0.05f),
        ) {
            Column(
                modifier = Modifier.padding(SakhiSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = stringResource(R.string.reports_doctor_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.reports_doctor_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    note: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = SakhiSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarLegendItem(
            label = stringResource(R.string.reports_marker_period),
            color = MaterialTheme.colorScheme.primary,
        )
        CalendarLegendItem(
            label = stringResource(R.string.reports_marker_predicted),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        )
        CalendarLegendItem(
            label = stringResource(R.string.reports_marker_fertile_ovulation),
            // Must be the same colour as the ovulation cells this legend explains. It was
            // `colorScheme.tertiary` — a slot this theme never sets, so it rendered as
            // Material's default purple-brown while the days below it were teal. A legend
            // whose key does not match the thing it is keying is worse than no legend, and
            // this one ships inside the report a user hands to a doctor.
            color = SakhiColors
                .resolved(isSystemInDarkTheme())
                .forPhase(CyclePhase.OVULATION)
                .ring
                .toComposeColor(),
        )
    }
}

@Composable
private fun CalendarLegendItem(
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun CalendarMonthPreview(month: CalendarMonth) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            Text(
                text = month.title(),
                style = MaterialTheme.typography.titleMedium,
            )
            WeekdayHeader()
            month.weeks().forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.size(28.dp))
                        } else {
                            val dayMark = month.dayMark(date)
                            // This preview is what the user checks before generating the PDF,
                            // so it has to agree with the PDF. `ReportPdfExporter.drawDayCell`
                            // and iOS's `SakhiReportPDFGenerator.dayCell` already agree with
                            // each other: pink for a period day, TEAL/`PhaseColorManager
                            // .ovulation` for ovulation, a light teal for fertile, and
                            // **nothing at all** behind an ordinary day.
                            // The preview disagreed with both: it filled ordinary days with
                            // `surfaceVariant` (Material's lavender) and drew ovulation in
                            // `colorScheme.tertiary` — a slot this theme never sets, so it was
                            // Material's default purple-brown, not Sakhi's teal. Ovulation now
                            // comes from the same `SakhiColorSystem` phase entry the calendars
                            // use, which is the single owner of every phase hex.
                            val ovulationColor = SakhiColors
                                .resolved(isSystemInDarkTheme())
                                .forPhase(CyclePhase.OVULATION)
                                .ring
                                .toComposeColor()
                            val markerColor = when {
                                dayMark?.isPeriod == true -> MaterialTheme.colorScheme.primary
                                dayMark?.isPredictedPeriod == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                dayMark?.isOvulation == true -> ovulationColor
                                dayMark?.isFertile == true -> ovulationColor.copy(alpha = 0.18f)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(markerColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dayMark?.isPeriod == true || dayMark?.isOvulation == true) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyTableCard(
    headers: Triple<String, String, String>,
    rows: List<Triple<String, String, String>>,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headers.first,
                style = MaterialTheme.typography.labelSmall,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.weight(1f),
            )
            if (headers.second.isNotBlank()) {
                Text(
                    text = headers.second,
                    style = MaterialTheme.typography.labelSmall,
                    color = sakhiSecondaryLabel(),
                )
            }
            Text(
                text = headers.third,
                style = MaterialTheme.typography.labelSmall,
                color = sakhiSecondaryLabel(),
            )
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SakhiSpacing.space1),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.first,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (row.second.isNotBlank()) {
                    Text(
                        text = row.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = row.third,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun PreviewInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SakhiSpacing.space1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InsightCard(insight: ReportInsight) {
    // iOS (`ReportViewModel`) has only two branches:
    //   `insight.severity == .notice ? DS.Colors.activityOther : DS.Colors.categoryCycles`
    // The `.notice` branch is mapped exactly here. Android keeps its own three-way split for
    // INFO/ALERT (pink / error) rather than folding both into iOS's single `categoryCycles`
    // purple — that reads as a deliberate refinement, and collapsing it would lose the
    // alert distinction, so it is flagged in the status log rather than changed unilaterally.
    // What is fixed is NOTICE: it was `colorScheme.tertiary`, a slot this theme never sets,
    // so it rendered in Material's default purple-brown (#7D5260) instead of iOS's
    // `ACT_OTHER` (#EA8C26).
    val accent = when (insight.severity) {
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
        InsightSeverity.NOTICE -> SakhiUIColors.ACT_OTHER.toComposeColor()
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

@Composable
private fun FooterBar(
    buttonText: String,
    buttonEnabled: Boolean,
    note: String?,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakhiFooter(
        primaryLabel = buttonText,
        onPrimaryClick = onButtonClick,
        primaryEnabled = buttonEnabled,
        note = note?.takeIf { it.isNotBlank() },
        showSecondarySlot = false,
        modifier = modifier,
    )
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
                    color = sakhiSecondaryLabel(),
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
        color = sakhiSecondaryLabel(),
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
                // Weekday letters -- iOS `SakhiCalendarView.weekdayRow`
                // renders these in `DS.Colors.tertiaryLabel`, matching the
                // app's other two weekday headers.
                color = sakhiTertiaryLabel(),
            )
        }
    }
}

private fun fullDate(date: kotlinx.datetime.LocalDate): String {
    return "${date.dayOfMonth} ${java.time.Month.of(date.monthNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.year}"
}

private fun contextPercent(value: Double): String = "${value.toInt()}%"

private fun buildPreviewPages(
    report: ReportData,
    selectedSections: Set<ReportSection>,
): List<PreviewPage> = buildReportPages(report, selectedSections).map { page ->
    when (page) {
        ReportDocumentPage.Cover -> PreviewPage(R.string.reports_page_cover, PreviewPageType.Cover)
        ReportDocumentPage.CycleSummary -> PreviewPage(R.string.reports_page_cycle_summary, PreviewPageType.CycleSummary)
        ReportDocumentPage.PeriodCalendar -> PreviewPage(R.string.reports_page_period_calendar, PreviewPageType.PeriodCalendar)
        ReportDocumentPage.SymptomsFlow -> PreviewPage(R.string.reports_page_symptoms_flow, PreviewPageType.SymptomsFlow)
        ReportDocumentPage.MoodPatterns -> PreviewPage(R.string.reports_page_mood_patterns, PreviewPageType.MoodPatterns)
        ReportDocumentPage.Insights -> PreviewPage(R.string.reports_page_insights, PreviewPageType.Insights)
    }
}

private fun reportSectionIcon(section: ReportSection): ImageVector = when (section) {
    ReportSection.CycleOverview -> Icons.Outlined.Autorenew
    ReportSection.PeriodCalendar -> Icons.Outlined.OutlinedCalendarMonth
    ReportSection.Symptoms -> Icons.Filled.Healing
    ReportSection.MoodPatterns -> Icons.Filled.SentimentSatisfied
    ReportSection.Medications -> Icons.Filled.Medication
    ReportSection.Insights -> Icons.Filled.Lightbulb
}

/**
 * Bottom inset for the reports list so it can scroll clear of the floating
 * `FooterBar`. Covers the 52dp button, the footer's own vertical padding and the
 * navigation-bar inset.
 */
private val ReportsFooterClearance = 132.dp
