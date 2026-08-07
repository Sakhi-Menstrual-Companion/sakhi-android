package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * Shared text-field shell. Real port of iOS's `SakhiTextField`
 * (`SakhiDesignSystem.swift`/`SakhiTextField.swift`): `HStack(spacing: 0) { prefix();
 * input; suffix() }`, fixed 56pt height, `.dsCard(.pink)` fill (`profileCardBackground`
 * -- plain white in light mode, not a Material outline with no fill), and
 * `.dsErrorBorder`: a 1pt pink border that appears ONLY when `isError`, never a default
 * border. Was a bare Material3 `OutlinedTextField` -- an outlined field has no
 * container fill by design, which is why every screen using this component (search
 * fields, profile name edit, period/cycle length, chat search) rendered with no
 * background at all, reported live as "textfield abhi tak thik kyun nahi hue hain,
 * still transparent hai." No floating label either -- iOS's real field doesn't have
 * one; [label] here is a plain line of text above the field, kept only because the
 * public API already had the parameter, not because any real call site uses it.
 */
@Composable
fun SakhiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingContent: (@Composable (() -> Unit))? = null,
    trailingContent: (@Composable (() -> Unit))? = null,
) {
    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(bottom = SakhiSpacing.space1),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(sakhiSystemBackground(), RoundedCornerShape(SakhiRadius.xl))
                .then(
                    if (isError) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(SakhiRadius.xl))
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.let { leading ->
                Box(modifier = Modifier.padding(start = SakhiSpacing.space4)) { leading() }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SakhiSpacing.space4),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = sakhiTertiaryLabel(),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().then(textFieldModifier),
                )
            }

            trailingContent?.let { trailing ->
                Box(modifier = Modifier.padding(end = SakhiSpacing.space4)) { trailing() }
            }
        }

        if (isError && !errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    start = SakhiSpacing.space4,
                    top = SakhiSpacing.space2,
                ),
            )
        }
    }
}
