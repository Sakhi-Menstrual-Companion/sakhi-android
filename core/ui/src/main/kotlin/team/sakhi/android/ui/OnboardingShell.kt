package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.sakhiPageBackgroundBrush

/**
 * The frame every onboarding-shaped screen sits in: safe-area insets, the gap above the
 * header, and the shared [SakhiNavBar] carrying back or close.
 *
 * Extracted from `OnboardingFlowHost`, which is the definition of what these screens look
 * like. Emergency Assistance's own three-page intro used to re-declare the same Column by
 * hand, and had already drifted from it in ways Karan spotted on a device: no top
 * safe-area inset, so its close button sat roughly 24dp higher than every account
 * onboarding step's, and a white page under a white card instead of the pink ground the
 * rest of the flow has. Both flows now render through this, so that cannot happen again.
 *
 * @param showChrome false for a step that owns its own header, or a full-screen loading
 *   state with no way back -- the top gap and nav bar are both suppressed together.
 * @param onBack back chevron in the leading slot. Null on the first step.
 * @param onClose close cross, which shares that same leading slot with back and shows
 *   only when there is nothing to go back to. iOS puts close top-LEFT for this reason.
 * @param paintsPageBackground true for a screen presented over something else, which has
 *   to paint its own opaque ground. False -- the default -- for the flow inside
 *   `RootNavHost`, where the theme root already paints exactly this brush behind it, and
 *   for the Care invite flow, which sits on `SheetSurface`'s own white card.
 */
@Composable
fun OnboardingShell(
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    paintsPageBackground: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The brush is read before the insets padding so the gradient it draws in dark mode
    // spans the whole window, exactly as the theme root's does. Padding first would
    // restart it inside a shorter box and the two would not line up.
    val background = if (paintsPageBackground) {
        Modifier.background(sakhiPageBackgroundBrush())
    } else {
        Modifier
    }

    // `consumeWindowInsets` after the padding matters: Compose does not treat insets as
    // globally consumed just because a parent padded for them, so without this a footer
    // that calls `navigationBarsPadding()` itself would apply the bottom inset twice.
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.safeDrawing),
    ) {
        if (showChrome) {
            // Sits ABOVE the nav bar, not below it. The distance from the status bar down
            // to the step title is the same either way, but placing it here buys the
            // back/close button its own clearance instead of opening a gap between that
            // button and the title. Karan's call after seeing both on a real device.
            Spacer(modifier = Modifier.height(OnboardingHeaderTopGap))

            SakhiNavBar(
                modifier = Modifier.heightIn(min = OnboardingNavBarMinHeight),
                onBack = onBack,
                leading = if (onBack == null && onClose != null) {
                    {
                        CloseButton(onClick = onClose)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                } else {
                    null
                },
            )
        }

        content()
    }
}
