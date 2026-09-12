package team.sakhi.android.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
    val target = remember(phase, isDark) {
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

    // Every colour animates, so ANYTHING tinted by phase — icons, accents, card fills,
    // strokes — moves together instead of each call site snapping on its own frame. Doing it
    // here rather than at the eleven call sites is what keeps them in step: a phase change is
    // one visual event, not eleven.
    //
    // COST, because it is not free: this returns a NEW palette on every animation frame, so
    // anything reading it recomposes for the duration (~24 frames at 400ms). That is accepted
    // deliberately — a phase change happens on a save or a date change, not continuously, and
    // a transition that recomposes is the entire point. It would not be acceptable on
    // something that changes every frame, which is why the hero's scroll progress is still
    // read in the draw phase instead.
    val primary by animateColorAsState(target.primary, phaseColorSpec(), label = "phase_primary")
    val secondary by animateColorAsState(target.secondary, phaseColorSpec(), label = "phase_secondary")
    val surface by animateColorAsState(target.surface, phaseColorSpec(), label = "phase_surface")
    val bgTop by animateColorAsState(target.bgTop, phaseColorSpec(), label = "phase_bg_top")
    val bgMid by animateColorAsState(target.bgMid, phaseColorSpec(), label = "phase_bg_mid")
    val bgBot by animateColorAsState(target.bgBot, phaseColorSpec(), label = "phase_bg_bot")
    val tileFill by animateColorAsState(target.tileFill, phaseColorSpec(), label = "phase_tile_fill")
    val tileStroke by animateColorAsState(target.tileStroke, phaseColorSpec(), label = "phase_tile_stroke")

    return SakhiPhasePalette(
        primary = primary,
        secondary = secondary,
        surface = surface,
        bgTop = bgTop,
        bgMid = bgMid,
        bgBot = bgBot,
        tileFill = tileFill,
        tileStroke = tileStroke,
    )
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
    // Animated, not swapped. The whole page is this gradient, so changing phase — or simply
    // finishing a load — repainted the entire screen in one frame. That reads as a jolt
    // rather than a change, and it happens on the two moments she is most likely to be
    // looking: right after logging, and when paging to another day.
    //
    // Animating the three stops rather than cross-fading two full brushes means one gradient
    // is ever drawn, so this costs nothing extra per frame. `animateColorAsState` also handles
    // being interrupted mid-flight, which matters when she pages through days quickly: each
    // change continues from the current colour instead of restarting from the old phase.
    // The palette's own stops are already animated (see `rememberPhasePalette`), so these are
    // mid-transition values, not targets. Only the no-data theme colours need animating here,
    // because those come from the Material scheme rather than the phase palette.
    val palette = rememberPhasePalette(phase)
    val noData = !hasCycleData
    val themeTop by animateColorAsState(
        MaterialTheme.colorScheme.background, phaseColorSpec(), label = "page_bg_top",
    )
    val themeBot by animateColorAsState(
        MaterialTheme.colorScheme.surface, phaseColorSpec(), label = "page_bg_bot",
    )

    return if (noData) {
        Brush.verticalGradient(colors = listOf(themeTop, themeBot))
    } else {
        Brush.verticalGradient(colors = listOf(palette.bgTop, palette.bgMid, palette.bgBot))
    }
}

/**
 * Shared timing for every phase-driven colour change, so the background, the accent and
 * anything else tinted by phase move together instead of arriving at different moments.
 *
 * 400ms with a standard ease: long enough to read as a transition rather than a flicker,
 * short enough that it never delays her. Deliberately a tween and not a spring — a spring
 * overshoots, and a colour that overshoots briefly shows a hue that belongs to no phase.
 */
@Composable
fun phaseColorSpec(): AnimationSpec<Color> = SakhiMotion.iosSpring(
    response = 0.5f,
    dampingFraction = 0.88f,
)

/**
 * iOS's `HomeView.homePhaseTransition`, `.spring(response: 0.5, dampingFraction: 0.88,
 * blendDuration: 0.14)`, which is what it applies to `snapshot.displayPhase`, i.e. to every
 * phase-tinted thing on Home at once.
 *
 * This was a 400ms `FastOutSlowInEasing` tween. A tween of a fixed length is the one shape a
 * spring never has: it starts and stops at exactly the same rate every time regardless of how
 * far the colour has to travel, which is what made a phase change read as a timed fade rather
 * than as the screen settling. `blendDuration` has no Compose equivalent and is not needed
 * here: it only matters when a second animation interrupts the first, and Compose's
 * `animateColorAsState` already retargets smoothly from wherever the colour currently is.
 */
@Deprecated("Kept so nothing breaks if a call site still reads it; the spec is a spring now.")
const val PHASE_COLOR_TRANSITION_MS = 400

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
