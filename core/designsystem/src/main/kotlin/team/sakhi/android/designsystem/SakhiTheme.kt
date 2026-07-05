package team.sakhi.android.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.design.DesignTokens
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.CyclePhase
import team.sakhi.design.PhaseVisualStyle

/**
 * Brand colors resolved once from KMM `DesignTokens`, per mode. Feature code should
 * prefer `MaterialTheme.colorScheme` for standard roles and `LocalSakhiPhaseColor`
 * (see below) for phase-driven surfaces; this object exists for the handful of
 * Sakhi-specific brand roles Material3's scheme has no slot for.
 *
 * Font family: the plan document's Section 4 says "General Sans (FontShare)", but
 * that is stale — the real, shipping iOS app actually uses **Lato**
 * (`SakhiApp/Resources/Lato-{Bold,Regular,Light}.ttf`, referenced via `.lato(...)`
 * throughout the Swift source, e.g. `CountryPickerSheet.swift`). Since "the iOS app
 * is the exact visual and behavioural spec" is the hard rule, the real shipped font
 * wins over the plan's description. The exact same 3 `.ttf` files were copied
 * verbatim into `:core:designsystem/src/main/res/font/` (Lato is SIL Open Font
 * Licensed, freely redistributable).
 */
private val SakhiFontFamily = FontFamily(
    Font(R.font.lato_light, weight = FontWeight.Light),
    Font(R.font.lato_regular, weight = FontWeight.Normal),
    Font(R.font.lato_bold, weight = FontWeight.Bold),
)
private val LightBrandColors = SakhiBrandColors(
    background = DesignTokens.COLOR_BACKGROUND.toComposeColor(),
    lightPink = DesignTokens.COLOR_LIGHT_PINK.toComposeColor(),
    buttonFill = DesignTokens.COLOR_BUTTON_FILL.toComposeColor(),
    pink = DesignTokens.COLOR_PINK.toComposeColor(),
    deepRose = DesignTokens.COLOR_DEEP_ROSE.toComposeColor(),
    confirm = DesignTokens.COLOR_CONFIRM.toComposeColor(),
    error = SakhiUIColors.TOAST_ERROR.toComposeColor(),
)

private val DarkBrandColors = SakhiBrandColors(
    background = SakhiUIColors.BRAND_BG_DARK.toComposeColor(),
    lightPink = SakhiUIColors.BRAND_LIGHT_PINK_DARK.toComposeColor(),
    buttonFill = SakhiUIColors.BRAND_BUTTON_FILL_DARK.toComposeColor(),
    pink = SakhiUIColors.BRAND_PINK.toComposeColor(),
    deepRose = SakhiUIColors.BRAND_DEEP_ROSE_DARK.toComposeColor(),
    confirm = DesignTokens.COLOR_CONFIRM.toComposeColor(),
    error = SakhiUIColors.TOAST_ERROR.toComposeColor(),
)

data class SakhiBrandColors(
    val background: androidx.compose.ui.graphics.Color,
    val lightPink: androidx.compose.ui.graphics.Color,
    val buttonFill: androidx.compose.ui.graphics.Color,
    val pink: androidx.compose.ui.graphics.Color,
    val deepRose: androidx.compose.ui.graphics.Color,
    val confirm: androidx.compose.ui.graphics.Color,
    val error: androidx.compose.ui.graphics.Color,
)

private fun materialColorScheme(brand: SakhiBrandColors, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = brand.pink,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = brand.background,
        surface = brand.lightPink,
        error = brand.error,
    )
} else {
    lightColorScheme(
        primary = brand.pink,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = brand.background,
        surface = brand.lightPink,
        error = brand.error,
    )
}

private fun sakhiTypography(): Typography {
    val default = Typography()
    // Apply the shared font family to every Material3 text style first (so nothing
    // silently stays on Roboto), then override the sizes plan/KMM actually name
    // tokens for. Lato only ships Light/Regular/Bold weights (matching iOS's exact
    // font files), so styles asking for a weight the font lacks fall back to Bold —
    // the same fallback iOS's own `.lato` helper makes.
    val withFontFamily = Typography(
        displayLarge = default.displayLarge.copy(fontFamily = SakhiFontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = SakhiFontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = SakhiFontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = SakhiFontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = SakhiFontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = SakhiFontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = SakhiFontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = SakhiFontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = SakhiFontFamily),
        bodyLarge = default.bodyLarge.copy(fontFamily = SakhiFontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = SakhiFontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = SakhiFontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = SakhiFontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = SakhiFontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = SakhiFontFamily),
    )
    return withFontFamily.copy(
        headlineLarge = withFontFamily.headlineLarge.copy(fontSize = SakhiFontSize.xxxxl, fontWeight = FontWeight.Bold),
        headlineMedium = withFontFamily.headlineMedium.copy(fontSize = SakhiFontSize.xxxl, fontWeight = FontWeight.Bold),
        titleLarge = withFontFamily.titleLarge.copy(fontSize = SakhiFontSize.xxl, fontWeight = FontWeight.Bold),
        bodyLarge = withFontFamily.bodyLarge.copy(fontSize = SakhiFontSize.lg, lineHeight = SakhiFontSize.lg * 1.4f),
        bodyMedium = withFontFamily.bodyMedium.copy(fontSize = SakhiFontSize.base, lineHeight = SakhiFontSize.base * 1.4f),
        bodySmall = withFontFamily.bodySmall.copy(fontSize = SakhiFontSize.sm, lineHeight = SakhiFontSize.sm * 1.4f),
        labelSmall = withFontFamily.labelSmall.copy(fontSize = SakhiFontSize.xs),
    )
}

/** Resolves a phase's primary hex (KMM `PhaseVisualStyle.colorHex`) straight to Compose. */
@Composable
fun phasePrimaryColor(phase: CyclePhase): androidx.compose.ui.graphics.Color =
    PhaseVisualStyle.colorHex(phase).toComposeColor()

@Composable
fun SakhiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val brand = if (darkTheme) DarkBrandColors else LightBrandColors
    MaterialTheme(
        colorScheme = materialColorScheme(brand, darkTheme),
        typography = sakhiTypography(),
        content = content,
    )
}
