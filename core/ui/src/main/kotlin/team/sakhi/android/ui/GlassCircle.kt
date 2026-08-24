package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.LocalSakhiDarkTheme

/**
 * The floating circular chrome behind [BackButton] and [CloseButton].
 *
 * iOS gets this from `.glassEffect(.regular, in: Circle())` — a real frosted-material
 * blur. Compose has no equivalent, and every attempt to fake it with a flat fill read
 * wrong on a real device:
 *  - filling with `#F4E4EA` (`buttonFill`) made it vanish into the pale pink page — that
 *    token is iOS's pre-26 fallback and sits almost exactly on the page colour;
 *  - adding a 6dp elevation to compensate produced a heavy dark halo, nothing like the
 *    iOS control;
 *  - a pink-tinted fill with a pink hairline border was reviewed live on a real device
 *    and called out as looking "weird" -- the tint plus hairline read as a smudge, not
 *    chrome.
 *
 * Light-mode tuning, per Karan: keep the button as a clean liquid-glass touch, not a flat
 * solid chip. The fill stays white at 70% opacity with a white stroke. Those exact values
 * are preserved below.
 *
 * **Dark mode is not that treatment inverted, and it is not the same values either.** iOS
 * does have a dark branch here and it is dramatic: `GlassCard.swift`'s
 * `_GlassCircleBackground` drops the white tint from `0.55` to `0.07` and the stroke
 * gradient from `0.95 -> 0.25` to `0.22 -> 0.06`. Android had no branch at all, so a
 * near-opaque white disc was drawn on every dark screen -- the back and close buttons
 * were the brightest things in the app, on top of a dark page. iOS's numbers are used
 * directly for the dark side; the stroke stays a real top-to-bottom gradient rather than
 * a flat average, because that vertical falloff is what makes the ring read as a lit edge
 * instead of an outline.
 */
@Composable
internal fun Modifier.sakhiGlassCircle(diameter: Dp): Modifier {
    val dark = LocalSakhiDarkTheme.current
    val fill = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.70f)
    val stroke: Brush = if (dark) {
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f)),
        )
    } else {
        SolidColor(Color.White)
    }
    return this
        .size(diameter)
        .background(fill, CircleShape)
        .border(1.dp, stroke, CircleShape)
}
