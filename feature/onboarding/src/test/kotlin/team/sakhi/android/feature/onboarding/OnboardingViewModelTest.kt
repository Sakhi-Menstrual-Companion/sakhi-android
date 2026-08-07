package team.sakhi.android.feature.onboarding

import android.content.Context
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.OnboardingHealthConnectImportResult
import team.sakhi.auth.AuthRepository
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStatusResponse
import team.sakhi.care.CareStore
import team.sakhi.models.CycleData
import team.sakhi.models.LogSource
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserProfile
import team.sakhi.onboarding.OnboardingFlowIntent
import team.sakhi.onboarding.OnboardingFlowStep
import team.sakhi.onboarding.OnboardingFlowStore
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.repositories.CareInviteException

/**
 * State-machine test for `OnboardingViewModel`. `AuthRepository`/`CareStore`/
 * `PeriodLogRepository`/`CycleDataRepository`/`UserProfileRepository`/
 * `AndroidHealthConnectManager`/`AndroidHapticManager` are concrete, non-open
 * KMM/platform classes (same situation as `HomeViewModelTest`), so this uses mockk
 * for those boundaries. `OnboardingFlowStore` is a real, self-contained KMM state
 * machine with no external side effects (pure step navigation), so a real instance
 * is used instead of a mock -- this proves `continueFlow()`'s
 * validate-then-advance behavior against the actual navigation logic, not a
 * stubbed transition. Field validation runs through the real `ValidationRules`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Several onboarding actions (`handleSetupLoading`, `createCareInvitationAndContinue`,
    // `cancelCareInvitation`, `acceptBeHerSakhiInvite`) run their real work inside
    // `withContext(Dispatchers.IO)`, which the virtual test scheduler doesn't control --
    // `advanceUntilIdle()` alone can return before that real IO-dispatched coroutine
    // finishes. Same technique `ReportsViewModelTest`/`ChatViewModelTest` established:
    // interleave a real, off-scheduler `delay` with `advanceUntilIdle()` so the IO work
    // actually progresses and the test dispatcher drains the resumption once it lands.
    // The timeout is a BOUND, not a sleep: a passing test leaves as soon as the
    // predicate holds, so a generous bound costs nothing. 2s was too tight under load
    // -- these steps do real `Dispatchers.IO` work that the virtual scheduler does not
    // control, and in a full multi-module `./gradlew test` (every module's tests running
    // at once) that work regularly took longer than 2s. The loop then gave up while the
    // view-model coroutine was still in flight, `tearDown`'s `resetMain()` ran underneath
    // it, and it died on "Dispatchers.Main was accessed ... test dispatcher was unset".
    // That is the whole reason `convertAccountToPartnerAndProceed` failed only in full
    // runs (and only sometimes, in either variant) while passing on its own.
    private suspend fun TestScope.awaitCondition(
        timeoutMs: Long = 15_000,
        predicate: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            withContext(Dispatchers.Default) { delay(5) }
            advanceUntilIdle()
        }
    }

    private fun mockContext(): Context = mockk {
        every { getString(R.string.onboarding_error_generic) } returns "Something went wrong. Please try again."
        every { getString(R.string.onboarding_error_code_missing) } returns "This code doesn't exist."
        every { getString(R.string.onboarding_error_offline) } returns "No internet connection. Please check and try again."
        every { getString(R.string.onboarding_error_code_expired) } returns "This code has expired. Ask your partner to create a new invite."
        every { getString(R.string.onboarding_error_code_wrong_phone) } returns "This invitation was sent to a different phone number."
        every { getString(R.string.onboarding_error_sign_in_before_invite) } returns "Please sign in before creating a care invite."
        every { getString(R.string.onboarding_fallback_user) } returns "User"
        every { getString(R.string.onboarding_error_create_invite) } returns "Couldn't create invite right now."
        every { getString(R.string.onboarding_info_invite_closed) } returns "That invite has been closed. Nothing was shared."
        every { getString(R.string.onboarding_error_cancel_request) } returns "Could not cancel request."
        every { getString(R.string.onboarding_validation_invalid_dob) } returns "Choose a valid date of birth"
        every { getString(R.string.onboarding_validation_invalid_height) } returns "Choose a valid height"
        every { getString(R.string.onboarding_validation_invalid_weight) } returns "Choose a valid weight"
        every { getString(R.string.onboarding_validation_invalid_date) } returns "Choose a valid date"
        every { getString(R.string.onboarding_validation_invalid_cycle_length) } returns "Choose a valid cycle length"
        every { getString(R.string.onboarding_error_health_connect_missing_details) } returns
            "Health Connect doesn't have the details needed for onboarding."
        every { getString(R.string.onboarding_error_health_connect_permission_denied) } returns
            "Health Connect access was not granted."
        every { getString(R.string.onboarding_error_health_connect_unavailable) } returns
            "Health Connect is not available on this device."
        every { getString(R.string.onboarding_error_health_connect_unsupported) } returns
            "Health Connect is not supported on this device."
    }

    private fun mockHealthConnectManager(): AndroidHealthConnectManager = mockk {
        every { availability() } returns HealthConnectAvailability.NotSupported
        every { onboardingRequiredPermissions } returns emptySet()
        coEvery { hasAllOnboardingPermissions() } returns false
    }

    private fun mockCareStore(
        careState: CareRuntimeState = CareRuntimeState.Disconnected,
    ) = mockk<CareStore> {
        every { this@mockk.careState } returns MutableStateFlow(careState)
    }

    private fun newViewModel(
        flowId: String = "newUser",
        flowStore: OnboardingFlowStore = OnboardingFlowStore(flowId),
        authRepository: AuthRepository = mockk { every { currentUserId } returns "user-1" },
        careStore: CareStore = mockCareStore(),
        periodLogRepository: PeriodLogRepository = mockk(relaxed = true),
        cycleDataRepository: CycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.upsert(any()) } answers { Result.success(firstArg()) }
        },
        userProfileRepository: UserProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.upsert(any()) } answers { Result.success(firstArg()) }
        },
        healthConnectManager: AndroidHealthConnectManager = mockHealthConnectManager(),
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockContext(),
    ) = OnboardingViewModel(
        flowId,
        appContext,
        flowStore,
        authRepository,
        careStore,
        periodLogRepository,
        cycleDataRepository,
        userProfileRepository,
        healthConnectManager,
        hapticManager,
    )

    @Test
    fun `continueFlow rejects a real invalid date of birth via ValidationRules without advancing`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.DateOfBirth.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.updateDateOfBirth(java.time.LocalDate.now().plusDays(1))

        viewModel.continueFlow()

        assertEquals("Choose a valid date of birth", flowStore.state.value.fieldError)
        assertEquals(OnboardingFlowStep.DateOfBirth, flowStore.state.value.currentStep)
    }

    @Test
    fun `continueFlow advances the real flow store when the date of birth is valid`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.DateOfBirth.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.updateDateOfBirth(java.time.LocalDate.now().minusYears(25))

        viewModel.continueFlow()

        assertNull(flowStore.state.value.fieldError)
        assertEquals(OnboardingFlowStep.Height, flowStore.state.value.currentStep)
    }

    @Test
    fun `continueFlow ignores rapid repeat continue during the transition window`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.DateOfBirth.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.updateDateOfBirth(java.time.LocalDate.now().minusYears(25))

        viewModel.continueFlow(expectedStep = OnboardingFlowStep.DateOfBirth)
        viewModel.continueFlow(expectedStep = OnboardingFlowStep.Height)

        assertEquals(OnboardingFlowStep.Height, flowStore.state.value.currentStep)
    }

    @Test
    fun `continueFlow ignores stale outgoing step clicks after navigation advances`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.DateOfBirth.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.updateDateOfBirth(java.time.LocalDate.now().minusYears(25))

        viewModel.continueFlow(expectedStep = OnboardingFlowStep.DateOfBirth)
        viewModel.continueFlow(expectedStep = OnboardingFlowStep.DateOfBirth)

        assertEquals(OnboardingFlowStep.Height, flowStore.state.value.currentStep)
    }

    @Test
    fun `setHeightCm always clamps within the real ValidationRules-valid range, so continueFlow never rejects it`() = runTest {
        // Real, confirmed-not-a-bug finding: `setHeightCm` coerces into [100, 220],
        // a strict subset of `ValidationRules.isValidHeightCm`'s [50, 220] valid
        // range, so an invalid height can never actually be reached through this
        // setter -- proven here rather than asserting a fieldError that would
        // never fire in the real app.
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.Height.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.setHeightCm(1.0)

        viewModel.continueFlow()

        assertNull(flowStore.state.value.fieldError)
        assertEquals(OnboardingFlowStep.Weight, flowStore.state.value.currentStep)
    }

    @Test
    fun `resolveTerms false triggers a haptic error and is rejected by the real flow store's own guard`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.Terms.stepId))
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(flowStore = flowStore, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.resolveTerms(accepted = false)

        assertEquals(OnboardingFlowStep.Terms, flowStore.state.value.currentStep)
        assertTrue(flowStore.state.value.fieldError != null)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `continueFlow rejects a real invalid cycle length via ValidationRules`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.NavigateToStep(OnboardingFlowStep.CycleLength.stepId))
        val viewModel = newViewModel(flowStore = flowStore)
        advanceUntilIdle()
        viewModel.updateCycleLengthText("99")

        viewModel.continueFlow()

        // "99" is out of the real 21..45 accepted range for updateCycleLengthText,
        // so cycleLength stays at its prior valid default and never even reaches
        // the invalid state ValidationRules would reject -- confirms the UI-level
        // guard and the KMM validation agree rather than fighting each other.
        assertNull(flowStore.state.value.fieldError)
    }

    @Test
    fun `toggleCondition adds then removes the same health condition`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleCondition(team.sakhi.models.HealthCondition.PCOS)
        assertTrue(team.sakhi.models.HealthCondition.PCOS in viewModel.healthUiState.value.selectedConditions)

        viewModel.toggleCondition(team.sakhi.models.HealthCondition.PCOS)
        assertTrue(viewModel.healthUiState.value.selectedConditions.isEmpty())
    }

    @Test
    fun `setHeightCm and setWeightKg clamp to their real bounds`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setHeightCm(500.0)
        assertEquals(220.0, viewModel.healthUiState.value.heightCm, 0.0)

        viewModel.setWeightKg(1.0)
        assertEquals(30.0, viewModel.healthUiState.value.weightKg, 0.0)
    }

    @Test
    fun `updateLastPeriodDate clamps a future date to today`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val today = java.time.LocalDate.now()

        viewModel.updateLastPeriodDate(today.plusDays(5))

        assertEquals(today, viewModel.healthUiState.value.lastPeriodDate)
    }

    @Test
    fun `updatePeriodLengthText rejects an out-of-range value and keeps the previous valid length`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val before = viewModel.healthUiState.value.periodLength

        viewModel.updatePeriodLengthText("99")

        assertEquals(before, viewModel.healthUiState.value.periodLength)
        assertEquals("99", viewModel.healthUiState.value.periodLengthText)
    }

    @Test
    fun `handleSetupLoading completes directly when the real plan has no HealthConditions step`() = runTest {
        val flowStore = OnboardingFlowStore("returningSync")
        val userProfileRepository = mockk<UserProfileRepository>()
        val viewModel = newViewModel(flowId = "returningSync", flowStore = flowStore, userProfileRepository = userProfileRepository)
        advanceUntilIdle()

        viewModel.handleSetupLoading()
        advanceUntilIdle()
        // Settle before the test ends -- see the conversion re-entry guard test for why
        // a view-model coroutine left running here fails some unrelated test later.
        awaitCondition { !viewModel.setupUiState.value.isSaving }

        coVerify(exactly = 0) { userProfileRepository.upsert(any()) }
    }

    @Test
    fun `handleSetupLoading saves a real UserProfile, CycleData and period log built from the real health state`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        val profileSlot: CapturingSlot<UserProfile> = slot()
        val cycleSlot: CapturingSlot<CycleData> = slot()
        val periodLogSlot: CapturingSlot<PeriodLog> = slot()
        val userProfileRepository = mockk<UserProfileRepository> {
            coEvery { upsert(capture(profileSlot)) } answers { Result.success(profileSlot.captured) }
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { upsert(capture(cycleSlot)) } answers { Result.success(cycleSlot.captured) }
        }
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { upsert(capture(periodLogSlot)) } answers { Result.success(periodLogSlot.captured) }
        }
        val viewModel = newViewModel(
            flowStore = flowStore,
            userProfileRepository = userProfileRepository,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )
        advanceUntilIdle()
        viewModel.setHeightCm(165.0)
        viewModel.setWeightKg(58.0)
        viewModel.updateCycleLengthText("30")

        viewModel.handleSetupLoading()
        advanceUntilIdle()
        awaitCondition { !viewModel.setupUiState.value.isSaving }

        assertFalse(viewModel.setupUiState.value.isSaving)
        assertNull(viewModel.setupUiState.value.error)
        assertEquals("user-1", profileSlot.captured.id)
        assertEquals(165.0, profileSlot.captured.heightCm)
        assertEquals(58.0, profileSlot.captured.weightKg)
        assertEquals(30, cycleSlot.captured.cycleLength)
        assertEquals("user-1", cycleSlot.captured.userId)
        // Onboarding must write the confirmed last-period date as a real period log,
        // not just a CycleData. Every calendar mark (period, predicted, fertile,
        // ovulation, PMS) is built from logged days only, so a cycle with no log
        // renders a completely blank calendar -- which is exactly what shipped.
        assertEquals("user-1", periodLogSlot.captured.userId)
        assertEquals(cycleSlot.captured.periodStartDate, periodLogSlot.captured.logDate)
        assertTrue(periodLogSlot.captured.periodPresent)
        assertEquals(LogSource.USER, periodLogSlot.captured.loggedBy)
    }

    @Test
    fun `handleSetupLoading with no current user completes onboarding instead of erroring`() = runTest {
        // No `currentUserId` means an OFFLINE user (chose "Continue Offline" at the
        // privacy step, never went through Phone/OTP) -- not a failure. This used to
        // assert a generic error, which matched the old behaviour but was itself the
        // bug: confirmed live on a real device that offline onboarding dead-ended on
        // an unrecoverable "Something went wrong" screen with no way forward.
        //
        // The offline session is minted HERE, at the end of the flow, rather than at the
        // privacy step: `SessionState.LocalOnlyUser` routes straight to Home via
        // `AccountClassifier`, so starting it earlier skipped the whole rest of
        // onboarding (a real regression, caught on device). Asserting the call happens
        // pins that ordering.
        val flowStore = OnboardingFlowStore("newUser")
        val authRepository = mockk<AuthRepository> {
            every { currentUserId } returns null
            every { startLocalOnlySession(any()) } returns "offline_test"
        }
        val viewModel = newViewModel(flowStore = flowStore, authRepository = authRepository)
        advanceUntilIdle()

        viewModel.handleSetupLoading()
        advanceUntilIdle()
        awaitCondition { !viewModel.setupUiState.value.isSaving }

        assertNull(viewModel.setupUiState.value.error)
        verify { authRepository.startLocalOnlySession(any()) }
    }

    @Test
    fun `acceptBeHerSakhiInvite with a blank pending invite code surfaces a real error and never calls the store`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        val careStore = mockCareStore()
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()

        assertEquals("This code doesn't exist.", viewModel.acceptUiState.value.error)
        assertFalse(viewModel.acceptUiState.value.canRetry)
        coVerify(exactly = 0) { careStore.acceptInvitation(any(), any()) }
    }

    @Test
    fun `createCareInvitationAndContinue reuses a real existing pending invitation instead of creating a new one`() = runTest {
        val existing = PartnerInvitation(
            id = "invite-1",
            inviterId = "user-1",
            inviterName = "Test User",
            inviteePhone = "",
            inviteCode = "ABC123",
            expiresAt = "2026-08-01T00:00:00Z",
        )
        val careStore = mockCareStore(careState = CareRuntimeState.PendingInvitation(existing))
        val viewModel = newViewModel(careStore = careStore)
        advanceUntilIdle()

        viewModel.createCareInvitationAndContinue()
        advanceUntilIdle()
        // Settle the invite coroutine before the test ends -- see the conversion
        // re-entry guard test for why leaking it fails an unrelated test later.
        awaitCondition { viewModel.careInviteUiState.value.inviteCode.isNotBlank() }

        assertEquals("ABC123", viewModel.careInviteUiState.value.inviteCode)
        coVerify(exactly = 0) {
            careStore.createInvitation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `createCareInvitationAndContinue creates a real new invite when none exists`() = runTest {
        val careStore = mockCareStore(careState = CareRuntimeState.Disconnected)
        val response = CareStatusResponse(
            state = "pending_invitation",
            invitation = CareStatusResponse.InvitationPayload(
                id = "invite-2",
                inviteCode = "XYZ999",
                expiresAt = "2026-08-01T00:00:00Z",
            ),
        )
        coEvery {
            careStore.createInvitation(any(), any(), any(), any(), any(), any(), any())
        } returns response
        val viewModel = newViewModel(careStore = careStore)
        advanceUntilIdle()

        viewModel.createCareInvitationAndContinue()
        advanceUntilIdle()
        awaitCondition { !viewModel.careInviteUiState.value.isCreatingInvite }

        val state = viewModel.careInviteUiState.value
        assertFalse(state.isCreatingInvite)
        assertEquals("XYZ999", state.inviteCode)
        assertNull(state.errorMessage)
    }

    @Test
    fun `createCareInvitationAndContinue completion after an account switch is discarded`() = runTest {
        val currentUserIdSlot = arrayOf<String?>("user-1")
        val authRepository = mockk<AuthRepository> {
            every { currentUserId } answers { currentUserIdSlot[0] }
        }
        val careStore = mockCareStore(careState = CareRuntimeState.Disconnected)
        val gate = CompletableDeferred<Unit>()
        coEvery {
            careStore.createInvitation(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            gate.await()
            CareStatusResponse(
                state = "pending_invitation",
                invitation = CareStatusResponse.InvitationPayload(
                    id = "invite-1",
                    inviteCode = "ABC123",
                    expiresAt = "2026-08-01T00:00:00Z",
                ),
            )
        }
        val flowStore = OnboardingFlowStore("newUser")
        val initialStep = flowStore.state.value.currentStep
        val viewModel = newViewModel(
            flowStore = flowStore,
            authRepository = authRepository,
            careStore = careStore,
        )
        advanceUntilIdle()

        viewModel.createCareInvitationAndContinue()
        advanceUntilIdle()
        assertTrue(viewModel.careInviteUiState.value.isCreatingInvite)

        currentUserIdSlot[0] = "user-2"
        gate.complete(Unit)
        awaitCondition { !viewModel.careInviteUiState.value.isCreatingInvite }

        val state = viewModel.careInviteUiState.value
        assertFalse(state.isCreatingInvite)
        assertEquals("", state.inviteCode)
        assertEquals("", state.invitationId)
        assertEquals(initialStep, flowStore.state.value.currentStep)
    }

    @Test
    fun `cancelCareInvitation success clears invite state and surfaces a real close message`() = runTest {
        val existing = PartnerInvitation(
            id = "invite-1",
            inviterId = "user-1",
            inviterName = "Test User",
            inviteePhone = "",
            inviteCode = "ABC123",
            expiresAt = "2026-08-01T00:00:00Z",
        )
        val careStore = mockCareStore(careState = CareRuntimeState.PendingInvitation(existing))
        coEvery { careStore.cancelInvitation("invite-1", "user-1") } returns CareStatusResponse(state = "disconnected")
        val viewModel = newViewModel(careStore = careStore)
        advanceUntilIdle()

        viewModel.cancelCareInvitation()
        advanceUntilIdle()
        awaitCondition { !viewModel.careInviteUiState.value.isCancellingInvite }

        val state = viewModel.careInviteUiState.value
        assertFalse(state.isCancellingInvite)
        assertEquals("", state.inviteCode)
        assertEquals("That invite has been closed. Nothing was shared.", state.cancelMessage)
    }

    @Test
    fun `convertAccountToPartnerAndProceed deletes the real existing health data through the real shared repositories, then advances`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        val periodLogRepository = mockk<PeriodLogRepository>(relaxed = true)
        val cycleDataRepository = mockk<CycleDataRepository>(relaxed = true).also {
            coEvery { it.upsert(any()) } answers { Result.success(firstArg()) }
        }
        val viewModel = newViewModel(
            flowStore = flowStore,
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
        )
        advanceUntilIdle()
        val indexBefore = flowStore.state.value.currentIndex

        viewModel.convertAccountToPartnerAndProceed()
        advanceUntilIdle()
        // Waits on the LAST observable effect of the conversion coroutine (the flow
        // advancing), not just `isConverting` flipping. `isConverting = false` is set
        // before the coroutine's final step, so waiting only on it let the test finish
        // while that coroutine was still in flight; `tearDown`'s `resetMain()` then ran
        // underneath it and it died on "Dispatchers.Main was accessed ... test
        // dispatcher was unset". That surfaced as an order-dependent failure of this
        // test (release variant especially) which passed in isolation.
        awaitCondition {
            !viewModel.conversionUiState.value.isConverting &&
                flowStore.state.value.currentIndex > indexBefore
        }

        assertFalse(viewModel.conversionUiState.value.isConverting)
        assertNull(viewModel.conversionUiState.value.error)
        coVerify(exactly = 1) { periodLogRepository.deleteAll("user-1") }
        coVerify(exactly = 1) { cycleDataRepository.deleteAll("user-1") }
        assertTrue(flowStore.state.value.currentIndex > indexBefore)
    }

    @Test
    fun `convertAccountToPartnerAndProceed failure surfaces a real message and does not advance the flow`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { deleteAll(any()) } throws RuntimeException("disk full")
        }
        val viewModel = newViewModel(flowStore = flowStore, periodLogRepository = periodLogRepository)
        advanceUntilIdle()
        val indexBefore = flowStore.state.value.currentIndex

        viewModel.convertAccountToPartnerAndProceed()
        advanceUntilIdle()
        awaitCondition { !viewModel.conversionUiState.value.isConverting }

        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Something went wrong. Please try again.", viewModel.conversionUiState.value.error)
        assertEquals(indexBefore, flowStore.state.value.currentIndex)
    }

    @Test
    fun `convertAccountToPartnerAndProceed is a no-op re-entry guard while a conversion is already in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { deleteAll(any()) } coAnswers { gate.await(); Result.success(Unit) }
        }
        val cycleDataRepository = mockk<CycleDataRepository>(relaxed = true)
        val viewModel = newViewModel(
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
        )
        advanceUntilIdle()

        viewModel.convertAccountToPartnerAndProceed()
        advanceUntilIdle()
        assertTrue(viewModel.conversionUiState.value.isConverting)

        viewModel.convertAccountToPartnerAndProceed()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        // Opening the gate resumes the conversion on `Dispatchers.IO`, which the virtual
        // scheduler does not drive, so `advanceUntilIdle()` alone returns with that
        // coroutine still running. Letting the test end there leaked it past
        // `tearDown`'s `resetMain()`, where it died on "Dispatchers.Main was accessed ...
        // test dispatcher was unset" -- and because the crash lands on whichever test is
        // running at that moment, it surfaced as a random OTHER test failing in full
        // suite runs. Settle here so nothing outlives the test that started it.
        awaitCondition { !viewModel.conversionUiState.value.isConverting }

        coVerify(exactly = 1) { periodLogRepository.deleteAll(any()) }
    }

    @Test
    fun `acceptBeHerSakhiInvite with no current user surfaces a real error and never calls the store`() = runTest {
        val authRepository = mockk<AuthRepository> { every { currentUserId } returns null }
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(
            flowStore = flowStore,
            authRepository = authRepository,
            careStore = careStore,
            hapticManager = hapticManager,
        )
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()

        assertEquals("Something went wrong. Please try again.", viewModel.acceptUiState.value.error)
        verify(exactly = 1) { hapticManager.error() }
        coVerify(exactly = 0) { careStore.acceptInvitation(any(), any()) }
    }

    @Test
    fun `acceptBeHerSakhiInvite success through the real store call fires a haptic success`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        coEvery { careStore.acceptInvitation("ABC123", "user-1") } returns CareStatusResponse(state = "connected")
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()
        awaitCondition { !viewModel.acceptUiState.value.isAccepting }

        val state = viewModel.acceptUiState.value
        assertTrue(state.succeeded)
        assertNull(state.error)
        verify(exactly = 1) { hapticManager.success() }
    }

    // Was: asserted that `throwable.message` reached the UI verbatim, using a synthetic
    // RuntimeException("invite expired") that happened to read like a sentence. Real
    // failures here are Ktor/kotlinx throwables, so that contract put transport text --
    // status lines, serialization complaints, sometimes a URL -- on screen in front of
    // someone typing an invite code. The contract is now "an unrecognised failure reads
    // as the generic sentence", with the recognised ones asserted separately below.
    @Test
    fun `acceptBeHerSakhiInvite unrecognised failure shows the generic message and allows retry`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        coEvery { careStore.acceptInvitation("ABC123", "user-1") } throws RuntimeException("boom")
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()
        awaitCondition { !viewModel.acceptUiState.value.isAccepting }

        val state = viewModel.acceptUiState.value
        assertFalse(state.succeeded)
        assertEquals("Something went wrong. Please try again.", state.error)
        // The raw throwable text must never reach the UI.
        assertFalse(state.error.orEmpty().contains("boom"))
        assertTrue(state.canRetry)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `acceptBeHerSakhiInvite expired code shows the expired message`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        coEvery { careStore.acceptInvitation("ABC123", "user-1") } throws
            CareInviteException(CareInviteException.Reason.EXPIRED, 410)
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()
        awaitCondition { !viewModel.acceptUiState.value.isAccepting }

        val state = viewModel.acceptUiState.value
        assertEquals("This code has expired. Ask your partner to create a new invite.", state.error)
        assertTrue(state.canRetry)
    }

    @Test
    fun `acceptBeHerSakhiInvite unknown code shows the check-with-partner message and blocks retry`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        coEvery { careStore.acceptInvitation("ABC123", "user-1") } throws
            CareInviteException(CareInviteException.Reason.NOT_FOUND, 404)
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()
        awaitCondition { !viewModel.acceptUiState.value.isAccepting }

        val state = viewModel.acceptUiState.value
        // Fixture value for onboarding_error_code_missing; the shipped string is the
        // longer "…Please double-check with your partner." sentence.
        assertEquals("This code doesn't exist.", state.error)
        // Retrying the same wrong/spent code cannot succeed, so the affordance is dropped.
        assertFalse(state.canRetry)
    }

    @Test
    fun `acceptBeHerSakhiInvite offline failure shows the connection message`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = "ABC123"))
        val careStore = mockCareStore()
        coEvery { careStore.acceptInvitation("ABC123", "user-1") } throws
            java.io.IOException("unable to resolve host")
        val viewModel = newViewModel(flowStore = flowStore, careStore = careStore)
        advanceUntilIdle()

        viewModel.acceptBeHerSakhiInvite()
        advanceUntilIdle()
        awaitCondition { !viewModel.acceptUiState.value.isAccepting }

        val state = viewModel.acceptUiState.value
        assertEquals("No internet connection. Please check and try again.", state.error)
        assertTrue(state.canRetry)
    }

    @Test
    fun `onDataSourcePermissionsResult with all real required permissions granted triggers a real Health Connect import`() = runTest {
        val requiredPermissions = setOf("perm.a", "perm.b")
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns requiredPermissions
            coEvery { hasAllOnboardingPermissions() } returns true
            coEvery { importOnboardingSnapshot() } returns OnboardingHealthConnectImportResult(
                failureMessage = null,
                heightCm = 165.0,
            )
        }
        val viewModel = newViewModel(healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.onDataSourcePermissionsResult(requiredPermissions)
        advanceUntilIdle()
        awaitCondition { !viewModel.dataSourceUiState.value.isImporting }

        val state = viewModel.dataSourceUiState.value
        assertTrue(state.hasPermissions)
        assertFalse(state.importFailed)
        assertEquals(165.0, viewModel.healthUiState.value.heightCm, 0.0)
    }

    @Test
    fun `onDataSourcePermissionsResult with a real required permission missing fires a haptic error and never imports`() = runTest {
        val requiredPermissions = setOf("perm.a", "perm.b")
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns requiredPermissions
            coEvery { hasAllOnboardingPermissions() } returns false
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(healthConnectManager = healthConnectManager, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.onDataSourcePermissionsResult(setOf("perm.a"))
        advanceUntilIdle()

        val state = viewModel.dataSourceUiState.value
        assertTrue(state.importFailed)
        assertTrue(state.showFailureAlert)
        verify(exactly = 1) { hapticManager.error() }
        coVerify(exactly = 0) { healthConnectManager.importOnboardingSnapshot() }
    }

    @Test
    fun `handleUnavailableHealthConnectSelection surfaces the real message matching each real availability state`() = runTest {
        listOf(
            HealthConnectAvailability.NotInstalled to "Health Connect is not available on this device.",
            HealthConnectAvailability.NotSupported to "Health Connect is not supported on this device.",
            HealthConnectAvailability.Available to "Health Connect access was not granted.",
        ).forEach { (availability, expectedMessage) ->
            val healthConnectManager = mockk<AndroidHealthConnectManager> {
                every { this@mockk.availability() } returns availability
                every { onboardingRequiredPermissions } returns emptySet()
                coEvery { hasAllOnboardingPermissions() } returns false
            }
            val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
            val viewModel = newViewModel(
                healthConnectManager = healthConnectManager,
                hapticManager = hapticManager,
            )
            advanceUntilIdle()

            viewModel.handleUnavailableHealthConnectSelection()
            advanceUntilIdle()

            assertEquals(expectedMessage, viewModel.dataSourceUiState.value.failureMessage)
            verify(atLeast = 1) { hapticManager.error() }
        }
    }

    @Test
    fun `importFromHealthConnect with real imported data applies it and skips only the real fields that were imported`() = runTest {
        val flowStore = OnboardingFlowStore("newUser")
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns emptySet()
            coEvery { hasAllOnboardingPermissions() } returns true
            coEvery { importOnboardingSnapshot() } returns OnboardingHealthConnectImportResult(
                failureMessage = null,
                heightCm = 170.0,
                weightKg = 62.0,
            )
        }
        val viewModel = newViewModel(flowStore = flowStore, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.importFromHealthConnect()
        advanceUntilIdle()
        awaitCondition { !viewModel.dataSourceUiState.value.isImporting }

        val dataSourceState = viewModel.dataSourceUiState.value
        assertFalse(dataSourceState.importFailed)
        assertEquals(OnboardingDataSourceChoice.HealthConnect, dataSourceState.selectedChoice)
        val healthState = viewModel.healthUiState.value
        assertEquals(170.0, healthState.heightCm, 0.0)
        assertEquals(62.0, healthState.weightKg, 0.0)
        // Real fields that came back non-null (height, weight) are skipped; the flow
        // should have advanced straight to the first *unimported* real health step.
        assertEquals(OnboardingFlowStep.DateOfBirth, flowStore.state.value.currentStep)
    }

    @Test
    fun `importFromHealthConnect with no real imported data surfaces the real missing-details failure`() = runTest {
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns emptySet()
            coEvery { hasAllOnboardingPermissions() } returns true
            coEvery { importOnboardingSnapshot() } returns OnboardingHealthConnectImportResult(failureMessage = null)
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(healthConnectManager = healthConnectManager, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.importFromHealthConnect()
        advanceUntilIdle()
        awaitCondition { !viewModel.dataSourceUiState.value.isImporting }

        val state = viewModel.dataSourceUiState.value
        assertTrue(state.importFailed)
        assertTrue(state.showFailureAlert)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `importFromHealthConnect failure surfaces the real thrown message`() = runTest {
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns emptySet()
            coEvery { hasAllOnboardingPermissions() } returns true
            coEvery { importOnboardingSnapshot() } throws RuntimeException("Health Connect crashed")
        }
        val viewModel = newViewModel(healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.importFromHealthConnect()
        advanceUntilIdle()
        awaitCondition { !viewModel.dataSourceUiState.value.isImporting }

        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Health Connect access was not granted.", viewModel.dataSourceUiState.value.failureMessage)
    }

    @Test
    fun `importFromHealthConnect is a no-op re-entry guard while an import is already in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val healthConnectManager = mockk<AndroidHealthConnectManager> {
            every { availability() } returns HealthConnectAvailability.Available
            every { onboardingRequiredPermissions } returns emptySet()
            coEvery { hasAllOnboardingPermissions() } returns true
            coEvery { importOnboardingSnapshot() } coAnswers {
                gate.await()
                OnboardingHealthConnectImportResult(failureMessage = null, heightCm = 165.0)
            }
        }
        val viewModel = newViewModel(healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.importFromHealthConnect()
        advanceUntilIdle()
        assertTrue(viewModel.dataSourceUiState.value.isImporting)

        viewModel.importFromHealthConnect()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        // Same reason as the conversion re-entry guard above: settle the resumed work
        // instead of leaking it past `resetMain()`.
        awaitCondition { !viewModel.dataSourceUiState.value.isImporting }

        coVerify(exactly = 1) { healthConnectManager.importOnboardingSnapshot() }
    }
}
