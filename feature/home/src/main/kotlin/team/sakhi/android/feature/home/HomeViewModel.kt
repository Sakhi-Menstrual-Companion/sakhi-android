package team.sakhi.android.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import team.sakhi.cycle.CycleMath
import team.sakhi.date.DateConverter
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
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
    // Real feature build (2026-07-16): the single shared source of truth for
    // "which day is Home currently showing," matching iOS's real `HomeView`
    // `@State private var selectedDate` -- defaults to today, but changes
    // whenever the user taps a day in the Calendar sheet or resets via the
    // top-bar date label. `phase`/`dayInCycle`/`cycleLength`/
    // `daysUntilNextPeriod`/`selectedLog`/`hasLoggedForSelectedDate` below are
    // ALL computed *for this date*, not hardcoded to "today" -- this is what
    // makes every day-detail card downstream (which just reads these flat
    // fields) automatically date-aware without needing its own changes.
    val selectedDate: LocalDate = DateConverter.today(),
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
    // Port of iOS `HomeActionBar`'s `hasLogged` -- whether `selectedDate` already
    // has a period-log entry, so the bottom-bar log button shows a pencil instead
    // of a `+`. Was a documented KMM/platform gap (no shared signal existed);
    // resolved with a direct `PeriodLogRepository` read for the selected date.
    // Renamed from `hasLoggedToday` (2026-07-16): the field now reflects
    // `selectedDate`, not always today -- matches iOS's real
    // `isSelectedDatePeriodLogged`.
    val hasLoggedForSelectedDate: Boolean = false,
    // Backs the "logged details" card (iOS `loggedDetailsCard`) -- the actual
    // flow/weight/BBT/symptoms/moods for `selectedDate`, not just whether a log
    // exists. Renamed from `todayLog` (2026-07-16) for the same reason above.
    val selectedLog: PeriodLog? = null,
    // Backs the "cycle details" card (iOS `cycleDetailsCard`)'s per-day pill
    // strip + "Started X" label -- the raw cycle record, not pre-derived UI
    // state, so `CalendarMarker.buildMarks` (the same shared per-day marking
    // Calendar already uses) can be called from the composable.
    val currentCycle: CycleData? = null,
    val isLoadingCycle: Boolean = false,
    val error: String? = null,
    // Backs iOS `HomeDayDetailGlassView`'s `cycleStatusTile` (Regular/Irregular
    // badge + "N cycles analysed" detail row inside the Current Cycle card).
    // iOS computes this from the same shared `CycleMath.computeStatistics`
    // Reports/Care already use, then judges regularity itself in the view
    // (`longestCycle - shortestCycle <= 7`) rather than via a KMM helper --
    // matched here rather than reusing `CycleMath.profileHealthStatus` (a
    // different, delayed-period-based algorithm Profile's own "Cycle Health"
    // badge uses), since the two are genuinely different measurements on iOS.
    val cyclesAnalyzed: Int = 0,
    val shortestCycle: Int = 0,
    val longestCycle: Int = 0,
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
    private val appContext: Context,
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
        val canViewCycle = canViewHomeCycle(session)
        val visiblePartnerSnapshot = partnerSnapshot?.takeIf {
            !session.isViewingOwnData &&
                canViewCycle &&
                it.subjectUserId == session.targetUserId
        }

        _uiState.update {
            it.copy(
                session = session,
                syncState = syncState,
                // Real feature build (2026-07-16): reset to today when switching
                // accounts (matches every other targetChanged reset below) --
                // showing "day 45 of a cycle" for a newly-viewed account the
                // moment you switch to it wouldn't make sense.
                selectedDate = if (targetChanged) DateConverter.today() else it.selectedDate,
                canViewPredictions = session.can(Permission.VIEW_PREDICTIONS),
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                phase = if (targetChanged) CyclePhase.UNKNOWN else it.phase,
                dayInCycle = if (targetChanged) null else it.dayInCycle,
                cycleLength = if (targetChanged) null else it.cycleLength,
                daysUntilNextPeriod = if (targetChanged) null else it.daysUntilNextPeriod,
                hasCycleData = if (targetChanged) false else it.hasCycleData,
                hasLoggedForSelectedDate = if (targetChanged) false else it.hasLoggedForSelectedDate,
                selectedLog = if (targetChanged) null else it.selectedLog,
                currentCycle = if (targetChanged) null else it.currentCycle,
                cyclesAnalyzed = if (targetChanged) 0 else it.cyclesAnalyzed,
                shortestCycle = if (targetChanged) 0 else it.shortestCycle,
                longestCycle = if (targetChanged) 0 else it.longestCycle,
                isLoadingCycle = true,
                error = null,
                partnerSnapshotRevision = visiblePartnerSnapshot?.revision,
                partnerSnapshotRefreshedAt = visiblePartnerSnapshot?.refreshedAt,
            )
        }

        val requestedTargetUserId = session.targetUserId
        if (canViewCycle) {
            cycleDataRepository.getLatest(requestedTargetUserId)
                .onSuccess { cycle ->
                    if (sessionManager.current?.targetUserId != requestedTargetUserId) return

                    _uiState.update {
                        // Real feature build (2026-07-16): compute phase/dayInCycle/
                        // daysUntilNextPeriod for `it.selectedDate` (the freshest
                        // known selection at update time, in case the user tapped a
                        // new date while this cycle fetch was in flight), not
                        // always "today" -- the same shared `CycleMath` functions
                        // Calendar already uses, just no longer relying on their
                        // implicit today-default.
                        val date = it.selectedDate
                        it.copy(
                            phase = cycle?.let { c -> CycleMath.currentPhase(c, date) } ?: CyclePhase.UNKNOWN,
                            dayInCycle = cycle?.let { c -> CycleMath.dayOfCycle(c, date) },
                            cycleLength = cycle?.cycleLength,
                            daysUntilNextPeriod = cycle?.let { c -> CycleMath.daysUntilNextPeriod(c, date) },
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
                            error = throwable.message
                                ?: appContext.getString(R.string.home_load_cycle_failed),
                        )
                    }
                }
            refreshCycleStatistics(requestedTargetUserId)
        } else {
            _uiState.update {
                it.copy(
                    phase = CyclePhase.UNKNOWN,
                    dayInCycle = null,
                    cycleLength = null,
                    daysUntilNextPeriod = null,
                    hasCycleData = false,
                    currentCycle = null,
                    cyclesAnalyzed = 0,
                    shortestCycle = 0,
                    longestCycle = 0,
                    isLoadingCycle = false,
                    error = null,
                )
            }
        }

        refreshSelectedDateLog(session, requestedTargetUserId, _uiState.value.selectedDate)
    }

    // iOS `HomeViewModel.statistics(for:)` computes this from the user's *full*
    // cycle history via the same shared `CycleMath.computeStatistics` Reports/
    // Care already call, not just the single latest cycle `refresh()` fetches
    // above -- a separate read is needed here for the same reason.
    private suspend fun refreshCycleStatistics(targetUserId: String) {
        cycleDataRepository.getAll(targetUserId)
            .onSuccess { cycles ->
                if (sessionManager.current?.targetUserId != targetUserId) return
                val stats = CycleMath.computeStatistics(cycles.filter { it.isComplete })
                _uiState.update {
                    it.copy(
                        cyclesAnalyzed = stats.cyclesAnalyzed,
                        shortestCycle = stats.shortestCycle,
                        longestCycle = stats.longestCycle,
                    )
                }
            }
    }

    /**
     * Re-checks `selectedDate`'s log presence without touching cycle/sync
     * state -- `HomeScreen` calls this on lifecycle resume (e.g. returning
     * from the logging sheet after a save), since `refresh()`'s own triggers
     * (session/syncState/partnerSnapshot) don't fire on a plain nav pop.
     * Renamed from `refreshToday` (2026-07-16) now that it re-checks
     * whichever date is currently selected, not always today.
     */
    fun refreshSelectedDate() {
        val session = sessionManager.current ?: return
        viewModelScope.launch {
            refreshSelectedDateLog(session, session.targetUserId, _uiState.value.selectedDate)
        }
    }

    /**
     * Real feature build (2026-07-16): matches iOS's real `HomeView` top-bar
     * date-label toggle (`isToday ? showCalendar = true : selectedDate =
     * Date()`) plus Calendar's day-tap (`onDayTap: { date in selectedDate =
     * date }`, which does NOT dismiss the sheet -- verified directly against
     * `HomeView.swift`/`HomeCalendarSheet.swift`). Recomputes phase/dayInCycle/
     * daysUntilNextPeriod from the already-loaded `currentCycle` as a pure,
     * synchronous computation (no network call needed -- `CycleMath` already
     * supports arbitrary dates), then refreshes just the log-presence read for
     * the newly selected date.
     */
    fun selectDate(date: LocalDate) {
        val session = sessionManager.current ?: return
        if (_uiState.value.selectedDate == date) return
        val cycle = _uiState.value.currentCycle
        _uiState.update {
            it.copy(
                selectedDate = date,
                phase = cycle?.let { c -> CycleMath.currentPhase(c, date) } ?: CyclePhase.UNKNOWN,
                dayInCycle = cycle?.let { c -> CycleMath.dayOfCycle(c, date) },
                daysUntilNextPeriod = cycle?.let { c -> CycleMath.daysUntilNextPeriod(c, date) },
            )
        }
        viewModelScope.launch { refreshSelectedDateLog(session, session.targetUserId, date) }
    }

    private suspend fun refreshSelectedDateLog(
        session: SessionContext,
        targetUserId: String,
        date: LocalDate,
    ) {
        val canViewLoggedDetails = canViewHomeLoggedDetails(session)
        // Real gap found (2026-07-16), cross-checking this feature against the
        // earlier privacy sweep: `LOG_PERIOD` alone was only ever a safe
        // stand-in for "can see today's log presence" (so a partner who logs
        // on someone's behalf doesn't accidentally create a duplicate entry)
        // back when this read was hardcoded to `DateConverter.today()`. Once
        // arbitrary-date browsing landed, that same clause would let a
        // partner with ONLY `LOG_PERIOD` (no view permission at all) tap any
        // day in Calendar's grid -- which isn't gated by `hasAnyCalendarAccess`,
        // see `CalendarScreen.kt`'s `SwipeableMonthPager` -- and learn whether
        // period was logged on that arbitrary past/future date too. Scoping
        // the `LOG_PERIOD` short-circuit back to today only preserves the
        // original narrow behavior and closes the newly-reachable leak.
        val canReadLogPresence = session.isViewingOwnData ||
            canViewLoggedDetails ||
            (session.can(Permission.LOG_PERIOD) && date == DateConverter.today())
        if (!canReadLogPresence) {
            if (sessionManager.current?.targetUserId != targetUserId) return
            _uiState.update { it.copy(hasLoggedForSelectedDate = false, selectedLog = null) }
            return
        }

        periodLogRepository.getForDateRange(userId = targetUserId, from = date, to = date)
            .onSuccess { logs ->
                if (sessionManager.current?.targetUserId != targetUserId) return
                // Guards against a stale, slower-to-resolve fetch for a
                // previously-selected date landing after the user has since
                // moved on to a different date (session staleness alone
                // wouldn't catch this since the account hasn't changed).
                if (_uiState.value.selectedDate != date) return
                val visibleLog = logs.firstOrNull()?.sanitizeForHome(session, canViewLoggedDetails)
                _uiState.update { it.copy(hasLoggedForSelectedDate = logs.isNotEmpty(), selectedLog = visibleLog) }
            }
    }

    private fun canViewHomeCycle(session: SessionContext): Boolean {
        return session.isViewingOwnData ||
            session.can(Permission.VIEW_PREDICTIONS) ||
            session.can(Permission.VIEW_CYCLE_HISTORY)
    }

    private fun canViewHomeLoggedDetails(session: SessionContext): Boolean {
        return session.isViewingOwnData ||
            session.can(Permission.VIEW_DAILY_LOGS) ||
            session.can(Permission.VIEW_SYMPTOMS) ||
            session.can(Permission.VIEW_MOODS) ||
            session.can(Permission.VIEW_WEIGHT) ||
            session.can(Permission.VIEW_TEMPERATURE)
    }

    private fun PeriodLog.sanitizeForHome(
        session: SessionContext,
        canViewLoggedDetails: Boolean,
    ): PeriodLog? {
        if (!canViewLoggedDetails) return null
        if (session.isViewingOwnData) return this

        val visibleFlow = flowIntensity.takeIf { session.can(Permission.VIEW_DAILY_LOGS) }
        val joinedSymptoms = symptoms.joinToString(" ")
        val visibleSymptoms = if (session.can(Permission.VIEW_SYMPTOMS)) {
            symptoms.filter { Symptom.from(it) != null }
        } else {
            emptyList()
        }
        val visibleTokens = buildList {
            if (session.can(Permission.VIEW_WEIGHT)) {
                LogTokenEncoder.decodeWeight(joinedSymptoms)?.let { add(LogTokenEncoder.encodeWeight(it)) }
            }
            if (session.can(Permission.VIEW_TEMPERATURE)) {
                LogTokenEncoder.decodeBbt(joinedSymptoms)?.let { add(LogTokenEncoder.encodeBbt(it)) }
            }
        }
        val visibleMoods = if (session.can(Permission.VIEW_MOODS)) {
            moods.filter { Mood.from(it) != null }
        } else {
            emptyList()
        }

        // Whitelist only the exact fields the Home logged-details card consumes.
        return PeriodLog(
            id = id,
            userId = userId,
            logDate = logDate,
            periodPresent = visibleFlow != null,
            flowIntensity = visibleFlow,
            createdByUserId = "",
            sourceUserId = "",
            symptoms = visibleSymptoms + visibleTokens,
            moods = visibleMoods,
        )
    }
}
