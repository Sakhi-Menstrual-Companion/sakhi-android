package team.sakhi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Real port of iOS `WeightWheelPicker` (`OnboardingInputPickers.swift`) -- a
 * horizontal drag-to-scrub dial with ticks laid out along a real arc (same
 * `center + radius * (cos, sin)` trig as the Swift `Canvas` version), not a
 * flat horizontal strip. Faithful to the real interaction: dragging right
 * *decreases* the value, each whole-unit crossing fires a selection haptic,
 * releasing settles with an impact haptic (iOS never re-snaps weight -- it's
 * already integer-stepped during drag).
 */
@Composable
fun WeightWheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onHapticSelection: () -> Unit = {},
    onHapticImpact: () -> Unit = {},
) {
    val density = LocalDensity.current
    val dialRadiusPx = with(density) { 200.dp.toPx() }
    val arcTopPadPx = with(density) { 26.dp.toPx() }
    val degreesPerUnit = 2.2
    val textMeasurer = rememberTextMeasurer()

    var dragBase by remember { mutableFloatStateOf(value.toFloat()) }
    var lastHapticInt by remember { mutableIntStateOf(value) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnHapticSelection = rememberUpdatedState(onHapticSelection)
    val currentOnHapticImpact = rememberUpdatedState(onHapticImpact)
    val currentRange = rememberUpdatedState(range)

    val tickColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val labelColor = sakhiSecondaryLabel()
    // Fades to the card it's drawn inside, which is `sakhiSystemBackground()` (white) --
    // was `colorScheme.surface` (this app's brand pink tint), which rendered as two
    // solid pink blocks cutting the dial off instead of a smooth fade into the white
    // card, reported live and confirmed on-device ("inside white container pink color
    // element kyun hai").
    val fadeColor = sakhiSystemBackground()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(wheelHeight)
            // Matches `.clipped()` on the iOS wheel (OnboardingInputPickers.swift:112).
            // The +-78 degree sweep is nearly a semicircle, so its end ticks fall well
            // outside this 148dp band. SwiftUI clips them; Compose does NOT clip draw
            // content to layout bounds, so those ticks were painting over the card as
            // stray marks in the lower corners.
            .clipToBounds()
            .pointerInput(Unit) {
                // Same cumulative-delta correction as `HeightRulerPicker` --
                // Compose's `dragAmount` is per-frame, SwiftUI's `translation`
                // is cumulative since drag start.
                var cumulativeDelta = 0f
                detectDragGestures(
                    onDragStart = {
                        dragBase = currentValue.value.toFloat()
                        cumulativeDelta = 0f
                        lastHapticInt = currentValue.value
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumulativeDelta += dragAmount.x
                        // 0.07 units per point, negated: drag-right = lower value,
                        // exactly matching the real iOS `dragGesture`.
                        val newValue = (dragBase - cumulativeDelta * 0.07f)
                            .roundToInt()
                            .coerceIn(currentRange.value.first, currentRange.value.last)
                        currentOnValueChange.value(newValue)
                        if (newValue != lastHapticInt) {
                            currentOnHapticSelection.value()
                            lastHapticInt = newValue
                        }
                    },
                    onDragEnd = {
                        currentOnHapticImpact.value()
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, arcTopPadPx + dialRadiusPx)
        val outerRadius = dialRadiusPx - with(density) { 4.dp.toPx() }

        for (tickValue in range) {
            val relativeDegrees = (tickValue - value) * degreesPerUnit
            if (abs(relativeDegrees) > 78) continue

            val isMajor = tickValue % 10 == 0
            val isMid = tickValue % 5 == 0
            val isSelected = tickValue == value
            val tickLength = with(density) { (if (isMajor) 26.dp else if (isMid) 15.dp else 8.dp).toPx() }
            val strokeWidth = with(density) { (if (isMajor) 2.5.dp else 1.5.dp).toPx() }
            val radians = (relativeDegrees - 90) * PI / 180

            val outerPoint = arcPoint(center, outerRadius, radians)
            val innerPoint = arcPoint(center, outerRadius - tickLength, radians)

            val color = when {
                isSelected -> selectedColor
                isMajor -> tickColor
                isMid -> tickColor.copy(alpha = 0.35f)
                else -> tickColor.copy(alpha = 0.15f)
            }
            drawLine(color = color, start = outerPoint, end = innerPoint, strokeWidth = strokeWidth)

            if (isMajor && !isSelected) {
                val labelPoint = arcPoint(center, outerRadius + with(density) { 13.dp.toPx() }, radians)
                val text = textMeasurer.measure(
                    text = tickValue.toString(),
                    style = TextStyle(color = labelColor, fontSize = 10.sp, textAlign = TextAlign.Center),
                )
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(labelPoint.x - text.size.width / 2f, labelPoint.y - text.size.height / 2f),
                )
            }
        }

        // Left/right fade so the arc dissolves into the card background,
        // matching the real iOS `LinearGradient` edge overlays.
        drawRect(
            brush = Brush.horizontalGradient(listOf(fadeColor, Color.Transparent)),
            size = androidx.compose.ui.geometry.Size(64.dp.toPx(), size.height),
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, fadeColor)),
            topLeft = Offset(size.width - 64.dp.toPx(), 0f),
            size = androidx.compose.ui.geometry.Size(64.dp.toPx(), size.height),
        )

        // Fixed pink downward-pointing needle at top-centre, matching the real
        // iOS `arrowtriangle.down.fill` + rule overlay.
        drawLine(
            color = selectedColor,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, with(density) { 24.dp.toPx() }),
            strokeWidth = with(density) { 2.dp.toPx() },
        )
    }
}

private fun arcPoint(center: Offset, radius: Float, radians: Double): Offset {
    return Offset(
        x = center.x + (radius * cos(radians)).toFloat(),
        y = center.y + (radius * sin(radians)).toFloat(),
    )
}

private val wheelHeight = 148.dp
