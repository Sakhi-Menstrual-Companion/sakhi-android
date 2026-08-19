package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.emergency.EmergencyStore

/**
 * Port of iOS `EmergencyAskButton`, itself a port of `main`'s `ProgressButtonView`.
 *
 * Three states, exactly as the original had them: "Request Help" before, a filling progress
 * track with the seconds remaining while she waits, and "Request Again" once the window has
 * run out. Android had one plain Button that dismissed the sheet immediately, so the
 * countdown the original showed did not exist here at all.
 *
 * The window is [EmergencyStore.REQUEST_ANSWER_WINDOW_SECONDS] -- 120 seconds, the same
 * `progressButtonTotalTime` the original used, held in shared code so both platforms count
 * the same. It is deliberately far shorter than the 15-minute server-side expiry: the request
 * stays live that long, but she should not have to stand there for fifteen minutes before the
 * app lets her try somebody else.
 */
@Composable
internal fun EmergencyAskButton(
    alreadyAsked: Boolean,
    onAsk: () -> Unit,
    onWindowElapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = EmergencyStore.REQUEST_ANSWER_WINDOW_SECONDS
    var isSending by remember { mutableStateOf(false) }
    var isCounting by remember { mutableStateOf(alreadyAsked) }
    var didElapse by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(if (alreadyAsked) total else 0) }

    LaunchedEffect(isCounting) {
        if (!isCounting) return@LaunchedEffect
        while (remaining > 1) {
            delay(1_000)
            remaining -= 1
        }
        delay(1_000)
        // Window over. She goes back to the list so she can pick someone else; the request
        // itself stays live server-side until it expires.
        isCounting = false
        didElapse = true
        onWindowElapsed()
    }

    val progress = if (total > 0) (total - remaining).toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1_000, easing = { it }),
        label = "ask-progress",
    )

    val label = when {
        isSending -> stringResource(R.string.emergency_sending_request)
        isCounting -> stringResource(R.string.emergency_waiting_for_answer)
        didElapse -> stringResource(R.string.emergency_request_again)
        else -> stringResource(R.string.emergency_request_help)
    }

    val accent = MaterialTheme.colorScheme.primary
    val pale = sakhiLightPink()

    // One button in every state, with the countdown filling it rather than replacing it.
    //
    // The wait used to swap in a separate progress bar: different shape, different colours,
    // the seconds alone in the middle. Tapping Request Help made the button vanish and
    // something else appear in its place, which reads as the screen changing rather than as
    // the thing she just pressed now working. So the geometry never changes -- same rounded
    // rect, same height, same label position; only the fill sweeps across it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            // Counting is not a tap target: the request is already out to her.
            .clickable(enabled = !isSending && !isCounting) {
                isSending = true
                onAsk()
                isSending = false
                remaining = total
                didElapse = false
                isCounting = true
            },
    ) {
        // The unfilled ground. Pale while counting so the fill has somewhere to go, solid
        // otherwise so it looks like the button it is.
        Surface(color = if (isCounting) pale else accent, modifier = Modifier.fillMaxSize()) {}
        AskButtonContent(
            label = label,
            seconds = if (isCounting) remaining else null,
            isSending = isSending,
            color = if (isCounting) accent else Color.White,
        )

        if (isCounting) {
            // The same content again, in white, revealed only as far as the fill has
            // reached. Two full-width copies clipped at the same point, so the label
            // recolours letter by letter as the accent passes under it rather than sitting
            // in one colour over both halves.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * animatedProgress) { this@drawWithContent.drawContent() }
                    },
            ) {
                Surface(color = accent, modifier = Modifier.fillMaxSize()) {}
                AskButtonContent(
                    label = label,
                    seconds = remaining,
                    isSending = false,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Identical in both layers -- if these ever differ the clipped copy will not line up with
 * the one underneath and the text will look doubled at the fill edge.
 */
@Composable
private fun AskButtonContent(
    label: String,
    seconds: Int?,
    isSending: Boolean,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = color,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.size(SakhiSpacing.space2))
        }
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = color)
        if (seconds != null) {
            Spacer(modifier = Modifier.size(SakhiSpacing.space2))
            Text(
                text = "${seconds}s",
                // Tabular figures so the seconds do not jitter as digits change width,
                // matching iOS's `.monospacedDigit()`.
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = color.copy(alpha = 0.75f),
            )
        }
    }
}
