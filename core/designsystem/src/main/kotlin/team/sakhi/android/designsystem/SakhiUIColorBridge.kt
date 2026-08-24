package team.sakhi.android.designsystem

import androidx.compose.ui.graphics.Color
import team.sakhi.design.DesignTokens
import team.sakhi.design.SakhiColors
import team.sakhi.design.SakhiUIColors

/**
 * Every KMM `SakhiUIColors` token, as a Compose [Color]. The Android counterpart of iOS's
 * `Core/KMM/KMMDesignBridge.swift`.
 *
 * iOS has had this file since the KMM migration, and it is why an iOS feature never types a
 * hex: it reaches for `DS.Colors.activityMood` and KMM owns the value. Android had no such
 * bridge, so features typed hexes by hand -- and they drifted. Real examples found while
 * writing this, all of them shipping:
 *
 *  - the toast capsule was `#1C1C1E`; KMM's `TOAST_DARK_BG` is `#121214`
 *  - toast success was `#34C759`; `TOAST_SUCCESS` is `#38E06B`
 *  - toast error was `#FF3B30`; `TOAST_ERROR` is `#FF5B6B`
 *  - toast warning was `#FF9F0A`; iOS resolves it to `groupD.light.level8` = `#FFB74C`
 *  - toast info was `#FF5A8A`; iOS uses the brand pink `#F61887`
 *  - the profile/activity icon tints were a dozen one-off hexes with no token behind them
 *
 * None of those are dark-mode bugs on their own -- they were wrong in both themes -- but
 * they are the same root cause: no shared vocabulary, so each screen invented its own
 * colour. Anything new belongs in KMM `SakhiUIColors` first and is surfaced here second.
 * Nothing in a `:feature:` module should contain a hex literal.
 *
 * These are flat tokens, not theme-aware pairs. The adaptive brand roles live on
 * `MaterialTheme.colorScheme` and the `sakhi*()` helpers in `SakhiTheme.kt`; the phase
 * palette lives in `SakhiPhasePalette.kt`. This file is only the KMM passthrough.
 */
object SakhiTokens {

    // ── Brand / status ───────────────────────────────────────────────────────
    val Pink: Color get() = SakhiUIColors.BRAND_PINK.toComposeColor()
    val Confirm: Color get() = DesignTokens.COLOR_CONFIRM.toComposeColor()

    // ── Shimmer ──────────────────────────────────────────────────────────────
    val ShimmerBase: Color get() = SakhiUIColors.SHIMMER_BASE.toComposeColor()
    val ShimmerBaseDark: Color get() = SakhiUIColors.SHIMMER_BASE_DARK.toComposeColor()

    // ── AI chat ──────────────────────────────────────────────────────────────
    val AiUserBubble: Color get() = SakhiUIColors.AI_USER_BUBBLE.toComposeColor()
    val AiUserText: Color get() = SakhiUIColors.AI_USER_TEXT.toComposeColor()

    // ── Toast ────────────────────────────────────────────────────────────────
    val ToastError: Color get() = SakhiUIColors.TOAST_ERROR.toComposeColor()
    val ToastSuccess: Color get() = SakhiUIColors.TOAST_SUCCESS.toComposeColor()
    /** iOS resolves this one straight off the hue ramp, not a named constant. */
    val ToastWarning: Color get() = SakhiColors.groupD.light.level8.toComposeColor()
    val ToastDarkCapsule: Color get() = SakhiUIColors.TOAST_DARK_BG.toComposeColor()

    // ── Data categories ──────────────────────────────────────────────────────
    val CategoryPeriod: Color get() = SakhiUIColors.CAT_PERIOD.toComposeColor()
    val CategoryCycles: Color get() = SakhiUIColors.CAT_CYCLES.toComposeColor()
    val CategoryCare: Color get() = SakhiUIColors.CAT_CARE.toComposeColor()
    val CategoryMessages: Color get() = SakhiUIColors.CAT_MESSAGES.toComposeColor()
    val CategoryAi: Color get() = SakhiUIColors.CAT_AI.toComposeColor()

