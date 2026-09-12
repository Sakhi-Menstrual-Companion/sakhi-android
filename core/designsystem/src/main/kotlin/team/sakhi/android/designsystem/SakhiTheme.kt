package team.sakhi.android.designsystem

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.CyclePhase
import team.sakhi.design.PhaseVisualStyle
import team.sakhi.design.PhaseColorFamily
import team.sakhi.design.SakhiColors

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
    background = SakhiUIColors.BRAND_BG_LIGHT.toComposeColor(),
    lightPink = SakhiUIColors.BRAND_LIGHT_PINK_LIGHT.toComposeColor(),
    buttonFill = SakhiUIColors.BRAND_BUTTON_FILL_LIGHT.toComposeColor(),
    pink = SakhiUIColors.BRAND_PINK.toComposeColor(),
    deepRose = SakhiUIColors.BRAND_DEEP_ROSE_LIGHT.toComposeColor(),
    confirm = SakhiUIColors.BRAND_CONFIRM.toComposeColor(),
    error = SakhiUIColors.TOAST_ERROR.toComposeColor(),
)

private val DarkBrandColors = SakhiBrandColors(
    background = SakhiUIColors.BRAND_BG_DARK.toComposeColor(),
    lightPink = SakhiUIColors.BRAND_LIGHT_PINK_DARK.toComposeColor(),
    buttonFill = SakhiUIColors.BRAND_BUTTON_FILL_DARK.toComposeColor(),
    pink = SakhiUIColors.BRAND_PINK.toComposeColor(),
    deepRose = SakhiUIColors.BRAND_DEEP_ROSE_DARK.toComposeColor(),
    confirm = SakhiUIColors.BRAND_CONFIRM.toComposeColor(),
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

/**
 * Every Material slot the app actually draws with, filled from Sakhi's own values.
 *
 * Only `primary`, `onPrimary`, `background`, `surface` and `error` used to be set. Every
 * other slot fell through to Material's defaults, which are purple-tinted: `onSurface`
 * #1C1B1F, `onSurfaceVariant` #49454F, `surfaceVariant` #E7E0EC, and the outlines to match.
 * Any composable reaching for `colorScheme.onSurface` -- and most do, it is the default for
 * `Text` inside a `Surface` -- was drawing Material's grey-purple where iOS draws a plain
 * label, which is why Android screens read subtly lilac against the same iOS screen.
 *
 * These mirror the `sakhi*()` helpers below one for one, so a composable gets the same
 * colour whether it asks Material or asks Sakhi directly. iOS's names are in the comments.
 */
private fun materialColorScheme(brand: SakhiBrandColors, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = brand.pink,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = brand.background,
        // iOS `DS.Colors.label`
        onBackground = androidx.compose.ui.graphics.Color.White,
        surface = brand.lightPink,
        onSurface = androidx.compose.ui.graphics.Color.White,
        // iOS `DS.Colors.fill` / `secondaryLabel`
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2E),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFEBEBF5).copy(alpha = 0.60f),
        // iOS `DS.Colors.separator`
        outline = androidx.compose.ui.graphics.Color(0xFF545458).copy(alpha = 0.65f),
        outlineVariant = androidx.compose.ui.graphics.Color(0xFF545458).copy(alpha = 0.65f),
        error = brand.error,
    )
} else {
    lightColorScheme(
        primary = brand.pink,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = brand.background,
        onBackground = androidx.compose.ui.graphics.Color.Black,
        surface = brand.lightPink,
        onSurface = androidx.compose.ui.graphics.Color.Black,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.60f),
        outline = androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.36f),
        outlineVariant = androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.36f),
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
        displayLarge = default.displayLarge.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        displayMedium = default.displayMedium.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        displaySmall = default.displaySmall.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        headlineLarge = default.headlineLarge.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        headlineMedium = default.headlineMedium.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        // `letterSpacing = 0.sp` on every style, deliberately.
        //
        // Material3's defaults carry tracking on most styles (0.25sp on bodyMedium,
        // 0.4sp on bodySmall, 0.5sp on the label styles), which is what made Sakhi's
        // copy look spaced out next to iOS. iOS applies NO tracking: a grep of the whole
        // iOS app finds exactly four `.tracking()` calls, all deliberate one-offs (the
        // invite code at 2, a places card at 0.6). Zeroing it here fixes every screen at
        // once rather than per-Text.
        headlineSmall = default.headlineSmall.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        titleLarge = default.titleLarge.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        titleMedium = default.titleMedium.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        titleSmall = default.titleSmall.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        bodyLarge = default.bodyLarge.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        bodyMedium = default.bodyMedium.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        bodySmall = default.bodySmall.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        labelLarge = default.labelLarge.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        labelMedium = default.labelMedium.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
        labelSmall = default.labelSmall.copy(fontFamily = SakhiFontFamily, letterSpacing = 0.sp),
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

