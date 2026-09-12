package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors

/**
 * Port of iOS's `DSCloseButton` **including its button style**, which is where the
 * visual actually lives.
 *
 * The first Android port carried only the glyph and dropped
 * `.buttonStyle(DS.Buttons.Close)`, so it rendered a bare `onSurface` cross where iOS
 * draws a floating circle with a pink `xmark`. That is why the nav bar looked flat next
 * to iOS — the affordance was never a bare icon there.
 *
 * The circle comes from [sakhiGlassCircle]: white fill at 70% opacity with a white
 * stroke, tuned on device so it reads closer to liquid glass than a flat chip.
 *
 * @param onGradient iOS's `onGradient: true` branch, for a saturated phase background:
 *   a white glyph in a 30x30 circle of `white.opacity(0.20)`. Deliberately different
 *   geometry from the default, exactly as iOS has it.
 */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
    contentDescription: String? = null,
    /** Glyph/tint override for the [onGradient] variant on inverted surfaces. */
    onGradientColor: Color = Color.White,
) {
    val description = contentDescription ?: stringResource(R.string.close)
    // iOS `DS.Buttons.Back/Close`: the whole control, circle included, scales to 0.90
    // while pressed, easing out over 120ms.
    val interactionSource = remember { MutableInteractionSource() }
    if (onGradient) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .sakhiPressFeedback(interactionSource, pressedScale = 0.90f)
                .size(30.dp)
                .background(onGradientColor.copy(alpha = 0.20f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = description,
                tint = onGradientColor,
                modifier = Modifier.size(NavGlyphSizeSmall),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .sakhiPressFeedback(interactionSource, pressedScale = 0.90f)
                .sakhiGlassCircle(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = description,
                tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                modifier = Modifier.size(NavGlyphSize),
            )
        }
    }
}

// The circular button keeps its size; only the glyph inside grows. At 14-20dp inside a
// 44dp circle the chevron and cross were swimming in padding and barely legible, which
// is what Karan reported. These are the sizes that actually read at a glance without
// changing the tap target or the circle.
private val NavGlyphSize = 24.dp
private val NavGlyphSizeSmall = 22.dp
