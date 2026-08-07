package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.DesignTokens

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
    if (onGradient) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(30.dp)
                .background(Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.sakhiGlassCircle(44.dp),
        ) {
            Icon(
                // iOS `DSBackButton` is `chevron.left` at `.lato(15, .bold)`, not a full
                // arrow glyph.
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = description,
                tint = DesignTokens.COLOR_PINK.toComposeColor(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
