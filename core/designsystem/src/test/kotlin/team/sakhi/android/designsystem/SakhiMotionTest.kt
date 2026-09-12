package team.sakhi.android.designsystem

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the iOS-to-Compose motion conversion and the rubber-band overscroll logic.
 *
 * The conversions matter because every "feels like iOS" spring in the app is derived from
 * them; a wrong constant here would quietly make the whole app feel different from iOS
 * with nothing visibly broken. The overscroll tests pin the two ways it could go wrong
 * in practice: stealing scroll that a nested parent (a modal sheet) should have had, and
 * being left stretched after a gesture ends.
 */
class SakhiMotionTest {

    // ── Spring conversion ──────────────────────────────────────────────────────────────

    @Test
    fun `response converts to stiffness as the square of angular frequency`() {
        // (2π / 0.3)² = 438.65
        assertEquals(438.65f, SakhiMotion.iosStiffness(0.3f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `iOS calendar sheet spring keeps its stiffness and lands just under critical damping`() {
        val spec = SakhiMotion.sheet<Float>()
        assertEquals(340f, spec.stiffness)
        // 34 / (2 · √340) = 0.9220
        assertEquals(0.9220f, spec.dampingRatio, absoluteTolerance = 0.0005f)
    }

    @Test
    fun `a spring without a damping fraction uses SwiftUI's default`() {
        val spec: SpringSpec<Float> = SakhiMotion.quick()
        assertEquals(0.825f, spec.dampingRatio)
    }

    // ── Scroll glide ───────────────────────────────────────────────────────────────────

    @Test
    fun `the glide decays at UIScrollView's normal rate`() {
        assertEquals(0.4767f, IOS_FRICTION_MULTIPLIER, absoluteTolerance = 0.0005f)
        // At 0.998 per ms a fling travels v0 / 2.002 in total. Asking Compose itself where
        // the decay ends also pins the 4.2 base friction the multiplier was derived from.
        val decay = exponentialDecay<Float>(frictionMultiplier = IOS_FRICTION_MULTIPLIER)
        assertEquals(1000f, decay.calculateTargetValue(0f, 2002f), absoluteTolerance = 1f)
    }

    // ── Projection and rubber band ─────────────────────────────────────────────────────

    @Test
    fun `projection at the normal deceleration rate is about half a second of travel`() {
        assertEquals(499f, SakhiMotion.projectedDistance(1000f), absoluteTolerance = 0.5f)
        assertEquals(-499f, SakhiMotion.projectedDistance(-1000f), absoluteTolerance = 0.5f)
    }

    @Test
    fun `rubber band always moves less than the finger, keeps its sign, and never reaches the edge`() {
        val dimension = 2000f
        var previous = 0f
        for (pull in listOf(10f, 100f, 500f, 2000f, 20_000f)) {
            val shown = SakhiMotion.scrollRubberBand(pull, dimension)
            assertTrue(shown in 0f..pull, "shown $shown for pull $pull")
            assertTrue(shown > previous, "must keep growing with the pull")
            assertTrue(shown < dimension)
            assertEquals(-shown, SakhiMotion.scrollRubberBand(-pull, dimension))
            previous = shown
        }
    }

    @Test
    fun `rubber band is inert before the container has a size`() {
        assertEquals(0f, SakhiMotion.scrollRubberBand(300f, 0f))
    }

    // ── Overscroll effect ──────────────────────────────────────────────────────────────

    private fun effect() = SakhiRubberBandOverscrollEffect().apply {
        containerSize = IntSize(1080, 2000)
    }

    /** A list already at its edge: consumes nothing. */
    private val atEdge: (Offset) -> Offset = { Offset.Zero }

    @Test
    fun `a finger pulling past the edge stretches, and what is drawn is less than the pull`() {
        val e = effect()
        val consumed = e.applyToScroll(Offset(0f, 120f), NestedScrollSource.UserInput, atEdge)

        assertEquals(Offset(0f, 120f), consumed, "the pull is consumed, not passed on")
        assertEquals(120f, e.pullY)
        assertTrue(e.isInProgress)
        assertTrue(e.displayedY() in 1f..119f)
    }

    @Test
    fun `moving back toward the content relaxes the stretch before anything scrolls`() {
        val e = effect()
        e.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput, atEdge)

        var scrolledWith = Offset.Zero
        e.applyToScroll(Offset(0f, -160f), NestedScrollSource.UserInput) { delta ->
            scrolledWith = delta
            delta
        }

        assertEquals(0f, e.pullY, "fully relaxed")
        assertEquals(Offset(0f, -60f), scrolledWith, "only the excess scrolls the content")
        assertTrue(!e.isInProgress)
    }

    @Test
    fun `scroll a nested parent consumes never turns into stretch`() {
        // performScroll includes nested-scroll parents, e.g. a modal sheet dragging down
        // when its list is at the top. If the parent takes it all, nothing is left over.
        val e = effect()
        e.applyToScroll(Offset(0f, 80f), NestedScrollSource.UserInput) { it }
        assertEquals(0f, e.pullY)
    }

    @Test
    fun `a fling running out of room does not stretch by creeping`() {
        val e = effect()
        e.applyToScroll(Offset(0f, 40f), NestedScrollSource.SideEffect, atEdge)
        assertEquals(0f, e.pullY)
    }

    @Test
    fun `letting go while stretched always springs all the way home`() = runBlocking(TestFrameClock()) {
        val e = effect()
        e.applyToScroll(Offset(0f, 300f), NestedScrollSource.UserInput, atEdge)

        var flungContent = false
        e.applyToFling(Velocity(0f, 900f)) { flungContent = true; it }

        assertEquals(0f, e.pullY)
        assertTrue(!e.isInProgress)
        assertTrue(!flungContent, "released past the end, the content itself does not fling")
    }

    @Test
    fun `a fling that hits the edge bounces out and comes back to rest`() = runBlocking(TestFrameClock()) {
        val e = effect()
        var furthest = 0f
        // Every velocity the list could not use is returned, i.e. it hit the edge at speed.
        e.applyToFling(Velocity(0f, 4000f)) { it }
        // Re-run while sampling, to confirm the bounce actually went out before returning.
        val sampler = SakhiRubberBandOverscrollEffect().apply { containerSize = IntSize(1080, 2000) }
        runBlocking(SamplingFrameClock { furthest = maxOf(furthest, abs(sampler.pullY)) }) {
            sampler.applyToFling(Velocity(0f, 4000f)) { it }
        }

        assertEquals(0f, e.pullY)
        assertTrue(furthest > 1f, "the bounce must visibly leave the edge, went $furthest")
    }
}

/** Advances 16ms per frame, so `animate` runs to completion without a real display. */
private open class TestFrameClock : MonotonicFrameClock {
    private var frameTimeNanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        frameTimeNanos += 16_000_000L
        return onFrame(frameTimeNanos)
    }
}

private class SamplingFrameClock(private val afterFrame: () -> Unit) : TestFrameClock() {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        super.withFrameNanos(onFrame).also { afterFrame() }
}
