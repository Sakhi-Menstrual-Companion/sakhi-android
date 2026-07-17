package team.sakhi.android.feature.auth

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AccountClassifier
import team.sakhi.auth.AccountState
import team.sakhi.auth.AuthRepository
import team.sakhi.auth.AuthResultWithAccount
import team.sakhi.validation.PhoneCountry
import team.sakhi.validation.PhoneValidationResult
import team.sakhi.state.AuthError
import team.sakhi.state.SessionState

/**
 * State-machine test for `AuthViewModel`, the Android adapter around the shared
 * auth/session logic. This proves the Android-owned transient screen state:
 * phone parsing/validation, OTP send/resend transitions, verify success/failure,
 * and the post-sign-out `resetPhoneFlow()` regression guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockContext(): Context = mockk {
        every { getString(R.string.auth_error_code_empty) } returns "Code cannot be empty, please enter the 6-digit code we sent you"
        every { getString(R.string.auth_error_phone_empty) } returns "Please enter your phone number"
        every { getString(R.string.auth_error_phone_wrong_length, 10) } returns "Enter a valid 10-digit number"
        every { getString(R.string.auth_error_phone_wrong_length, 9) } returns "Enter a valid 9-digit number"
        every { getString(R.string.auth_error_phone_invalid_start_india) } returns "Indian numbers start with 6, 7, 8, or 9"
        every { getString(R.string.auth_error_too_many_attempts) } returns "Too many attempts, we sent a new code to your number."
        every { getString(R.string.auth_error_generic) } returns "Something went wrong. Please try again."
    }

    private fun authResult(
        userId: String = "user-1",
        accountState: AccountState = AccountState.ExistingComplete(userId),
    ) = AuthResultWithAccount(
        userId = userId,
        accountState = accountState,
        accessToken = "access-token",
        refreshToken = "refresh-token",
    )

    private fun newViewModel(
        authRepository: AuthRepository = mockk(),
        accountClassifier: AccountClassifier = mockk(),
        appStateInputBridge: AppStateInputBridge = AppStateInputBridge(),
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockContext(),
    ) = AuthViewModel(
        authRepository = authRepository,
        accountClassifier = accountClassifier,
        appStateInputBridge = appStateInputBridge,
        hapticManager = hapticManager,
        appContext = appContext,
    )

    @Test
    fun `pasting a full international number auto-detects the country and keeps only local digits`() = runTest {
        val viewModel = newViewModel()

        viewModel.onPhoneDigitsChanged("+447700900123")

        val state = viewModel.uiState.value
        assertEquals(PhoneCountry.findByCode("GB"), state.selectedCountry)
        assertEquals("7700900123", state.localDigits)
        assertEquals(PhoneValidationResult.Valid, state.validation)
    }

    @Test
    fun `selectCountry trims existing digits to the new country's expected length and revalidates`() = runTest {
        val viewModel = newViewModel()

        viewModel.onPhoneDigitsChanged("9876543210")
        viewModel.selectCountry(PhoneCountry.findByCode("ET")!!)

        val state = viewModel.uiState.value
        assertEquals("ET", state.selectedCountry.code)
        assertEquals("987654321", state.localDigits)
        assertEquals(PhoneValidationResult.Valid, state.validation)
    }

    @Test
    fun `sendOtp with invalid phone surfaces the real validation message and never calls the repository`() = runTest {
        val authRepository = mockk<AuthRepository>()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(authRepository = authRepository, hapticManager = hapticManager)

        viewModel.sendOtp()

        val state = viewModel.uiState.value
        assertEquals("Please enter your phone number", state.error)
        assertEquals(PhoneValidationResult.Empty, state.validation)
        verify { hapticManager.error() }
        coVerify(exactly = 0) { authRepository.sendOtp(any()) }
    }

    @Test
    fun `sendOtp success stores the normalized phone and clears any stale otp digits`() = runTest {
        val authRepository = mockk<AuthRepository>().also {
            coEvery { it.sendOtp("+919876543210") } returns Result.success(Unit)
        }
        val viewModel = newViewModel(authRepository = authRepository)
        viewModel.onPhoneDigitsChanged("9876543210")
        viewModel.onOtpChanged("123456")

        viewModel.sendOtp()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("+919876543210", state.otpSentTo)
        assertEquals("", state.otpDigits)
        assertNull(state.error)
        assertTrue(!state.isSendingOtp)
    }

    @Test
    fun `resendOtp reuses the stored destination and writes failures to otpError`() = runTest {
        val authRepository = mockk<AuthRepository>().also {
            coEvery { it.sendOtp("+919876543210") } returnsMany listOf(
                Result.success(Unit),
                Result.failure(AuthError.TooManyAttempts),
            )
        }
        val viewModel = newViewModel(authRepository = authRepository)
        viewModel.onPhoneDigitsChanged("9876543210")
        viewModel.sendOtp()
        advanceUntilIdle()
        viewModel.onOtpChanged("123456")

        viewModel.resendOtp()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("+919876543210", state.otpSentTo)
        assertEquals("", state.otpDigits)
        assertEquals("Too many attempts, we sent a new code to your number.", state.otpError)
        assertNull(state.error)
    }

    @Test
    fun `verifyOtp with no known phone surfaces the empty-code message and does not hit the repository`() = runTest {
        val authRepository = mockk<AuthRepository>()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(authRepository = authRepository, hapticManager = hapticManager)

        viewModel.verifyOtp("123456")

        assertEquals(
            "Code cannot be empty, please enter the 6-digit code we sent you",
            viewModel.uiState.value.otpError,
        )
        verify { hapticManager.error() }
        coVerify(exactly = 0) { authRepository.verifyOtpAndClassify(any(), any(), any()) }
    }

    @Test
    fun `verifyOtp filters non-digits and rejects any code that is not a full 6 digits`() = runTest {
        val authRepository = mockk<AuthRepository>()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(authRepository = authRepository, hapticManager = hapticManager)
        viewModel.onPhoneDigitsChanged("9876543210")

        viewModel.verifyOtp("12a34")

        val state = viewModel.uiState.value
        assertEquals("1234", state.otpDigits)
        assertEquals(
            "Code cannot be empty, please enter the 6-digit code we sent you",
            state.otpError,
        )
        verify { hapticManager.error() }
        coVerify(exactly = 0) { authRepository.verifyOtpAndClassify(any(), any(), any()) }
    }

    @Test
    fun `verifyOtp success for a cloud account stores the auth result and flips the real app state to Authenticated`() = runTest {
        val authRepository = mockk<AuthRepository>()
        val accountClassifier = mockk<AccountClassifier>()
        val appStateInputBridge = AppStateInputBridge()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val result = authResult(accountState = AccountState.ExistingComplete("user-1"))
        coEvery {
            authRepository.verifyOtpAndClassify("+919876543210", "123456", accountClassifier)
        } returns result
        val viewModel = newViewModel(
            authRepository = authRepository,
            accountClassifier = accountClassifier,
            appStateInputBridge = appStateInputBridge,
            hapticManager = hapticManager,
        )
        viewModel.onPhoneDigitsChanged("9876543210")

        viewModel.verifyOtp("123456")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(result, state.verifiedAuthResult)
        assertNull(state.otpError)
        assertEquals(SessionState.Authenticated("user-1"), appStateInputBridge.sessionState.value)
        verify { hapticManager.success() }
    }

    @Test
    fun `verifyOtp success for a local-only account routes through the real LocalOnlyUser app state`() = runTest {
        val authRepository = mockk<AuthRepository>()
        val result = authResult(
            userId = "offline-user",
            accountState = AccountState.LocalOnlyComplete("offline-user"),
        )
        coEvery {
            authRepository.verifyOtpAndClassify(any(), any(), any())
        } returns result
        val appStateInputBridge = AppStateInputBridge()
        val viewModel = newViewModel(
            authRepository = authRepository,
            appStateInputBridge = appStateInputBridge,
        )
        viewModel.onPhoneDigitsChanged("9876543210")

        viewModel.verifyOtp("123456")
        advanceUntilIdle()

        assertEquals(SessionState.LocalOnlyUser("offline-user"), appStateInputBridge.sessionState.value)
    }

    @Test
    fun `verifyOtp failure surfaces the shared safe auth message and clears any stale verified result`() = runTest {
        val authRepository = mockk<AuthRepository>().also {
            coEvery { it.verifyOtpAndClassify(any(), any(), any()) } throws AuthError.InvalidOtp
            coEvery { it.sendOtp("+919876543210") } returns Result.success(Unit)
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(authRepository = authRepository, hapticManager = hapticManager)
        viewModel.onPhoneDigitsChanged("9876543210")
        viewModel.sendOtp()
        advanceUntilIdle()

        viewModel.verifyOtp("123456")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Invalid OTP. Please try again.", state.otpError)
        assertNull(state.verifiedAuthResult)
        verify { hapticManager.error() }
    }

    @Test
    fun `resetPhoneFlow clears stale otp destination verified result and errors after a completed auth run`() = runTest {
        val authRepository = mockk<AuthRepository>().also {
            coEvery { it.sendOtp("+919876543210") } returns Result.success(Unit)
            coEvery { it.verifyOtpAndClassify(any(), any(), any()) } returns authResult()
        }
        val viewModel = newViewModel(authRepository = authRepository)
        viewModel.onPhoneDigitsChanged("9876543210")
        viewModel.sendOtp()
        advanceUntilIdle()
        viewModel.verifyOtp("123456")
        advanceUntilIdle()

        viewModel.resetPhoneFlow()

        assertEquals(PhoneUiState(), viewModel.uiState.value)
    }
}
