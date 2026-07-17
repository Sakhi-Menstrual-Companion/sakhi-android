package team.sakhi.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Real port of iOS `HeightRulerPicker` (`OnboardingInputPickers.swift`) -- a
 * vertical drag-to-scrub ruler, not a text field or stepper. Faithful to the
 * real interaction: dragging down increases the value (taller), each whole-unit
 * crossing fires a selection haptic, releasing snaps to the nearest whole unit
 * with a short spring, and a fixed needle marks the centre while the tick stack
 * scrolls behind it. `value`/`range` are in the *caller's current display unit*
 * (cm or inch) -- same contract as the real iOS component, which is handed an
 * already-unit-converted binding by `HeightEditView` rather than converting
 * internally.
 */
@Composable
fun HeightRulerPicker(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onHapticSelection: () -> Unit = {},
    onHapticImpact: () -> Unit = {},
) {
    val pxPerUnit = with(LocalDensity.current) { 6.dp.toPx() }
    val lo = range.start.roundToInt()
    val hi = range.endInclusive.roundToInt()
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    var dragBase by remember { mutableFloatStateOf(value) }
    var lastHapticInt by remember { mutableIntStateOf(value.roundToInt()) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnHapticSelection = rememberUpdatedState(onHapticSelection)
    val currentOnHapticImpact = rememberUpdatedState(onHapticImpact)
    val currentRange = rememberUpdatedState(range)

    val tickColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fadeColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(rulerHeight)
            .pointerInput(Unit) {
                // `detectDragGestures`'s `dragAmount` is the delta *since the last
                // callback*, unlike SwiftUI's `DragGesture.translation`, which is
                // cumulative since the gesture started -- accumulate it ourselves
                // so the drag-to-value mapping matches the real iOS math exactly
                // (`value = dragBase + translation / ppu`, not a re-based sum of
                // small deltas, which would drift under fast/jittery drags).
                var cumulativeDelta = 0f
                detectDragGestures(
                    onDragStart = {
                        dragBase = currentValue.value
                        cumulativeDelta = 0f
                        lastHapticInt = currentValue.value.roundToInt()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumulativeDelta += dragAmount.y
                        val newValue = (dragBase + cumulativeDelta / pxPerUnit).coerceIn(
                            currentRange.value.start,
                            currentRange.value.endInclusive,
                        )
                        currentOnValueChange.value(newValue)
                        val iv = newValue.roundToInt()
                        if (iv != lastHapticInt) {
                            currentOnHapticSelection.value()
                            lastHapticInt = iv
                        }
                    },
                    onDragEnd = {
                        val snapped = currentValue.value.roundToInt().toFloat()
                            .coerceIn(currentRange.value.start, currentRange.value.endInclusive)
                        currentOnHapticImpact.value()
                        // Real port of iOS's `withAnimation(.spring(response: 0.22,
                        // dampingFraction: 0.72))` snap-to-nearest-unit on release,
                        // not an instant jump.
                        scope.launch {
                            Animatable(currentValue.value).animateTo(
                                targetValue = snapped,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = 900f,
                                ),
                            ) {
                                currentOnValueChange.value(this.value)
                            }
                        }
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f

        for (v in lo..hi) {
            val y = centerY + (value - v) * pxPerUnit
            if (y < -20f || y > size.height + 20f) continue

            val isSelected = v == value.roundToInt()
            val isMajor = v % 10 == 0
            val isMid = v % 5 == 0
            val tickLength = if (isMajor) 52f else if (isMid) 28f else 14f
            val strokeWidth = if (isMajor) 3.5f else if (isMid) 1.5f else 1f
            val color = when {
                isSelected -> selectedColor
                isMajor -> tickColor
                isMid -> tickColor.copy(alpha = 0.35f)
                else -> tickColor.copy(alpha = 0.15f)
            }

            drawTick(
                startX = centerX + 24.dp.toPx(),
                y = y,
                length = tickLength,
                color = color,
                strokeWidth = strokeWidth,
            )

            if (isMajor && !isSelected) {
                val text = textMeasurer.measure(
                    text = v.toString(),
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        centerX + 24.dp.toPx() - text.size.width - 6.dp.toPx(),
                        y - text.size.height / 2f,
                    ),
                )
            }
        }

        // Top/bottom fade so ticks dissolve into the card background instead of
        // hard-clipping, matching the real iOS `LinearGradient` overlays.
        drawRect(
            brush = Brush.verticalGradient(listOf(fadeColor, Color.Transparent)),
            size = androidx.compose.ui.geometry.Size(size.width, 60.dp.toPx()),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, fadeColor)),
            topLeft = Offset(0f, size.height - 60.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width, 60.dp.toPx()),
        )

        // Fixed pink needle at vertical centre, pointing right into the ruler --
        // matches the real iOS `arrowtriangle.right.fill` + rule overlay.
        val needleY = centerY
        drawLine(
            color = selectedColor,
            start = Offset(0f, needleY),
            end = Offset(centerX + 24.dp.toPx(), needleY),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private fun DrawScope.drawTick(startX: Float, y: Float, length: Float, color: Color, strokeWidth: Float) {
    drawLine(
        color = color,
        start = Offset(startX, y),
        end = Offset(startX + length, y),
        strokeWidth = strokeWidth,
    )
}

private val rulerHeight = 300.dp