/**
 * iOS's `DS.Colors.systemBackground` (`UIColor.systemBackground`): white in light, near
 * black in dark. Distinct from `colorScheme.surface`, which this app maps to brand
 * `lightPink`, and from `surfaceVariant`, which is Material's default lavender.
 *
 * iOS uses it for the cards that must read as plain white sheets — the logging sheet's
 * flow chips and its symptom block — so those needed a token neither of the Material
 * slots provides.
 */
@Composable
fun sakhiSystemBackground(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    } else {
        androidx.compose.ui.graphics.Color.White
    }

/**
 * iOS's `DS.Colors.groupedBackground` (`UIColor.systemGroupedBackground`): #F2F2F7 in
 * light, black in dark.
 *
 * The neutral grey iOS puts *behind* white cards, and inside small inset controls like
 * the report sheet's date-range capsule. Android had been reaching for
 * `colorScheme.surfaceVariant` in those places, which is Material's lavender (#ECE5EE) —
 * visibly purple next to a pink app, and the same default that already had to be driven
 * out of the logging sheet and the quick-log menu.
 */
@Composable
fun sakhiGroupedBackground(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color.Black
    } else {
        androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    }

/**
 * iOS's `DS.Colors.gray5` (`UIColor.systemGray5`): #E5E5EA in light, #2C2C2E in dark.
 *
 * The inert-control grey — iOS fills the chat send button with it while there is nothing
 * to send. Same reason as [sakhiGroupedBackground]: the Material slot that looks closest
 * by name, `surfaceVariant`, is lavender.
 */
@Composable
fun sakhiSystemGray5(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFF2C2C2E)
    } else {
        androidx.compose.ui.graphics.Color(0xFFE5E5EA)
    }

/**
 * iOS's `DS.Colors.gray6` (`UIColor.systemGray6`): #F2F2F7 in light, #1C1C1E in dark.
 *
 * Deliberately **not** [sakhiGroupedBackground], even though the two are the same #F2F2F7 in
 * light. That one models `systemGroupedBackground`, which goes to pure black in dark; this one
 * goes to #1C1C1E. Reusing it would have rendered iOS's small inset circles as black holes on
 * a dark background. iOS reaches for `gray6` on tiny inset controls — the chat report card's
 * dismiss button is the case this was added for.
 */
@Composable
fun sakhiSystemGray6(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    } else {
        androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    }

/**
 * The brand's light pink — `DS.Colors.lightPink` on iOS (#F8E5EC in light, its own dark
 * variant in dark), read straight off the active [SakhiBrandColors] so it tracks the theme.
 *
 * Exposed because iOS uses `lightPink` as a *fill for small elements* (icon badges, the
 * user-side avatar in chat search), not only as a page surface. Feature code previously
 * approximated those with `primary.copy(alpha = …)`, which is a different colour.
 */
/**
 * The brand's icon-button background — `DS.Colors.buttonFill` on iOS (#F4E4EA light).
 *
 * The palette documents it as "Icon button background", which is exactly what the nearby
 * circle in the bottom bar is when there is no map to draw in it. `systemBackground` there
 * is the page's own colour, so the control disappeared into the page.
 */
@Composable
fun sakhiButtonFill(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) DarkBrandColors.buttonFill else LightBrandColors.buttonFill

@Composable
fun sakhiLightPink(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) DarkBrandColors.lightPink else LightBrandColors.lightPink

/**
 * iOS's `DS.Colors.profileCardBackground` — the fill behind every card in the grouped
 * Profile/settings list. **White** in light mode (`UIColor.systemBackground`), white at 6%
 * in dark, where a pure-black card would disappear into the profile gradient.
 *
 * Android was letting these cards fall through to `colorScheme.surface`, which this app
 * maps to brand `lightPink` — so the whole screen was pink cards on a pink page, with the
 * card edges barely visible, where iOS shows crisp white cards on a pale pink page.
 */
