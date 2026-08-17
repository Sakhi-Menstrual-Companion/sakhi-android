package team.sakhi.android.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.res.ResourcesCompat

/**
 * Draws a Sakhi's map pin: her avatar in a white ring, her name in a pill above it, and a
 * small pointer at the coordinate. Port of iOS `EmergencyPinRenderer.swift`.
 *
 * Android drew `BitmapDescriptorFactory.defaultMarker()` here -- Google's stock teardrop,
 * hue-shifted for approximate pins. That carried none of the information the iOS pin does
 * and, worse, said nothing about accuracy beyond a colour nobody can read as a meaning.
 *
 * **The dashed ring is not decoration.** A solid ring means the coordinate is real, which is
 * only true inside an accepted session. Everywhere else the pin is placed from a bucketed
 * distance and a 45-degree-snapped bearing, and the dashes are the only thing on screen
 * saying so. Do not make them solid to tidy the look.
 */
internal object EmergencyPinRenderer {

    private object Metrics {
        const val AVATAR = 44f
        const val RING = 3f
        const val PILL_HEIGHT = 22f
        /** Extra height when the spot line is present. */
        const val PILL_SPOT_EXTRA = 13f
        const val SPOT_SIZE = 10f
        const val PILL_GAP = 5f
        const val PILL_PADDING_X = 8f
        const val POINTER_WIDTH = 11f
        const val POINTER_HEIGHT = 7f
        const val POINTER_OVERLAP = 2f
        const val NAME_SIZE = 12f
    }

    /**
     * Cached because Maps asks for the icon again on pan and zoom, and re-rendering the same
     * pin every time is visible as stutter on a busy map. Keyed on appearance only.
     */
    private val cache = mutableMapOf<String, Bitmap>()

    fun bitmap(
        context: Context,
        pin: EmergencyMapPin,
        accent: Int,
        surface: Int,
        onSurface: Int,
        onSurfaceVariant: Int,
        separator: Int,
        isDark: Boolean,
    ): Bitmap {
        // Position is deliberately absent: moving a pin does not change how it looks. The id
        // is present, because it picks the face.
        val key = "${pin.id}|${pin.title}|${pin.spotLabel}|${pin.isApproximate}|$isDark"
        cache[key]?.let { return it }
        val rendered = render(context, pin, accent, surface, onSurface, onSurfaceVariant, separator)
        cache[key] = rendered
        return rendered
    }

    /** Call when the theme flips, so light-mode pills are not reused on a dark map. */
    fun clearCache() = cache.clear()

