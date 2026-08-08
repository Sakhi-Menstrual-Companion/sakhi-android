package team.sakhi.android.feature.reports

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import team.sakhi.report.ReportHtml
import team.sakhi.report.ReportSectionKey

/**
 * Renders the shared `ReportHtml` document to a PDF.
 *
 * This replaces the 900 lines of imperative `Canvas` drawing in
 * [ReportPdfExporter]. All layout now lives in SakhiCore's `ReportHtml`, so this
 * class only has to rasterise: load the HTML into an offscreen [WebView] and let
 * the platform's own print pipeline produce the PDF. iOS does the equivalent with
 * `WKWebView` + `UIPrintPageRenderer`, which is what makes the two outputs match.
 *
 * The signature deliberately mirrors [ReportPdfExporter.export] so this is a
 * drop-in swap at the call site and the old exporter can be deleted once the
 * output has been compared.
 */
class ReportHtmlPdfExporter(
    private val context: Context,
    private val strings: AndroidReportStrings,
) {

    /**
     * Everything here must run on the main thread: `WebView` and
     * `PrintDocumentAdapter` are both main-thread-only, and the adapter's
     * callbacks are delivered there too.
     */
    suspend fun export(
        document: ReportDocument,
        selectedSections: Set<ReportSection>,
    ): File = withContext(Dispatchers.Main) {
        val html = ReportHtml.render(
            data = document.report,
            strings = strings.build(document),
            sections = selectedSections.toSharedKeys(),
        )

        val webView = WebView(context).apply {
            // No JS in the document, so leave it off: this renders user health data
            // and there is no reason to give it an execution context.
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }

        awaitPageFinished(webView, html)

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(reportsDir, "SakhiReport_${System.currentTimeMillis()}.pdf")
        writePdf(webView, outputFile)
        outputFile
    }

    fun buildShareUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    /** Resumes once the WebView reports the document laid out, or fails loudly. */
    private suspend fun awaitPageFinished(webView: WebView, html: String): Unit =
        withTimeout(LOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?,
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Report WebView failed: ${error?.description}"),
                            )
                        }
                    }
                }
                // `null` base URL keeps the document isolated -- it cannot reference
                // anything on disk or the network, which is what we want for a
                // self-contained report.
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

    /**
     * Draws the laid-out WebView into a [PdfDocument], one A4 page at a time.
     *
     * The print pipeline would have been the obvious route, but
     * `PrintDocumentAdapter.LayoutResultCallback` and `WriteResultCallback` both
     * have package-private constructors, so they cannot be subclassed from app
     * code without declaring a class inside `android.print` or using reflection.
     * Neither is worth it here: the shared stylesheet already lays the document
     * out as fixed-height A4 sections, so page N is simply the slice of the
     * rendered view between N*pageHeight and (N+1)*pageHeight. Translating the
     * canvas per page gives exact pagination with no guesswork.
     */
    private fun writePdf(webView: WebView, outputFile: File) {
        val widthPx = (PAGE_WIDTH_PT * RENDER_SCALE).toInt()
        val pageHeightPx = (PAGE_HEIGHT_PT * RENDER_SCALE).toInt()

        // Measure unbounded vertically so the full document height is known.
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        webView.layout(0, 0, widthPx, webView.measuredHeight)

        val contentHeightPx = maxOf(webView.measuredHeight, pageHeightPx)
        val pageCount = ceil(contentHeightPx.toDouble() / pageHeightPx).toInt().coerceAtLeast(1)

        val pdf = PdfDocument()
        for (index in 0 until pageCount) {
            val info = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH_PT.toInt(),
                PAGE_HEIGHT_PT.toInt(),
                index + 1,
            ).create()
            val page = pdf.startPage(info)
            page.canvas.apply {
                // PDF units are points; the view was rendered at RENDER_SCALE for
                // crispness, so scale back down before drawing.
                val inv = 1f / RENDER_SCALE
                scale(inv, inv)
                translate(0f, -(index * pageHeightPx).toFloat())
                webView.draw(this)
            }
            pdf.finishPage(page)
        }
        FileOutputStream(outputFile).use { pdf.writeTo(it) }
        pdf.close()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L

        /** Matches the shared stylesheet's `@page` size. */
        const val PAGE_WIDTH_PT = 595.28f
        const val PAGE_HEIGHT_PT = 841.89f

        /** Render above 1x so text is crisp, then scale back when drawing. */
        const val RENDER_SCALE = 2f
    }
}

/** Maps the UI's section enum onto the shared keys. */
internal fun Set<ReportSection>.toSharedKeys(): Set<ReportSectionKey> = mapNotNullTo(mutableSetOf()) {
    when (it) {
        ReportSection.PeriodCalendar -> ReportSectionKey.PERIOD_CALENDAR
        ReportSection.Symptoms -> ReportSectionKey.SYMPTOMS
        ReportSection.MoodPatterns -> ReportSectionKey.MOOD_PATTERNS
        ReportSection.Insights -> ReportSectionKey.INSIGHTS
        else -> null
    }
}