@Composable
fun sakhiProfileCardBackground(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)
    } else {
        androidx.compose.ui.graphics.Color.White
    }

/**
 * iOS's `DS.Colors.label` (`UIColor.label`) — full-strength primary ink.
 *
 * Completes the ladder next to [sakhiSecondaryLabel] and [sakhiTertiaryLabel], which were
 * already derived the same way. Material's nearest slot, `onSurface`, is the baseline
 * `#1D1B20` — a dark *purple*-grey rather than iOS's plain black, and this theme never sets
 * it, so every `colorScheme.onSurface` in the app is that baseline.
 *
 * Added because the primary/secondary pair has to move together: migrating secondary text to
 * `sakhiSecondaryLabel()` while primary stayed on Material's slot would leave a single text
 * ladder drawing its two levels from two different colour systems.
 */
@Composable
fun sakhiLabel(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color.White
    } else {
        androidx.compose.ui.graphics.Color.Black
    }

/**
 * iOS's `DS.Colors.secondaryLabel` (`UIColor.secondaryLabel`) — the label ink at 60%.
 *
 * Material's nearest slot, `onSurfaceVariant`, resolves to the baseline `#49454F`: darker
 * than iOS's and faintly purple, so secondary text sat heavier here than on iOS.
 */
@Composable
fun sakhiSecondaryLabel(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFFEBEBF5).copy(alpha = 0.60f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.60f)
    }

/** iOS's `DS.Colors.tertiaryLabel` — the same ink at 30%, for section captions. */
@Composable
fun sakhiTertiaryLabel(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFFEBEBF5).copy(alpha = 0.30f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.30f)
    }

/**
 * iOS `DS.Colors.separator` (`UIColor.separator`) — the hairline between list rows.
 *
 * Distinct from Material's `outlineVariant`, which is a much heavier grey and made the
 * profile list read as ruled paper next to iOS's near-invisible rule.
 */
@Composable
fun sakhiSeparator(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) {
        androidx.compose.ui.graphics.Color(0xFF545458).copy(alpha = 0.65f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF3C3C43).copy(alpha = 0.36f)
    }

/** iOS `DS.Colors.confirm` — the green used for "Synced & secure" and a regular cycle. */
@Composable
fun sakhiConfirm(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) DarkBrandColors.confirm else LightBrandColors.confirm

/**
 * iOS `DS.Colors.warning` — `deepRose` at 65%, NOT an orange.
 *
 * The "Irregular" cycle badge uses it. Android had a hardcoded `#F39C48`, which read as a
 * tan/orange pill where iOS shows a muted rose one.
 */
@Composable
fun sakhiWarning(): androidx.compose.ui.graphics.Color =
    sakhiDeepRose().copy(alpha = 0.65f)

/** iOS `DS.Colors.danger` — plain `deepRose`. */
@Composable
fun sakhiDeepRose(): androidx.compose.ui.graphics.Color =
    if (LocalSakhiDarkTheme.current) DarkBrandColors.deepRose else LightBrandColors.deepRose

/**
 * A phase's primary colour for the theme actually on screen.
 *
 * Light mode is `PhaseVisualStyle.colorHex`, unchanged. Dark mode used to be that same
 * light hex, because `colorHex` can only return one value -- so under the dark theme the
 * calendar's Ask Sakhi bar drew deep maroon text on a near-black pill and all but vanished.
 * It now takes the dark half of the same pair, through the same phase family, which is
 * what iOS's adaptive `phasePalette.primary` resolves to.
 */
@Composable
fun phasePrimaryColor(phase: CyclePhase): androidx.compose.ui.graphics.Color {
    if (!LocalSakhiDarkTheme.current) return PhaseVisualStyle.colorHex(phase).toComposeColor()
    val bundle = when (PhaseVisualStyle.family(phase)) {
        PhaseColorFamily.MENSTRUAL -> SakhiColors.menstrual
        PhaseColorFamily.FOLLICULAR -> SakhiColors.follicular
        PhaseColorFamily.OVULATION -> SakhiColors.ovulation
        PhaseColorFamily.DELAYED -> SakhiColors.delayed
        PhaseColorFamily.UNKNOWN -> SakhiColors.unknown
    }
    return bundle.primary.dark.toComposeColor()
}

