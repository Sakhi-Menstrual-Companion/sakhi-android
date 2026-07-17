package team.sakhi.android.feature.profile

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.localdb.SharedLocalRecordCodec
import team.sakhi.models.CarePartnership
import team.sakhi.models.ConversationMessage
import team.sakhi.models.CycleData
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.PeriodLog
import team.sakhi.models.RelationType
import team.sakhi.models.UserCareRole
import team.sakhi.models.UserProfile
import team.sakhi.repositories.AIRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PartnerCareRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.sync.OfflineUpgradeDataset

@OptIn(ExperimentalCoroutinesApi::class)
class MyDataViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sessionContext(userId: String = "user-1") = SessionContext(
        userId = userId,
        userName = "Asha",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = userId,
        permissions = SessionPermissions.primaryUser,
    )

    private fun profile(userId: String = "user-1") = UserProfile(
        id = userId,
        name = "Asha",
        email = "asha@example.com",
        phone = "9999999999",
        createdAt = "2026-07-10T10:00:00Z",
    )

    private fun periodLog(userId: String = "user-1") = PeriodLog(
        id = "log-1",
        userId = userId,
        logDate = kotlinx.datetime.LocalDate(2026, 7, 12),
        periodPresent = true,
        createdByUserId = userId,
        sourceUserId = userId,
        symptoms = listOf("cramps"),
    )

    private fun cycle(userId: String = "user-1") = CycleData(
        id = "cycle-1",
        userId = userId,
        cycleStartDate = kotlinx.datetime.LocalDate(2026, 7, 1),
        periodStartDate = kotlinx.datetime.LocalDate(2026, 7, 1),
        cycleLength = 29,
        isComplete = true,
    )

    private fun partnership(userId: String = "user-1") = CarePartnership(
        id = "care-1",
        userId = userId,
        partnerId = "partner-1",
        partnerName = "Riya",
    )

    private fun invitation(userId: String = "user-1") = PartnerInvitation(
        id = "invite-1",
        inviterId = userId,
        inviterName = "Asha",
        inviteePhone = "8888888888",
        inviteCode = "ABCD12",
        relationType = RelationType.PARTNER,
    )

    private fun message(userId: String = "user-1") = ConversationMessage(
        id = "msg-1",
        role = "assistant",
        content = "Drink some water today.",
        timestamp = "2026-07-12T09:30:00Z",
        sessionId = userId,
        userId = userId,
        isSynced = true,
    )

    private fun newViewModel(
        sessionManager: SessionManager,
        localStore: SakhiPhaseALocalStore = mockk(),
        userProfileRepository: UserProfileRepository = mockk(),
        periodLogRepository: PeriodLogRepository = mockk(),
        cycleDataRepository: CycleDataRepository = mockk(),
        partnerCareRepository: PartnerCareRepository = mockk(),
        aiRepository: AIRepository = mockk(),
        appContext: Context = mockk {
            every { getString(R.string.profile_my_data_local_load_failed) } returns "local failed"
            every { getString(R.string.profile_my_data_cloud_load_failed) } returns "cloud failed"
        },
    ) = MyDataViewModel(
        sessionManager = sessionManager,
        localStore = localStore,
        userProfileRepository = userProfileRepository,
        periodLogRepository = periodLogRepository,
        cycleDataRepository = cycleDataRepository,
        partnerCareRepository = partnerCareRepository,
        aiRepository = aiRepository,
        appContext = appContext,
    )

    @Test
    fun `no session clears both snapshots and loading state`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }

        val viewModel = newViewModel(sessionManager = sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNull(state.localSnapshot.profile)
        assertTrue(state.localSnapshot.periodLogs.isEmpty())
        assertTrue(state.cloudSnapshot.periodLogs.isEmpty())
        assertFalse(state.isLoadingLocal)
        assertFalse(state.isLoadingCloud)
    }

    @Test
    fun `own session loads local and cloud snapshots from the shared store and repositories`() = runTest {
        val activeSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(activeSession)
            every { current } returns activeSession
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.CARE_PARTNERSHIPS) } returns listOf(
                SharedLocalRecordCodec.carePartnershipEnvelope(partnership()),
            )
            coEvery { it.exportRecords(OfflineUpgradeDataset.PARTNER_INVITATIONS) } returns listOf(
                SharedLocalRecordCodec.partnerInvitationEnvelope(invitation()),
            )
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns listOf(
                SharedLocalRecordCodec.conversationMessageEnvelope(message()),
            )
            coEvery { it.getUserProfile("user-1") } returns profile()
            coEvery { it.getPeriodLogs("user-1") } returns listOf(periodLog())
            coEvery { it.getCycles("user-1") } returns listOf(cycle())
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returnsMany listOf(
                Result.success(profile()),
                Result.failure(RuntimeException("network down")),
            )
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(periodLog()))
        }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(cycle()))
        }
        val partnerCareRepository = mockk<PartnerCareRepository>().also {
            coEvery { it.listPartnerships("user-1") } returns Result.success(listOf(partnership()))
            coEvery { it.fetchInvitationsByInviter("user-1") } returns Result.success(listOf(invitation()))
        }
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getMessages("user-1", 0, 50) } returns Result.success(listOf(message()))
        }

        val viewModel = newViewModel(
            sessionManager = sessionManager,
            localStore = localStore,
            userProfileRepository = userProfileRepository,
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
            partnerCareRepository = partnerCareRepository,
            aiRepository = aiRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Asha", state.localSnapshot.profile?.name)
        assertEquals(1, state.localSnapshot.periodLogs.size)
        assertEquals(1, state.localSnapshot.cycles.size)
        assertEquals(1, state.localSnapshot.partnerships.size)
        assertEquals(1, state.localSnapshot.invitations.size)
        assertEquals(1, state.localSnapshot.aiMessages.size)
        assertEquals("Asha", state.cloudSnapshot.profile?.name)
        assertEquals(1, state.cloudSnapshot.periodLogs.size)
        assertEquals(1, state.cloudSnapshot.cycles.size)
        assertEquals(1, state.cloudSnapshot.partnerships.size)
        assertEquals(1, state.cloudSnapshot.invitations.size)
        assertEquals(1, state.cloudSnapshot.aiMessages.size)
        assertNull(state.localError)
        assertNull(state.cloudError)
        assertFalse(state.isLoadingLocal)
        assertFalse(state.isLoadingCloud)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `partner session in care mode loads MY DATA for the partner's own id, not the viewed primary user`() = runTest {
        // Real bug found in this session's own critical self-review: iOS's real
        // `MyDataView.swift` always reads `DataManager.shared.currentUserID` -- the
        // actual signed-in device owner's own id, never whoever's cycle a partner
        // happens to be viewing in care mode. "My Data" is a universal, self-only
        // account tool (same family as Sign Out/Delete Account), not a lens on
        // the person being cared for. The bug used `session.targetUserId` (which
        // diverges from `session.userId` precisely when `!isViewingOwnData`),
        // which would have shown a partner the PRIMARY USER's complete, unredacted
        // profile/period logs/cycles/AI chat history -- bypassing every granular
        // Logging/Recommendations/Calendar view permission, since this screen has
        // no permission gating of its own. Every other test in this file uses
        // `sessionContext()`'s default `targetUserId = userId` (always self-viewing)
        // and would pass under the old buggy code too -- this is the one that
        // would have failed.
        val partnerSession = SessionContext(
            userId = "partner-1",
            userName = "Riya",
            activeRole = UserCareRole.PARTNER,
            targetUserId = "primary-1",
            permissions = SessionPermissions.primaryUser,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(partnerSession)
            every { current } returns partnerSession
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.CARE_PARTNERSHIPS) } returns emptyList()
            coEvery { it.exportRecords(OfflineUpgradeDataset.PARTNER_INVITATIONS) } returns emptyList()
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns emptyList()
            coEvery { it.getUserProfile("partner-1") } returns profile(userId = "partner-1")
            coEvery { it.getPeriodLogs("partner-1") } returns emptyList()
            coEvery { it.getCycles("partner-1") } returns emptyList()
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("partner-1") } returns Result.success(profile(userId = "partner-1"))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getAll("partner-1") } returns Result.success(emptyList())
        }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("partner-1") } returns Result.success(emptyList())
        }
        val partnerCareRepository = mockk<PartnerCareRepository>().also {
            coEvery { it.listPartnerships("partner-1") } returns Result.success(emptyList())
            coEvery { it.fetchInvitationsByInviter("partner-1") } returns Result.success(emptyList())
        }
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getMessages("partner-1", 0, 50) } returns Result.success(emptyList())
        }

        val viewModel = newViewModel(
            sessionManager = sessionManager,
            localStore = localStore,
            userProfileRepository = userProfileRepository,
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
            partnerCareRepository = partnerCareRepository,
            aiRepository = aiRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.localError)
        assertNull(state.cloudError)
        assertEquals("partner-1", state.localSnapshot.profile?.id)
        assertEquals("partner-1", state.cloudSnapshot.profile?.id)
    }

    @Test
    fun `failed cloud refresh preserves the last good cloud snapshot and clears refreshing`() = runTest {
        val activeSession = sessionContext()
        val currentSession = arrayOf<SessionContext?>(activeSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(activeSession)
            every { current } answers { currentSession[0] }
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(any()) } returns emptyList()
            coEvery { it.getUserProfile("user-1") } returns profile()
            coEvery { it.getPeriodLogs("user-1") } returns emptyList()
            coEvery { it.getCycles("user-1") } returns emptyList()
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returns Result.success(profile())
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(emptyList())
        }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(emptyList())
        }
        val partnerCareRepository = mockk<PartnerCareRepository>().also {
            coEvery { it.listPartnerships("user-1") } returns Result.success(emptyList())
            coEvery { it.fetchInvitationsByInviter("user-1") } returns Result.success(emptyList())
        }
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getMessages("user-1", 0, 50) } returns Result.success(emptyList())
        }

        val viewModel = newViewModel(
            sessionManager = sessionManager,
            localStore = localStore,
            userProfileRepository = userProfileRepository,
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
            partnerCareRepository = partnerCareRepository,
            aiRepository = aiRepository,
        )
        advanceUntilIdle()

        assertEquals("Asha", viewModel.uiState.value.cloudSnapshot.profile?.name)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Asha", state.cloudSnapshot.profile?.name)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoadingCloud)
    }
}
