package team.sakhi.android.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Capsule-shaped primary CTA — the iOS `DSButton` primary variant. Same shape
 * (full radius), same brand fill, same disabled-state dimming as iOS; this is the
 * component every feature screen's main action should use, never a raw `Button`.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(SakhiRadius.full),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .height(52.dp)
            .padding(horizontal = SakhiSpacing.space1),
    ) {
        // iOS `DS.Buttons.Primary`: only the LABEL dims, to 0.85, while pressed. The pink
        // capsule stays solid. See sakhiPressFeedback for why buttons need this at all.
        Text(
            text,
            // iOS `DS.Typography.buttonLabel` is `.lato(17, .bold)`. Material's default here
            // was `labelLarge`, 14sp at Medium, which is visibly smaller and lighter than the
            // same button on iOS. 17 has no shared token (the scale goes 16 -> 18), the same
            // gap SakhiNavBar and SakhiAlertSheet already handle with a literal 17sp.
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = PrimaryButtonLabelSize,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.sakhiPressFeedback(interactionSource, pressedAlpha = 0.85f),
        )
    }
}

private val PrimaryButtonLabelSize = 17.sp
