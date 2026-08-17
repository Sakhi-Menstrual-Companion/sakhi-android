package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiLightPink

/**
 * A circular avatar inside a pulsing dashed ring. Port of iOS
 * `EmergencyRequestedProfile.swift`, itself a port of `RequestedProfile` from `main`.
 *
 * From the original, and matched here exactly:
 * - the avatar clipped to a circle
 * - a dashed ring (3dp, round caps, dash [2, 5]) in the accent colour around it
 * - the ring scales 1.0 to 1.1 on a 0.4s linear loop, and fades 1 to 0 on a 1s ease-in-out
 *   loop, so it reads as "waiting, still going" rather than as a spinner
 *
 * **Sized explicitly**, for the same reason iOS is: the original was a 200pt block with
 * 70pt of padding on the shapes inside it, so the circle's real diameter came out of
 * whatever height was left over.
 *
 * Replaces `EmergencyPulse()` on the waiting screen. That drew expanding rings with no
 * avatar, so the screen never said who was being waited on -- which is the one thing this
 * screen exists to say.
 */
@Composable
internal fun EmergencyRequestedProfile(
    name: String?,
    /**
     * Which face she gets. Pass the helper's id so the map pin and this screen show the
     * same person; without it the avatar falls back to her initial.
     */
    avatarId: String?,
    size: Dp = 128.dp,
    modifier: Modifier = Modifier,
) {
    // The ring sits outside the avatar with room to grow into when it pulses.
    val avatarSize = size * 0.74f
    val accent = MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "requested-profile")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(400, easing = { it }), RepeatMode.Reverse),
        label = "ring-scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "ring-alpha",
    )

    Box(
        // Room for the ring at its largest, so the pulse is never clipped by the frame.
        modifier = modifier.size(size * 1.1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(sakhiLightPink()),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarId != null) {
                Image(
                    painter = painterResource(EmergencyAvatarCatalog.drawableFor(avatarId)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(avatarSize).clip(CircleShape),
                )
            } else {
                Text(
                    text = name?.trim()?.firstOrNull()?.uppercase() ?: "S",
                    style = MaterialTheme.typography.headlineSmall,
                    color = sakhiDeepRose(),
                )
            }
        }

        Canvas(modifier = Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            val diameter = this.size.minDimension * scale
            drawCircle(
                color = accent,
                radius = (diameter - stroke) / 2f,
                alpha = alpha,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(2.dp.toPx(), 5.dp.toPx()),
                        0f,
                    ),
                ),
            )
        }
    }
}
