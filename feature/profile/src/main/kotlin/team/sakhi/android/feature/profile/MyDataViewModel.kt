package team.sakhi.android.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.localdb.SharedLocalRecordCodec
import team.sakhi.models.CarePartnership
import team.sakhi.models.ConversationMessage
import team.sakhi.models.CycleData
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserProfile
import team.sakhi.repositories.AIRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PartnerCareRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.DataMigration
import team.sakhi.sync.OfflineUpgradeDataset
import team.sakhi.android.common.toSafeUserMessage

data class MyDataLocalSnapshot(
    val profile: UserProfile? = null,
    val periodLogs: List<PeriodLog> = emptyList(),
    val cycles: List<CycleData> = emptyList(),
    val partnerships: List<CarePartnership> = emptyList(),
    val invitations: List<PartnerInvitation> = emptyList(),
    val aiMessages: List<ConversationMessage> = emptyList(),
)

data class MyDataCloudSnapshot(
    val profile: UserProfile? = null,
    val periodLogs: List<PeriodLog> = emptyList(),
    val cycles: List<CycleData> = emptyList(),
    val partnerships: List<CarePartnership> = emptyList(),
    val invitations: List<PartnerInvitation> = emptyList(),
    val aiMessages: List<ConversationMessage> = emptyList(),
)

data class MyDataUiState(
    val session: SessionContext? = null,
    val localSnapshot: MyDataLocalSnapshot = MyDataLocalSnapshot(),
    val cloudSnapshot: MyDataCloudSnapshot = MyDataCloudSnapshot(),
    val isLoadingLocal: Boolean = true,
    val isLoadingCloud: Boolean = true,
    val isRefreshing: Boolean = false,
    val localError: String? = null,
    val cloudError: String? = null,
    /**
     * True for `offline_*` accounts, which have no Supabase row at all. The cloud
     * section renders an explanatory note instead of an empty snapshot, which would
     * otherwise read as "synced, and the server has nothing".
     */
    val isOfflineAccount: Boolean = false,
)

