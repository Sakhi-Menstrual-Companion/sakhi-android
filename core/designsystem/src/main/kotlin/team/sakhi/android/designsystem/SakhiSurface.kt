package team.sakhi.android.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp

/**
 * The soft pink ground every "here is what this does" screen stands on.
 *
 * Port of iOS `SoftPinkPageBackground` in `SakhiSurface.swift`, stop for stop: brand
 * `lightPink` at the top, easing to a paler pink at the bottom. The bottom never reaches
 * the page colour, so a white block drawn on top of it reads as a block without needing an
 * outline or a shadow. That is the whole point of the gradient, and it is why the card in
 * [team.sakhi.android.ui.SakhiOnboardingView] carries neither.
 *
 * The middle stop is not decoration. Two stops put the colour change in the middle of the
 * page, right where the heading sits; the 30% stop moves it up above the words so the copy
 * reads on an even ground.
 *
 * Both ends come from the theme, so this is correct in dark mode without a second
 * definition: `lightPink` is `#2C1A22` there and `systemBackground` is `#1C1C1E`, which
 * makes the same gentle fade in the dark that it makes in the light.
 */
@Composable
fun sakhiSoftPinkPageBrush(): Brush {
    val top = sakhiLightPink()
    // iOS: `lightPink.mix(with: systemBackground, by: 0.82)`.
    val bottom = lerp(top, sakhiSystemBackground(), SoftPinkBottomMix)
    return Brush.verticalGradient(
        0f to top,
        SoftPinkMiddleStop to lerp(top, bottom, SoftPinkMiddleMix),
        1f to bottom,
    )
}

private const val SoftPinkBottomMix = 0.82f
private const val SoftPinkMiddleStop = 0.30f
private const val SoftPinkMiddleMix = 0.72f
