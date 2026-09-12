package team.sakhi.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 *
 * ── The only divider in the app ─────────────────────────────────────────────────────
 *
 * This is THE divider. Every line between rows, under a header or above an input bar is this
 * component, and the `SakhiDivider` lint check fails the build on any Material divider drawn
 * anywhere else. Karan's rule (2026-09-12): one divider, used everywhere, so that no screen
 * ever grows its own again.
 *
 * Do not wrap it in a private `RowDivider()` / `IndentedDivider()` of your own either; that is
 * how five near-copies accumulated. If a screen needs a particular inset, give that INSET a
 * name (`val EmergencyRowInset = 58.dp`) and pass it here.
 *
 * Lint cannot see a hand-drawn line (`Box(Modifier.height(1.dp).background(...))`), so do not
 * draw one. And do not try `Modifier.height(Dp.Hairline)` on a Box: `Dp.Hairline` is zero, so
 * that draws nothing. Only this component turns Hairline into exactly one pixel.
 *
 * [color] defaults to Profile's separator. Override it only where a surface is deliberately
 * tinted, e.g. a Home card over the phase colour, where a grey rule would sit wrong.
 */
@Composable
fun SakhiListDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    color: Color = sakhiSeparator(),
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = Dp.Hairline,
        color = color,
    )
}