class MyDataViewModel(
    private val sessionManager: SessionManager,
    private val localStore: SakhiPhaseALocalStore,
    private val userProfileRepository: UserProfileRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val cycleDataRepository: CycleDataRepository,
    private val partnerCareRepository: PartnerCareRepository,
    private val aiRepository: AIRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyDataUiState())
    val uiState: StateFlow<MyDataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                load(session = session, userInitiated = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            load(session = sessionManager.current, userInitiated = true)
        }
    }

    private suspend fun load(session: SessionContext?, userInitiated: Boolean) {
        if (session == null) {
            if (!isStillCurrent(null)) return
            _uiState.value = MyDataUiState(
                session = null,
                isLoadingLocal = false,
                isLoadingCloud = false,
            )
            return
        }

        _uiState.update {
            it.copy(
                session = session,
                isLoadingLocal = true,
                isLoadingCloud = true,
                isRefreshing = userInitiated,
                localError = null,
                cloudError = null,
                isOfflineAccount = false,
            )
        }

        // `supervisorScope`, NOT `coroutineScope`: a failing `async` child propagates
        // its exception to the parent job the moment it throws, long before anyone
        // calls `await()`. Under `coroutineScope` that cancelled this scope and killed
        // the process, so the `runCatching { ...await() }` blocks below were dead code
        // and a single unreachable Supabase table crashed all of Manage Account. iOS's
        // `MyDataView.loadCloud()` just catches and shows `cloud.error`.
        supervisorScope {
            // Real bug found in this session's own critical self-review: iOS's real
            // `MyDataView.swift` always reads `DataManager.shared.currentUserID` --
            // the actual signed-in device owner's own id, never whoever's cycle is
            // currently being *viewed* in care mode. "My Data"/"Manage Account" is a
            // universal, self-only account tool (same as Sign Out, Delete Account),
            // not a lens on the person a partner happens to be viewing right now.
            // Using `targetUserId` here (which differs from `userId` precisely when
            // `!isViewingOwnData`) would show a partner in care mode the PRIMARY
            // USER's complete, unredacted profile/period logs/cycles/AI chat history
            // -- bypassing every granular Logging/Recommendations/Calendar view
            // permission entirely, since this screen has no permission gating of its
            // own at all.
            val userId = session.userId
            val localDeferred = async { loadLocalSnapshot(userId) }
            // An `offline_*` id is not a UUID, so every one of these tables rejects it
            // with `invalid input syntax for type uuid` -- there is no cloud row to
            // read in the first place. Skipping is both correct and offline-first.
            val cloudDeferred = if (DataMigration.isOfflineUserId(userId)) {
                null
            } else {
                async { loadCloudSnapshot(userId) }
            }

            runCatching { localDeferred.await() }
                .onSuccess { snapshot ->
                    if (!isStillCurrent(session)) return@onSuccess
                    _uiState.update {
                        it.copy(
                            localSnapshot = snapshot,
                            isLoadingLocal = false,
                            localError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (!isStillCurrent(session)) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            localError = throwable.toSafeUserMessage(appContext, R.string.profile_my_data_local_load_failed),
                        )
                    }
                }

            if (cloudDeferred == null) {
                if (isStillCurrent(session)) {
                    _uiState.update {
                        it.copy(
                            cloudSnapshot = MyDataCloudSnapshot(),
                            isLoadingCloud = false,
                            isRefreshing = false,
                            cloudError = null,
                            isOfflineAccount = true,
                        )
                    }
                }
                return@supervisorScope
            }

            runCatching { cloudDeferred.await() }
                .onSuccess { snapshot ->
                    if (!isStillCurrent(session)) return@onSuccess
                    _uiState.update {
                        it.copy(
                            cloudSnapshot = snapshot,
                            isLoadingCloud = false,
                            isRefreshing = false,
                            cloudError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (!isStillCurrent(session)) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingCloud = false,
                            isRefreshing = false,
                            cloudError = throwable.toSafeUserMessage(appContext, R.string.profile_my_data_cloud_load_failed),
                        )
                    }
                }
        }
    }

    private suspend fun loadLocalSnapshot(userId: String): MyDataLocalSnapshot {
        val partnerships = localStore.exportRecords(OfflineUpgradeDataset.CARE_PARTNERSHIPS)
            .map(SharedLocalRecordCodec::decodeCarePartnership)
            .filter { it.userId == userId || it.partnerId == userId }
            .sortedByDescending { it.updatedAt }
        val invitations = localStore.exportRecords(OfflineUpgradeDataset.PARTNER_INVITATIONS)
            .map(SharedLocalRecordCodec::decodePartnerInvitation)
            .filter { it.inviterId == userId }
            .sortedByDescending { it.createdAt }
        val aiMessages = localStore.exportRecords(OfflineUpgradeDataset.AI_MESSAGES)
            .map(SharedLocalRecordCodec::decodeConversationMessage)
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }

        return MyDataLocalSnapshot(
            profile = localStore.getUserProfile(userId),
            periodLogs = localStore.getPeriodLogs(userId).sortedByDescending { it.logDate.toString() },
            cycles = localStore.getCycles(userId).sortedByDescending { it.cycleStartDate.toString() },
            partnerships = partnerships,
            invitations = invitations,
            aiMessages = aiMessages,
        )
    }

    private suspend fun loadCloudSnapshot(userId: String): MyDataCloudSnapshot = coroutineScope {
        val profileDeferred = async { userProfileRepository.get(userId).getOrThrow() }
        val logsDeferred = async { periodLogRepository.getAll(userId).getOrThrow() }
        val cyclesDeferred = async { cycleDataRepository.getAll(userId).getOrThrow() }
        val partnershipsDeferred = async { partnerCareRepository.listPartnerships(userId).getOrThrow() }
        val invitationsDeferred = async { partnerCareRepository.fetchInvitationsByInviter(userId).getOrThrow() }
        val aiMessagesDeferred = async { aiRepository.getMessages(userId = userId, page = 0, pageSize = 50).getOrThrow() }

        MyDataCloudSnapshot(
            profile = profileDeferred.await(),
            periodLogs = logsDeferred.await(),
            cycles = cyclesDeferred.await(),
            partnerships = partnershipsDeferred.await(),
            invitations = invitationsDeferred.await(),
            aiMessages = aiMessagesDeferred.await(),
        )
    }

    private fun isStillCurrent(session: SessionContext?): Boolean {
        val current = sessionManager.current
        return current?.userId == session?.userId && current?.targetUserId == session?.targetUserId
    }
}
