package team.sakhi.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Port of iOS `HorizontalRulerSlider` for the logging sheet's weight/BBT rows:
 * horizontal drag, fixed pink needle, integer tick labels, and spring snap to
 * the nearest step on release.
 */
@Composable
fun HorizontalRulerSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    anchorFraction: Float = 0.5f,
    step: Float = 1f,
    onHapticSelection: () -> Unit = {},
    onHapticImpact: () -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val pointsPerUnit = with(density) { 20.dp.toPx() }

    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var lastHapticStep by remember { mutableIntStateOf((value / step).roundToInt()) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnHapticSelection = rememberUpdatedState(onHapticSelection)
    val currentOnHapticImpact = rememberUpdatedState(onHapticImpact)
    val currentRange = rememberUpdatedState(range)
    val currentStep = rememberUpdatedState(step)

    val tickColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val labelColor = sakhiSecondaryLabel()
    // iOS masks the ruler's edges with a `LinearGradient(.clear -> .black)`, i.e. it
    // fades the ticks out to TRANSPARENT. Android paints an opaque rectangle instead, so
    // the colour has to be whatever the ruler sits on -- the white symptom card. It was
    // `colorScheme.surface`, the brand pink, which drew two solid pink blocks over the
    // ends of the ruler rather than fading it.
    val fadeColor = sakhiSystemBackground()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(horizontalRulerHeight)
            .pointerInput(Unit) {
                var cumulativeDelta = 0f
                detectDragGestures(
                    onDragStart = {
                        dragStartValue = currentValue.value
                        cumulativeDelta = 0f
                        lastHapticStep = (currentValue.value / currentStep.value).roundToInt()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumulativeDelta += dragAmount.x
                        val newValue = (dragStartValue - cumulativeDelta / pointsPerUnit).coerceIn(
                            currentRange.value.start,
                            currentRange.value.endInclusive,
                        )
                        currentOnValueChange.value(newValue)
                        val stepIndex = (newValue / currentStep.value).roundToInt()
                        if (stepIndex != lastHapticStep) {
                            currentOnHapticSelection.value()
                            lastHapticStep = stepIndex
                        }
                    },
                    onDragEnd = {
                        val snapped = ((currentValue.value / currentStep.value).roundToInt() * currentStep.value)
                            .coerceIn(currentRange.value.start, currentRange.value.endInclusive)
                        currentOnHapticImpact.value()
                        scope.launch {
                            Animatable(currentValue.value).animateTo(
                                targetValue = snapped,
                                animationSpec = spring(
                                    dampingRatio = 0.75f,
                                    stiffness = 700f,
                                ),
                            ) {
                                currentOnValueChange.value(this.value)
                            }
                        }
                    },
                )
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val anchorX = widthPx * anchorFraction
        val lo = range.start.roundToInt()
        val hi = range.endInclusive.roundToInt()
        val span = hi - lo
        val labelInterval = when {
            span >= 50 -> 10
            span >= 15 -> 5
            else -> 1
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(horizontalRulerHeight)
                .clipToBounds(),
        ) {
            drawNeedle(anchorX = anchorX, color = selectedColor)

            val currentTick = value.roundToInt()
            for (tick in lo..hi) {
                val x = anchorX + (tick - value) * pointsPerUnit
                if (x < -pointsPerUnit || x > size.width + pointsPerUnit) continue

                val isMajor = tick % 10 == 0
                val isMid = tick % 5 == 0
                val tickHeight = when {
                    isMajor -> 36.dp.toPx()
                    isMid -> 24.dp.toPx()
                    else -> 13.dp.toPx()
                }
                val strokeWidth = if (isMajor) 2.dp.toPx() else 1.5.dp.toPx()
                val alpha = when {
                    isMajor -> 0.5f
                    isMid -> 0.3f
                    else -> 0.15f
                }

                drawRect(
                    color = tickColor.copy(alpha = alpha),
                    topLeft = Offset(x - strokeWidth / 2f, rulerTop.toPx() + (rulerTickBand.toPx() - tickHeight) / 2f),
                    size = Size(strokeWidth, tickHeight),
                )

                if (tick == currentTick) {
                    drawTickLabel(
                        textMeasurer = textMeasurer,
                        text = tick.toString(),
                        x = x,
                        y = rulerTop.toPx() + rulerTrackHeight.toPx() - 7.dp.toPx(),
                        color = selectedColor,
                        bold = true,
                    )
                } else if (tick % labelInterval == 0) {
                    drawTickLabel(
                        textMeasurer = textMeasurer,
                        text = tick.toString(),
                        x = x,
                        y = rulerTop.toPx() + rulerTrackHeight.toPx() - 7.dp.toPx(),
                        color = labelColor,
                        bold = false,
                    )
                }
            }

            drawRect(
                brush = Brush.horizontalGradient(listOf(fadeColor, Color.Transparent)),
                topLeft = Offset(0f, rulerTop.toPx()),
                size = Size(40.dp.toPx(), rulerTrackHeight.toPx()),
            )
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, fadeColor)),
                topLeft = Offset(size.width - 40.dp.toPx(), rulerTop.toPx()),
                size = Size(40.dp.toPx(), rulerTrackHeight.toPx()),
            )

            drawLine(
                color = selectedColor,
                start = Offset(anchorX, rulerTop.toPx()),
                end = Offset(anchorX, rulerTop.toPx() + rulerTrackHeight.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawNeedle(anchorX: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(anchorX, 6.dp.toPx()),
        end = Offset(anchorX, 20.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
    )

    val triangleWidth = 8.dp.toPx()
    val triangleHeight = 6.dp.toPx()
    val triangle = Path().apply {
        moveTo(anchorX - triangleWidth / 2f, 0f)
        lineTo(anchorX + triangleWidth / 2f, 0f)
        lineTo(anchorX, triangleHeight)
        close()
    }
    drawPath(path = triangle, color = color)
}

private fun DrawScope.drawTickLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    bold: Boolean,
) {
    val layout = textMeasurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = 10.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
    )
}

private val horizontalRulerHeight = 64.dp

/**
 * iOS draws the ruler in a `Canvas().frame(height: 60)` and centres each tick inside a
 * 42pt band (`y: (42 - h) / 2`).
 *
 * These were `rulerTopPx = 18f` / `rulerTrackHeightPx = 60f` -- named as pixels and used
 * as raw floats straight in `DrawScope`, never density-converted. On a 2.75x screen that
 * is a ~7dp offset and a ~22dp track inside a 64dp component, so the ticks bunched into a
 * thin strip and the labels landed on top of them.
 */
private val rulerTop = 4.dp
private val rulerTrackHeight = 60.dp
private val rulerTickBand = 42.dp
