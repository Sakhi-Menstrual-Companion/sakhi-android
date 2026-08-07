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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

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
    val density = LocalDensity.current
    val pxPerUnit = with(density) { 6.dp.toPx() }
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
    val labelColor = sakhiSecondaryLabel()
    // Fades to the card it's drawn inside, which is `sakhiSystemBackground()` (white) --
    // was `colorScheme.surface` (this app's brand pink tint), same bug as
    // `WeightWheelPicker`'s fade, confirmed on-device.
    val fadeColor = sakhiSystemBackground()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(rulerHeight)
            // Matches `.clipped()` on the iOS ruler (OnboardingInputPickers.swift:302).
            // See `WeightWheelPicker` for why Compose needs this explicitly.
            .clipToBounds()
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
        // iOS's real tick row is `HStack(spacing: 0) { Spacer(); label; Rectangle() }`
        // with no trailing padding -- the tick sits flush against the ruler's own
        // right edge, with the Spacer absorbing all the space to its left. The
        // previous version anchored ticks to `size.width / 2f` (canvas centre) instead
        // of the right edge, which was fine while the canvas was accidentally narrow
        // (a since-fixed sibling bug) but meant widening the canvas just opened a gap
        // between the value column and the ticks instead of widening the ruler itself
        // -- reported live as "ruler ke marks kitne kam width mai hai." Anchoring to
        // `size.width` instead makes the tick stack actually grow with the canvas.
        val tickEndX = size.width

        for (v in lo..hi) {
            val y = centerY + (value - v) * pxPerUnit
            if (y < -20f || y > size.height + 20f) continue

            val isSelected = v == value.roundToInt()
            val isMajor = v % 10 == 0
            val isMid = v % 5 == 0
            // Was bare pixel counts, not dp-converted -- unlike `WeightWheelPicker`'s
            // equivalent, which correctly wraps its tick lengths in `.dp.toPx()`. On
            // this device's 2.75x density, a raw "52" rendered at under 19dp, well
            // short of the intended 52dp. Fixed size is `HeightTickLength*`, deliberately
            // longer than iOS's literal 52/28/14pt per Karan's live review ("more increase
            // karo width"); stroke width is `HeightTickStrokeWidth*`, deliberately thinner
            // than iOS's literal 3.5/1.5/1pt ("stick kafi bold hogaya hai, usko kam karo").
            val tickLength = with(density) {
                (if (isMajor) HeightTickLengthMajor else if (isMid) HeightTickLengthMid else HeightTickLengthMinor).toPx()
            }
            val strokeWidth = with(density) {
                (if (isMajor) HeightTickStrokeWidthMajor else if (isMid) HeightTickStrokeWidthMid else HeightTickStrokeWidthMinor).toPx()
            }
            val color = when {
                isSelected -> selectedColor
                isMajor -> tickColor
                isMid -> tickColor.copy(alpha = 0.35f)
                else -> tickColor.copy(alpha = 0.15f)
            }

            drawTick(
                startX = tickEndX - tickLength,
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
                        tickEndX - tickLength - text.size.width - 6.dp.toPx(),
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

        // Fixed pink needle at vertical centre, spanning the full ruler width and
        // pointing right into the tick stack -- matches the real iOS
        // `arrowtriangle.right.fill` + rule overlay, now that the tick stack itself
        // spans the same full width instead of stopping at the old centre anchor.
        val needleY = centerY
        drawLine(
            color = selectedColor,
            start = Offset(0f, needleY),
            end = Offset(size.width, needleY),
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

// Karan, live: the 300dp card was tall enough to get clipped by the footer on this
// device. Reduced together with `OnboardingHealthStepUi.kt`'s matching
// `HeightRulerPickerHeight` -- the two are deliberately duplicated (different Gradle
// modules) and must be changed together.
private val rulerHeight = 260.dp

/** DELIBERATE DEVIATION FROM iOS's literal 52/28/14pt -- see the call-site comment. */
private val HeightTickLengthMajor = 68.dp
private val HeightTickLengthMid = 36.dp
private val HeightTickLengthMinor = 18.dp

/** DELIBERATE DEVIATION FROM iOS's literal 3.5/1.5/1pt -- see the call-site comment. */
private val HeightTickStrokeWidthMajor = 2.5.dp
private val HeightTickStrokeWidthMid = 1.2.dp
private val HeightTickStrokeWidthMinor = 0.8.dp
