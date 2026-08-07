package team.sakhi.android.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import team.sakhi.android.common.CycleDetectionCoordinator
import team.sakhi.android.common.CycleInsightAdapter
import team.sakhi.cycle.CycleMath
import team.sakhi.cycle.CyclePhaseInsight
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
import team.sakhi.repositories.RecommendationRepository
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
    // The shared engine's own answers, kept structured rather than pre-flattened to a
    // string so the UI can render exactly the same cases iOS does (in-period vs
    // upcoming vs delayed vs no-data). `phaseKind` also drives Home's background
    // colour, matching iOS's per-phase palette.
    val phaseKind: CyclePhaseInsight.PhaseKind = CyclePhaseInsight.PhaseKind.UNKNOWN,
    val prediction: CyclePhaseInsight.PeriodPredictionSnapshot? = null,
    // Logged period days, so the Current Cycle pill strip can mark its days from the
    // same shared engine the calendar uses instead of deriving them from CycleData.
    val periodLogDates: Set<LocalDate> = emptySet(),
    /** One-line phase tip for the hero pill, from the shared engine (iOS `heroTip`). */
    val heroTip: String? = null,
    val partnerSnapshotRevision: Long? = null,
    val partnerSnapshotRefreshedAt: String? = null,
)

/**
 * Thin home-state adapter over KMM session and sync state. The only derived values
 * are the current phase and day-in-cycle, both computed through shared `CycleMath`.
 */
/** Home data-flow trace: `adb logcat -s SakhiHome`. No health values are logged, only counts and flags. */
private val homeLog = Logger.withTag("SakhiHome")

