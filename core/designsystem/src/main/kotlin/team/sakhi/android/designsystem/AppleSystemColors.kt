package team.sakhi.android.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Apple's dynamic `UIColor.system*` palette, as theme-aware Compose colours.
 *
 * The iOS app reaches for these directly in a handful of places where the meaning is
 * "the platform's red / green / blue", not "a Sakhi brand colour" -- the emergency
 * session's call button (`Color(UIColor.systemBlue)`), its safe-arrival confirmation
 * (`systemGreen`), the feedback sheet's severity tints, and the health-integration cards'
 * sleep and steps icons (`systemIndigo`, `systemGreen`).
 *
 * **These are dynamic on iOS.** Every one of them has a distinct dark-mode value, brighter
 * and slightly desaturated so it holds up on a dark ground. Android had hardcoded the
 * *light* value of each -- and in two cases not even that, but an approximation
 * (`#5C6BC0` for indigo, `#2E9E7E` for green) -- so these read muddy against a dark page
 * and matched nothing on either side.
 *
 * Values are Apple's published sRGB pairs for the standard (non-accessible) contrast
 * setting. They are intentionally NOT in KMM `SakhiUIColors`: that object is Sakhi's own
 * palette, and these belong to the platform. On iOS they come from UIKit for the same
 * reason. Only the ones the app actually uses are defined; add a pair when a real call
 * site needs it, rather than transcribing the whole table.
 */
object AppleSystemColors {

    private val RedLight = Color(0xFFFF3B30)
    private val RedDark = Color(0xFFFF453A)

    private val OrangeLight = Color(0xFFFF9500)
    private val OrangeDark = Color(0xFFFF9F0A)

    private val GreenLight = Color(0xFF34C759)
    private val GreenDark = Color(0xFF30D158)

    private val TealLight = Color(0xFF30B0C7)
    private val TealDark = Color(0xFF40C8E0)

    private val BlueLight = Color(0xFF007AFF)
    private val BlueDark = Color(0xFF0A84FF)

    private val IndigoLight = Color(0xFF5856D6)
    private val IndigoDark = Color(0xFF5E5CE6)

    private val PurpleLight = Color(0xFFAF52DE)
    private val PurpleDark = Color(0xFFBF5AF2)

    val red: Color @Composable get() = pick(RedLight, RedDark)
    val orange: Color @Composable get() = pick(OrangeLight, OrangeDark)
    val green: Color @Composable get() = pick(GreenLight, GreenDark)
    val teal: Color @Composable get() = pick(TealLight, TealDark)
    val blue: Color @Composable get() = pick(BlueLight, BlueDark)
    val indigo: Color @Composable get() = pick(IndigoLight, IndigoDark)
    val purple: Color @Composable get() = pick(PurpleLight, PurpleDark)

    /**
     * Reads [LocalSakhiDarkTheme], the app's own resolved theme, and never
     * `isSystemInDarkTheme()` -- an in-app Light/Dark override has to win here exactly as
     * it does for every other token.
     */
    @Composable
    private fun pick(light: Color, dark: Color): Color =
        if (LocalSakhiDarkTheme.current) dark else light
}
