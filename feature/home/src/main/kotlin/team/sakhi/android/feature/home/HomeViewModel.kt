package team.sakhi.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.cycle.CycleMath
import team.sakhi.date.DateConverter
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PartnerHealthSnapshot
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.SyncRuntimeState
import team.sakhi.sync.SyncStore

data class HomeUiState(
    val session: SessionContext? = null,
    val syncState: SyncRuntimeState = SyncRuntimeState.Idle,
    val phase: CyclePhase = CyclePhase.UNKNOWN,
    val dayInCycle: Int? = null,
    // Built on the same CycleMath primitives CalendarViewModel already uses (not the
    // parallel, currently-unused CyclePhaseInsight engine) so Home and Calendar never
    // disagree about which phase a given day is in.
    val cycleLength: Int? = null,
    val daysUntilNextPeriod: Int? = null,
    val hasCycleData: Boolean = false,
    val canViewPredictions: Boolean = false,
    val canLogPeriod: Boolean = false,
    // Port of iOS `HomeActionBar`'s `hasLogged` -- whether today already has a
    // period-log entry, so the bottom-bar log button shows a pencil instead of
    // a `+`. Was a documented KMM/platform gap (no shared signal existed);
    // resolved with a direct `PeriodLogRepository` read for today's date.
    val hasLoggedToday: Boolean = false,
    // Backs the "logged details" card (iOS `loggedDetailsCard`) -- the actual
    // flow/weight/BBT/symptoms/moods for today, not just whether a log exists.
    val todayLog: PeriodLog? = null,
    // Backs the "cycle details" card (iOS `cycleDetailsCard`)'s per-day pill
    // strip + "Started X" label -- the raw cycle record, not pre-derived UI
    // state, so `CalendarMarker.buildMarks` (the same shared per-day marking
    // Calendar already uses) can be called from the composable.
    val currentCycle: CycleData? = null,
    val isLoadingCycle: Boolean = false,
    val error: String? = null,
    val partnerSnapshotRevision: Long? = null,
    val partnerSnapshotRefreshedAt: String? = null,
)

/**
 * Thin home-state adapter over KMM session and sync state. The only derived values
 * are the current phase and day-in-cycle, both computed through shared `CycleMath`.
 */
class HomeViewModel(
    private val sessionManager: SessionManager,
    private val syncStore: SyncStore,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.session,
                syncStore.syncState,
                syncStore.partnerHealthSnapshot,
            ) { session, syncState, partnerSnapshot ->
                Triple(session, syncState, partnerSnapshot)
            }.collectLatest { (session, syncState, partnerSnapshot) ->
                refresh(session, syncState, partnerSnapshot)
            }
        }
    }

    private suspend fun refresh(
        session: SessionContext?,
        syncState: SyncRuntimeState,
        partnerSnapshot: PartnerHealthSnapshot?,
    ) {
        if (session == null) {
            _uiState.value = HomeUiState(syncState = syncState)
            return
        }

        val previousSession = _uiState.value.session
        val targetChanged = previousSession?.targetUserId != session.targetUserId ||
            previousSession?.isViewingOwnData != session.isViewingOwnData

        _uiState.update {
            it.copy(
                session = session,
                syncState = syncState,
                canViewPredictions = session.can(Permission.VIEW_PREDICTIONS),
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                phase = if (targetChanged) CyclePhase.UNKNOWN else it.phase,
                dayInCycle = if (targetChanged) null else it.dayInCycle,
                cycleLength = if (targetChanged) null else it.cycleLength,
                daysUntilNextPeriod = if (targetChanged) null else it.daysUntilNextPeriod,
                hasCycleData = if (targetChanged) false else it.hasCycleData,
                hasLoggedToday = if (targetChanged) false else it.hasLoggedToday,
                todayLog = if (targetChanged) null else it.todayLog,
                currentCycle = if (targetChanged) null else it.currentCycle,
                isLoadingCycle = true,
                error = null,
                partnerSnapshotRevision = if (session.isViewingOwnData) null else partnerSnapshot?.revision,
                partnerSnapshotRefreshedAt = if (session.isViewingOwnData) null else partnerSnapshot?.refreshedAt,
            )
        }

        val requestedTargetUserId = session.targetUserId
        cycleDataRepository.getLatest(requestedTargetUserId)
            .onSuccess { cycle ->
                if (sessionManager.current?.targetUserId != requestedTargetUserId) return

                _uiState.update {
                    it.copy(
                        phase = cycle?.let(CycleMath::currentPhase) ?: CyclePhase.UNKNOWN,
                        dayInCycle = cycle?.let(CycleMath::dayOfCycle),
                        cycleLength = cycle?.cycleLength,
                        daysUntilNextPeriod = cycle?.let(CycleMath::daysUntilNextPeriod),
                        hasCycleData = cycle != null,
                        currentCycle = cycle,
                        isLoadingCycle = false,
                        error = null,
                    )
                }
            }
            .onFailure { throwable ->
                if (sessionManager.current?.targetUserId != requestedTargetUserId) return

                _uiState.update {
                    it.copy(
                        phase = CyclePhase.UNKNOWN,
                        dayInCycle = null,
                        cycleLength = null,
                        daysUntilNextPeriod = null,
                        hasCycleData = false,
                        isLoadingCycle = false,
                        error = throwable.message ?: "Failed to load cycle data",
                    )
                }
            }

        refreshHasLoggedToday(requestedTargetUserId)
    }

    /**
     * Re-checks today's log presence without touching cycle/sync state --
     * `HomeScreen` calls this on lifecycle resume (e.g. returning from the
     * logging sheet after a save), since `refresh()`'s own triggers
     * (session/syncState/partnerSnapshot) don't fire on a plain nav pop.
     */
    fun refreshToday() {
        val session = sessionManager.current ?: return
        viewModelScope.launch { refreshHasLoggedToday(session.targetUserId) }
    }

    private suspend fun refreshHasLoggedToday(targetUserId: String) {
        val today = DateConverter.today()
        periodLogRepository.getForDateRange(userId = targetUserId, from = today, to = today)
            .onSuccess { logs ->
                if (sessionManager.current?.targetUserId != targetUserId) return
                _uiState.update { it.copy(hasLoggedToday = logs.isNotEmpty(), todayLog = logs.firstOrNull()) }
            }
    }
}
