package team.sakhi.android.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The app's motion, taken from the iOS app's own animation values and converted exactly.
 *
 * ── Why this exists ─────────────────────────────────────────────────────────────────
 *
 * "iOS feels heavy and smooth, Android feels light and jumpy" comes down mostly to two
 * things, and both are about springs rather than looks:
 *
 *  1. iOS animates almost everything with SOFT springs. Sakhi's iOS source uses roughly
 *     thirty `.spring(response:dampingFraction:)` calls, clustered around a 0.3-0.5s
 *     response with ~0.8 damping. Compose and Material default to much stiffer ones
 *     (Material's standard spatial spring is stiffness 700; the iOS calendar sheet's is
 *     340). Lower stiffness is literally what reads as weight: the thing takes a moment
 *     to get going and a moment to settle, instead of snapping.
 *  2. iOS content resists being pulled past its limits instead of stopping dead
 *     (see [scrollRubberBand] and [SakhiRubberBandOverscrollFactory]).
 *
 * Every spring in the Android app that is meant to feel like iOS should come from here,
 * so the two platforms cannot drift apart one hand-tuned number at a time.
 *
 * ── The conversion ──────────────────────────────────────────────────────────────────
 *
 * SwiftUI describes a spring by `response` (the period of the undamped oscillation, in
 * seconds) and `dampingFraction`. Compose describes the same physical spring, at unit
 * mass, by `stiffness` and `dampingRatio`. They are the same spring when
 *
 *     stiffness    = (2π / response)²
 *     dampingRatio = dampingFraction
 *
 * and SwiftUI's `.interpolatingSpring(stiffness:damping:)` (mass 1) maps as
 *
 *     stiffness    = stiffness
 *     dampingRatio = damping / (2 · √stiffness)
 *
 * Springs are unit-agnostic, so iOS points versus Android pixels does not matter to the
 * curve. It only matters to distance thresholds, which are converted as pt → dp.
 */
object SakhiMotion {

    /**
     * SwiftUI's own default for `.spring(response:dampingFraction:)` when the fraction is
     * left out, as in the iOS app's most common call, `.spring(response: 0.3)`.
     */
    const val IOS_DEFAULT_DAMPING_FRACTION = 0.825f

    /** `(2π / response)²`. See the conversion note on [SakhiMotion]. */
    fun iosStiffness(response: Float): Float {
        val angularFrequency = 2f * PI.toFloat() / response
        return angularFrequency * angularFrequency
    }

    /** SwiftUI `.spring(response:dampingFraction:)`, converted exactly. */
    fun <T> iosSpring(
        response: Float,
        dampingFraction: Float = IOS_DEFAULT_DAMPING_FRACTION,
        visibilityThreshold: T? = null,
    ): SpringSpec<T> = spring(
        dampingRatio = dampingFraction,
        stiffness = iosStiffness(response),
        visibilityThreshold = visibilityThreshold,
    )

    /** SwiftUI `.interpolatingSpring(stiffness:damping:)` at its default mass of 1. */
    fun <T> iosInterpolatingSpring(
        stiffness: Float,
        damping: Float,
        visibilityThreshold: T? = null,
    ): SpringSpec<T> = spring(
        dampingRatio = damping / (2f * sqrt(stiffness)),
        stiffness = stiffness,
        visibilityThreshold = visibilityThreshold,
    )

    // ── Named tokens. Each names the iOS call it comes from. ────────────────────────────

    /**
     * iOS `.spring(response: 0.3)`, the single most used spring in the iOS app. Small,
     * quick things: toggles, chips, a thumb moving.
     */
    fun <T> quick(visibilityThreshold: T? = null): SpringSpec<T> =
        iosSpring(response = 0.3f, visibilityThreshold = visibilityThreshold)

    /**
     * iOS `.spring(response: 0.38, dampingFraction: 0.84)`, the most used spring that
     * names both values. The general-purpose "something moved on screen" spring.
     */
    fun <T> standard(visibilityThreshold: T? = null): SpringSpec<T> =
        iosSpring(response = 0.38f, dampingFraction = 0.84f, visibilityThreshold = visibilityThreshold)

