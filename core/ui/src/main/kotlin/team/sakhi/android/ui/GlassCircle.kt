package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Current device tuning, per Karan: keep the button as a clean liquid-glass touch,
 * not a flat solid chip. The fill stays white at 70% opacity with a white stroke.
 */
internal fun Modifier.sakhiGlassCircle(diameter: Dp): Modifier = this
    .size(diameter)
    .background(Color.White.copy(alpha = 0.70f), CircleShape)
    .border(1.dp, Color.White, CircleShape)
