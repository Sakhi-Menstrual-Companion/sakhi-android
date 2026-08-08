package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Port of iOS's `DSNavBar` (`SakhiDesignSystem.swift:505`) — the one nav bar every
 * screen should use.
 *
 * iOS composes it from three optional slots and derives everything else:
 * ```
 * HStack(spacing: 0) {
 *     if let onBack  { DSBackButton(action: onBack) }
 *     if let title   { Text(title).font(.lato(17, .bold))
 *                         .frame(maxWidth: .infinity,
 *                                alignment: onBack == nil && onClose == nil ? .leading : .center) }
 *     else           { Spacer() }
 *     if let onClose { DSCloseButton(action: onClose) }
 * }
 * .padding(.horizontal, DS.Spacing.screenHorizontal)  // 24
 * .padding(.top, DS.Spacing.ml)                       // 20
 * .padding(.bottom, DS.Spacing.xs)                    // 8
 * ```
 *
 * The alignment rule is the subtle part and is reproduced exactly: a title **centres**
 * whenever there is a back or close button to balance against, and **left-aligns** when
 * it is alone. That is why a bare title page reads like a heading while a modal reads
 * like a nav bar, from one component.
 *
 * Android previously had no equivalent. Each surface hand-rolled its own header
 * (`DetailSheetScaffold`'s row, `ChatSubscreenHeader`, Chat's own header, Profile's bare
 * `Text`), so the back/close affordance, its metrics and even its presence drifted per
 * screen — Profile had no close button at all despite its own comment saying iOS "relies
 * on the in-header close button".
 *
 * @param onBack leading back chevron. Null hides it.
 * @param onClose trailing close cross. Null hides it.
 * @param title optional inline title; centred when either button is present.
 * @param onGradient use the light-on-dark treatment for both buttons, for nav bars drawn
 *   over a saturated phase background rather than a neutral surface.
 * @param trailing extra trailing content placed before [onClose], for the
 *   `trailingAction` slot iOS's `ProfileSettingsDetailView` exposes (e.g. a sync button).
 */
@Composable
fun SakhiNavBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    title: String? = null,
    onGradient: Boolean = false,
    /**
     * Replaces the [title] slot for the two headers whose "title" is not a string: Chat
     * (logo + name + presence line) and the logging sheet (date over phase name).
     *
     * They previously hand-rolled the whole bar to get that, which is how their buttons
     * ended up with different paddings and sizes from every other sheet's. The content is
     * given [RowScope], so it applies `Modifier.weight(1f)` itself.
     */
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    /**
     * Vertical padding override. Defaults are iOS's `.ml` / `.xs` (20 / 8), which suit
     * the pushed screens that have no drag handle above them.
     *
     * The Logging sheet passes its own: it sits under a grabber, so its 20dp top stacks
     * on the handle's 10dp and leaves 30dp above the date while only 8dp separates the
     * phase line from the divider. iOS's own logging header is 22 / 18 -- weighted the
     * other way -- which is what Karan is asking for here.
     */
    topPadding: Dp = 20.dp,
    bottomPadding: Dp = 8.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // iOS: screenHorizontal (24) / .ml (20) / .xs (8).
            .padding(horizontal = 24.dp)
            .padding(top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (onBack != null) {
            BackButton(onClick = onBack, onGradient = onGradient)
        }

        if (leading != null) {
            leading()
        } else if (title != null) {
            Text(
                text = title,
                // iOS: .lato(17, .bold).
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (onBack == null && onClose == null) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }

        trailing()

        if (onClose != null) {
            CloseButton(onClick = onClose, onGradient = onGradient)
        }
    }
}
