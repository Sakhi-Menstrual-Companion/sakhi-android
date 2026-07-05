package team.sakhi.android.feature.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import team.sakhi.report.CalendarMonth
import team.sakhi.report.ReportData

/**
 * Android-side PDF generator mirroring iOS's `SakhiReportPDFGenerator`: the page
 * content comes from KMM `ReportData`, while the actual PDF rendering is native.
 */
class ReportPdfExporter(
    private val context: Context,
) {

    fun export(
        report: ReportData,
        selectedSections: Set<ReportSection>,
    ): File {
        val pages = buildPages(report, selectedSections)
        val document = PdfDocument()

        pages.forEachIndexed { index, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val pdfPage = document.startPage(pageInfo)
            drawPage(
                canvas = pdfPage.canvas,
                report = report,
                page = page,
            )
            document.finishPage(pdfPage)
        }

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(reportsDir, "SakhiReport_${System.currentTimeMillis()}.pdf")

        FileOutputStream(outputFile).use { output ->
            document.writeTo(output)
        }
        document.close()
        return outputFile
    }

    fun buildShareUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun drawPage(
        canvas: Canvas,
        report: ReportData,
        page: ReportPdfPage,
    ) {
        canvas.drawColor(Color.WHITE)
        when (page.type) {
            ReportPdfPageType.Cover -> drawCoverPage(canvas, report)
            ReportPdfPageType.CycleSummary -> drawSummaryPage(canvas, report)
            ReportPdfPageType.PeriodCalendar -> drawCalendarPage(canvas, report)
            ReportPdfPageType.SymptomsFlow -> drawSymptomsPage(canvas, report)
            ReportPdfPageType.MoodPatterns -> drawMoodsPage(canvas, report)
            ReportPdfPageType.Medications -> drawMedicationsGapPage(canvas, report)
            ReportPdfPageType.Insights -> drawInsightsPage(canvas, report)
        }
    }

    private fun drawCoverPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawAccentBand(canvas)
        var y = 180f

        canvas.drawText("MENSTRUAL", MARGIN.toFloat(), y, titlePaint)
        y += 52f
        canvas.drawText("HEALTH REPORT", MARGIN.toFloat(), y, accentTitlePaint)
        y += 72f

        drawDivider(canvas, y)
        y += 42f

        y = drawInfoRow(canvas, "Prepared from", "Sakhi Health App", y)
        y = drawInfoRow(canvas, "Report period", "${formatShort(report.periodFrom)} - ${formatShort(report.periodTo)}", y)
        y = drawInfoRow(canvas, "Generated on", report.generatedAt, y)
        y = drawInfoRow(canvas, "Cycles analyzed", report.cyclesAnalyzed.toString(), y)

        val disclaimer = "This report contains personal health data. It is intended to support your own tracking and conversations with a healthcare provider. It is not a medical diagnosis."
        drawWrappedText(
            canvas = canvas,
            text = disclaimer,
            x = MARGIN.toFloat(),
            startY = PAGE_HEIGHT - 180f,
            maxWidth = PAGE_WIDTH - MARGIN * 2f,
            paint = footnotePaint,
            lineHeight = 18f,
        )
    }

    private fun drawSummaryPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Cycle Summary", report)
        var y = 160f
        y = drawStatRow(canvas, "Cycles analyzed", report.cyclesAnalyzed.toString(), y)
        y = drawStatRow(canvas, "Average cycle length", "${report.averageCycleLength.toInt()} days", y)
        if (report.cyclesAnalyzed > 0) {
            y = drawStatRow(canvas, "Shortest / longest", "${report.shortestCycleDays} / ${report.longestCycleDays} days", y)
        }
        y = drawStatRow(canvas, "Average period length", "${report.averagePeriodLength.toInt()} days", y)
        y = drawStatRow(canvas, "Regularity", "${(report.regularityScore * 100).toInt()}%", y)
        y = drawStatRow(canvas, "Prediction confidence", "${(report.predictionConfidence * 100).toInt()}%", y)
        drawFooter(canvas, "Cycle Summary", report)
    }

    private fun drawCalendarPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Period Calendar", report)
        var y = 150f
        report.calendarMonths.forEach { month ->
            if (y > PAGE_HEIGHT - 170f) return@forEach
            y = drawCalendarMonth(canvas, month, y) + 24f
        }
        drawFooter(canvas, "Period Calendar", report)
    }

    private fun drawSymptomsPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Symptoms & Flow", report)
        var y = 160f
        canvas.drawText("Top symptoms", MARGIN.toFloat(), y, sectionPaint)
        y += 24f
        report.topSymptoms.take(8).forEach { symptom ->
            y = drawStatRow(canvas, symptom.name, "${symptom.percentage.toInt()}%", y)
        }
        y += 16f
        canvas.drawText("Recent flow entries", MARGIN.toFloat(), y, sectionPaint)
        y += 24f
        report.flowTimeline.takeLast(8).forEach { flow ->
            y = drawStatRow(canvas, formatShort(flow.date), flow.intensity, y)
        }
        drawFooter(canvas, "Symptoms & Flow", report)
    }

    private fun drawMoodsPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Mood Patterns", report)
        var y = 160f
        report.topMoods.take(10).forEach { mood ->
            y = drawStatRow(canvas, mood.name, "${mood.percentage.toInt()}%", y)
        }
        drawFooter(canvas, "Mood Patterns", report)
    }

    private fun drawMedicationsGapPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Medications & Visits", report)
        drawWrappedText(
            canvas = canvas,
            text = "Android PDF export is ready, but this section still needs shared report fields for medications, doctor visits, and related notes before it can match the full iOS export.",
            x = MARGIN.toFloat(),
            startY = 180f,
            maxWidth = PAGE_WIDTH - MARGIN * 2f,
            paint = bodyPaint,
            lineHeight = 26f,
        )
        drawFooter(canvas, "Medications & Visits", report)
    }

    private fun drawInsightsPage(
        canvas: Canvas,
        report: ReportData,
    ) {
        drawStandardHeader(canvas, "Health Insights", report)
        var y = 160f
        report.insights.take(5).forEach { insight ->
            canvas.drawText(insight.title, MARGIN.toFloat(), y, sectionPaint)
            y += 18f
            y = drawWrappedText(
                canvas = canvas,
                text = insight.body,
                x = MARGIN.toFloat(),
                startY = y,
                maxWidth = PAGE_WIDTH - MARGIN * 2f,
                paint = bodyPaint,
                lineHeight = 22f,
            ) + 22f
        }
        drawFooter(canvas, "Health Insights", report)
    }

    private fun drawStandardHeader(
        canvas: Canvas,
        title: String,
        report: ReportData,
    ) {
        drawAccentBand(canvas)
        canvas.drawText(title.uppercase(), MARGIN.toFloat(), 64f, eyebrowPaint)
        canvas.drawText(title, MARGIN.toFloat(), 96f, headerPaint)
        canvas.drawText("${formatShort(report.periodFrom)} - ${formatShort(report.periodTo)}", PAGE_WIDTH - MARGIN.toFloat(), 96f, headerMetaPaint)
        drawDivider(canvas, 120f)
    }

    private fun drawFooter(
        canvas: Canvas,
        pageName: String,
        report: ReportData,
    ) {
        drawDivider(canvas, PAGE_HEIGHT - 44f)
        canvas.drawText(pageName, MARGIN.toFloat(), PAGE_HEIGHT - 20f, footerLeftPaint)
        canvas.drawText("sakhi health report  •  ${report.generatedAt}", PAGE_WIDTH - MARGIN.toFloat(), PAGE_HEIGHT - 20f, footerRightPaint)
    }

    private fun drawCalendarMonth(
        canvas: Canvas,
        month: CalendarMonth,
        startY: Float,
    ): Float {
        var y = startY
        val days = month.days.keys.sortedBy { it.toEpochDays() }
        val monthName = days.firstOrNull()?.month?.name?.lowercase()?.replaceFirstChar { it.titlecase() }
            ?: "Month ${month.month}"

        canvas.drawText("$monthName ${month.year}", MARGIN.toFloat(), y, sectionPaint)
        y += 22f

        val cellWidth = (PAGE_WIDTH - MARGIN * 2f) / 7f
        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, label ->
            canvas.drawText(label, MARGIN + cellWidth * index + 10f, y, smallLabelPaint)
        }
        y += 18f

        days.chunked(7).take(5).forEach { week ->
            week.forEachIndexed { index, date ->
                val left = MARGIN + cellWidth * index
                val top = y
                val markerKey = month.days[date]?.toString()?.lowercase().orEmpty()
                cellPaint.color = when {
                    "ovulation" in markerKey || "fertile" in markerKey -> TEAL
                    "predicted" in markerKey -> LIGHT_PINK
                    "pms" in markerKey -> MAUVE
                    "period" in markerKey -> PINK
                    else -> LIGHT_GREY
                }
                canvas.drawRoundRect(left, top, left + cellWidth - 8f, top + 26f, 13f, 13f, cellPaint)
                canvas.drawText(date.dayOfMonth.toString(), left + 10f, top + 18f, calendarDayPaint)
            }
            y += 34f
        }
        return y
    }

    private fun drawAccentBand(canvas: Canvas) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, accentFillPaint)
    }

    private fun drawDivider(
        canvas: Canvas,
        y: Float,
    ) {
        canvas.drawLine(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), y, dividerPaint)
    }

    private fun drawInfoRow(
        canvas: Canvas,
        label: String,
        value: String,
        startY: Float,
    ): Float {
        canvas.drawText(label, MARGIN.toFloat(), startY, labelPaint)
        canvas.drawText(value, MARGIN.toFloat() + 180f, startY, valuePaint)
        return startY + 32f
    }

    private fun drawStatRow(
        canvas: Canvas,
        label: String,
        value: String,
        startY: Float,
    ): Float {
        canvas.drawText(label, MARGIN.toFloat(), startY, labelPaint)
        canvas.drawText(value, PAGE_WIDTH - MARGIN.toFloat(), startY, valueRightPaint)
        drawDivider(canvas, startY + 12f)
        return startY + 28f
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
    ): Float {
        val words = text.split(" ")
        val builder = StringBuilder()
        var y = startY

        words.forEach { word ->
            val testLine = if (builder.isEmpty()) word else "${builder} $word"
            if (paint.measureText(testLine) > maxWidth && builder.isNotEmpty()) {
                canvas.drawText(builder.toString(), x, y, paint)
                builder.clear()
                builder.append(word)
                y += lineHeight
            } else {
                builder.clear()
                builder.append(testLine)
            }
        }

        if (builder.isNotEmpty()) {
            canvas.drawText(builder.toString(), x, y, paint)
        }
        return y
    }

    private fun buildPages(
        report: ReportData,
        selectedSections: Set<ReportSection>,
    ): List<ReportPdfPage> {
        val pages = mutableListOf(
            ReportPdfPage("Cover", ReportPdfPageType.Cover),
            ReportPdfPage("Cycle Summary", ReportPdfPageType.CycleSummary),
        )
        if (ReportSection.PeriodCalendar in selectedSections && report.calendarMonths.isNotEmpty()) {
            pages += ReportPdfPage("Period Calendar", ReportPdfPageType.PeriodCalendar)
        }
        if (ReportSection.Symptoms in selectedSections &&
            (report.topSymptoms.isNotEmpty() || report.flowTimeline.isNotEmpty())
        ) {
            pages += ReportPdfPage("Symptoms & Flow", ReportPdfPageType.SymptomsFlow)
        }
        if (ReportSection.MoodPatterns in selectedSections && report.topMoods.isNotEmpty()) {
            pages += ReportPdfPage("Mood Patterns", ReportPdfPageType.MoodPatterns)
        }
        if (ReportSection.Medications in selectedSections) {
            pages += ReportPdfPage("Medications & Visits", ReportPdfPageType.Medications)
        }
        if (ReportSection.Insights in selectedSections && report.insights.isNotEmpty()) {
            pages += ReportPdfPage("Health Insights", ReportPdfPageType.Insights)
        }
        return pages
    }

    private fun formatShort(date: kotlinx.datetime.LocalDate): String {
        val month = date.month.name.lowercase().replaceFirstChar { it.titlecase() }.take(3)
        return "${date.dayOfMonth} $month ${date.year}"
    }

    private data class ReportPdfPage(
        val title: String,
        val type: ReportPdfPageType,
    )

    private enum class ReportPdfPageType {
        Cover,
        CycleSummary,
        PeriodCalendar,
        SymptomsFlow,
        MoodPatterns,
        Medications,
        Insights,
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 44

        const val PINK = -0x399a4a
        const val LIGHT_PINK = -0xb13db
        const val LIGHT_GREY = -0x111112
        const val TEAL = -0xa19432
        const val MAUVE = -0x525b8

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val accentTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valueRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val footnotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        val footerLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.LEFT
        }
        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val accentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = LIGHT_GREY
        }
        val calendarDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
}
