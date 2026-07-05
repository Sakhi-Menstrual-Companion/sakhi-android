package team.sakhi.android.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import team.sakhi.android.designsystem.SakhiRadius
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.PrimaryButton

/**
 * Phone-entry shell for the signed-out route. It renders only shared auth state
 * from [AuthViewModel]; phone validation, OTP dispatch, and the country/dial-code
 * list ([team.sakhi.validation.PhoneCountry]) all stay in KMM.
 */
@Composable
fun PhoneScreen(
    onOtpSent: (phone: String) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCountryPicker by remember { mutableStateOf(false) }

    uiState.otpSentTo?.takeIf { uiState.verifiedAuthResult == null }?.let { phone ->
        onOtpSent(phone)
        return
    }

    if (showCountryPicker) {
        CountryPicker(
            selectedCountry = uiState.selectedCountry,
            onCountrySelected = { country ->
                viewModel.selectCountry(country)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Text(
            text = "Let's Begin",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Your number stays private. It's just how we keep your account safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PhoneEntryField(
            countryFlag = uiState.selectedCountry.flag,
            dialCode = uiState.selectedCountry.dialCode,
            phoneDigits = uiState.localDigits,
            onCountryTap = { showCountryPicker = true },
            onDigitsChanged = viewModel::onPhoneDigitsChanged,
            hasError = uiState.error != null,
            errorText = uiState.error,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )

        PrimaryButton(
            text = if (uiState.isSendingOtp) "Sending..." else "Continue",
            onClick = viewModel::sendOtp,
            enabled = !uiState.isSendingOtp,
            modifier = Modifier.padding(top = SakhiSpacing.space3),
        )
    }
}

@Composable
private fun PhoneEntryField(
    countryFlag: String,
    dialCode: String,
    phoneDigits: String,
    onCountryTap: () -> Unit,
    onDigitsChanged: (String) -> Unit,
    hasError: Boolean,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SakhiSpacing.space12 + SakhiSpacing.space2)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(SakhiRadius.xl),
                )
                .border(
                    width = if (hasError) SakhiSpacing.space1 / 2 else SakhiSpacing.space1 / 4,
                    color = if (hasError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(SakhiRadius.xl),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onCountryTap)
                    .padding(horizontal = SakhiSpacing.space4)
                    .height(SakhiSpacing.space12 + SakhiSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = countryFlag,
                    fontSize = SakhiFontSize.xl,
                )
                Text(
                    text = dialCode,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Select your country",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Box(
                modifier = Modifier
                    .size(
                        width = SakhiSpacing.space1 / 4,
                        height = SakhiSpacing.space8 - SakhiSpacing.space1,
                    )
                    .background(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            )

            BasicTextField(
                value = phoneDigits,
                onValueChange = onDigitsChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SakhiSpacing.space4),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (phoneDigits.isEmpty()) {
                            Text(
                                text = "7898565431",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (hasError && !errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
