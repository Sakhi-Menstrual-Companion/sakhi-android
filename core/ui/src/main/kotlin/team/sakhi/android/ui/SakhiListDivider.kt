package team.sakhi.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.sakhiSeparator

/**
 * The hairline between rows of a grouped list, matching iOS.
 *
 * Material's `HorizontalDivider` defaults to 1dp of `outlineVariant`. Two things are
 * wrong with that here. 1dp is a *density-scaled* value, so on a 2.75x screen it is
 * roughly three physical pixels, where iOS draws a single pixel; and `outlineVariant` is
 * a far heavier grey than `UIColor.separator`. Together they made the profile list look
 * ruled. [Dp.Hairline] is the thinnest line the platform can draw -- one physical pixel,
 * whatever the density -- and [sakhiSeparator] is the real iOS colour.
 *
 * [startInset] indents the rule so it begins at the row's text rather than its icon,
 * which is what a grouped iOS list does.
 */
@Composable
fun SakhiListDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = Dp.Hairline,
        color = sakhiSeparator(),
    )
}