    private fun render(
        context: Context,
        pin: EmergencyMapPin,
        accent: Int,
        surface: Int,
        onSurface: Int,
        onSurfaceVariant: Int,
        separator: Int,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        fun dp(v: Float) = v * d

        // First name only, exactly as iOS does.
        val name = pin.title?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
        // Only ever set inside an accepted session, where the exact spot is the point of the
        // pin: "IS IN 2ND FLOOR WASHROOM" is what gets her to the door.
        val spot = pin.spotLabel?.takeIf { it.isNotBlank() }?.let { "Is in ${it.uppercase()}" }

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(Metrics.NAME_SIZE)
            color = onSurface
            isFakeBoldText = true
        }
        val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(Metrics.SPOT_SIZE)
            color = onSurfaceVariant
            isFakeBoldText = true
        }

        val nameWidth = name?.let { namePaint.measureText(it) } ?: 0f
        val spotWidth = spot?.let { spotPaint.measureText(it) } ?: 0f
        val textWidth = maxOf(nameWidth, spotWidth)

        val hasPill = name != null || spot != null
        val pillWidth = if (hasPill) textWidth + dp(Metrics.PILL_PADDING_X) * 2 else 0f
        val pillHeight = dp(Metrics.PILL_HEIGHT) + if (spot == null) 0f else dp(Metrics.PILL_SPOT_EXTRA)

        val avatarTotal = dp(Metrics.AVATAR) + dp(Metrics.RING) * 2
        val width = maxOf(avatarTotal, pillWidth)
        val pillBlock = if (hasPill) pillHeight + dp(Metrics.PILL_GAP) else 0f
        val height = pillBlock + avatarTotal + dp(Metrics.POINTER_HEIGHT) - dp(Metrics.POINTER_OVERLAP)

        val bitmap = createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        val canvas = Canvas(bitmap)

        if (hasPill) {
            drawPill(
                canvas = canvas,
                rect = RectF((width - pillWidth) / 2f, 0f, (width + pillWidth) / 2f, pillHeight),
                name = name, namePaint = namePaint,
                spot = spot, spotPaint = spotPaint,
                surface = surface, separator = separator,
                // A capsule for one line, a rounded rectangle for two -- a full capsule
                // around a two-line block bows out at the sides and stops reading as a label.
                radius = if (spot == null) pillHeight / 2f else dp(9f),
                strokeWidth = dp(0.5f),
            )
        }

        val avatarFrame = RectF(
            (width - avatarTotal) / 2f,
            pillBlock,
            (width + avatarTotal) / 2f,
            pillBlock + avatarTotal,
        )

        // Pointer first, so the ring is drawn over where the two meet and there is no seam.
        drawPointer(
            canvas = canvas,
            centreX = width / 2f,
            topY = avatarFrame.bottom - dp(Metrics.POINTER_OVERLAP),
            widthPx = dp(Metrics.POINTER_WIDTH),
            heightPx = dp(Metrics.POINTER_HEIGHT),
            surface = surface,
        )
        drawAvatar(
            context = context,
            canvas = canvas,
            pin = pin,
            frame = avatarFrame,
            accent = accent,
            surface = surface,
            ringInset = dp(Metrics.RING),
            ringStroke = dp(2f),
            dash = dp(3f) to dp(4f),
        )

        return bitmap
    }

    private fun drawPill(
        canvas: Canvas,
        rect: RectF,
        name: String?,
        namePaint: Paint,
        spot: String?,
        spotPaint: Paint,
        surface: Int,
        separator: Int,
        radius: Float,
        strokeWidth: Float,
    ) {
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = separator
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        })

        // iOS stacks the two lines centred as a block. Font metrics differ between the
        // platforms, so the block is centred from measured ascent/descent rather than from
        // a hardcoded offset.
        val nameH = if (name != null) namePaint.descent() - namePaint.ascent() else 0f
        val spotH = if (spot != null) spotPaint.descent() - spotPaint.ascent() else 0f
        var y = rect.centerY() - (nameH + spotH) / 2f

        if (name != null) {
            canvas.drawText(name, rect.centerX() - namePaint.measureText(name) / 2f, y - namePaint.ascent(), namePaint)
            y += nameH
        }
        if (spot != null) {
            canvas.drawText(spot, rect.centerX() - spotPaint.measureText(spot) / 2f, y - spotPaint.ascent(), spotPaint)
        }
    }

    private fun drawAvatar(
        context: Context,
        canvas: Canvas,
        pin: EmergencyMapPin,
        frame: RectF,
        accent: Int,
        surface: Int,
        ringInset: Float,
        ringStroke: Float,
        dash: Pair<Float, Float>,
    ) {
        // The white collar. A filled disc under the image rather than a stroke over it, so
        // the image's edge is never clipped into the ring.
        canvas.drawOval(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
            style = Paint.Style.FILL
        })

        val imageRect = RectF(frame).apply { inset(ringInset, ringInset) }

        val saved = canvas.save()
        canvas.clipPath(Path().apply { addOval(imageRect, Path.Direction.CW) })
        val avatar = ResourcesCompat.getDrawable(
            context.resources,
            EmergencyAvatarCatalog.drawableFor(pin.id),
            context.theme,
        )?.toBitmap(imageRect.width().toInt().coerceAtLeast(1), imageRect.height().toInt().coerceAtLeast(1))

        if (avatar != null) {
            canvas.drawBitmap(avatar, null, imageRect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            // Nothing to draw a face with. The initial is the same fallback iOS uses.
            canvas.drawOval(imageRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((0.18f * 255).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            })
            val initial = pin.title?.trim()?.firstOrNull()?.uppercase() ?: "S"
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = imageRect.height() * 0.45f
                color = accent
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(initial, imageRect.centerX(), imageRect.centerY() - (p.descent() + p.ascent()) / 2f, p)
        }
        canvas.restoreToCount(saved)

        // The accuracy ring, outside the collar. Dashed whenever the coordinate is an
        // estimate -- see the note on this object.
        canvas.drawOval(RectF(frame).apply { inset(1f, 1f) }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = ringStroke
            if (pin.isApproximate) pathEffect = DashPathEffect(floatArrayOf(dash.first, dash.second), 0f)
        })
    }

    private fun drawPointer(
        canvas: Canvas,
        centreX: Float,
        topY: Float,
        widthPx: Float,
        heightPx: Float,
        surface: Int,
    ) {
        canvas.drawPath(
            Path().apply {
                moveTo(centreX - widthPx / 2f, topY)
                lineTo(centreX + widthPx / 2f, topY)
                lineTo(centreX, topY + heightPx)
                close()
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface
                style = Paint.Style.FILL
            },
        )
    }
}
