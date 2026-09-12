package team.sakhi.android.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import team.sakhi.design.SakhiUIColors
import team.sakhi.android.designsystem.toComposeColor

/**
 * Any extra button in a sheet header (a ⋯ menu, a share, an edit), drawn exactly like
 * [CloseButton] so the two sit side by side as a pair: the same 44dp glass circle, the same
 * pink glyph, the same press shrink.
 *
 * Use this rather than a hand-rolled circle, which is how headers end up with buttons of
 * three slightly different sizes. Put it in [SakhiNavBar]'s `trailing` slot.
 */
@Composable
fun NavIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .sakhiPressFeedback(interactionSource, pressedScale = 0.90f)
            .sakhiGlassCircle(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
            modifier = Modifier.size(24.dp),
        )
    }
}