    // ── Activity log ─────────────────────────────────────────────────────────
    val ActivityExternal: Color get() = SakhiUIColors.ACT_EXTERNAL.toComposeColor()
    val ActivityMood: Color get() = SakhiUIColors.ACT_MOOD.toComposeColor()
    val ActivityHealth: Color get() = SakhiUIColors.ACT_HEALTH.toComposeColor()
    val ActivityMedication: Color get() = SakhiUIColors.ACT_MEDICATION.toComposeColor()
    val ActivityNotes: Color get() = SakhiUIColors.ACT_NOTES.toComposeColor()
    val ActivityOther: Color get() = SakhiUIColors.ACT_OTHER.toComposeColor()

    // ── Profile sections ─────────────────────────────────────────────────────
    val SectionRose: Color get() = SakhiUIColors.SECTION_ROSE.toComposeColor()
    val SectionMood: Color get() = SakhiUIColors.SECTION_MOOD.toComposeColor()
    val SectionBlue: Color get() = SakhiUIColors.SECTION_BLUE.toComposeColor()
    val SectionAmber: Color get() = SakhiUIColors.SECTION_AMBER.toComposeColor()
    val SectionGreen: Color get() = SakhiUIColors.SECTION_GREEN.toComposeColor()

    // ── Partner recommendation badges ────────────────────────────────────────
    val BadgeAlert: Color get() = SakhiUIColors.BADGE_ALERT.toComposeColor()
    val BadgeCare: Color get() = SakhiUIColors.BADGE_CARE.toComposeColor()
    val BadgeFood: Color get() = SakhiUIColors.BADGE_FOOD.toComposeColor()
    val BadgeMood: Color get() = SakhiUIColors.BADGE_MOOD.toComposeColor()
    val BadgeSurprise: Color get() = SakhiUIColors.BADGE_SURPRISE.toComposeColor()

    // ── AI places categories ─────────────────────────────────────────────────
    val PlacesHealth: Color get() = SakhiUIColors.PLACES_HEALTH.toComposeColor()
    val PlacesInfo: Color get() = SakhiUIColors.PLACES_INFO.toComposeColor()
    val PlacesWellness: Color get() = SakhiUIColors.PLACES_WELLNESS.toComposeColor()
    val PlacesActivity: Color get() = SakhiUIColors.PLACES_ACTIVITY.toComposeColor()

    // ── Calendar day states ──────────────────────────────────────────────────
    val CalPeriodLight: Color get() = SakhiUIColors.CAL_PERIOD_LIGHT.toComposeColor()
    val CalPeriodDark: Color get() = SakhiUIColors.CAL_PERIOD_DARK.toComposeColor()
    val CalHighEnergy: Color get() = SakhiUIColors.CAL_HIGH_ENERGY.toComposeColor()
    val CalLowEnergy: Color get() = SakhiUIColors.CAL_LOW_ENERGY_DARK.toComposeColor()
    val CalSafeDay: Color get() = SakhiUIColors.CAL_SAFE_DAY.toComposeColor()
    val CalBarDark: Color get() = SakhiUIColors.CAL_BAR_DARK.toComposeColor()

    // ── Calendar sheet phase backgrounds (dark only) ─────────────────────────
    // KMM groups these on their own because they exist only for dark mode: iOS's
    // `HomeCalendarSheet.sheetBackground` is flat `systemBackground` in light and
    // switches between these three in dark. See `calendarSheetBackground()`.
    val CalSheetMenstrualDark: Color get() = SakhiUIColors.CAL_SHEET_MENSTRUAL_DARK.toComposeColor()
    val CalSheetOvulationDark: Color get() = SakhiUIColors.CAL_SHEET_OVULATION_DARK.toComposeColor()
    val CalSheetDefaultDark: Color get() = SakhiUIColors.CAL_SHEET_DEFAULT_DARK.toComposeColor()

    // ── Log permission / PDF / help ──────────────────────────────────────────
    val PermissionSuccess: Color get() = SakhiUIColors.PERMISSION_SUCCESS.toComposeColor()
    val PdfDoctorTeal: Color get() = SakhiUIColors.PDF_DOCTOR_TEAL.toComposeColor()
    val HelpIconBlue: Color get() = SakhiUIColors.HELP_ICON_BLUE.toComposeColor()

    // ── Leave / offboarding reasons ──────────────────────────────────────────
    val LeaveReasonRose: Color get() = SakhiUIColors.LEAVE_REASON_ROSE.toComposeColor()
    val LeaveReasonBlue: Color get() = SakhiUIColors.LEAVE_REASON_BLUE.toComposeColor()
}
