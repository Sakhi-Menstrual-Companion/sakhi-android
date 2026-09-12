package team.sakhi.android.designsystem

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln

/**
 * The iOS scroll glide, for every scrollable that is not a pager.
 *
 * Pass it as `flingBehavior = rememberSakhiFlingBehavior()` on `verticalScroll`,
 * `horizontalScroll` and `Lazy*` lists. It has to be passed per call site: Compose reads its
 * default fling from `rememberPlatformDefaultFlingBehavior()` with no theme-level override,
 * unlike overscroll (see [SakhiRubberBandOverscrollFactory]).
 *
 * ── What is different about it ──────────────────────────────────────────────────────
 *
 * Android's default fling follows `OverScroller`'s spline: it starts fast and then brakes
 * fairly hard, so a list comes to a stop sooner and more abruptly. A UIScrollView loses the
 * same FRACTION of its speed every millisecond, 0.2% at the normal deceleration rate of
 * 0.998, so it glides further and fades out smoothly instead of braking. That long, even
 * tail is a large part of why iOS scrolling reads as having weight.
 *
 * Compose's `exponentialDecay` is exactly that shape. Its velocity after t seconds is
 * `v0 · e^(-4.2 · m · t)` for a friction multiplier m, where 4.2 is Compose's own base
 * friction. iOS's is `v0 · 0.998^(1000 · t) = v0 · e^(1000 · ln(0.998) · t)`. The two are the
 * same curve when
 *
 *     m = −1000 · ln(0.998) / 4.2 ≈ 0.4767
 *
 * which is [IOS_FRICTION_MULTIPLIER]. A fling at v0 then travels v0 / 2.002 in total, i.e.
 * about half a second's worth of its starting speed, the same projection [SakhiMotion
 * .projectedDistance] uses.
 */
@Composable
fun rememberSakhiFlingBehavior(): FlingBehavior {
    val density = LocalDensity.current
    return remember(density) {
        SakhiFlingBehavior(
            exponentialDecay(
                frictionMultiplier = IOS_FRICTION_MULTIPLIER,
                // Below this the list is moving less than a pixel every few frames, which
                // reads as stopped. Without a threshold an exponential decay never quite
                // reaches zero, and the list would keep "flinging" invisibly for seconds,
                // eating the next tap as a fling-stop.
                absVelocityThreshold = with(density) { STOP_BELOW_DP_PER_SECOND.dp.toPx() },
            ),
        )
    }
}

/** `−1000 · ln(0.998) / 4.2`. See [rememberSakhiFlingBehavior]. */
internal val IOS_FRICTION_MULTIPLIER: Float =
    (-1000.0 * ln(SakhiMotion.IOS_NORMAL_DECELERATION.toDouble()) / COMPOSE_BASE_FRICTION).toFloat()

/** The base friction inside Compose's `FloatExponentialDecaySpec`. */
private const val COMPOSE_BASE_FRICTION = 4.2

private const val STOP_BELOW_DP_PER_SECOND = 20f

/**
 * Compose's own default fling, with the decay swapped. Same structure as
 * `DefaultFlingBehavior`: run the decay, feed each frame's distance to the scrollable, and
 * stop the moment the scrollable could not use it (it hit an edge). The velocity left at
 * that point is returned, which is what the rubber-band overscroll turns into a bounce.
 */
internal class SakhiFlingBehavior(
    private val decay: DecayAnimationSpec<Float>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        // A fixed duration scale of 1, as Compose's default fling uses. Without it the
        // system "animator duration scale" (including accessibility's Remove animations,
        // which sets it to 0) would make every fling finish instantly, i.e. jump.
        withContext(UnscaledMotion) {
            AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity
                if (abs(delta - consumed) > 0.5f) cancelAnimation()
            }
        }
        return velocityLeft
    }
}

private object UnscaledMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}
