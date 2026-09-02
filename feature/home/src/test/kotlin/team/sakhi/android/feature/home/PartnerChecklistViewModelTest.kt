package team.sakhi.android.feature.home

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import team.sakhi.android.testing.MainDispatcherRule
import org.junit.Test
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.models.CarePartnership
import team.sakhi.models.CyclePhase
import team.sakhi.models.PartnerChecklist
import team.sakhi.models.PartnerChecklistItem
import team.sakhi.models.UserCareRole
import team.sakhi.repositories.AIRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * First ViewModel-level state-machine test in this module. `SessionManager`,
 * `AIRepository`, and `AndroidHapticManager` are all concrete, non-open KMM/platform
 * classes with no interfaces to hand-roll a fake against (unlike the pure-function
 * tests elsewhere in this repo), so this test uses mockk against those three
 * boundaries only -- `SessionContext`, `CarePartnership`, and `PartnerChecklist` are
 * real KMM data classes constructed directly, no mocking needed for those.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerChecklistViewModelTest {

    // The VM now takes a Context so it can fall back to a real string when the user's
    // name is unknown -- the shared session resolver returns "" for that case instead of
    // substituting the word "User".
    private fun mockContext(): android.content.Context = mockk {
        every { getString(R.string.home_partner_checklist_fallback_name) } returns "Her care partner"
    }


    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val testDispatcher get() = mainDispatcherRule.testDispatcher



    private fun sessionContext(
        userId: String = "partner-1",
        targetUserId: String = "primary-1",
        partnershipId: String? = "partnership-1",
        canViewPredictions: Boolean = true,
        canViewCycleHistory: Boolean = true,
    ): SessionContext = SessionContext(
        userId = userId,
        userName = "Partner Name",
        activeRole = UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = SessionPermissions(
            canViewPeriodDates = true,
            canLogPeriod = true,
            canViewSymptoms = true,
            canViewMoods = true,
            canViewMedications = true,
            canViewPredictions = canViewPredictions,
            canViewCycleHistory = canViewCycleHistory,
            canViewDailyLogs = true,
            canViewOvulationTests = true,
            canViewTemperature = true,
            canViewWeight = true,
            canViewNotes = true,
            canViewDischarge = true,
            canViewSexualActivity = true,
        ),
        activePartnership = partnershipId?.let {
            CarePartnership(id = it, userId = targetUserId, partnerId = userId, partnerName = "Partner Name")
        },
    )

    private fun checklist(items: List<PartnerChecklistItem>): PartnerChecklist = PartnerChecklist(
        id = "checklist-1",
        partnerUserId = "partner-1",
        primaryUserId = "primary-1",
        partnershipId = "partnership-1",
        dateString = "2026-07-11",
        cyclePhase = CyclePhase.FOLLICULAR.value,
        cycleDay = 8,
        items = items,
        completedCount = items.count { it.isCompleted },
        generatedAt = "2026-07-11T00:00:00Z",
        updatedAt = "2026-07-11T00:00:00Z",
    )

    @Test
    fun `loadOrGenerate does nothing when there is no active session`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns null
        val aiRepository = mockk<AIRepository>()
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        assertEquals(PartnerChecklistUiState(), viewModel.uiState.value)
        coVerify(exactly = 0) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `loadOrGenerate does nothing when the partner has no granted phase access`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every {
            sessionManager.current
        } returns sessionContext(canViewPredictions = false, canViewCycleHistory = false)
        val aiRepository = mockk<AIRepository>()
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        assertEquals(PartnerChecklistUiState(), viewModel.uiState.value)
        coVerify(exactly = 0) { aiRepository.getChecklist(any(), any()) }
        coVerify(exactly = 0) {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `loadOrGenerate does nothing when viewing own data`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext(userId = "same-user", targetUserId = "same-user")
        val aiRepository = mockk<AIRepository>()
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        assertEquals(PartnerChecklistUiState(), viewModel.uiState.value)
        coVerify(exactly = 0) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `loadOrGenerate does nothing without an active partnership`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext(partnershipId = null)
        val aiRepository = mockk<AIRepository>()
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        assertEquals(PartnerChecklistUiState(), viewModel.uiState.value)
        coVerify(exactly = 0) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `loadOrGenerate uses an existing server checklist without regenerating`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        val existing = checklist(listOf(PartnerChecklistItem("i1", "Buy snacks", false)))
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success(existing)
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(existing.items, state.items)
        assertEquals(0, state.completedCount)
        assertFalse(state.isGenerating)
        assertFalse(state.failedToGenerate)
        coVerify(exactly = 0) {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `loadOrGenerate generates a new checklist when none exists yet`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success<PartnerChecklist?>(null)
        val generated = checklist(listOf(PartnerChecklistItem("i1", "Bring painkillers", true)))
        coEvery {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(generated)
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(generated.items, state.items)
        assertEquals(1, state.completedCount)
        assertFalse(state.isGenerating)
        assertFalse(state.failedToGenerate)
    }

    @Test
    fun `loadOrGenerate discards a late existing checklist after the session changes`() = runTest {
        val currentSession = arrayOf<SessionContext?>(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { current } answers { currentSession[0] }
        }
        val aiRepository = mockk<AIRepository>()
        val gate = CompletableDeferred<Unit>()
        val existing = checklist(listOf(PartnerChecklistItem("i1", "Buy snacks", false)))
        coEvery { aiRepository.getChecklist(any(), any()) } coAnswers {
            gate.await()
            Result.success(existing)
        }
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()
        currentSession[0] = null
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.completedCount)
        assertTrue(state.isGenerating)
        assertFalse(state.failedToGenerate)
        coVerify(exactly = 1) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `loadOrGenerate discards a late generated checklist after the session changes`() = runTest {
        val currentSession = arrayOf<SessionContext?>(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { current } answers { currentSession[0] }
        }
        val aiRepository = mockk<AIRepository>()
        val gate = CompletableDeferred<Unit>()
        val generated = checklist(listOf(PartnerChecklistItem("i1", "Bring painkillers", true)))
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success<PartnerChecklist?>(null)
        coEvery {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            gate.await()
            Result.success(generated)
        }
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()
        currentSession[0] = null
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.completedCount)
        assertTrue(state.isGenerating)
        assertFalse(state.failedToGenerate)
        coVerify(exactly = 1) {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `loadOrGenerate surfaces a failure and clears the load key so retry can run again`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success<PartnerChecklist?>(null)
        coEvery {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("network down"))
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        val failedState = viewModel.uiState.value
        assertFalse(failedState.failedToGenerate)
        assertFalse(failedState.isGenerating)
        assertEquals(
            listOf(
                "Suggest one light plan",
                "Celebrate one small win",
                "Match her fresh energy",
                "Ask what she wants next",
            ),
            failedState.items.map(PartnerChecklistItem::text),
        )

        // lastLoadedKey was reset on failure, so retry must genuinely hit the
        // repository again instead of being silently swallowed by the idempotency guard.
        viewModel.retry(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()
        coVerify(exactly = 2) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `loadOrGenerate uses the near-period luteal fallback when AI generation fails`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success<PartnerChecklist?>(null)
        coEvery {
            aiRepository.generatePartnerChecklist(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("network down"))
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(
            cyclePhase = CyclePhase.LUTEAL,
            cycleDay = 23,
            daysUntilNextPeriod = 2,
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                "Stock her comfort snack",
                "Keep evening plans calm",
                "Listen without fixing",
                "Avoid unnecessary arguments",
            ),
            viewModel.uiState.value.items.map(PartnerChecklistItem::text),
        )
    }

    @Test
    fun `loadOrGenerate is idempotent for the same partner and day`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success(checklist(emptyList()))
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, mockk())

        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()
        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        coVerify(exactly = 1) { aiRepository.getChecklist(any(), any()) }
    }

    @Test
    fun `toggle flips completion optimistically before the repository call resolves`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext()
        val aiRepository = mockk<AIRepository>()
        val existing = checklist(listOf(PartnerChecklistItem("i1", "Buy snacks", false)))
        coEvery { aiRepository.getChecklist(any(), any()) } returns Result.success(existing)
        coEvery { aiRepository.toggleChecklistItem(any(), any(), any()) } returns Result.success(existing)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, hapticManager)
        viewModel.loadOrGenerate(CyclePhase.FOLLICULAR, 8)
        advanceUntilIdle()

        viewModel.toggle("i1")

        // The optimistic update runs synchronously on the caller, before the
        // fire-and-forget repository sync coroutine is even given a chance to run.
        assertTrue(viewModel.uiState.value.items.single { it.id == "i1" }.isCompleted)
        assertEquals(1, viewModel.uiState.value.completedCount)

        advanceUntilIdle()
        verify(exactly = 1) { hapticManager.impact(HapticImpact.LIGHT) }
        coVerify(exactly = 1) { aiRepository.toggleChecklistItem(any(), any(), "i1") }
    }

    @Test
    fun `toggle does nothing without an active session`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns null
        val aiRepository = mockk<AIRepository>()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, hapticManager)

        viewModel.toggle("i1")
        advanceUntilIdle()

        assertEquals(PartnerChecklistUiState(), viewModel.uiState.value)
        verify(exactly = 0) { hapticManager.impact(any()) }
        coVerify(exactly = 0) { aiRepository.toggleChecklistItem(any(), any(), any()) }
    }

    @Test
    fun `toggle does nothing when viewing own data`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.current } returns sessionContext(userId = "same-user", targetUserId = "same-user")
        val aiRepository = mockk<AIRepository>()
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = PartnerChecklistViewModel(mockContext(), sessionManager, aiRepository, hapticManager)

        viewModel.toggle("i1")
        advanceUntilIdle()

        verify(exactly = 0) { hapticManager.impact(any()) }
        coVerify(exactly = 0) { aiRepository.toggleChecklistItem(any(), any(), any()) }
    }
}
