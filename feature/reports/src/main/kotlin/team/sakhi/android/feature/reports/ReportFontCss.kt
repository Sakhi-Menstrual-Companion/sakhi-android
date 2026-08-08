package team.sakhi.android.feature.reports

import android.content.Context
import android.util.Base64

/**
 * Builds the `@font-face` rules that give the report its Lato typography.
 *
 * The WebView renders the document with a null base URL and no file access, so it
 * cannot load a font off disk -- the bytes have to travel inside the document. The
 * app already ships Lato as a `res/font` resource, so it is read and base64'd into
 * a data URI here.
 *
 * Without this the report falls back to Roboto on Android (and San Francisco on
 * iOS), which would leave the layout shared but the typography different -- the
 * exact drift moving the report into SakhiCore was meant to end.
 *
 * Encoded lazily and cached: Lato regular + bold is roughly 300KB of base64, so
 * this should happen once per process, not once per report.
 */
class ReportFontCss(private val context: Context) {

    private val css: String by lazy { build() }

    operator fun invoke(): String = css

    private fun build(): String = buildString {
        appendFace(team.sakhi.android.designsystem.R.font.lato_regular, weight = 400)
        appendFace(team.sakhi.android.designsystem.R.font.lato_bold, weight = 700)
    }

    private fun StringBuilder.appendFace(fontRes: Int, weight: Int) {
        val encoded = runCatching {
            context.resources.openRawResource(fontRes).use { stream ->
                Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            }
        }.getOrNull() ?: return   // fall back to the generic stack rather than failing the report

        append("@font-face{font-family:'Lato';font-style:normal;font-weight:")
        append(weight)
        append(";src:url(data:font/ttf;base64,")
        append(encoded)
        append(") format('truetype');}")
    }
}
