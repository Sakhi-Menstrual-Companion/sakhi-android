package team.sakhi.android.feature.reports

import java.time.DayOfWeek
import java.time.LocalDate as JavaLocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDate
import team.sakhi.cycle.CalendarMarker
import team.sakhi.report.CalendarMonth
import team.sakhi.report.ReportData

data class ReportDocument(
    val report: ReportData,
    val subjectName: String,
    val generatedOn: LocalDate,
    val nextPredictedPeriod: LocalDate?,
    val trackedCyclesCount: Int,
)

internal enum class ReportDocumentPage {
    Cover,
    CycleSummary,
    PeriodCalendar,
    SymptomsFlow,
    MoodPatterns,
    Insights,
}

internal data class ReportFlowDistribution(
    val label: String,
    val count: Int,
)

internal fun buildReportPages(
    report: ReportData,
    selectedSections: Set<ReportSection>,
): List<ReportDocumentPage> {
    val pages = mutableListOf(
        ReportDocumentPage.Cover,
        ReportDocumentPage.CycleSummary,
    )

    if (ReportSection.PeriodCalendar in selectedSections && report.calendarMonths.isNotEmpty()) {
        pages += ReportDocumentPage.PeriodCalendar
    }
    if (ReportSection.Symptoms in selectedSections && report.topSymptoms.isNotEmpty()) {
        pages += ReportDocumentPage.SymptomsFlow
    }
    if (ReportSection.MoodPatterns in selectedSections && report.topMoods.isNotEmpty()) {
        pages += ReportDocumentPage.MoodPatterns
    }
    if (ReportSection.Insights in selectedSections && report.insights.isNotEmpty()) {
        pages += ReportDocumentPage.Insights
    }

    return pages
}

internal fun ReportData.totalPeriodDays(): Int =
    calendarMonths.sumOf { month -> month.days.values.count { it.isPeriod } }

internal fun ReportData.regularityPercent(): Int = (regularityScore * 100).toInt()

internal fun ReportData.regularityLabel(): String {
    val percent = regularityPercent()
    return when {
        percent >= 85 -> "Very Regular"
        percent >= 70 -> "Regular"
        percent >= 55 -> "Somewhat Irregular"
        else -> "Irregular"
    }
}

internal fun ReportData.flowDistribution(): List<ReportFlowDistribution> =
    flowTimeline
        .groupingBy { it.intensity.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { ReportFlowDistribution(label = it.key, count = it.value) }

internal fun CalendarMonth.weeks(): List<List<LocalDate?>> {
    val firstDay = JavaLocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val leadingBlanks = firstDay.dayOfWeek.sundayIndex()
    val flatDays = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) {
            add(LocalDate(year, month, day))
        }
    }.toMutableList()
    while (flatDays.size % 7 != 0) {
        flatDays += null
    }
    return flatDays.chunked(7)
}

internal fun CalendarMonth.title(locale: Locale = Locale.getDefault()): String =
    JavaLocalDate.of(year, month, 1).month.getDisplayName(TextStyle.FULL, locale)
        .let { monthName -> "$monthName $year" }

internal fun CalendarMonth.dayMark(date: LocalDate): CalendarMarker.DayMark? = days[date]

private fun DayOfWeek.sundayIndex(): Int = when (this) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
}
