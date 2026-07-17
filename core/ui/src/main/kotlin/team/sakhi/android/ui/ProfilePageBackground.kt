package team.sakhi.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiColors
import team.sakhi.models.CyclePhase

/**
 * Port of iOS `profileStaticPageBackground()` (`DesignSystem/PhaseBackground.swift`) --
 * the shared background every `ProfileSettingsDetailView`-wrapped screen uses (About,
 * HelpSupport, PrivacySecurity, Legal/ContentPage, Feedback, AppIntegration, Appearance,
 * Notifications, ActivityLog, EditProfile, ManageAccount, and Reports' `ReportConfigSheet`
 * -- confirmed by grepping every real `ProfileSettingsDetailView(...)` call site, which
 * matches this app's `DetailSheetScaffold` callers 1:1 down to Reports, initially
 * mis-read as flat-background-only before re-checking the full file). iOS's real
 * treatment is genuinely asymmetric, not a plain color swap:
 *   - dark mode:  `PhasedGradientBackground(phase: .follicular)` -- a fixed *reference*
 *                 phase gradient (bgTop/bgMid/bgBot), not the user's live current phase.
 *                 Follicular is hardcoded in the Swift source itself, not derived.
 *   - light mode: flat `DS.Colors.background` (the plain background role, not the
 *                 surface/card role `SheetSurface` currently uses in both themes).
 * Kept deliberately separate from `HomeScreen.kt`'s own private
 * `rememberHomePhasePalette`/`homeBackgroundBrush` (which uses the *live* current phase,
 * matching iOS's distinct `phasePageBackground()`/`PhasePageBackgroundWrapper`) rather
 * than merged with it -- same gradient math, genuinely different phase input and a
 * different iOS source function, so keeping them as separate call sites mirrors iOS's
 * own architecture instead of collapsing two distinct concepts into one.
 */
@Composable
fun profilePageBackgroundBrush(): Brush {
    val isDark = LocalSakhiDarkTheme.current
    val background = MaterialTheme.colorScheme.background
    return if (isDark) {
        val bundle = remember(isDark) { SakhiColors.resolved(true).forPhase(CyclePhase.FOLLICULAR) }
        Brush.verticalGradient(
            colors = listOf(
                bundle.bgTop.toComposeColor(),
                bundle.bgMid.toComposeColor(),
                bundle.bgBot.toComposeColor(),
            ),
        )
    } else {
        Brush.verticalGradient(colors = listOf(background, background))
    }
}
