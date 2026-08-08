package team.sakhi.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Horizontal attention shake, a direct port of iOS `ShakeEffect` in
 * `ValidationViewModifiers.swift`:
 *
 * ```swift
 * CGAffineTransform(translationX: 10 * sin(shakes * .pi * 2), y: 0)
 * ```
 *
 * where `shakes` animates 0 -> 2 and is reset after 0.3s. Two full sine cycles at a
 * 10pt amplitude, so the view ends exactly where it started.
 *
 * Implemented with `layout` rather than `Modifier.offset` so the shake is a pure
 * placement change and never re-measures the content mid-animation.
 *
 * [trigger] fires the shake on every change to a new non-null value; pass a counter
 * (or any value that changes per failed attempt) so repeated taps re-shake, which a
 * plain `Boolean` would not do.
 */
fun Modifier.sakhiShakeOnError(trigger: Any?): Modifier = composed {
    val shakes = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        shakes.snapTo(0f)
        shakes.animateTo(
            targetValue = ShakeCycles,
            animationSpec = tween(durationMillis = ShakeDurationMillis),
        )
        shakes.snapTo(0f)
    }

    val amplitudePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        ShakeAmplitude.toPx()
    }

    // `offset` with a lambda, so `shakes.value` is read in the LAYOUT phase and only
    // re-places the content -- no recomposition per animation frame.
    offset {
        IntOffset(
            x = (amplitudePx * sin(shakes.value * PI * 2).toFloat()).roundToInt(),
            y = 0,
        )
    }
}

/** iOS `ShakeEffect`: `translationX: 10 * sin(...)`. */
private val ShakeAmplitude = 10.dp

/** iOS animates `shakes` to 2, i.e. two complete sine cycles. */
private const val ShakeCycles = 2f

/** iOS resets `shake` after `.now() + 0.3`. */
private const val ShakeDurationMillis = 300
