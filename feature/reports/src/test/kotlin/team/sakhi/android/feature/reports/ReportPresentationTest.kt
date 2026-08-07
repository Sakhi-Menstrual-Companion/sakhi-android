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

    /**
     * DEFECT PINNED, NOT ENDORSED — and it is **shared with iOS**, so do not "fix" it here
     * alone or Android will diverge.
     *
     * `buildReportPages` seeds the list with `Cover` and `CycleSummary` *unconditionally* and
     * only then consults `selectedSections`. So switching "Cycle Overview" off in the UI does
     * not remove the cycle page from the generated PDF. That matters more than a normal dead
     * control: the report is a document users hand to a doctor, so a switch that looks like it
     * excludes a section and does not is a privacy-shaped failure.
     *
     * iOS does exactly the same thing — `SakhiReportPDFGenerator.generate` opens with
     * `var pages: [AnyView] = [ReportCoverPage, ReportCyclePage]` and gates only the same four
     * later sections. When this is fixed it has to be fixed on both platforms together, and
     * this test should then flip to asserting the page is absent.
     */
    @Test
    fun `switching Cycle Overview off does NOT remove the cycle page - matches iOS, pending a both-platform fix`() {
        val withoutCycleOverview = ReportSection.entries.toSet() - ReportSection.CycleOverview

        val pages = buildReportPages(sampleReport(), selectedSections = withoutCycleOverview)

        assertTrue(
            "CycleSummary is seeded unconditionally, so deselecting Cycle Overview has no effect",
            ReportDocumentPage.CycleSummary in pages,
        )
        assertTrue("Cover is likewise unconditional", ReportDocumentPage.Cover in pages)
    }

    /**
     * The other half of the same finding: `ReportSection.Medications` renders a toggle in
     * `ReportsScreen` but `buildReportPages` builds no page for it, so turning it on adds
     * nothing. iOS gates `medications` nowhere in `Features/Reports/` either.
     */
    @Test
    fun `selecting only Medications produces no section pages beyond the unconditional two`() {
        val pages = buildReportPages(sampleReport(), selectedSections = setOf(ReportSection.Medications))

        assertEquals(listOf(ReportDocumentPage.Cover, ReportDocumentPage.CycleSummary), pages)
    }

    /**
     * The controls that *do* work, so the two tests above read as a specific defect rather
     * than "section toggles are broken". Deselecting each of these really does drop its page.
     */
    @Test
    fun `the four gated sections each drop their page when deselected`() {
        val report = sampleReport(
            calendarMonths = listOf(
                CalendarMonth(
                    year = 2026,
                    month = 7,
                    days = mapOf(LocalDate(2026, 7, 1) to CalendarMarker.DayMark(LocalDate(2026, 7, 1))),
                ),
            ),
        )
        val all = ReportSection.entries.toSet()

        assertTrue(ReportDocumentPage.PeriodCalendar in buildReportPages(report, all))
        assertFalse(ReportDocumentPage.PeriodCalendar in buildReportPages(report, all - ReportSection.PeriodCalendar))

        assertTrue(ReportDocumentPage.SymptomsFlow in buildReportPages(report, all))
        assertFalse(ReportDocumentPage.SymptomsFlow in buildReportPages(report, all - ReportSection.Symptoms))

        assertTrue(ReportDocumentPage.MoodPatterns in buildReportPages(report, all))
        assertFalse(ReportDocumentPage.MoodPatterns in buildReportPages(report, all - ReportSection.MoodPatterns))

        assertTrue(ReportDocumentPage.Insights in buildReportPages(report, all))
        assertFalse(ReportDocumentPage.Insights in buildReportPages(report, all - ReportSection.Insights))
    }

    /**
     * Matches iOS exactly: `if data.config.sections.contains(.periodCalendar)` has no
     * emptiness check, so a report with no calendar data still gets a (blank) calendar page
     * when the section is selected. Previously Android added an `isNotEmpty()` guard iOS
     * does not have; removed per the standing rule that Android replicates iOS rather than
     * quietly improving on it.
     */
    @Test
    fun `an empty calendar range still produces a Period Calendar page, matching iOS`() {
        val pages = buildReportPages(
            sampleReport(calendarMonths = emptyList()),
            selectedSections = ReportSection.entries.toSet(),
        )

        assertTrue(ReportDocumentPage.PeriodCalendar in pages)
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
