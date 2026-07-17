package team.sakhi.android.feature.reports

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sakhi.cycle.CalendarMarker
import team.sakhi.report.CalendarMonth
import team.sakhi.report.FlowDataPoint
import team.sakhi.report.InsightSeverity
import team.sakhi.report.MoodFrequency
import team.sakhi.report.ReportData
import team.sakhi.report.ReportInsight
import team.sakhi.report.SymptomFrequency

class ReportPresentationTest {

    @Test
    fun `symptoms page follows iOS rule and stays hidden when there are no top symptoms`() {
        val report = sampleReport(
            topSymptoms = emptyList(),
            flowTimeline = listOf(FlowDataPoint(LocalDate(2026, 7, 1), "medium")),
        )

        val pages = buildReportPages(report, selectedSections = ReportSection.entries.toSet())

        assertFalse(ReportDocumentPage.SymptomsFlow in pages)
    }

    @Test
    fun `calendar weeks preserve leading blanks for real month layout`() {
        val month = CalendarMonth(
            year = 2026,
            month = 5,
            days = mapOf(
                LocalDate(2026, 5, 1) to CalendarMarker.DayMark(LocalDate(2026, 5, 1)),
                LocalDate(2026, 5, 31) to CalendarMarker.DayMark(LocalDate(2026, 5, 31)),
            ),
        )

        val weeks = month.weeks()

        assertEquals("May 1 2026 is a Friday in a Sunday-start grid", 5, weeks.first().count { it == null })
        assertEquals(LocalDate(2026, 5, 1), weeks.first().first { it != null })
        assertEquals(LocalDate(2026, 5, 2), weeks.first().last())
        assertEquals(LocalDate(2026, 5, 31), weeks.last().first())
    }

    @Test
    fun `flow distribution groups by display label and sorts by count`() {
        val report = sampleReport(
            flowTimeline = listOf(
                FlowDataPoint(LocalDate(2026, 7, 1), "medium"),
                FlowDataPoint(LocalDate(2026, 7, 2), "heavy"),
                FlowDataPoint(LocalDate(2026, 7, 3), "medium"),
            ),
        )

        val distribution = report.flowDistribution()

        assertEquals("Medium", distribution.first().label)
        assertEquals(2, distribution.first().count)
        assertEquals("Heavy", distribution.last().label)
    }

    @Test
    fun `total period days counts marked period cells across months`() {
        val month = CalendarMonth(
            year = 2026,
            month = 7,
            days = mapOf(
                LocalDate(2026, 7, 1) to CalendarMarker.DayMark(LocalDate(2026, 7, 1), isPeriod = true),
                LocalDate(2026, 7, 2) to CalendarMarker.DayMark(LocalDate(2026, 7, 2), isPeriod = true),
                LocalDate(2026, 7, 3) to CalendarMarker.DayMark(LocalDate(2026, 7, 3), isPeriod = false),
            ),
        )

        assertEquals(2, sampleReport(calendarMonths = listOf(month)).totalPeriodDays())
    }

    private fun sampleReport(
        topSymptoms: List<SymptomFrequency> = listOf(SymptomFrequency("cramps", 2, 40.0)),
        topMoods: List<MoodFrequency> = listOf(MoodFrequency("happy", 2, 50.0)),
        flowTimeline: List<FlowDataPoint> = emptyList(),
        insights: List<ReportInsight> = listOf(ReportInsight("Insight", "Body", InsightSeverity.INFO)),
        calendarMonths: List<CalendarMonth> = emptyList(),
    ) = ReportData(
        userId = "u1",
        generatedAt = "15 Jul 2026",
        periodFrom = LocalDate(2026, 7, 1),
        periodTo = LocalDate(2026, 7, 31),
        cyclesAnalyzed = 3,
        averageCycleLength = 28.0,
        shortestCycleDays = 27,
        longestCycleDays = 29,
        averagePeriodLength = 5.0,
        regularityScore = 0.9,
        predictionConfidence = 0.7,
        topSymptoms = topSymptoms,
        topMoods = topMoods,
        phaseCorrelations = emptyMap(),
        flowTimeline = flowTimeline,
        painkillerDays = 1,
        doctorVisitDays = 0,
        daysWithNotes = 1,
        insights = insights,
        calendarMonths = calendarMonths,
    )
}
