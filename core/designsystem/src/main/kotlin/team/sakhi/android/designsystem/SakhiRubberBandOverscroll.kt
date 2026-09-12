package team.sakhi.android.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * iOS-style overscroll for every scrollable in the app, provided once at the theme root
 * through `LocalOverscrollFactory` (see [SakhiTheme]).
 *
 * Android's default overscroll is a "stretch": the content distorts in place and the list
 * itself never moves past its edge. iOS does the opposite. Pull a list past its end and
 * the content follows your finger with growing resistance, showing what is behind it,
 * then springs back when you let go; fling into an end and the content overshoots and
 * bounces back. That behaviour is most of what people mean when they say iOS scrolling
 * feels heavy and physical, and it is the iOS app's behaviour on every screen, because
 * there it comes free from the system scroll view.
 *
 * Covered automatically: every `verticalScroll`/`horizontalScroll`, every `Lazy*` list and
 * grid, and pagers. A call site that passes its own `overscrollEffect` (or `null`) keeps
 * that instead.
 *
 * Nested scrolling still works exactly as before, because this effect only ever gets
 * the delta and velocity that NOTHING else wanted. `performScroll` and `performFling`
 * already include the nested-scroll passes to parents, so a list inside a modal sheet
 * still drags the sheet down when pulled at its top; only a pull nobody consumed turns
 * into rubber band. That is the same contract Android's own stretch effect relies on.
 */
internal object SakhiRubberBandOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = SakhiRubberBandOverscrollEffect()
    override fun hashCode(): Int = javaClass.hashCode()
    override fun equals(other: Any?): Boolean = other === this
}

internal class SakhiRubberBandOverscrollEffect : OverscrollEffect {
    /**
     * How far the finger has travelled past the edge, per axis, in pixels, signed in the
     * direction the content is displaced. This is FINGER distance, not what is drawn:
     * the drawn offset is [displayedX]/[displayedY], which runs it through the rubber
     * band. Keeping the raw distance is what lets pulling back toward the content retrace
     * exactly the same curve it went out on.
     */
    internal var pullX by mutableFloatStateOf(0f)
        private set
    internal var pullY by mutableFloatStateOf(0f)
        private set

    /** Written by [RubberBandNode] on every measure; the rubber band is relative to it. */
    internal var containerSize: IntSize = IntSize.Zero

    internal fun displayedX(): Float = SakhiMotion.scrollRubberBand(pullX, containerSize.width.toFloat())
    internal fun displayedY(): Float = SakhiMotion.scrollRubberBand(pullY, containerSize.height.toFloat())

    override val isInProgress: Boolean
        get() = pullX != 0f || pullY != 0f

    override val node: DelegatableNode = RubberBandNode(this)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // 1. A stretched list relaxes BEFORE it scrolls. Moving the finger back toward the
        //    content first gives back the pull, and only what is left over scrolls.
        val relaxX = relaxAmount(pullX, delta.x)
        val relaxY = relaxAmount(pullY, delta.y)
        pullX += relaxX
        pullY += relaxY
        val consumedByRelax = Offset(relaxX, relaxY)

        // 2. The real scroll, including every nested-scroll parent.
        val remaining = delta - consumedByRelax
        val consumedByScroll = performScroll(remaining)
        val leftover = remaining - consumedByScroll

        // 3. Only a FINGER turns leftover into pull. A fling that runs out of room arrives
        //    here too (as SideEffect) but is handled by velocity in [applyToFling], which is
        //    what makes it bounce rather than creep.
        if (source == NestedScrollSource.UserInput && leftover != Offset.Zero) {
            pullX += leftover.x
            pullY += leftover.y
            return delta
        }
        return consumedByRelax + consumedByScroll
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (isInProgress) {
            // Let go while stretched: spring home, carrying the release velocity so it does
            // not start from a standstill. The content itself does not fling, which is what
            // an iOS scroll view does when released past its end.
            coroutineScope {
                launch { springHome(pullX, velocity.x) { pullX = it } }
                launch { springHome(pullY, velocity.y) { pullY = it } }
            }
            return
        }
        // Normal fling. Whatever velocity is left when it hits an edge is the bounce.
        val remaining = performFling(velocity)
        coroutineScope {
            if (remaining.x != 0f) launch { springHome(0f, remaining.x) { pullX = it } }
            if (remaining.y != 0f) launch { springHome(0f, remaining.y) { pullY = it } }
        }
    }

    /**
     * Springs a pull back to zero. Started from zero with a velocity, the same spring
     * first carries the content OUT past the edge and then brings it home, which is the
     * bounce; the rubber band in the drawn offset keeps a very hard fling from throwing
     * the content absurdly far.
     */
    private suspend fun springHome(from: Float, velocity: Float, write: (Float) -> Unit) {
        if (from == 0f && velocity == 0f) return
        animate(
            initialValue = from,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = SakhiMotion.scrollBounce(visibilityThreshold = 0.5f),
        ) { value, _ -> write(value) }
        write(0f)
    }

    /**
     * How much of [delta] goes to un-stretching, as a signed amount to ADD to [pull]. Zero
     * unless the finger is moving back toward the content, and never more than the pull
     * itself, so it cannot overshoot past zero into the opposite edge.
     */
    private fun relaxAmount(pull: Float, delta: Float): Float = when {
        pull > 0f && delta < 0f -> maxOf(delta, -pull)
        pull < 0f && delta > 0f -> minOf(delta, -pull)
        else -> 0f
    }
}

/**
 * Draws the content displaced by the effect. The offset is read only inside the layer
 * block, so a pull or a bounce updates a layer property on each frame and never
 * re-measures or recomposes the list.
 */
private class RubberBandNode(
    private val effect: SakhiRubberBandOverscrollEffect,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        effect.containerSize = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = effect.displayedX()
                translationY = effect.displayedY()
            }
        }
    }
}
