package team.sakhi.android.feature.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import team.sakhi.report.CalendarMonth

/**
 * Android-side PDF generator mirroring iOS's `SakhiReportPDFGenerator`: the page
 * content comes from shared report data plus a small amount of Android-owned
 * document metadata, while the actual PDF rendering is native.
 */
class ReportPdfExporter(
    private val context: Context,
) {
    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(context.getString(R.string.reports_pdf_short_date_format), Locale.getDefault())
    private val fullDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(context.getString(R.string.reports_pdf_full_date_format), Locale.getDefault())

    fun export(
        document: ReportDocument,
        selectedSections: Set<ReportSection>,
    ): File {
        val pages = buildReportPages(document.report, selectedSections)
        val pdfDocument = PdfDocument()

        pages.forEachIndexed { index, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            drawPage(pdfPage.canvas, document, page)
            pdfDocument.finishPage(pdfPage)
        }

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(reportsDir, "SakhiReport_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { output -> pdfDocument.writeTo(output) }
        pdfDocument.close()
        return outputFile
    }

    fun buildShareUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun drawPage(
        canvas: Canvas,
        document: ReportDocument,
        page: ReportDocumentPage,
    ) {
        canvas.drawColor(Color.WHITE)
        when (page) {
            ReportDocumentPage.Cover -> drawCoverPage(canvas, document)
            ReportDocumentPage.CycleSummary -> drawSummaryPage(canvas, document)
            ReportDocumentPage.PeriodCalendar -> drawCalendarPage(canvas, document)
            ReportDocumentPage.SymptomsFlow -> drawSymptomsPage(canvas, document)
            ReportDocumentPage.MoodPatterns -> drawMoodsPage(canvas, document)
            ReportDocumentPage.Insights -> drawInsightsPage(canvas, document)
        }
    }

    private fun drawCoverPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        drawAccentBand(canvas)
        var y = 214f

        canvas.drawText(context.getString(R.string.reports_cover_title_menstrual), MARGIN.toFloat(), y, titlePaint)
        y += 52f
        canvas.drawText(context.getString(R.string.reports_cover_title_health_report), MARGIN.toFloat(), y, accentTitlePaint)
        y += 64f

        drawDivider(canvas, y)
        y += 40f

        y = drawInfoRow(canvas, context.getString(R.string.reports_prepared_for), document.subjectName, y)
        y = drawInfoRow(
            canvas,
            context.getString(R.string.reports_report_period),
            context.getString(
                R.string.reports_date_range_span,
                formatShort(document.report.periodFrom),
                formatShort(document.report.periodTo),
            ),
            y,
        )
        y = drawInfoRow(
            canvas,
            context.getString(R.string.reports_generated_on),
            formatFull(document.generatedOn),
            y,
        )
        drawInfoRow(
            canvas,
            context.getString(R.string.reports_data_source),
            context.getString(R.string.reports_source_app_name),
            y,
        )

        drawWrappedText(
            canvas = canvas,
            text = context.getString(R.string.reports_pdf_disclaimer),
            x = MARGIN.toFloat(),
            startY = PAGE_HEIGHT - 126f,
            maxWidth = PAGE_WIDTH - MARGIN * 2f,
            paint = footnotePaint,
            lineHeight = 16f,
        )

        canvas.drawText(context.getString(R.string.reports_brand_wordmark), MARGIN.toFloat(), PAGE_HEIGHT - 34f, brandPaint)
        canvas.drawText(
            context.getString(R.string.reports_confidential),
            PAGE_WIDTH - MARGIN.toFloat(),
            PAGE_HEIGHT - 34f,
            footerRightPaint,
        )
        canvas.drawRect(0f, PAGE_HEIGHT - 4f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), accentFillPaint)
    }

    private fun drawSummaryPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        val report = document.report
        drawStandardHeader(canvas, context.getString(R.string.reports_page_cycle_summary), document)
        var y = 156f

        canvas.drawText(context.getString(R.string.reports_key_statistics), MARGIN.toFloat(), y, eyebrowSectionPaint)
        y += 18f
        y = drawTableRow(
            canvas,
            label = context.getString(R.string.reports_average_cycle_length),
            value = context.getString(R.string.reports_days_value, report.averageCycleLength.toInt()),
            startY = y,
            valuePaint = pinkValuePaint,
            note = if (report.cyclesAnalyzed > 0) {
                context.getString(
                    R.string.reports_range_value,
                    report.shortestCycleDays,
                    report.longestCycleDays,
                )
            } else {
                null
            },
        )
        y = drawTableRow(
            canvas,
            label = context.getString(R.string.reports_average_period_length),
            value = context.getString(R.string.reports_days_value_decimal, report.averagePeriodLength),
            startY = y,
            valuePaint = pinkValuePaint,
        )
        y = drawTableRow(
            canvas,
            label = context.getString(R.string.reports_cycles_tracked),
            value = document.trackedCyclesCount.toString(),
            startY = y,
            note = context.getString(R.string.reports_cycles_tracked_note),
        )
        y = drawTableRow(
            canvas,
            label = context.getString(R.string.reports_regularity),
            value = context.getString(
                R.string.reports_regularity_value,
                report.regularityPercent(),
                report.regularityLabel(),
            ),
            startY = y,
            valuePaint = regularityPaint(report.regularityPercent()),
        )
        y = drawTableRow(
            canvas,
            label = context.getString(R.string.reports_period_days_logged),
            value = context.getString(R.string.reports_days_value, report.totalPeriodDays()),
            startY = y,
        )

        document.nextPredictedPeriod?.let { predictedDate ->
            y += 30f
            canvas.drawText(context.getString(R.string.reports_next_period_prediction), MARGIN.toFloat(), y, eyebrowSectionPaint)
            y += 16f
            y = drawCalloutBlock(
                canvas = canvas,
                top = y,
                accentColor = PINK,
                fillColor = VERY_LIGHT_PINK,
                strokeColor = LIGHT_PINK_BORDER,
                title = context.getString(R.string.reports_expected_start_date),
                headline = formatFull(predictedDate),
                body = context.getString(R.string.reports_expected_start_note),
            )
        }

        y += 24f
        canvas.drawText(context.getString(R.string.reports_logged_activity), MARGIN.toFloat(), y, eyebrowSectionPaint)
        y += 18f
        y = drawTableRow(
            canvas,
            context.getString(R.string.reports_total_symptoms_logged),
            context.getString(R.string.reports_entries_value, report.topSymptoms.sumOf { it.count }),
            y,
        )
        y = drawTableRow(
            canvas,
            context.getString(R.string.reports_total_mood_entries),
            context.getString(R.string.reports_entries_value, report.topMoods.sumOf { it.count }),
            y,
        )
        y = drawTableRow(
            canvas,
            context.getString(R.string.reports_days_with_notes),
            context.getString(R.string.reports_days_value, report.daysWithNotes),
            y,
        )
        y = drawTableRow(
            canvas,
            context.getString(R.string.reports_medication_days),
            context.getString(R.string.reports_days_value, report.painkillerDays),
            y,
        )
        drawTableRow(
            canvas,
            context.getString(R.string.reports_doctor_visits),
            context.getString(R.string.reports_count_value, report.doctorVisitDays),
            y,
        )

        drawFooter(canvas, context.getString(R.string.reports_page_cycle_summary), document)
    }

    private fun drawCalendarPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        drawStandardHeader(canvas, context.getString(R.string.reports_page_period_calendar), document)
        var y = 150f

        drawLegendDot(canvas, x = MARGIN.toFloat(), y = y, label = context.getString(R.string.reports_marker_period), fill = PINK)
        drawLegendDot(
            canvas,
            x = MARGIN + 122f,
            y = y,
            label = context.getString(R.string.reports_marker_predicted),
            stroke = LIGHT_PINK_BORDER,
            dashed = true,
        )
        drawLegendDot(canvas, x = MARGIN + 286f, y = y, label = context.getString(R.string.reports_marker_fertile_ovulation), fill = TEAL)
        y += 24f

        document.report.calendarMonths.take(3).forEach { month ->
            y = drawCalendarMonth(canvas, month, y) + 14f
        }

        drawFooter(canvas, context.getString(R.string.reports_page_period_calendar), document)
    }

    private fun drawSymptomsPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        val report = document.report
        drawStandardHeader(canvas, context.getString(R.string.reports_page_symptoms_flow), document)
        var y = 156f

        canvas.drawText(context.getString(R.string.reports_symptom_frequency), MARGIN.toFloat(), y, eyebrowSectionPaint)
        y += 20f
        y = drawFrequencyHeader(
            canvas,
            y,
            left = context.getString(R.string.reports_table_symptom),
            middle = context.getString(R.string.reports_table_days),
            right = context.getString(R.string.reports_table_percent_tracked),
        )
        report.topSymptoms.take(8).forEach { symptom ->
            y = drawFrequencyRow(
                canvas = canvas,
                name = symptom.name,
                count = symptom.count.toString(),
                value = context.getString(R.string.reports_percent_value, symptom.percentage.toInt()),
                startY = y,
            )
        }

        val flowDistribution = report.flowDistribution()
        if (flowDistribution.isNotEmpty()) {
            y += 24f
            canvas.drawText(context.getString(R.string.reports_flow_distribution), MARGIN.toFloat(), y, eyebrowSectionPaint)
            y += 20f
            y = drawFrequencyHeader(
                canvas,
                y,
                left = context.getString(R.string.reports_table_flow_level),
                middle = "",
                right = context.getString(R.string.reports_table_days),
            )
            flowDistribution.forEach { entry ->
                y = drawFrequencyRow(
                    canvas = canvas,
                    name = entry.label,
                    count = "",
                    value = entry.count.toString(),
                    startY = y,
                )
            }
        }

        drawFooter(canvas, context.getString(R.string.reports_page_symptoms_flow), document)
    }

    private fun drawMoodsPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        val report = document.report
        drawStandardHeader(canvas, context.getString(R.string.reports_page_mood_patterns), document)
        var y = 156f

        canvas.drawText(context.getString(R.string.reports_mood_frequency), MARGIN.toFloat(), y, eyebrowSectionPaint)
        y += 20f
        y = drawFrequencyHeader(
            canvas,
            y,
            left = context.getString(R.string.reports_table_mood),
            middle = context.getString(R.string.reports_table_days),
            right = context.getString(R.string.reports_table_percent_logged),
        )
        report.topMoods.take(8).forEach { mood ->
            y = drawFrequencyRow(
                canvas = canvas,
                name = mood.name,
                count = mood.count.toString(),
                value = context.getString(R.string.reports_percent_value, mood.percentage.toInt()),
                startY = y,
            )
        }

        y += 28f
        drawInsetNote(
            canvas = canvas,
            top = y,
            accentColor = PINK,
            text = context.getString(R.string.reports_mood_note),
        )

        drawFooter(canvas, context.getString(R.string.reports_page_mood_patterns), document)
    }

    private fun drawInsightsPage(
        canvas: Canvas,
        document: ReportDocument,
    ) {
        val report = document.report
        drawStandardHeader(canvas, context.getString(R.string.reports_page_insights), document)
        var y = 146f

        y = drawWrappedText(
            canvas = canvas,
            text = context.getString(R.string.reports_insights_disclaimer),
            x = MARGIN.toFloat(),
            startY = y,
            maxWidth = PAGE_WIDTH - MARGIN * 2f,
            paint = footnotePaint,
            lineHeight = 14f,
        ) + 10f
        drawDivider(canvas, y)
        y += 16f

        report.insights.take(4).forEachIndexed { index, insight ->
            canvas.drawRect(MARGIN.toFloat(), y - 2f, MARGIN + 2f, y + 42f, insightBarPaint)
            canvas.drawText(insight.title, MARGIN + 16f, y + 10f, valuePaint)
            y = drawWrappedText(
                canvas = canvas,
                text = insight.body,
                x = MARGIN + 16f,
                startY = y + 28f,
                maxWidth = PAGE_WIDTH - MARGIN * 2f - 16f,
                paint = bodyMutedPaint,
                lineHeight = 14f,
                maxLines = 3,
            ) + 18f
            if (index < minOf(3, report.insights.size - 1)) {
                drawDivider(canvas, y)
                y += 14f
            }
        }

        drawCalloutBlock(
            canvas = canvas,
            top = PAGE_HEIGHT - 122f,
            accentColor = DOCTOR_TEAL,
            fillColor = LIGHT_TEAL,
            strokeColor = LIGHT_TEAL_BORDER,
            title = context.getString(R.string.reports_doctor_title),
            headline = context.getString(R.string.reports_doctor_headline),
            body = context.getString(R.string.reports_doctor_note),
            compact = true,
        )

        drawFooter(canvas, context.getString(R.string.reports_page_insights), document)
    }

    private fun drawStandardHeader(
        canvas: Canvas,
        title: String,
        document: ReportDocument,
    ) {
        drawAccentBand(canvas)
        canvas.drawText(title.uppercase(Locale.getDefault()), MARGIN.toFloat(), 66f, eyebrowPaint)
        canvas.drawText(title, MARGIN.toFloat(), 96f, headerPaint)
        canvas.drawText(
            context.getString(
                R.string.reports_date_range_span,
                formatShort(document.report.periodFrom),
                formatShort(document.report.periodTo),
            ),
            PAGE_WIDTH - MARGIN.toFloat(),
            96f,
            headerMetaPaint,
        )
        drawDivider(canvas, 120f)
    }

    private fun drawFooter(
        canvas: Canvas,
        pageName: String,
        document: ReportDocument,
    ) {
        drawDivider(canvas, PAGE_HEIGHT - 44f)
        canvas.drawText(pageName, MARGIN.toFloat(), PAGE_HEIGHT - 20f, footerLeftPaint)
        canvas.drawText(
            context.getString(R.string.reports_footer_generated_at, formatShort(document.generatedOn)),
            PAGE_WIDTH - MARGIN.toFloat(),
            PAGE_HEIGHT - 20f,
            footerRightPaint,
        )
    }

    private fun drawCalendarMonth(
        canvas: Canvas,
        month: CalendarMonth,
        top: Float,
    ): Float {
        val cardTop = top
        var y = cardTop + 20f
        val cardRect = RectF(MARGIN.toFloat(), cardTop, PAGE_WIDTH - MARGIN.toFloat(), cardTop + 176f)
        canvas.drawRoundRect(cardRect, 8f, 8f, monthCardPaint)
        canvas.drawRoundRect(cardRect, 8f, 8f, monthCardStrokePaint)

        canvas.drawText(month.title(), MARGIN + 12f, y, valuePaint)
        y += 18f

        val cellWidth = (PAGE_WIDTH - MARGIN * 2f - 20f) / 7f
        weekdayLabels.forEachIndexed { index, label ->
            canvas.drawText(label, MARGIN + 12f + cellWidth * index + 3f, y, smallLabelPaint)
        }
        y += 14f
        canvas.drawLine(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y, dividerPaint)
        y += 8f

        month.weeks().forEach { week ->
            week.forEachIndexed { index, date ->
                val left = MARGIN + 12f + cellWidth * index
                if (date != null) {
                    drawCalendarCell(
                        canvas = canvas,
                        left = left,
                        top = y,
                        width = cellWidth - 2f,
                        date = date,
                        month = month,
                    )
                }
            }
            y += 26f
        }

        return cardRect.bottom
    }

    private fun drawCalendarCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        date: kotlinx.datetime.LocalDate,
        month: CalendarMonth,
    ) {
        val mark = month.dayMark(date)
        val rect = RectF(left, top, left + width, top + 20f)
        when {
            mark?.isPeriod == true -> {
                fillRectPaint.color = PINK
                canvas.drawRoundRect(rect, 5f, 5f, fillRectPaint)
            }
            mark?.isPredictedPeriod == true -> {
                dashedStrokePaint.color = LIGHT_PINK_BORDER
                canvas.drawRoundRect(rect, 5f, 5f, dashedStrokePaint)
            }
            mark?.isOvulation == true -> {
                fillRectPaint.color = TEAL
                canvas.drawRoundRect(rect, 5f, 5f, fillRectPaint)
            }
            mark?.isFertile == true -> {
                fillRectPaint.color = LIGHT_TEAL
                canvas.drawRoundRect(rect, 5f, 5f, fillRectPaint)
            }
        }

        val textPaint = when {
            mark?.isPeriod == true || mark?.isOvulation == true -> inverseDayPaint
            else -> calendarDayPaint
        }
        canvas.drawText(date.dayOfMonth.toString(), left + 7f, top + 14f, textPaint)
    }

    private fun drawLegendDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        fill: Int? = null,
        stroke: Int? = null,
        dashed: Boolean = false,
    ) {
        val radius = 4.5f
        fill?.let {
            fillRectPaint.color = it
            canvas.drawCircle(x + radius, y, radius, fillRectPaint)
        }
        stroke?.let {
            dashedStrokePaint.color = it
            dashedStrokePaint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(3f, 3f), 0f) else null
            canvas.drawCircle(x + radius, y, radius, dashedStrokePaint)
            dashedStrokePaint.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        canvas.drawText(label, x + 14f, y + 3f, footnotePaint)
    }

    private fun drawAccentBand(canvas: Canvas) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, accentFillPaint)
    }

    private fun drawDivider(canvas: Canvas, y: Float) {
        canvas.drawLine(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), y, dividerPaint)
    }

    private fun drawInfoRow(
        canvas: Canvas,
        label: String,
        value: String,
        startY: Float,
    ): Float {
        canvas.drawText(label, MARGIN.toFloat(), startY, labelPaint)
        canvas.drawText(value, MARGIN + 120f, startY, valuePaint)
        return startY + 30f
    }

    private fun drawTableRow(
        canvas: Canvas,
        label: String,
        value: String,
        startY: Float,
        valuePaint: Paint = Companion.valuePaint,
        note: String? = null,
    ): Float {
        canvas.drawText(label, MARGIN.toFloat(), startY, labelPaint)
        canvas.drawText(value, MARGIN + 190f, startY, valuePaint)
        var nextY = startY + 18f
        if (!note.isNullOrBlank()) {
            canvas.drawText(note, MARGIN + 190f, nextY, footnotePaint)
            nextY += 12f
        }
        drawDivider(canvas, nextY + 2f)
        return nextY + 16f
    }

    private fun drawFrequencyHeader(
        canvas: Canvas,
        startY: Float,
        left: String,
        middle: String,
        right: String,
    ): Float {
        canvas.drawText(left, MARGIN.toFloat(), startY, tinyHeaderPaint)
        if (middle.isNotBlank()) {
            canvas.drawText(middle, PAGE_WIDTH - MARGIN - 118f, startY, tinyHeaderRightPaint)
        }
        canvas.drawText(right, PAGE_WIDTH - MARGIN.toFloat(), startY, tinyHeaderRightPaint)
        drawDivider(canvas, startY + 8f)
        return startY + 24f
    }

    private fun drawFrequencyRow(
        canvas: Canvas,
        name: String,
        count: String,
        value: String,
        startY: Float,
    ): Float {
        canvas.drawText(name, MARGIN.toFloat(), startY, valuePlainPaint)
        if (count.isNotBlank()) {
            canvas.drawText(count, PAGE_WIDTH - MARGIN - 118f, startY, pinkValueRightPaint)
        }
        canvas.drawText(value, PAGE_WIDTH - MARGIN.toFloat(), startY, valueRightMutedPaint)
        drawDivider(canvas, startY + 8f)
        return startY + 24f
    }

    private fun drawCalloutBlock(
        canvas: Canvas,
        top: Float,
        accentColor: Int,
        fillColor: Int,
        strokeColor: Int,
        title: String,
        headline: String,
        body: String,
        compact: Boolean = false,
    ): Float {
        val height = if (compact) 58f else 70f
        val rect = RectF(MARGIN.toFloat(), top, PAGE_WIDTH - MARGIN.toFloat(), top + height)
        fillRectPaint.color = fillColor
        canvas.drawRoundRect(rect, 6f, 6f, fillRectPaint)
        outlineRectPaint.color = strokeColor
        canvas.drawRoundRect(rect, 6f, 6f, outlineRectPaint)
        fillRectPaint.color = accentColor
        canvas.drawRect(MARGIN.toFloat() + 10f, top + 10f, MARGIN + 13f, top + height - 10f, fillRectPaint)
        canvas.drawText(title, MARGIN + 24f, top + 22f, tinyLabelPaint)
        canvas.drawText(headline, MARGIN + 24f, top + 40f, calloutHeadlinePaint)
        drawWrappedText(
            canvas = canvas,
            text = body,
            x = MARGIN + 24f,
            startY = top + if (compact) 54f else 56f,
            maxWidth = PAGE_WIDTH - MARGIN * 2f - 34f,
            paint = footnotePaint,
            lineHeight = 12f,
            maxLines = if (compact) 2 else 3,
        )
        return rect.bottom
    }

    private fun drawInsetNote(
        canvas: Canvas,
        top: Float,
        accentColor: Int,
        text: String,
    ) {
        fillRectPaint.color = accentColor
        canvas.drawRect(MARGIN.toFloat(), top, MARGIN + 2f, top + 46f, fillRectPaint)
        drawWrappedText(
            canvas = canvas,
            text = text,
            x = MARGIN + 14f,
            startY = top + 14f,
            maxWidth = PAGE_WIDTH - MARGIN * 2f - 14f,
            paint = bodyMutedPaint,
            lineHeight = 13f,
            maxLines = 4,
        )
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int = Int.MAX_VALUE,
    ): Float {
        var y = startY
        var currentLine = StringBuilder()
        var lineCount = 0

        text.split(" ").forEach { word ->
            if (lineCount >= maxLines) return@forEach
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(candidate) > maxWidth && currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), x, y, paint)
                currentLine = StringBuilder(word)
                y += lineHeight
                lineCount += 1
            } else {
                currentLine = StringBuilder(candidate)
            }
        }

        if (currentLine.isNotEmpty() && lineCount < maxLines) {
            canvas.drawText(currentLine.toString(), x, y, paint)
        }
        return y
    }

    private fun regularityPaint(percent: Int): Paint = when {
        percent >= 85 -> confirmValuePaint
        percent >= 70 -> amberValuePaint
        else -> pinkValuePaint
    }

    private fun formatShort(date: kotlinx.datetime.LocalDate): String =
        JavaLocalDate.of(date.year, date.monthNumber, date.dayOfMonth).format(shortDateFormatter)

    private fun formatFull(date: kotlinx.datetime.LocalDate): String =
        JavaLocalDate.of(date.year, date.monthNumber, date.dayOfMonth).format(fullDateFormatter)

    private companion object {
        val weekdayLabels: List<String> = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        ).map { dayOfWeek ->
            dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }

        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 44

        const val PINK = -0x399a4a
        const val VERY_LIGHT_PINK = -0x4c506
        const val LIGHT_PINK_BORDER = -0xb13db
        const val TEAL = -0xa19432
        const val LIGHT_TEAL = -0x1b0c0a
        const val LIGHT_TEAL_BORDER = -0x3c7b76
        const val DOCTOR_TEAL = -0x6b8a83
        const val CONFIRM_GREEN = -0x7f9d65
        const val AMBER = -0x30a0

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
        }
        val eyebrowSectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.RIGHT
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val tinyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valuePlainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
        }
        val pinkValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val confirmValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CONFIRM_GREEN
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val amberValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pinkValueRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val valueRightMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }
        val bodyMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val footnotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val calloutHeadlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val footerLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
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
            style = Paint.Style.FILL
        }
        val fillRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val outlineRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val dashedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        val monthCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        }
        val monthCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E8E8")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val calendarDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#474747")
            textSize = 9f
        }
        val inverseDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tinyHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tinyHeaderRightPaint = Paint(tinyHeaderPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val insightBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINK
            alpha = 140
            style = Paint.Style.FILL
        }
    }
}