    /**
     * iOS `HomeCalendarSheet.spring`, `.interpolatingSpring(stiffness: 340, damping: 34)`.
     * The iOS calendar sheet uses this one spring for EVERY move it makes: present,
     * expand, collapse, dismiss and drag-release. Damping ratio ≈ 0.92, so it lands
     * without a visible bounce.
     */
    fun <T> sheet(visibilityThreshold: T? = null): SpringSpec<T> =
        iosInterpolatingSpring(stiffness = 340f, damping = 34f, visibilityThreshold = visibilityThreshold)

    /**
     * iOS `HomeCalendarSheet.expandSpring`, `.spring(response: 0.58, dampingFraction:
     * 0.90)`. The slowest deliberate spring iOS uses, for large reveals.
     */
    fun <T> slow(visibilityThreshold: T? = null): SpringSpec<T> =
        iosSpring(response = 0.58f, dampingFraction = 0.90f, visibilityThreshold = visibilityThreshold)

    /**
     * The press-in / press-out of a button. iOS `DS.Buttons.Back/Close/Refresh` animate
     * their pressed state with `.easeOut(duration: 0.12)`. The curve is Core Animation's
     * ease-out control points, (0, 0, 0.58, 1).
     */
    fun <T> press(): TweenSpec<T> = tween(durationMillis = 120, easing = IosEaseOut)

    /** Core Animation's `easeOut` timing function. */
    val IosEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** Core Animation's `easeInEaseOut` timing function, i.e. SwiftUI `.easeInOut`. */
    val IosEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    // ── Gesture physics ─────────────────────────────────────────────────────────────────

    /**
     * Where a release would come to rest if it were allowed to glide, as extra distance
     * beyond the release point. This is Apple's projection function from WWDC 2018
     * "Designing Fluid Interfaces" (session 803), at UIScrollView's normal deceleration
     * rate of 0.998 per millisecond:
     *
     *     projected = (velocity / 1000) · rate / (1 − rate)      ≈ velocity · 0.499 s
     *
     * iOS gesture code compares THIS kind of number, not raw velocity, against its
     * thresholds. SwiftUI's `predictedEndTranslation`, which the iOS calendar sheet uses,
     * does not document its own formula; this is the documented UIKit one, so the
     * thresholds ported against it should be checked by feel on a device.
     *
     * @param velocity in any unit per second; the result is in that same unit.
     */
    fun projectedDistance(velocity: Float, decelerationRate: Float = IOS_NORMAL_DECELERATION): Float =
        (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

    /** `UIScrollView.DecelerationRate.normal`. */
    const val IOS_NORMAL_DECELERATION = 0.998f

    /**
     * How far content visibly moves when a finger has pulled it [distance] past its edge,
     * inside a container [dimension] long. Always less than the finger moved, and ever
     * more so the further it goes, approaching but never reaching [dimension].
     *
     *     displayed = (1 − 1 / (|distance| · c / dimension + 1)) · dimension
     *
     * with c = 0.55. Apple does not publish UIScrollView's resistance curve; this is the
     * widely used approximation of it, and it is what gives an over-pulled list that
     * "stretchy rubber" feel instead of either stopping dead or sliding freely.
     */
    fun scrollRubberBand(distance: Float, dimension: Float, coefficient: Float = 0.55f): Float {
        if (distance == 0f || dimension <= 0f) return 0f
        val magnitude = (1f - 1f / (abs(distance) * coefficient / dimension + 1f)) * dimension
        return magnitude * sign(distance)
    }

    /**
     * The spring an over-pulled scroll view returns home on, and the one a fling uses to
     * bounce off an edge. Critically damped so it settles without oscillating, which is
     * how iOS scroll views come back. The 0.4s response is tuned by feel against iOS; it
     * is NOT a value taken from Apple or from the iOS app, which relies on the system
     * scroll view for this.
     */
    fun <T> scrollBounce(visibilityThreshold: T? = null): SpringSpec<T> =
        iosSpring(response = 0.4f, dampingFraction = 1f, visibilityThreshold = visibilityThreshold)
}
