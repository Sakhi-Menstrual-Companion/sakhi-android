package team.sakhi.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import team.sakhi.android.designsystem.SakhiMotion

/**
 * A label that changes from one reading to the next with a transition instead of a hard cut.
 *
 * ── What this is NOT for: numbers ───────────────────────────────────────────────────
 *
 * iOS animates its figures with `.contentTransition(.numericText())`, which rolls the digits,
 * and this component was first built to match that on Home's countdown. Both that roll and a
 * plain fade were rejected on sight by Karan on 2026-09-12: the roll read as the countdown
 * "flying in from above", and of the fade he said "fade bhi na ho, ekdum se badle, sirf
 * numbers". So the rule for this app is:
 *
 *   a NUMBER changes instantly, with no transition at all
 *   everything around it (prose, colour, layout) transitions
 *
 * That is a deliberate divergence from iOS, and it is Karan's call as the designer. Do not
 * "fix" Home's countdown or its date back to an animated one; they are plain `Text` on
 * purpose.
 *
 * Use this for the wording AROUND a figure: Home's hero sub-line, a status label, a phase
 * name. The spring is iOS's own for that text, `.spring(response: 0.44, dampingFraction:
 * 0.76)`.
 */
@Composable
fun SakhiAnimatedValue(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    /**
     * OFF by default, deliberately.
     *
     * iOS rolls its numbers (`.contentTransition(.numericText())`) and this was first built to
     * match that. On Android it was wrong: at the hero's 68sp the rolling reading reads as the
     * countdown flying in from above rather than as an odometer, and Karan rejected it on
     * sight (2026-09-12: "countdown wala upar se hawa se aaraha hai, vo toh fade se change
     * hona chahiye"). A value changing on this screen should simply fade.
     *
     * The roll is kept behind this flag rather than deleted, because it may still suit a
     * small, fixed-width figure (the logging sheet's weight, say) where the movement is a few
     * pixels rather than a third of the screen. Turn it on ONLY with a look at the result.
     */
    roll: Boolean = false,
    /** iOS's own spring for its hero readings, `.spring(response: 0.44, dampingFraction: 0.76)`. */
    animationSpec: FiniteAnimationSpec<Float> = SakhiMotion.iosSpring(
        response = 0.44f,
        dampingFraction = 0.76f,
    ),
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val rollUp = numericValue(targetState) >= numericValue(initialState)
            val enter = if (roll) {
                slideInVertically(SakhiMotion.iosSpring(0.44f, 0.76f, IntOffsetThreshold)) { height ->
                    if (rollUp) height else -height
                } + fadeIn(animationSpec)
            } else {
                fadeIn(animationSpec)
            }
            val exit = fadeOut(animationSpec)
            // `using null` turns OFF the size animation `ContentTransform` adds by default:
            // these readings sit in fixed rows, and animating the container's width makes the
            // text around them shuffle while the number rolls. Same reason as
            // `sakhiScreenSlide`. Here it is reachable as an infix, because a `transitionSpec`
            // block IS an `AnimatedContentTransitionScope`.
            ((enter togetherWith exit) using null).apply { targetContentZIndex = 1f }
        },
        label = "sakhi_animated_value",
    ) { value ->
        Text(
            text = value,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

/**
 * The first number found in a reading, so "21 Days" and "Day 3 of 28" both compare by the
 * figure the user is actually watching. Anything with no number at all compares as zero, which
 * simply means those changes always roll the same way.
 */
private fun numericValue(text: String): Long =
    NumberPattern.find(text)?.value?.toLongOrNull() ?: 0L

private val NumberPattern = Regex("\\d+")

/** Half a pixel, so the slide settles instead of creeping. */
private val IntOffsetThreshold = androidx.compose.ui.unit.IntOffset(1, 1)
