package team.sakhi.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.LocalSakhiDarkTheme

/**
 * The app's switch, matching iOS's `SwitchToggleStyle(tint: DS.Colors.pink)`.
 *
 * Material's default is visibly a different control: when off it draws a small, dark
 * thumb inside an outlined track, and when on it keeps that outline. iOS draws a full
 * white thumb on a plain track with no border in both states, and only the track colour
 * changes. Every settings screen has these, so the difference read across the whole app.
 */
@Composable
fun SakhiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Whether `enabled = false` should also LOOK unavailable.
     *
     * False for the one case a switch is inert rather than unavailable: a permission
     * toggle she taps to grant and can never tap to revoke. That switch is genuinely on,
     * and Material's disabled wash (40% alpha on the track) made the permission she had
     * granted read as the greyed-out row -- the opposite of what it means.
     */
    dimWhenDisabled: Boolean = true,
) {
    // iOS's off-state track is a fixed light grey (`UIColor.systemFill`-ish), not a
    // theme-derived surface -- deriving it here would drift with the pink palette.
    val offTrack = if (LocalSakhiDarkTheme.current) Color(0xFF39393D) else Color(0xFFE9E9EA)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = offTrack,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White,
            disabledCheckedTrackColor = if (dimWhenDisabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = Color.White,
            disabledUncheckedTrackColor = if (dimWhenDisabled) offTrack.copy(alpha = 0.5f) else offTrack,
            disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}
