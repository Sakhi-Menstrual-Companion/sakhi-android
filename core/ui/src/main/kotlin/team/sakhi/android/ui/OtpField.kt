package team.sakhi.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

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
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.surface

    LaunchedEffect(autoFocus, filteredValue) {
        if (autoFocus && enabled && filteredValue.isEmpty()) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Box {
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
                                        width = cellWidth,
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
