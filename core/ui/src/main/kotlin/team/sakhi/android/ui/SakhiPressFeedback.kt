package team.sakhi.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import team.sakhi.android.designsystem.SakhiMotion

/**
 * The iOS pressed state: the thing you are touching dims and/or shrinks while your finger
 * is on it, and eases back when you let go.
 *
 * Why this is needed at all: the theme switches Material's ripple off app-wide
 * (`LocalRippleConfiguration provides null` in `SakhiTheme`), because iOS has no ripple.
 * That was right, but nothing replaced it, so every button in the app gave NO visible
 * response to a touch. A control that does not acknowledge the finger is a large part of
 * what makes an app feel light and unresponsive. iOS always acknowledges it; its design
 * system does so with exactly these two properties:
 *
 *  - `DS.Buttons.Primary`: the label at 0.85 opacity while pressed      → [pressedAlpha]
 *  - `DS.Buttons.Secondary`: the label at 0.6 opacity while pressed     → [pressedAlpha]
 *  - `DS.Buttons.Back/Close/Refresh`: the control at 0.90 scale,
 *    `.easeOut(duration: 0.12)`                                          → [pressedScale]
 *
 * Pass the SAME [interactionSource] the clickable/button uses. Applied as a layer, so a
 * press animates a layer property on each frame and never recomposes or re-measures the
 * button.
 */
fun Modifier.sakhiPressFeedback(
    interactionSource: InteractionSource,
    pressedAlpha: Float = 1f,
    pressedScale: Float = 1f,
): Modifier = this then PressFeedbackElement(interactionSource, pressedAlpha, pressedScale)

private data class PressFeedbackElement(
    val interactionSource: InteractionSource,
    val pressedAlpha: Float,
    val pressedScale: Float,
) : ModifierNodeElement<PressFeedbackNode>() {
    override fun create() = PressFeedbackNode(interactionSource, pressedAlpha, pressedScale)

    override fun update(node: PressFeedbackNode) {
        node.update(interactionSource, pressedAlpha, pressedScale)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sakhiPressFeedback"
        properties["pressedAlpha"] = pressedAlpha
        properties["pressedScale"] = pressedScale
    }
}

private class PressFeedbackNode(
    private var interactionSource: InteractionSource,
    private var pressedAlpha: Float,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {
    /** 0 = at rest, 1 = fully pressed. */
    private val pressProgress = Animatable(0f)
    private var collectJob: Job? = null

    override fun onAttach() = collectPresses()

    fun update(source: InteractionSource, alpha: Float, scale: Float) {
        if (alpha != pressedAlpha || scale != pressedScale) {
            pressedAlpha = alpha
            pressedScale = scale
            invalidatePlacement()
        }
        if (source != interactionSource) {
            interactionSource = source
            if (isAttached) collectPresses()
        }
    }

    private fun collectPresses() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            // A set, not a flag: two fingers can press the same control, and it should
            // only come back up when the last one lifts.
            val activePresses = mutableSetOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> activePresses += interaction
                    is PressInteraction.Release -> activePresses -= interaction.press
                    is PressInteraction.Cancel -> activePresses -= interaction.press
                }
                val target = if (activePresses.isEmpty()) 0f else 1f
                // Animatable's own mutex cancels the previous leg, so a quick tap turns
                // around mid-animation instead of finishing the press-in first.
                launch { pressProgress.animateTo(target, SakhiMotion.press()) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val progress = pressProgress.value
                alpha = 1f - (1f - pressedAlpha) * progress
                val scale = 1f - (1f - pressedScale) * progress
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
