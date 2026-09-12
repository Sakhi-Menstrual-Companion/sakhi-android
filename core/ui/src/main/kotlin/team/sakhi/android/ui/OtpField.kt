package team.sakhi.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/** Matches the shared `SCREEN_TRANSITION_DURATION_MS` every onboarding step-to-step slide uses. */
private const val OTP_AUTO_FOCUS_DELAY_MS = 380L

/**
 * Cell width that keeps **all** [length] cells inside [available], shrinking them evenly
 * rather than letting the last one absorb the whole shortfall.
 *
 * [length] fixed-width cells plus their gaps want `length * cellWidth + (length - 1) *
 * cellSpacing` -- 328dp for the 6x48dp default. A 360dp-wide phone leaves only 312dp inside
 * `OtpScreen`'s 24dp side padding, and `Row` measures fixed-width children in order against
 * whatever width is still unused: cells 1-5 each took their full 48dp and the SIXTH was
 * handed the remainder. That is 32dp on a 360dp phone and exactly **0dp** once the usable
 * width reaches ~320dp -- a narrow device, or any phone with Display Zoom / a larger display
 * size turned on, which is common.
 *
 * A zero-width final cell is the bug Karan reported as not being able to enter the last
 * digit: the value already held the sixth digit and `onComplete` had already fired, but there
 * was no box left on screen for it to appear in. `AuthScreenshotTest` hit the same thing from
 * the other side and pinned itself to a Pixel 5 because "the baseline showed only **five**
 * boxes" at Robolectric's 320dp default -- the capture was right about the screen, so the
 * device got changed instead of this.
 *
 * A no-op on widths that already fit: a 393dp Pixel 5 resolves back to the full 48dp.
 */
internal fun resolveOtpCellWidth(
    available: Dp,
    cellWidth: Dp,
    cellSpacing: Dp,
    length: Int,
): Dp {
    if (length <= 0) return 0.dp
    val roomPerCell = (available - cellSpacing * (length - 1)) / length
    return minOf(cellWidth, roomPerCell).coerceAtLeast(0.dp)
}

/**
 * Single hidden text input rendered as 6 visible cells. The shared auth flow only
 * deals with the full OTP string; this component owns the visual cell treatment.
 */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    length: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    autoFocus: Boolean = false,
    accentColor: Color? = null,
    cellSpacing: Dp = SakhiSpacing.space2,
    cellWidth: Dp = SakhiSpacing.space12,
    cellHeight: Dp = SakhiSpacing.space12 + SakhiSpacing.space3,
    cellCornerRadius: Dp = SakhiRadius.md,
    activeBorderWidth: Dp = SakhiSpacing.space1 / 2,
    inactiveBorderColor: Color = Color.Transparent,
    containerColor: Color? = null,
    onComplete: ((String) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val filteredValue = value.filter(Char::isDigit).take(length)
    val resolvedAccentColor = accentColor ?: MaterialTheme.colorScheme.primary
    // iOS `OTPStep`'s cell fill is `DS.Colors.profileCardBackground` (plain white in
    // light mode), not a tinted surface -- `colorScheme.surface` is bound to the brand
    // `lightPink` in this app's theme, which is why the cells were rendering pink.
    val resolvedContainerColor = containerColor ?: sakhiSystemBackground()

    LaunchedEffect(autoFocus, enabled, filteredValue.isEmpty()) {
        if (autoFocus && enabled && filteredValue.isEmpty()) {
            // iOS `PhoneStep`: "Dismiss immediately before moving to OTP. The shell does
            // not wait, and the OTP field focuses after its screen lands, avoiding
            // keyboard work mid-push." Requesting focus the instant this composes lands
            // mid-flight during the screen's own 380ms slide-in transition -- the IME
            // animating up while the screen is still sliding in is two unsynced motions
            // layered together, reported live as the OTP screen coming in "weird" next
            // to every other onboarding transition. Waiting out the transition first
            // matches iOS's explicit sequencing.
            delay(OTP_AUTO_FOCUS_DELAY_MS)
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
            )
        }

        BasicTextField(
            value = filteredValue,
            onValueChange = { raw ->
                val next = raw.filter(Char::isDigit).take(length)
                onValueChange(next)
                if (next.length == length) {
                    onComplete?.invoke(next)
                }
            },
            enabled = enabled,
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = SakhiFontSize.xl,
                fontWeight = FontWeight.SemiBold,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0f)),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                BoxWithConstraints {
                    // Evenly-shrinking cells so the sixth never collapses to zero width;
                    // see `resolveOtpCellWidth` for the measurement this works around.
                    val resolvedCellWidth = resolveOtpCellWidth(
                        available = maxWidth,
                        cellWidth = cellWidth,
                        cellSpacing = cellSpacing,
                        length = length,
                    )

                    Box(
                        modifier = Modifier
                            .size(SakhiSpacing.space1)
                            .alpha(0f),
                    ) {
                        innerTextField()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                    ) {
                        repeat(length) { index ->
                            val digit = filteredValue.getOrNull(index)?.toString().orEmpty()
                            val isActiveCell = if (filteredValue.length == length) {
                                index == length - 1
                            } else {
                                index == filteredValue.length
                            }
                            val borderColor = when {
                                isError -> MaterialTheme.colorScheme.error
                                isFocused && isActiveCell -> resolvedAccentColor
                                else -> inactiveBorderColor
                            }

                            Box(
                                modifier = Modifier
                                    .size(
                                        width = resolvedCellWidth,
                                        height = cellHeight,
                                    )
                                    .background(
                                        color = resolvedContainerColor,
                                        shape = RoundedCornerShape(cellCornerRadius),
                                    )
                                    .border(
                                        width = activeBorderWidth,
                                        color = borderColor,
                                        shape = RoundedCornerShape(cellCornerRadius),
                                    )
                                    .padding(SakhiSpacing.space1 / 4),
                            ) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = SakhiFontSize.xl,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            },
        )

        if (isError && !errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = SakhiSpacing.space2),
            )
        }
    }
}
