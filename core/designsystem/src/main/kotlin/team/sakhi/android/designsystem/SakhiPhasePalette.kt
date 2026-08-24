package team.sakhi.android.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import team.sakhi.design.SakhiColors
import team.sakhi.models.CyclePhase

/**
 * The one place phase colour is resolved for the whole app -- the port of iOS's
 * `PhaseColorManager` / `HomePhasePalette`.
 *
 * This lives in `:core:designsystem` for the same reason iOS keeps `PhaseColorManager.swift`
 * and `PhaseBackground.swift` in `DesignSystem/` rather than in `HomeView.swift`: Home is
 * the biggest consumer of phase colour but it is not the only one. The page background
 * behind every screen, the sheets, and the cards all resolve from the same palette there.
 *
 * Android had drifted from that. `HomeScreen.kt` held a private `HomePhasePalette`,
 * `rememberHomePhasePalette`, `homeCardFill` and `homeBackgroundBrush`, and `:core:ui` held
 * a second, separate copy of the same gradient maths for the profile pages -- two
 * implementations of one iOS concept, neither reachable from the other, so a screen outside
 * Home had no way to ask for a phase colour and simply used a flat Material role instead.
 * That is the root cause of the dark-mode drift: not a wrong value anywhere, but that most
 * of the app could not reach the values at all.
 *
 * Everything below is the shared vocabulary. Nothing here is Home-specific.
 */
data class SakhiPhasePalette(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBot: Color,
    val tileFill: Color,
    val tileStroke: Color,
)

/**
 * iOS `PhaseColorManager.homePalette(for:)`.
 *
 * Resolves against [LocalSakhiDarkTheme] -- the app's own resolved theme -- and never
 * `isSystemInDarkTheme()`, so an in-app Light/Dark override cannot desync the phase colours
 * from `MaterialTheme.colorScheme`.
 */
@Composable
fun rememberPhasePalette(phase: CyclePhase): SakhiPhasePalette {
    val isDark = LocalSakhiDarkTheme.current
    return remember(phase, isDark) {
        val resolved = SakhiColors.resolved(isDark).forPhase(
            if (phase == CyclePhase.UNKNOWN) CyclePhase.FOLLICULAR else phase,
        )
        SakhiPhasePalette(
            // iOS `PhaseColorManager.swift`:
            //   `let primary: Color = phase == .menstrual ? .white : c.primary`
            // The menstrual background is a saturated pink, so its raw primary token
            // (#E85787) sits almost on top of the background (#D9406F..#E85787) and every
            // text/icon/accent drawn with it disappears.
            primary = if (phase == CyclePhase.MENSTRUAL) {
                Color.White
            } else {
                resolved.primary.toComposeColor()
            },
            secondary = resolved.secondary.toComposeColor(),
            surface = resolved.surface.toComposeColor(),
            bgTop = resolved.bgTop.toComposeColor(),
            bgMid = resolved.bgMid.toComposeColor(),
            bgBot = resolved.bgBot.toComposeColor(),
            tileFill = resolved.tileFill.toComposeColor(),
            tileStroke = resolved.tileStroke.toComposeColor(),
        )
    }
}

/**
 * iOS `HomeDayDetailGlassView+GlassCard.swift`'s `cardFill` -- the fill behind every
 * phase-tinted card, and (per iOS's own `logFill: cardFill`) the log button that sits with
 * them. Light mode gets a soft 14% tint of the phase tile; dark mode gets the solid tile,
 * because a 14% tint of an already-dark colour on an already-dark page is invisible.
 */
@Composable
fun phaseCardFill(phase: CyclePhase, hasCycleData: Boolean): Color {
    val palette = rememberPhasePalette(phase)
    return when {
        !hasCycleData -> MaterialTheme.colorScheme.surface
        phase == CyclePhase.MENSTRUAL -> palette.surface
        LocalSakhiDarkTheme.current -> palette.tileFill
        else -> palette.tileFill.copy(alpha = 0.14f)
    }
}

