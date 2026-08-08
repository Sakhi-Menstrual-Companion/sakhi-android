package team.sakhi.android.feature.reports

import android.content.Context
import java.io.File

/**
 * Puts Lato next to the report document on disk and returns the `@font-face` rules
 * that point at it.
 *
 * Base64 data URIs were tried first and do not work here: the document is loaded
 * with an opaque origin, where a large `data:` font URI is refused, and the whole
 * render came back blank. Writing the TTFs beside the HTML and loading the page
 * over `file://` makes the font an ordinary same-origin fetch, which the WebView
 * is happy with.
 *
 * The app already ships Lato as a `res/font` resource; this copies it out once per
 * directory rather than re-encoding it per report.
 *
 * Without this the report falls back to Roboto on Android and San Francisco on
 * iOS -- the layout would be shared but the typography would not, which is exactly
 * the drift that moving the report into SakhiCore was meant to end.
 */
class ReportFontCss(private val context: Context) {

    /**
     * Copies the fonts into [workingDir] if they are not already there and returns
     * CSS referencing them by relative name.
     */
    fun cssFor(workingDir: File): String = buildString {
        appendFace(workingDir, REGULAR_FILE, team.sakhi.android.designsystem.R.font.lato_regular, 400)
        appendFace(workingDir, BOLD_FILE, team.sakhi.android.designsystem.R.font.lato_bold, 700)
    }

    private fun StringBuilder.appendFace(dir: File, fileName: String, fontRes: Int, weight: Int) {
        val target = File(dir, fileName)
        val copied = runCatching {
            if (!target.exists() || target.length() == 0L) {
                context.resources.openRawResource(fontRes).use { input ->
                    target.outputStream().use(input::copyTo)
                }
            }
            true
        }.getOrDefault(false)

        // If the copy fails the report still renders, just in the fallback face --
        // a missing font must never cost the user their report.
        if (!copied) return

        append("@font-face{font-family:'Lato';font-style:normal;font-weight:")
        append(weight)
        append(";src:url('")
        append(fileName)
        append("') format('truetype');}")
    }

    private companion object {
        const val REGULAR_FILE = "lato_regular.ttf"
        const val BOLD_FILE = "lato_bold.ttf"
    }
}
