package team.sakhi.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiTertiaryLabel

@Composable
fun ProfileSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        ),
        // iOS: `.foregroundColor(DS.Colors.tertiaryLabel)` -- the faintest of its three
        // label inks. `onSurfaceVariant` is the baseline `#49454F`, much darker and
        // slightly purple, so these captions read as body text rather than as captions.
        color = sakhiTertiaryLabel(),
        modifier = modifier.padding(horizontal = SakhiSpacing.space4),
    )
}
