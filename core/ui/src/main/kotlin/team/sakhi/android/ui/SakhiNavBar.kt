package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * ZStack {
 *     if let title { Text(title).font(.lato(17, .bold))
 *                       .frame(maxWidth: .infinity,
 *                              alignment: onBack == nil && onClose == nil ? .leading : .center) }
 *     HStack(spacing: DS.Spacing.xs) {
 *         if let onBack  { DSBackButton(action: onBack) }
 *         if let onClose { DSCloseButton(action: onClose) }
 *         Spacer(minLength: 0)
 *     }
 * }
 * .padding(.horizontal, DS.Spacing.screenHorizontal)  // 24
 * .padding(.top, DS.Spacing.ml)                       // 20
 * .padding(.bottom, DS.Spacing.xs)                    // 8
 * ```
 *
 * ── The close button lives on the LEFT ──────────────────────────────────────────────
 *
 * On every screen in the app, next to the back button when a screen has both, back first
 * (Karan, 2026-09-18). It used to sit on the right, which meant the way out moved
 * depending on which screen she was on. Changed here rather than per screen, so it applies
 * everywhere at once. `OnboardingShell` used to smuggle its close button into the
 * [leading] slot to get it on the left; it no longer has to.
 *
 * The controls are OVERLAID on the title rather than laid out beside it, so a centred
 * title stays centred on the screen instead of being pushed off centre by whatever is to
 * its left. Same reason iOS uses a ZStack here.
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
 * @param onClose the close cross, in the LEADING slot beside back. Null hides it.
 * @param title optional inline title; centred when either button is present.
 * @param onGradient use the light-on-dark treatment for both buttons, for nav bars drawn
 *   over a saturated phase background rather than a neutral surface.
 * @param trailing extra trailing content at the far end of the bar, for the
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            // iOS: screenHorizontal (24) / .ml (20) / .xs (8).
            .padding(horizontal = 24.dp)
            .padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn first, under the controls, so it keeps the centre of the screen. A `leading`
        // slot replaces the title entirely, and it is laid out in the row below instead.
        if (leading == null && title != null) {
            Text(
                text = title,
                // iOS: .lato(17, .bold).
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (onBack == null && onClose == null) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            // Zero, deliberately. The only gap iOS has here is between back and close, and
            // that one is spelled out below. A row-wide `spacedBy` would also open a gap in
            // front of the `leading` and `trailing` slots, which no screen asked for.
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (onBack != null) {
                BackButton(onClick = onBack, onGradient = onGradient)
            }

            // iOS: DS.Spacing.xs, only when a screen carries both controls.
            if (onBack != null && onClose != null) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            }

            if (onClose != null) {
                CloseButton(onClick = onClose, onGradient = onGradient)
            }

            if (leading != null) {
                // A gap between the controls and a custom leading block, which now sits
                // beside them rather than across the bar from them. Without it the logging
                // sheet's date started flush against the close button.
                if (onBack != null || onClose != null) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                }
                leading()
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }

            trailing()
        }
    }
}