/** iOS `cardStroke`, the hairline that goes with [phaseCardFill]. */
@Composable
fun phaseCardStroke(phase: CyclePhase, hasCycleData: Boolean): Color {
    val palette = rememberPhasePalette(phase)
    return when {
        !hasCycleData -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        phase == CyclePhase.MENSTRUAL -> palette.secondary.copy(alpha = 0.30f)
        else -> palette.tileStroke.copy(alpha = 0.50f)
    }
}

/**
 * iOS `phasePageBackground()` / `PhasedGradientBackground(phase:)` -- the aurora gradient
 * behind Home, driven by the user's **live** phase.
 *
 * Unlike [sakhiPageBackgroundBrush] this is a gradient in *both* themes, matching
 * `HomeView.swift`'s own header ("Layer 0 -- PhasedGradientBackground (always full screen,
 * aurora-style)") and its unconditional `PhasedGradientBackground(phase: displayPhase)`.
 */
@Composable
fun phasePageBackgroundBrush(phase: CyclePhase, hasCycleData: Boolean): Brush {
    if (!hasCycleData) {
        return Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
            ),
        )
    }
    val palette = rememberPhasePalette(phase)
    return Brush.verticalGradient(colors = listOf(palette.bgTop, palette.bgMid, palette.bgBot))
}

/**
 * The page background every screen that is *not* Home sits on -- iOS's
 * `profileStaticPageBackground()` (`DesignSystem/PhaseBackground.swift`).
 *
 * iOS's treatment is deliberately asymmetric, not a light/dark colour swap:
 *   - light mode: flat `DS.Colors.background` (#F8F2F4).
 *   - dark mode:  `PhasedGradientBackground(phase: .follicular)` -- a fixed *reference*
 *                 phase gradient. Follicular is hardcoded in the Swift source; it is not
 *                 the user's live phase (that is [phasePageBackgroundBrush], which Home
 *                 uses).
 *
 * This is the most load-bearing dark-mode token in the app. iOS applies one of the two
 * modifiers on essentially every page it draws -- Profile and all its detail screens, Care,
 * every onboarding step, the country picker, the loading and blocked-feature screens, the
 * AI sheets. Android had no equivalent at the root, so all of those fell through to
 * `colorScheme.background`, which in dark is `BRAND_BG_DARK` = pure #000000: a flat black
 * app where iOS shows a soft rose gradient.
 */
@Composable
fun sakhiPageBackgroundBrush(): Brush {
    if (!LocalSakhiDarkTheme.current) return SolidColor(MaterialTheme.colorScheme.background)
    return phasePageBackgroundBrush(phase = CyclePhase.FOLLICULAR, hasCycleData = true)
}

/**
 * iOS `HomeCalendarSheet.sheetBackground` -- the fill behind the Home calendar sheet.
 *
 * Genuinely asymmetric, like the page backgrounds:
 *   - light mode: flat `DS.Colors.systemBackground` (white). It does **not** tint.
 *   - dark mode:  one of three KMM tokens chosen by [phase] -- menstrual `#260310`,
 *                 ovulation `#003236`, everything else `#262628`.
 *
 * [phase] is the **selected day's** phase, not the user's current one. iOS passes
 * `snapshot.displayPhase`, and `snapshot` there is a `HomeSelectedDaySnapshot` -- so
 * tapping a period day in the grid really does re-tint the sheet under it. That is the
 * behaviour this exists for; the three tokens have no other call site on either platform.
 *
 * `UNKNOWN` lands on the default token, which is also where iOS ends up: its
 * `displayPhase` maps unknown to follicular first, and follicular is not one of the two
 * special cases.
 */
@Composable
fun calendarSheetBackground(phase: CyclePhase): Color {
    if (!LocalSakhiDarkTheme.current) return sakhiSystemBackground()
    return when (phase) {
        CyclePhase.MENSTRUAL -> SakhiTokens.CalSheetMenstrualDark
        CyclePhase.OVULATION -> SakhiTokens.CalSheetOvulationDark
        else -> SakhiTokens.CalSheetDefaultDark
    }
}
