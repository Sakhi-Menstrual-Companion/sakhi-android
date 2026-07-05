package team.sakhi.android.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Every color value in the app originates as a hex string in KMM `SakhiColors` /
 * `SakhiUIColors` / `DesignTokens` (team.sakhi.design, SakhiCore commonMain) — the
 * single source of truth shared with iOS. This is the one and only place a hex
 * string becomes a Compose [Color]. Feature code must never hardcode a hex value;
 * pull the KMM constant and call `.toComposeColor()` on it.
 */
fun String.toComposeColor(): Color {
    val hex = removePrefix("#")
    val colorLong = hex.toLong(16)
    return when (hex.length) {
        6 -> Color(0xFF000000L or colorLong)
        8 -> Color(colorLong)
        else -> error("Invalid hex color: $this")
    }
}
