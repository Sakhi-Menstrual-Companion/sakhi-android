package team.sakhi.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AccountClassifier
import team.sakhi.auth.AccountState
import team.sakhi.auth.AuthResultWithAccount
import team.sakhi.auth.AuthRepository
import team.sakhi.state.AuthError
import team.sakhi.validation.PhoneCountry
import team.sakhi.validation.PhoneValidationResult
import team.sakhi.validation.ValidationRules

/**
 * Thin auth-state adapter over the shared auth repository and account classifier.
 * Android owns only transient screen state; OTP verification and account-state
 * classification stay in KMM.
 */
data class PhoneUiState(
    val selectedCountry: PhoneCountry = PhoneCountry.india,
    val localDigits: String = "",
    val validation: PhoneValidationResult = PhoneValidationResult.Empty,
    val isSendingOtp: Boolean = false,
    val otpSentTo: String? = null,
    val otpDigits: String = "",
    val isVerifyingOtp: Boolean = false,
    val otpError: String? = null,
    val verifiedAuthResult: AuthResultWithAccount? = null,
    val error: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val accountClassifier: AccountClassifier,
    private val appStateInputBridge: AppStateInputBridge,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    fun onPhoneDigitsChanged(digits: String) {
        val state = _uiState.value
        val parsed = parsePhoneInput(raw = digits, selectedCountry = state.selectedCountry)
        _uiState.value = state.copy(
            selectedCountry = parsed.country,
            localDigits = parsed.localDigits,
            validation = validatePhone(parsed.localDigits, parsed.country),
            error = null,
        )
    }

    fun selectCountry(country: PhoneCountry) {
        val state = _uiState.value
        val sanitizedDigits = state.localDigits.take(country.expectedLocalDigits)
        _uiState.value = state.copy(
            selectedCountry = country,
            localDigits = sanitizedDigits,
            validation = validatePhone(sanitizedDigits, country),
            error = null,
        )
    }

    fun onOtpChanged(otp: String) {
        _uiState.value = _uiState.value.copy(
            otpDigits = otp.filter(Char::isDigit).take(6),
            otpError = null,
            verifiedAuthResult = null,
        )
    }

    fun sendOtp() {
        val state = _uiState.value
        if (state.isSendingOtp) return
        if (state.validation != PhoneValidationResult.Valid) {
            hapticManager.error()
            _uiState.value = state.copy(error = phoneValidationMessage(state.validation))
            return
        }

        _uiState.value = state.copy(
            isSendingOtp = true,
            error = null,
            otpError = null,
            verifiedAuthResult = null,
        )
        viewModelScope.launch {
            val phone = "${state.selectedCountry.dialCode}${state.localDigits}"
            authRepository.sendOtp(phone)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSendingOtp = false,
                        otpSentTo = phone,
                        otpDigits = "",
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSendingOtp = false,
                        error = authMessageFor(throwable),
                    )
                }
        }
    }

    fun resendOtp() {
        val state = _uiState.value
        val phone = state.otpSentTo ?: normalizedPhoneOrNull(state) ?: return
        if (state.isSendingOtp || state.isVerifyingOtp) return

        _uiState.value = state.copy(
            isSendingOtp = true,
            error = null,
            otpError = null,
            otpDigits = "",
            verifiedAuthResult = null,
        )

        viewModelScope.launch {
            authRepository.sendOtp(phone)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSendingOtp = false,
                        otpSentTo = phone,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSendingOtp = false,
                        otpError = authMessageFor(throwable),
                    )
                }
        }
    }

    fun verifyOtp(otp: String = _uiState.value.otpDigits) {
        val state = _uiState.value
        if (state.isVerifyingOtp || state.isSendingOtp) return

        val filteredOtp = otp.filter(Char::isDigit).take(6)
        val phone = state.otpSentTo ?: normalizedPhoneOrNull(state)
        if (phone == null) {
            hapticManager.error()
            _uiState.value = state.copy(otpError = "Code cannot be empty, please enter the 6-digit code we sent you")
            return
        }
        if (!ValidationRules.isValidOtp(filteredOtp)) {
            hapticManager.error()
            _uiState.value = state.copy(
                otpDigits = filteredOtp,
                otpError = "Code cannot be empty, please enter the 6-digit code we sent you",
                verifiedAuthResult = null,
            )
            return
        }

        _uiState.value = state.copy(
            otpDigits = filteredOtp,
            isVerifyingOtp = true,
            otpError = null,
            verifiedAuthResult = null,
        )

        viewModelScope.launch {
            runCatching {
                authRepository.verifyOtpAndClassify(
                    phone = phone,
                    otp = filteredOtp,
                    classifier = accountClassifier,
                )
            }.onSuccess { result ->
                hapticManager.success()
                _uiState.value = _uiState.value.copy(
                    isVerifyingOtp = false,
                    otpDigits = filteredOtp,
                    otpError = null,
                    verifiedAuthResult = result,
                )
                // Android's equivalent of iOS's Supabase auth listener driving
                // AppStateInputBridge (see that class's doc comment) — without this,
                // AppStateStore.appRoute would never leave SignedOut even though
                // verifyOtpAndClassify just succeeded.
                if (result.accountState is AccountState.LocalOnlyComplete) {
                    appStateInputBridge.setLocalOnly(result.userId)
                } else {
                    appStateInputBridge.setAuthenticated(result.userId)
                }
            }.onFailure { throwable ->
                hapticManager.error()
                _uiState.value = _uiState.value.copy(
                    isVerifyingOtp = false,
                    otpError = authMessageFor(throwable),
                    verifiedAuthResult = null,
                )
            }
        }
    }

    fun consumeVerifiedAuthResult() {
        _uiState.value = _uiState.value.copy(verifiedAuthResult = null)
    }

    private fun normalizedPhoneOrNull(state: PhoneUiState): String? {
        return if (state.validation == PhoneValidationResult.Valid) {
            "${state.selectedCountry.dialCode}${state.localDigits}"
        } else {
            null
        }
    }

    private fun validatePhone(
        localDigits: String,
        country: PhoneCountry,
    ): PhoneValidationResult {
        return ValidationRules.validatePhoneLocalDigits(
            localDigits = localDigits,
            countryCode = country.code,
            expectedDigits = country.expectedLocalDigits,
        )
    }

    private fun parsePhoneInput(
        raw: String,
        selectedCountry: PhoneCountry,
    ): ParsedPhoneInput {
        var digits = raw.filter(Char::isDigit)
        val localMax = selectedCountry.expectedLocalDigits

        if (digits.length <= localMax) {
            if (digits.startsWith("0")) digits = digits.drop(1)
            return ParsedPhoneInput(
                country = selectedCountry,
                localDigits = digits.take(localMax),
            )
        }

        val detectedCountry = PhoneCountry.findByDialCodePrefix("+$digits")
        if (detectedCountry != null) {
            val dialDigits = detectedCountry.dialCode.filter(Char::isDigit)
            var local = digits.removePrefix(dialDigits)
            if (local.startsWith("0")) local = local.drop(1)
            return ParsedPhoneInput(
                country = detectedCountry,
                localDigits = local.take(detectedCountry.expectedLocalDigits),
            )
        }

        if (digits.startsWith("0")) digits = digits.drop(1)
        return ParsedPhoneInput(
            country = selectedCountry,
            localDigits = digits.take(localMax),
        )
    }

    private fun phoneValidationMessage(validation: PhoneValidationResult): String? = when (validation) {
        PhoneValidationResult.Empty -> "Please enter your phone number"
        is PhoneValidationResult.WrongLength -> "Enter a valid ${validation.expected}-digit number"
        PhoneValidationResult.InvalidStart -> "Indian numbers start with 6, 7, 8, or 9"
        PhoneValidationResult.Valid -> null
    }

    private fun authMessageFor(throwable: Throwable): String {
        return when (throwable as? AuthError) {
            AuthError.TooManyAttempts -> "Too many attempts, we sent a new code to your number."
            is AuthError -> throwable.userMessage
            else -> throwable.message ?: "Something went wrong. Please try again."
        }
    }
}

private data class ParsedPhoneInput(
    val country: PhoneCountry,
    val localDigits: String,
)
