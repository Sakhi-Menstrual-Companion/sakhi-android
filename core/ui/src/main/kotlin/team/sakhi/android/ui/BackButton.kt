package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
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
 * Port of iOS's `DSBackButton` **including `DS.Buttons.Back`**, which is where the
 * visual actually lives.
 *
 * iOS's own doc on that style: "Floating (default): 44x44 glass circle (iOS 26+) or
 * buttonFill circle (iOS 18-)." So the default back affordance is a floating **circle**
 * with a pink `chevron.left` — not the bare glyph Android was drawing. Same omission as
 * [CloseButton]: the icon was ported, the button style was not, so both nav affordances
 * rendered flat.
 *
 * The circle comes from [sakhiGlassCircle]: white fill at 70% opacity with a white
 * stroke, tuned on device so it reads closer to liquid glass than a flat chip.
 *
 * @param onGradient light-on-dark treatment for nav bars over a saturated phase
 *   background, mirroring [CloseButton]'s `onGradient` so `SakhiNavBar` can flip both
 *   affordances with one flag.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onGradient: Boolean = false,
) {
    val description = contentDescription ?: stringResource(R.string.back)
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
                .background(Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = description,
                tint = Color.White,
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
                // iOS `DSBackButton` is `chevron.left` at `.lato(15, .bold)`, not a full
                // arrow glyph.
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
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
