package team.sakhi.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiSystemGray5

/** Lightweight shimmer placeholder for loading rows, cards, and hero blocks. */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    height: Dp = SakhiSpacing.space16,
    width: Dp? = null,
    // iOS's inert placeholder grey, not Material's lavender surfaceVariant.
    baseColor: Color = sakhiSystemGray5(),
    /** Overridable so a placeholder can take the shape of the thing it stands in for. */
    shape: Shape = RoundedCornerShape(SakhiRadius.xl),
) {
    val transition = rememberInfiniteTransition(label = "sakhiShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sakhiShimmerAlpha",
    )

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(baseColor.copy(alpha = alpha)),
    )
}
