package team.sakhi.android.feature.reports

import android.content.Context
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import team.sakhi.report.ReportStrings

/**
 * Feeds Android's string resources and locale formatting into the shared
 * `ReportHtml` generator.
 *
 * The shared module deliberately holds no copy of its own, so every label the
 * report shows is resolved here (and by the equivalent bridge on iOS). That keeps
 * the layout shared without forcing the two platforms onto one string catalogue,
 * and keeps the report translatable through the normal Android resource pipeline.
 */
class AndroidReportStrings(private val context: Context) {

    fun build(document: ReportDocument): ReportStrings {
        val locale = Locale.getDefault()
        return ReportStrings(
            subjectName = document.subjectName,
            coverTitleTop = context.getString(R.string.reports_cover_title_menstrual),
            coverTitleBottom = context.getString(R.string.reports_cover_title_health_report),
            preparedFor = context.getString(R.string.reports_prepared_for),
            dateRange = context.getString(R.string.reports_date_range),
            generatedOn = context.getString(R.string.reports_generated_on),
            cyclesAnalysed = context.getString(R.string.reports_cycles_analysed),
            disclaimer = context.getString(R.string.reports_disclaimer),
            cycleSummary = context.getString(R.string.reports_cycle_summary),
            keyStatistics = context.getString(R.string.reports_key_statistics),
            averageCycleLength = context.getString(R.string.reports_average_cycle_length),
            shortestCycle = context.getString(R.string.reports_shortest_cycle),
            longestCycle = context.getString(R.string.reports_longest_cycle),
            averagePeriodLength = context.getString(R.string.reports_average_period_length),
            regularity = context.getString(R.string.reports_regularity),
            loggedActivity = context.getString(R.string.reports_logged_activity),
            painkillerDays = context.getString(R.string.reports_painkiller_days),
            doctorVisitDays = context.getString(R.string.reports_doctor_visit_days),
            daysWithNotes = context.getString(R.string.reports_days_with_notes),
            // `reports_days_value` is `%1${'$'}d` (Int); the decimal variant is the one that
            // takes a Double. Averages here are fractional, so use that one.
            days = { value -> context.getString(R.string.reports_days_value_decimal, value) },
            percent = { score ->
                context.getString(R.string.reports_percent_value, (score * 100).toInt())
            },
            periodCalendar = context.getString(R.string.reports_period_calendar),
            calendarSubtitle = context.getString(R.string.reports_calendar_subtitle),
            calendarLegend = context.getString(R.string.reports_calendar_legend),
            symptomsFlow = context.getString(R.string.reports_symptoms_flow),
            symptomFrequency = context.getString(R.string.reports_symptom_frequency),
            flowDistribution = context.getString(R.string.reports_flow_distribution),
            moodPatterns = context.getString(R.string.reports_mood_patterns),
            moodFrequency = context.getString(R.string.reports_mood_frequency),
            insights = context.getString(R.string.reports_insights),
            insightsSubtitle = context.getString(R.string.reports_insights_subtitle),
            // Sunday-first, matching the shared grid's column order.
            weekdayInitials = listOf(
                R.string.reports_weekday_sun, R.string.reports_weekday_mon,
                R.string.reports_weekday_tue, R.string.reports_weekday_wed,
                R.string.reports_weekday_thu, R.string.reports_weekday_fri,
                R.string.reports_weekday_sat,
            ).map(context::getString),
            monthName = { year, month ->
                "${Month.of(month).getDisplayName(TextStyle.FULL, locale)} $year"
            },
            occurrences = { count ->
                context.resources.getQuantityString(R.plurals.reports_occurrences, count, count)
            },
        )
    }
}
