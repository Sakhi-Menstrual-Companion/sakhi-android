package team.sakhi.android.feature.auth

import android.content.Context
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
import team.sakhi.state.SessionState
import team.sakhi.sync.OfflineUpgradeMigrator
import team.sakhi.validation.PhoneCountry
import team.sakhi.validation.PhoneValidationResult
import team.sakhi.validation.ValidationRules
import team.sakhi.android.common.toSafeUserMessage
import team.sakhi.android.ui.SakhiAlertManager
import co.touchlab.kermit.Logger

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
    private val appContext: Context,
    /**
     * Present on Android, where the shared Room store exists. Optional so the auth tests
     * (and any platform without a local store) construct this view model unchanged.
     */
    private val offlineUpgradeMigrator: OfflineUpgradeMigrator? = null,
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
                        error = authMessageFor(throwable, onRetry = { sendOtp() }),
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
                        otpError = authMessageFor(throwable, onRetry = { resendOtp() }),
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
            _uiState.value = state.copy(otpError = appContext.getString(R.string.auth_error_code_empty))
            return
        }
        if (!ValidationRules.isValidOtp(filteredOtp)) {
            hapticManager.error()
            _uiState.value = state.copy(
                otpDigits = filteredOtp,
                otpError = appContext.getString(R.string.auth_error_code_empty),
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

        // Captured BEFORE verifying, because verification replaces the session and the
        // offline id is gone the moment it succeeds. Without it there is nothing left to
        // tell us which records need re-attributing.
        val upgradingFromOfflineUserId =
            (authRepository.currentSessionState() as? SessionState.LocalOnlyUser)?.userId

        viewModelScope.launch {
            runCatching {
                authRepository.verifyOtpAndClassify(
                    phone = phone,
                    otp = filteredOtp,
                    classifier = accountClassifier,
                )
            }.onSuccess { result ->
                migrateOfflineRecordsIfUpgrading(upgradingFromOfflineUserId, result.userId)
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
                    otpError = authMessageFor(throwable, onRetry = { verifyOtp(filteredOtp) }),
                    verifiedAuthResult = null,
                )
            }
        }
    }

    fun consumeVerifiedAuthResult() {
        _uiState.value = _uiState.value.copy(verifiedAuthResult = null)
    }

    // `AuthViewModel` is Koin `viewModel`-scoped (retained for the Activity's
    // whole lifetime, not recreated per sign-in attempt), so a real sign-out
    // followed by a return to `PhoneScreen` reuses this same instance with
    // whatever `otpSentTo` was left over from the *previous* successful
    // login -- `consumeVerifiedAuthResult()` clears `verifiedAuthResult` but
    // never touched `otpSentTo`. `PhoneScreen`'s own `otpSentTo != null &&
    // verifiedAuthResult == null` guard then fires on the very first
    // recomposition after sign-out and force-navigates to `OtpScreen` using a
    // phone number from a prior session, as a same-composition-frame state
    // write rather than a real user action -- the real, reproducible cause of
    // the sign-out screen going blank. Called once per fresh entry into the
    // `SignedOut` route (see `SignedOutFlow`).
    fun resetPhoneFlow() {
        _uiState.value = PhoneUiState()
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
        PhoneValidationResult.Empty -> appContext.getString(R.string.auth_error_phone_empty)
        is PhoneValidationResult.WrongLength -> appContext.getString(
            R.string.auth_error_phone_wrong_length,
            validation.expected,
        )
        PhoneValidationResult.InvalidStart -> appContext.getString(R.string.auth_error_phone_invalid_start_india)
        PhoneValidationResult.Valid -> null
    }

    /**
     * Returns the inline message for the field, or null when the failure has been raised
     * as an alert instead.
     *
     * A lost connection is the one auth failure that is not about what she typed, so it
     * does not belong under the phone field next to "that number doesn't look right". It
     * goes to Sakhi's alert sheet with a retry, the same treatment iOS gives it, and the
     * inline error is cleared so she is not told the same thing twice.
     */
    private fun authMessageFor(throwable: Throwable, onRetry: (() -> Unit)? = null): String? {
        if (throwable is AuthError.NetworkError) {
            // Keeps PhoneScreen's send-OTP trace unbroken. That trace reads `uiState.error`,
            // which is deliberately null on this path, so without this line the failure
            // that started all of this would leave no record at all.
            authLog.w { "send/verify FAILED, no connectivity: ${throwable.cause}" }
            SakhiAlertManager.showNoInternet(appContext, onRetry)
            return null
        }
        return when (throwable as? AuthError) {
            AuthError.TooManyAttempts -> appContext.getString(R.string.auth_error_too_many_attempts)
            is AuthError -> throwable.userMessage
            else -> throwable.toSafeUserMessage(appContext, R.string.auth_error_generic)
        }
    }

    /**
     * Moves an offline account's records onto the real one, immediately after the sign-in
     * that created it and before the app starts reading as that account.
     *
     * Local records are keyed by owner id and the repositories only consult the local store
     * for an `offline_` id, so without this every log she made before signing in stays
     * keyed to a user nothing reads any more: not corrupted, just invisible, and never
     * uploaded. For a health app that is her history gone, which is why Android has never
     * shipped the "Create a Sakhi Account" entry point iOS has.
     *
     * A failure is logged and swallowed rather than failing the sign-in. Her records are
     * still on the device untouched -- the migrator never deletes the local copy -- so the
     * recoverable outcome is being signed in with a migration to retry, not being locked
     * out of an account that now exists.
     */
    private suspend fun migrateOfflineRecordsIfUpgrading(offlineUserId: String?, realUserId: String) {
        val migrator = offlineUpgradeMigrator ?: return
        if (offlineUserId == null || offlineUserId == realUserId) return
        migrator.migrate(offlineUserId = offlineUserId, realUserId = realUserId)
            .onSuccess { authLog.i { "offline upgrade migrated: $it" } }
            .onFailure { authLog.e(it) { "offline upgrade FAILED; local records are untouched" } }
    }

    private val authLog = Logger.withTag("SakhiAuth/Phone")
}

private data class ParsedPhoneInput(
    val country: PhoneCountry,
    val localDigits: String,
)