/**
 * The app's actual resolved dark/light state, as `MainActivity` computed it from
 * `ThemePreferenceStore` (System/Light/Dark) -- NOT the raw OS setting. Any
 * composable deciding which phase-color variant to draw (`SakhiColors.resolved(isDark)`,
 * calendar marker fills, etc.) must read this, not call `isSystemInDarkTheme()`
 * directly: doing so ignores the user's in-app Light/Dark override and desyncs
 * from `MaterialTheme.colorScheme`, which *does* honor it -- e.g. Home's food-list
 * text silently went near-invisible (light-on-light) when the OS was in light mode
 * but the in-app Theme was set to Dark, since `MaterialTheme.colorScheme.onSurface`
 * correctly went dark-mode-light while the phase background stayed on the
 * OS-detected light palette. Found via a real light/dark on-device comparison.
 */
val LocalSakhiDarkTheme = compositionLocalOf { false }

@Composable
fun SakhiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val brand = if (darkTheme) DarkBrandColors else LightBrandColors
    CompositionLocalProvider(LocalSakhiDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = materialColorScheme(brand, darkTheme),
            typography = sakhiTypography(),
        ) {
            // Compose's default ripple derives its colour from `LocalContentColor`, not
            // a fixed system tone -- on this app's mostly-white cards `LocalContentColor`
            // resolves to a dark `onSurface`/`onBackground` (body text is black-ish), so
            // every tap ripple rendered as a harsh dark/black flash instead of a subtle
            // branded one. Reported live: "jo highlight aa raha hai... black sa aaraha
            // hai." The standard production fix -- explicitly providing a
            // `RippleConfiguration` tied to the brand colour at the theme root -- gives
            // every interactive element in the app a consistent, on-brand pink ripple
            // instead of one that happens to match whatever text colour sits nearby.
            // No tap highlight at all. This was first a black flash (Material's default
            // ripple takes `onSurface`, which is near-black here), then a brand-pink
            // ripple; Karan's call is that the tap should simply work with no highlight,
            // which is also how the iOS app behaves -- it has no ripple concept. A null
            // `RippleConfiguration` switches ripples off for every Material component in
            // one place, rather than each call site passing `indication = null`.
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                // iOS rubber-band overscroll for every scrollable in the app, in place of
                // Android's stretch. See SakhiRubberBandOverscroll.kt for how it keeps
                // nested scrolling (lists inside sheets) working unchanged.
                LocalOverscrollFactory provides SakhiRubberBandOverscrollFactory,
            ) {
            // Material3's `LocalContentColor` defaults to plain black app-wide unless
            // something explicitly provides it -- normally a `Surface`/`Scaffold` does
            // this. This app has neither at the root (each screen paints its own
            // background), so every `Text` without an explicit `color` silently
            // rendered black regardless of theme -- invisible in light mode by
            // coincidence, but genuinely illegible dark-on-dark throughout the app in
            // dark mode. Found via a real light/dark on-device comparison (Home's card
            // titles and stat numbers). This root `Surface` is fully painted over by
            // every screen's own background (verified), so it changes nothing visually
            // except correctly seeding `LocalContentColor` from `colorScheme.onBackground`.
            // The page background itself. iOS paints it per screen via
            // `profileStaticPageBackground()`; drawing it once here reaches every screen
            // that does not paint its own (Profile, Care, Onboarding, Auth, Calendar,
            // Emergency, Recommendations -- i.e. most of the app), and the two screens
            // that DO paint their own (Home's live-phase brush, `DetailSheetScaffold`'s
            // sheet brush) simply cover it, exactly as they cover a flat fill.
            //
            // The `Surface` stays -- it is what seeds `LocalContentColor`, which is the
            // reason this root exists at all -- but it is now transparent, so the brush
            // behind it shows through. A transparent `color` makes Material's
            // `contentColorFor` return `Unspecified`, so `contentColor` is passed
            // explicitly rather than inferred.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sakhiPageBackgroundBrush()),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    content = content,
                )
            }
            }
        }
    }
}
