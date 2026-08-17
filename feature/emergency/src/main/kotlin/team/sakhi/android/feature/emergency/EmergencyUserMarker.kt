package team.sakhi.android.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * "You" -- her own position on the Emergency map. Port of iOS `EmergencyUserMarker.swift`.
 *
 * A solid dot with a "You" pill under it. Used instead of the SDK's own blue location dot,
 * which is switched off: two markers at one coordinate reads as a rendering fault, and the
 * built-in dot cannot carry a label. On a map of a neighbourhood she has never seen before,
 * an unlabelled dot is not much of an answer to "where am I".
 *
 * No ring of its own. There is already a `Circle` around her drawn from the pulse radius,
 * and a second ring here lands concentric with it -- two circles round one point, which read
 * as a drawing error rather than as emphasis. The circle is the better of the two to keep:
 * it is measured in metres, so it stays true to the map as she zooms, where a fixed-size
 * marker would claim a different area at every zoom level.
 */
internal object EmergencyUserMarker {

    private object Style {
        /** Deliberately small. This marks a point, not an area. */
        const val DOT = 14f
        /** Wider than the dot so the "You" pill has somewhere to sit. */
        const val WIDTH = 44f
        const val LABEL_HEIGHT = 16f
        const val LABEL_WIDTH = 40f
        const val GAP = 4f
        const val COLLAR = 2.5f
    }

    private var cached: Bitmap? = null
    private var cachedKey: Int = 0

    fun bitmap(context: Context, tint: Int, label: String = "You"): Bitmap {
        val key = tint * 31 + label.hashCode()
        cached?.let { if (cachedKey == key) return it }

        val d = context.resources.displayMetrics.density
        fun dp(v: Float) = v * d

        val width = dp(Style.WIDTH)
        val height = dp(Style.DOT) + dp(Style.LABEL_HEIGHT) + dp(Style.GAP)
        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = Canvas(bitmap)

        val dotRect = RectF(
            (width - dp(Style.DOT)) / 2f,
            0f,
            (width + dp(Style.DOT)) / 2f,
            dp(Style.DOT),
        )
        // The white collar is what keeps the dot readable over dark green and water, the
        // same job the system dot's border does.
        canvas.drawOval(dotRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.FILL
        })
        canvas.drawOval(dotRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(Style.COLLAR)
        })

        val labelRect = RectF(
            (width - dp(Style.LABEL_WIDTH)) / 2f,
            dp(Style.DOT) + dp(Style.GAP),
            (width + dp(Style.LABEL_WIDTH)) / 2f,
            dp(Style.DOT) + dp(Style.GAP) + dp(Style.LABEL_HEIGHT),
        )
        canvas.drawRoundRect(labelRect, labelRect.height() / 2f, labelRect.height() / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint
                style = Paint.Style.FILL
            })

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(11f)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            label,
            labelRect.centerX(),
            labelRect.centerY() - (text.descent() + text.ascent()) / 2f,
            text,
        )

        cached = bitmap
        cachedKey = key
        return bitmap
    }
}
