package team.sakhi.android.feature.auth

import android.os.SystemClock
import co.touchlab.kermit.Logger
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import team.sakhi.android.designsystem.SakhiRadius
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.KeyboardSafeScaffold
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.ui.rememberSakhiModalSheetState
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Trace logger for the send-OTP path, greppable on its own:
 * `adb logcat -s SakhiAuth/Phone`.
 *
 * Privacy: this screen handles a phone number, which is personal data, so nothing
 * here logs it in full (repo rule: no personal data in logs, crash reports, or
 * notification payloads). Only the dial code, the digit count, and the last two
 * digits are emitted — enough to tell two test accounts apart while debugging, not
 * enough to identify a person from a captured logcat or a pasted bug report.
 */
private val phoneLog = Logger.withTag("SakhiAuth/Phone")

private fun maskedPhone(dialCode: String, localDigits: String): String = when {
    localDigits.isEmpty() -> "$dialCode(empty)"
    localDigits.length <= 2 -> "$dialCode**"
    else -> dialCode + "*".repeat(localDigits.length - 2) + localDigits.takeLast(2)
}

private fun maskedPhone(fullPhone: String): String =
    if (fullPhone.length <= 2) "**" else "*".repeat(fullPhone.length - 2) + fullPhone.takeLast(2)

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
    val countryPickerScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeCountryPicker() {
        countryPickerScope.launch {
            runCatching { countryPickerSheetState.hide() }
            showCountryPicker = false
            phoneFieldFocusToken += 1
        }
    }

    // Send-OTP trace bookkeeping. `sendOtpClickedAt` lets every downstream line
    // report elapsed time since the tap, which is what actually separates a hang
    // from a fast failure. `requestWentInFlight` separates a local validation
    // rejection (never reached the network) from a real backend failure — both
    // surface identically as `uiState.error`, so without this they read the same.
    var sendOtpClickedAt by remember { mutableStateOf<Long?>(null) }
    var requestWentInFlight by remember { mutableStateOf(false) }
    fun sinceClick(): String =
        sendOtpClickedAt?.let { "+${SystemClock.elapsedRealtime() - it}ms" } ?: "no tap recorded"

    // Declared before the `otpSentTo` early-return below so they compose on every
    // pass. If they sat after it, the success transition — the one that matters
    // most — would hit the early `return` and never log at all.
    LaunchedEffect(uiState.isSendingOtp) {
        if (uiState.isSendingOtp) {
            requestWentInFlight = true
            phoneLog.d {
                "[2] in flight (${sinceClick()}): AuthViewModel.sendOtp -> " +
                    "AuthRepository.sendOtp -> KMM SakhiSupabaseClient -> POST /auth/v1/otp"
            }
        }
    }
    // Numbered [4], after the navigation line below, because that is the real
    // emission order: the navigation log runs during recomposition while this
    // effect is only dispatched on the following pass. Verified on the emulator —
    // "[4] leaving…" printed at +727ms and this at +743ms on the same send.
    LaunchedEffect(uiState.otpSentTo) {
        val sentTo = uiState.otpSentTo ?: return@LaunchedEffect
        phoneLog.i { "[4] state confirmed: otpSentTo=${maskedPhone(sentTo)} (${sinceClick()})" }
    }
    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        if (requestWentInFlight) {
            phoneLog.w { "[3] send-OTP FAILED at the backend (${sinceClick()}): $message" }
        } else {
            phoneLog.w { "[2] rejected by KMM phone validation, no network call made: $message" }
        }
    }

    uiState.otpSentTo?.takeIf { uiState.verifiedAuthResult == null }?.let { phone ->
        // Logged in the composition body rather than an effect because the `return`
        // below unmounts this screen immediately — an effect placed here would be
        // disposed before it ran. Fires once or twice at most, for that reason.
        phoneLog.i { "[3] OTP sent, leaving PhoneScreen -> OtpScreen for ${maskedPhone(phone)} (${sinceClick()})" }
        // iOS `PhoneStep.dismissKeyboardBeforeContinue`: the keyboard comes down before
        // the push, not mid-transition -- paired with `OtpField`'s own delayed
        // auto-focus so the OTP field's keyboard only comes up once its screen has
        // landed, instead of two IME animations overlapping the screen slide.
        keyboardController?.hide()
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
                    closeCountryPicker()
                },
                onDismiss = ::closeCountryPicker,
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
                    // iOS `OnboardingFlowView` sticky header: title is `DS.Colors.label`,
                    // subtitle is `DS.Colors.secondaryLabel`.
                    color = sakhiSecondaryLabel(),
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
            // Shared `SakhiFooter` so the primary button sits at the identical Y as
            // every other screen in the app (this screen previously hand-placed its
            // own button, which put "Continue" ~480px higher than every onboarding
            // step -- verified on-device before the fix). `KeyboardSafeScaffold`
            // still owns lifting this above the IME.
            SakhiFooter(
                primaryLabel = if (uiState.isSendingOtp) {
                    stringResource(R.string.auth_phone_sending)
                } else {
                    stringResource(R.string.auth_phone_continue)
                },
                onPrimaryClick = {
                    sendOtpClickedAt = SystemClock.elapsedRealtime()
                    requestWentInFlight = false
                    phoneLog.i {
                        "[1] Send-OTP tapped: phone=" +
                            maskedPhone(uiState.selectedCountry.dialCode, uiState.localDigits) +
                            ", digits=${uiState.localDigits.length}/" +
                            "${uiState.selectedCountry.expectedLocalDigits}" +
                            ", validation=${uiState.validation}" +
                            ", isSendingOtp=${uiState.isSendingOtp}"
                    }
                    viewModel.sendOtp()
                },
                primaryEnabled = !uiState.isSendingOtp,
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
                // A prior pass here misread iOS's `SakhiTextField` as having no
                // background at all -- that was a literal `.background(` text search
                // missing `.dsCard(context)` (`SakhiDesignSystem.swift:231`), a named
                // modifier that applies one. `.dsCard(.pink)` (the default context, and
                // what `PhoneStep` uses) fills `DS.Colors.profileCardBackground` == plain
                // white in light mode == `sakhiSystemBackground()`. Removing the fill
                // entirely was a real regression, not a parity fix.
                .background(
                    color = sakhiSystemBackground(),
                    shape = RoundedCornerShape(SakhiRadius.xl),
                )
                // iOS `dsErrorBorder`: 1pt `DS.Colors.pink` when `hasError`, not the
                // Material error/red Android was drawing here.
                .then(
                    if (hasError) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(SakhiRadius.xl),
                        )
                    } else {
                        Modifier
                    },
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
                                // iOS passes `onboarding.phone.placeholder` -- a sample
                                // number ("7898565431") -- straight into `SakhiTextField`
                                // as the placeholder, so the field shows the expected
                                // shape and length rather than restating the label above
                                // it. Android already had that exact string
                                // (`auth_phone_placeholder_number`) but never referenced
                                // it, showing the generic "Phone number" instead. The
                                // label is kept for the accessibility contentDescription,
                                // where a sample number would read as a real value.
                                text = stringResource(R.string.auth_phone_placeholder_number),
                                style = MaterialTheme.typography.bodyLarge,
                                // iOS `SakhiTextField` renders its placeholder in
                                // `DS.Colors.placeholderText` (`UIColor.placeholderText`),
                                // which is the same #3C3C43 @ 30% as `tertiaryLabel` — not
                                // Material's purple grey at an invented 0.75 alpha.
                                color = sakhiTertiaryLabel(),
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
