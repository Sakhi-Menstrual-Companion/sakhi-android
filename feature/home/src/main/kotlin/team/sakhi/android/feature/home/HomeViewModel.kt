package team.sakhi.android.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import team.sakhi.android.common.CycleDetectionCoordinator
import team.sakhi.android.common.CycleInsightAdapter
import team.sakhi.android.common.LastKnownPhaseStore
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
import team.sakhi.platform.PlatformKeyValueStore
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
 * Exactly the nine fields the hero and the top bar render, and nothing else.
 *
 * `HomeUiState` carries 24 fields, and both `HeroSection` and `HomeTopBar` used to take the
 * whole object. Compose compares what it is handed, so a change to any unrelated field
 * recomposed both of them: a background sync moving `syncState` through Idle -> Syncing ->
 * Success recomposed the hero twice, despite the hero showing nothing about sync. The same
 * went for `isLoadingCycle`, `error`, `currentCycle`, `selectedLog`, `periodLogDates` and the
 * two `partnerSnapshot*` fields.
 *
 * Narrowing to this object means the other fifteen no longer reach either composable. When
 * only they change, the derived `HomeHeroState` compares equal and both surfaces skip.
 *
 * One state for both on purpose: the top bar renders the SAME information as the hero in a
 * collapsed form (that is what `HeroTopBarSubtitle` fades between as you scroll), so they
 * genuinely share one input set rather than being lumped together for convenience.
 *
 * Kept as a plain `data class` for its generated `equals` — that comparison IS the skip check.
 * Every field is a stable type, so `config/compose-stability.conf` already makes this stable
 * without an annotation.
 */
data class HomeHeroState(
    val session: SessionContext? = null,
    val selectedDate: LocalDate = DateConverter.today(),
    val phase: CyclePhase = CyclePhase.UNKNOWN,
    val phaseKind: CyclePhaseInsight.PhaseKind = CyclePhaseInsight.PhaseKind.UNKNOWN,
    val hasCycleData: Boolean = false,
    val hasLoggedForSelectedDate: Boolean = false,
    val prediction: CyclePhaseInsight.PeriodPredictionSnapshot? = null,
    val heroTip: String? = null,
    /**
     * Whether background work is in flight, so the top bar can say "Syncing" instead of
     * showing a phase name the app is still catching up on.
     *
     * A Boolean rather than the full `SyncRuntimeState`: the hero only needs "is something
     * happening", and keeping it a primitive keeps `HomeHeroState` stable, so the rest of the
     * hero still skips recomposition when only unrelated state moves.
     */
    val isSyncing: Boolean = false,
    /**
     * True until the first local read for this session has landed.
     *
     * Distinguishes "she has no cycle data" from "we have not looked yet". Without it the
     * hero rendered its not-started copy on every cold start, so a woman with six logged
     * cycles was told to "start tracking today" for as long as the read took. Empty is a
     * claim about her; unknown is a claim about us, and only one of them was true.
     */
    val isLoading: Boolean = false,
)

/**
 * Projects the hero's slice out of the full state. Cheap enough to call on every
 * recomposition of Home: it copies eight references and allocates one small object, and the
 * point is the `equals` on the result, not avoiding the allocation.
 */