class HomeViewModel(
    private val sessionManager: SessionManager,
    private val syncStore: SyncStore,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val cycleDetectionCoordinator: CycleDetectionCoordinator,
    private val recommendationRepository: RecommendationRepository,
    private val appContext: Context,
) : ViewModel() {

    // Cached engine inputs so `selectDate` can re-ask the SAME engine for another day
    // instead of falling back to a different algorithm.
    private var cachedCycles: List<CycleData> = emptyList()
    private var cachedPeriodLogDates: Set<LocalDate> = emptySet()
    private var cachedStats: team.sakhi.models.CycleStatistics? = null

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
                phaseKind = if (targetChanged) CyclePhaseInsight.PhaseKind.UNKNOWN else it.phaseKind,
                prediction = if (targetChanged) null else it.prediction,
                periodLogDates = if (targetChanged) emptySet() else it.periodLogDates,
                heroTip = if (targetChanged) null else it.heroTip,
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
            loadCycleInsight(requestedTargetUserId)
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

    /**
     * Loads phase and next-period prediction from the shared KMM engine — the same
     * `CyclePhaseInsight` iOS's Home uses, via [CycleInsightAdapter].
     *
     * Replaces the previous `CycleMath.currentPhase` / `daysUntilNextPeriod` path.
     * Those are a different algorithm answering a different question, which is why
     * Android and iOS disagreed on identical data (iOS: "Day 1 of your period /
     * Menstrual Phase", Android: "Luteal phase / 207 days" for the same account on
     * 2026-08-01). `CycleMath` also cannot produce a countdown for an in-progress
     * cycle at all, since it needs a `cycleLength` that only exists once the cycle has
     * closed.
     *
     * Needs the full log history and every cycle, not just the latest one: the engine
     * decides "am I inside a period right now" from the logged days themselves, which
     * is exactly the case a single-latest-cycle read cannot see.
     */
    private suspend fun loadCycleInsight(targetUserId: String) {
        val cyclesResult = cycleDataRepository.getAll(targetUserId)
        val logsResult = periodLogRepository.getAll(targetUserId)

        val failure = cyclesResult.exceptionOrNull() ?: logsResult.exceptionOrNull()
        if (failure != null) {
            if (sessionManager.current?.targetUserId != targetUserId) return
            homeLog.w { "cycle insight load FAILED: ${failure.message}" }
            _uiState.update {
                it.copy(
                    phase = CyclePhase.UNKNOWN,
                    phaseKind = CyclePhaseInsight.PhaseKind.UNKNOWN,
                    prediction = null,
                    heroTip = null,
                    dayInCycle = null,
                    cycleLength = null,
                    daysUntilNextPeriod = null,
                    hasCycleData = false,
                    isLoadingCycle = false,
                    // NEVER surface `failure.message` directly. Supabase/Ktor exception
                    // messages embed the full request URL, the `Authorization: Bearer …`
                    // header and the apikey -- those were rendering verbatim on Home as
                    // user-visible red text (seen on a real device). Show the app's own
                    // copy instead; the raw cause still goes to the log below for
                    // debugging.
                    error = appContext.getString(R.string.home_load_cycle_failed),
                )
            }
            return
        }

        val cycles = cyclesResult.getOrDefault(emptyList())
        val logs = logsResult.getOrDefault(emptyList())
        if (sessionManager.current?.targetUserId != targetUserId) return

        val periodLogDates = logs.filter { it.periodPresent }.mapTo(mutableSetOf()) { it.logDate }
        val stats = CycleMath.computeStatistics(cycles.filter { it.isComplete })
        cachedCycles = cycles
        cachedPeriodLogDates = periodLogDates
        cachedStats = stats

        _uiState.update {
            // Computed for `it.selectedDate`, not always today, so browsing to another
            // day in Calendar re-answers the same questions for that day.
            val insight = CycleInsightAdapter.insightFor(
                date = it.selectedDate,
                cycles = cycles,
                periodLogDates = periodLogDates,
                stats = stats,
            )
            homeLog.i {
                "insight for ${it.selectedDate}: phase=${insight.phase.kind}, " +
                    "cycleDay=${insight.phase.cycleDay}, status=${insight.prediction.status}, " +
                    "daysUntil=${insight.prediction.daysUntil}, periodDay=${insight.prediction.periodDay} | " +
                    "logs=${logs.size}, present=${periodLogDates.size}, latestPresent=${periodLogDates.maxOrNull()}, " +
                    "todayIsPresent=${it.selectedDate in periodLogDates}, cycles=${cycles.size}"
            }
            it.copy(
                phase = insight.phase.kind.toCyclePhase(),
                phaseKind = insight.phase.kind,
                prediction = insight.prediction,
                periodLogDates = periodLogDates,
                heroTip = heroTipFor(insight, it.hasLoggedForSelectedDate),
                dayInCycle = insight.phase.cycleDay.takeIf { day -> day > 0 },
                cycleLength = cycles.firstOrNull()?.cycleLength,
                daysUntilNextPeriod = insight.prediction.daysUntil.takeIf { _ ->
                    insight.prediction.status == CyclePhaseInsight.PredictionStatusKind.UPCOMING
                },
                hasCycleData = cycles.isNotEmpty() || periodLogDates.isNotEmpty(),
                currentCycle = cycles.firstOrNull(),
                isLoadingCycle = false,
                error = null,
            )
        }
    }

    /**
     * Hero tip, sourced the way iOS's `HomeDayDetailGlassView.heroTip` sources it:
     * `recoVM.phaseTips.first ?? RecommendationRepository.syncTips(for:).first`, with a
     * log nudge taking priority while she is in her period window but has not logged
     * today ("Please remember to log").
     *
     * Deliberately NOT the engine's `analyzePhase(...).shortTip`, which was the first
     * thing tried here: that field is terse ("Rest well") where iOS shows the curated
     * copy ("A heating pad can ease cramps significantly"). Same phase, different text,
     * so the platforms visibly disagreed.
     */
    private fun heroTipFor(
        insight: CycleInsightAdapter.Insight,
        hasLoggedToday: Boolean,
    ): String? {
        val inPeriodWindow = insight.prediction.status == CyclePhaseInsight.PredictionStatusKind.IN_PERIOD ||
            insight.prediction.status == CyclePhaseInsight.PredictionStatusKind.TODAY
        if (inPeriodWindow && !hasLoggedToday) {
            return appContext.getString(R.string.home_hero_tip_log_reminder)
        }
        val phase = insight.phase.kind.toCyclePhase()
        return recommendationRepository.getCuratedRecommendations(phase).tips.firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Maps the engine's phase to the app-wide [CyclePhase].
     *
     * PMS collapses to LUTEAL because that is what it physiologically is — iOS does the
     * same (`kind == .pms -> CyclePhaseInfo(phase: .luteal, name: "PMS Phase")`),
     * keeping the distinction in the label rather than the phase enum.
     */
    private fun CyclePhaseInsight.PhaseKind.toCyclePhase(): CyclePhase = when (this) {
        CyclePhaseInsight.PhaseKind.MENSTRUAL -> CyclePhase.MENSTRUAL
        CyclePhaseInsight.PhaseKind.FOLLICULAR -> CyclePhase.FOLLICULAR
        CyclePhaseInsight.PhaseKind.OVULATION -> CyclePhase.OVULATION
        CyclePhaseInsight.PhaseKind.LUTEAL, CyclePhaseInsight.PhaseKind.PMS -> CyclePhase.LUTEAL
        CyclePhaseInsight.PhaseKind.DELAYED -> CyclePhase.DELAYED
        CyclePhaseInsight.PhaseKind.UNKNOWN -> CyclePhase.UNKNOWN
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
     * Full reload after the user logs something, including the cycle.
     *
     * [refreshSelectedDate] deliberately does not touch cycle state, which is correct
     * for a plain nav pop but wrong after a log save: logging a period can create or
     * move a whole cycle. Using the narrow refresh there was why Home appeared frozen
     * after logging — `hasLoggedForSelectedDate` updated (so the log button's pencil
     * icon flipped, the one thing that did visibly change) while `currentCycle`,
     * `phase`, and `dayInCycle` kept their pre-log values.
     */
    fun refreshAfterLogChange() {
        val session = sessionManager.current ?: return
        viewModelScope.launch {
            refresh(
                session = session,
                syncState = syncStore.syncState.value,
                partnerSnapshot = syncStore.partnerHealthSnapshot.value,
            )
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
        // Re-ask the same shared engine for the newly selected day. Previously this
        // recomputed via `CycleMath`, so tapping a day in Calendar could report a
        // different phase than the hero above it was showing for the same date.
        val insight = CycleInsightAdapter.insightFor(
            date = date,
            cycles = cachedCycles,
            periodLogDates = cachedPeriodLogDates,
            stats = cachedStats,
        )
        _uiState.update {
            it.copy(
                selectedDate = date,
                phase = insight.phase.kind.toCyclePhase(),
                phaseKind = insight.phase.kind,
                prediction = insight.prediction,
                heroTip = heroTipFor(insight, it.hasLoggedForSelectedDate),
                dayInCycle = insight.phase.cycleDay.takeIf { day -> day > 0 },
                daysUntilNextPeriod = insight.prediction.daysUntil.takeIf { _ ->
                    insight.prediction.status == CyclePhaseInsight.PredictionStatusKind.UPCOMING
                },
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
                homeLog.i {
                    "read $date -> ${logs.size} log(s), hasLogged=${logs.isNotEmpty()}, " +
                        "visibleAfterSanitize=${visibleLog != null}, canViewLoggedDetails=$canViewLoggedDetails"
                }
                _uiState.update { it.copy(hasLoggedForSelectedDate = logs.isNotEmpty(), selectedLog = visibleLog) }
            }
            // Previously absent, which is why a failed read looked identical to
            // "nothing logged": the state was simply left untouched and nothing
            // was reported anywhere. Home still must not invent data on a failed
            // read, so the state stays as-is, but the failure is no longer silent.
            .onFailure { throwable ->
                homeLog.w { "read $date FAILED, Home left unchanged: ${throwable.message}" }
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
