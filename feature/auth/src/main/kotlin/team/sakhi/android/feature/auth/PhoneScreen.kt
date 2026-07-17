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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiRadius
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.KeyboardSafeScaffold
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.ui.rememberSakhiModalSheetState

/**
 * Phone-entry shell for the signed-out route. It renders only shared auth state
 * from [AuthViewModel]; phone validation, OTP dispatch, and the country/dial-code
 * list ([team.sakhi.validation.PhoneCountry]) all stay in KMM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    onOtpSent: (phone: String) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCountryPicker by remember { mutableStateOf(false) }
    var phoneFieldFocusToken by remember { mutableIntStateOf(0) }
    val countryPickerSheetState = rememberSakhiModalSheetState()

    uiState.otpSentTo?.takeIf { uiState.verifiedAuthResult == null }?.let { phone ->
        onOtpSent(phone)
        return
    }

    if (showCountryPicker) {
        SakhiModalSheet(
            onDismissRequest = {
                showCountryPicker = false
                phoneFieldFocusToken += 1
            },
            sheetState = countryPickerSheetState,
        ) {
            CountryPicker(
                selectedCountry = uiState.selectedCountry,
                onCountrySelected = { country ->
                    viewModel.selectCountry(country)
                    showCountryPicker = false
                    phoneFieldFocusToken += 1
                },
                onDismiss = {
                    showCountryPicker = false
                    phoneFieldFocusToken += 1
                },
                asSheet = true,
            )
        }
    }

    KeyboardSafeScaffold(
        body = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = stringResource(R.string.auth_phone_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.auth_phone_subtitle),
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
                    focusRequestToken = phoneFieldFocusToken,
                    modifier = Modifier.padding(top = SakhiSpacing.space6),
                )
            }
        },
        footer = {
            PrimaryButton(
                text = if (uiState.isSendingOtp) {
                    stringResource(R.string.auth_phone_sending)
                } else {
                    stringResource(R.string.auth_phone_continue)
                },
                onClick = viewModel::sendOtp,
                enabled = !uiState.isSendingOtp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
            )
        },
    )
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
    focusRequestToken: Int,
    modifier: Modifier = Modifier,
) {
    val phoneFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focusRequestToken) {
        // Match iOS PhoneStep's initial focusOnAppear and its refocus after the
        // country picker sheet closes so typing can continue without another tap.
        delay(150)
        phoneFieldFocusRequester.requestFocus()
        keyboardController?.show()
    }
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
            val countryCodeLabel = stringResource(R.string.auth_country_code_label)
            val countryCodeValue = stringResource(
                R.string.auth_country_code_value,
                countryFlag,
                dialCode,
            )
            Row(
                modifier = Modifier
                    .clickable(onClick = onCountryTap)
                    .clearAndSetSemantics {
                        contentDescription = countryCodeLabel
                        stateDescription = countryCodeValue
                        role = Role.Button
                    }
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
                    contentDescription = null,
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

            val phoneFieldLabel = stringResource(R.string.auth_phone_field_label)
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
                    .padding(horizontal = SakhiSpacing.space4)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusRequester(phoneFieldFocusRequester)
                    .semantics { contentDescription = phoneFieldLabel },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (phoneDigits.isEmpty() && !isFocused) {
                            Text(
                                text = phoneFieldLabel,
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