fun HomeUiState.toHeroState(): HomeHeroState = HomeHeroState(
    session = session,
    selectedDate = selectedDate,
    phase = phase,
    phaseKind = phaseKind,
    hasCycleData = hasCycleData,
    hasLoggedForSelectedDate = hasLoggedForSelectedDate,
    prediction = prediction,
    heroTip = heroTip,
    isSyncing = syncState == SyncRuntimeState.Syncing,
    isLoading = isLoadingCycle,
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
    /**
     * Where the cycle engine, the statistics and the log-set building run. Injected rather
     * than hardcoded so tests can hand in their own dispatcher: `Dispatchers.Default` is
     * outside the test scheduler's control, so `advanceUntilIdle()` returns before the work
     * lands and every derived field reads as its default.
     */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Backs `HomeHeroSnapshot`, so Home can open on the values it last showed. */
    private val kvStore: PlatformKeyValueStore = PlatformKeyValueStore(),
) : ViewModel() {

    // Cached engine inputs so `selectDate` can re-ask the SAME engine for another day
    // instead of falling back to a different algorithm.
    private var cachedCycles: List<CycleData> = emptyList()

    /** An empty read was held back because the session's role was not settled yet. */
    private var awaitingSettledRole = false
    private var cachedPeriodLogDates: Set<LocalDate> = emptySet()
    private var cachedStats: team.sakhi.models.CycleStatistics? = null

    /** What one off-main cycle-insight computation produces, so the state write stays a `copy`. */
    private data class ComputedCycleInsight(
        val periodLogDates: Set<LocalDate>,
        val stats: team.sakhi.models.CycleStatistics?,
        val insight: CycleInsightAdapter.Insight,
    )

    // Seeded, not defaulted. Reading the last known state here — synchronously, during
    // construction — is what stops Home drawing a wrong phase colour and "start tracking
    // today" for a frame or two on every launch. See `HomeHeroSnapshot`.
    private val _uiState = MutableStateFlow(
        sessionManager.session.value?.userId
            ?.let { HomeHeroSnapshot.restore(kvStore, it) }
            ?: HomeUiState(),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // The data path reloads on a change of WHO we are looking at, or after a sync has
        // actually landed new rows. It used to combine `syncState` itself, so every state
        // the sync passed through re-ran `refresh()` -- and `refresh()` re-reads every cycle
        // and every log, which for a cloud account is two network round-trips. A single sync
        // emits `Syncing` and then a terminal state, so one sync cost two full reloads of
        // Home, neither of which she asked for.
        //
        // `Success.lastSyncedAt` is the only signal that means "there may be new data", and
        // `distinctUntilChanged` keeps a repeated success from re-triggering. `onStart`
        // seeds it because `combine` waits for every source to emit, and a session that
        // never syncs must still load Home.
        val syncLandings = syncStore.syncState
            .filterIsInstance<SyncRuntimeState.Success>()
            .map { it.lastSyncedAt }
            .distinctUntilChanged()
            .onStart { emit(0L) }

        // Keyed on WHO we are looking at, not on the whole session object.
        //
        // `SessionContext` changes for reasons the cycle data does not care about: the local
        // boot publishes it immediately with an empty name and no invitations, and
        // `refreshPrimaryDetails` fills those in a moment later. Collecting the session
        // directly meant that second, purely cosmetic emission re-ran `refresh()` — a full
        // re-read of every cycle and every log plus a complete engine pass. Together with the
        // sync landing that follows it, one cold start ran the whole Home load THREE times,
        // which showed up on a real device as repeated `SakhiHome insight` lines and ~1s
        // frames while it happened.
        //
        // The session is still read inside `refresh` for names and permissions; this only
        // controls what counts as a reason to reload.
        val dataOwner = sessionManager.session
            .map { it?.targetUserId }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(
                dataOwner,
                syncStore.partnerHealthSnapshot,
                syncLandings,
            ) { _, partnerSnapshot, _ ->
                partnerSnapshot
            }.collectLatest { partnerSnapshot ->
                refresh(sessionManager.session.value, syncStore.syncState.value, partnerSnapshot)
            }
        }

        // An empty read under the provisional session was held back as "still loading" (see
        // `applyCycleInsight`). Once the role is settled, read again: if she really is the
        // primary user the empty result now stands; if this is a partner's phone, the session
        // has already moved to her and this reload is what draws her.
        viewModelScope.launch {
            sessionManager.roleSettled.collect { settled ->
                if (settled && awaitingSettledRole) {
                    awaitingSettledRole = false
                    refresh(sessionManager.session.value, syncStore.syncState.value, syncStore.partnerHealthSnapshot.value)
                }
            }
        }

        // The sync indicator still follows every state, it just no longer drags a full
        // database and network reload along with it.
        viewModelScope.launch {
            syncStore.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
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
            loadCycleInsight(requestedTargetUserId, visiblePartnerSnapshot, session.isViewingOwnData)
            refreshCycleStatistics(requestedTargetUserId, visiblePartnerSnapshot, session.isViewingOwnData)
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
    private suspend fun loadCycleInsight(
        targetUserId: String,
        partnerSnapshot: PartnerHealthSnapshot?,
        isViewingOwnData: Boolean,
    ) {
        // Her data, on a partner's phone, comes from the snapshot and never from this phone's
        // own store. `PeriodLogRepository.getAll` returns whatever is stored locally the moment
        // anything is stored, and asks the server only when the local store is empty, so a
        // partner who had once cached her logs kept them for good: on 2026-09-13 her period on
        // the 13th had been on the server for hours, and so had an edit to her 5 September log,
        // while his Home still drew the old cached copy of both. iOS never showed this because
        // it builds these repositories with no local store at all.
        //
        // The snapshot is the right partner read regardless: the server masks whatever she has
        // not shared, which a direct table read does not.
        if (!isViewingOwnData) {
            val snapshot = partnerSnapshot?.takeIf { it.subjectUserId == targetUserId }
            if (snapshot == null) {
                // Nothing of hers to draw yet, and a cached copy must not stand in for it.
                // Ask for a snapshot; the collector above re-enters here when it lands.
                if (sessionManager.current?.targetUserId != targetUserId) return
                _uiState.update { it.copy(isLoadingCycle = true, error = null) }
                viewModelScope.launch {
                    val refreshed = runCatching { syncStore.refreshPartnerHealth() }
                    if (refreshed.isFailure && sessionManager.current?.targetUserId == targetUserId) {
                        // Offline or refused. Better an honest message than her old numbers.
                        _uiState.update {
                            it.copy(
                                isLoadingCycle = false,
                                error = appContext.getString(R.string.home_load_cycle_failed),
                            )
                        }
                    }
                }
                return
            }
            applyCycleInsight(targetUserId, snapshot.cycles, snapshot.periodLogs)
            return
        }
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

        applyCycleInsight(
            targetUserId = targetUserId,
            cycles = cyclesResult.getOrDefault(emptyList()),
            logs = logsResult.getOrDefault(emptyList()),
        )
    }

    private suspend fun applyCycleInsight(
        targetUserId: String,
        cycles: List<CycleData>,
        logs: List<PeriodLog>,
    ) {
        if (sessionManager.current?.targetUserId != targetUserId) return

        // Nothing found, under a session whose role is not settled yet. On a care partner's
        // phone that session is his own empty record, not hers, so "nothing logged" would be
        // a false statement shown for the few seconds the care lookup takes, and then
        // replaced by her real cycle. Stay on the skeleton; the `roleSettled` collector reads
        // again once the lookup answers (Karan, 2026-09-13).
        if (cycles.isEmpty() && logs.isEmpty() && !sessionManager.roleSettled.value) {
            awaitingSettledRole = true
            _uiState.update { it.copy(isLoadingCycle = true) }
            return
        }

        // Everything below used to run INSIDE `_uiState.update { ... }`, on the main thread.
        // Two problems with that. `MutableStateFlow.update` is a compare-and-set loop, so
        // its lambda re-runs on contention -- and this lambda ran the whole prediction
        // engine plus a full log-line interpolation, so every retry paid for both again.
        // And `viewModelScope.launch` carries no dispatcher, which means `Dispatchers.Main`:
        // the engine, the statistics and the set-building were all competing with drawing.
        // Computed once, off the main thread, the state write is left as a pure `copy`.
        //
        // `selectedDate` is read once here rather than inside the update. A date change
        // while this is in flight is already served by the cached recompute path
        // (`refreshSelectedDateLog` re-asks the same engine from `cachedCycles`).
        val selectedDate = _uiState.value.selectedDate
        val computed = withContext(computeDispatcher) {
            val dates = logs.filter { it.periodPresent }.mapTo(mutableSetOf()) { it.logDate }
            val computedStats = CycleMath.computeStatistics(cycles.filter { it.isComplete })
            ComputedCycleInsight(
                periodLogDates = dates,
                stats = computedStats,
                insight = CycleInsightAdapter.insightFor(
                    date = selectedDate,
                    cycles = cycles,
                    periodLogDates = dates,
                    stats = computedStats,
                    userId = targetUserId,
                ),
            )
        }
        val periodLogDates = computed.periodLogDates
        val stats = computed.stats
        val insight = computed.insight
        cachedCycles = cycles
        cachedPeriodLogDates = periodLogDates
        cachedStats = stats

        homeLog.i {
            "insight for $selectedDate: phase=${insight.phase.kind}, " +
                "cycleDay=${insight.phase.cycleDay}, status=${insight.prediction.status}, " +
                "daysUntil=${insight.prediction.daysUntil}, periodDay=${insight.prediction.periodDay} | " +
                "logs=${logs.size}, present=${periodLogDates.size}, latestPresent=${periodLogDates.maxOrNull()}, " +
                "todayIsPresent=${selectedDate in periodLogDates}, cycles=${cycles.size}"
        }

        _uiState.update {
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

        // Record what Home is now showing, so the NEXT launch opens on these values instead
        // of on defaults. Written only after a successful load, and `save` itself refuses to
        // store a state with no cycle data, so a failed read can never blank the snapshot.
        HomeHeroSnapshot.save(kvStore, targetUserId, _uiState.value)
        // Also recorded one layer up, where the pre-Home loading screens can reach it, so the
        // app opens in this phase's colour instead of flashing brand pink first.
        LastKnownPhaseStore.save(kvStore, targetUserId, _uiState.value.phase)
        // So the splash can find this phase before the auth session has loaded.
        LastKnownPhaseStore.setLastActiveUser(kvStore, targetUserId)
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
    private suspend fun refreshCycleStatistics(
        targetUserId: String,
        partnerSnapshot: PartnerHealthSnapshot?,
        isViewingOwnData: Boolean,
    ) {
        // Same rule: her cycles come from the snapshot, never from this phone's store.
        if (!isViewingOwnData) {
            val snapshot = partnerSnapshot?.takeIf { it.subjectUserId == targetUserId } ?: return
            if (sessionManager.current?.targetUserId != targetUserId) return
            val stats = CycleMath.computeStatistics(snapshot.cycles.filter { it.isComplete })
            _uiState.update {
                it.copy(
                    cyclesAnalyzed = stats.cyclesAnalyzed,
                    shortestCycle = stats.shortestCycle,
                    longestCycle = stats.longestCycle,
                )
            }
            return
        }
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
            userId = session.targetUserId,
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

        if (!session.isViewingOwnData) {
            val snapshot = syncStore.partnerHealthSnapshot.value
                ?.takeIf { it.subjectUserId == targetUserId }
            if (snapshot == null) {
                if (sessionManager.current?.targetUserId != targetUserId) return
                _uiState.update { it.copy(hasLoggedForSelectedDate = false, selectedLog = null) }
                viewModelScope.launch { runCatching { syncStore.refreshPartnerHealth() } }
                return
            }

            if (sessionManager.current?.targetUserId != targetUserId) return
            if (_uiState.value.selectedDate != date) return
            val logs = snapshot.periodLogs.filter { it.logDate == date }
            val visibleLog = logs.firstOrNull()?.sanitizeForHome(session, canViewLoggedDetails)
            homeLog.i {
                "read $date from partner snapshot -> ${logs.size} log(s), " +
                    "hasLogged=${logs.isNotEmpty()}, visibleAfterSanitize=${visibleLog != null}, " +
                    "canViewLoggedDetails=$canViewLoggedDetails, revision=${snapshot.revision}"
            }
            _uiState.update { it.copy(hasLoggedForSelectedDate = logs.isNotEmpty(), selectedLog = visibleLog) }
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
