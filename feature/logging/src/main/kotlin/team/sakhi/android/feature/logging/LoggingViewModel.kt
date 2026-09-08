package team.sakhi.android.feature.logging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import team.sakhi.date.DateConverter
import team.sakhi.logging.DischargeColor
import team.sakhi.logging.LogDiffer
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.logging.Mood
import team.sakhi.logging.PeriodLogPolicy
import team.sakhi.logging.Symptom
import team.sakhi.logging.SymptomCategory
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserCareRole
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import co.touchlab.kermit.Logger
import team.sakhi.android.common.CycleDetectionCoordinator
import team.sakhi.notifications.NotificationRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.SyncStore
import team.sakhi.sync.DataMigration
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.android.common.CycleInsightAdapter
import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.android.common.toSafeUserMessage

data class LoggingUiState(
    val session: SessionContext? = null,
    val selectedDate: LocalDate = DateConverter.today(),
    val selectedFlow: FlowIntensity? = null,
    val selectedMoods: Set<Mood> = emptySet(),
    val selectedSymptoms: Set<Symptom> = emptySet(),
    val weightKg: Double? = null,
    val bbtCelsius: Double? = null,
    val dischargeColor: DischargeColor? = null,
    val painkillerTaken: Boolean = false,
    val doctorVisited: Boolean = false,
    val notes: String = "",
    val canLogPeriod: Boolean = false,
    val canEditMoods: Boolean = false,
    val canEditSymptoms: Boolean = false,
    val canEditNotes: Boolean = false,
    // Matches iOS `HomeLoggingSheet`'s per-section `Feature(.weight/.temperature/
    // .dailyLogs/.discharge/.medications)` gates -- each hides its whole section
    // (not just disables it) when the viewer lacks the specific granular
    // permission, independent of the coarser `canEditSymptoms`.
    val canViewWeight: Boolean = false,
    val canViewTemperature: Boolean = false,
    val canViewDailyLogs: Boolean = false,
    val canViewDischarge: Boolean = false,
    val canViewMedications: Boolean = false,
    val canMutateSelectedDate: Boolean = false,
    /**
     * Phase name shown under the date in the sheet header, matching iOS's
     * `Text(cyclePhase.name)`. Null until the cycle read resolves, so the header simply
     * shows the date rather than flashing a placeholder phase.
     */
    val phaseName: String? = null,
    val isLoadingEntry: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val error: String? = null,
    // Identifies which `save()` call produced the current `error`/`saveMessage`.
    // A fast failure (e.g. an immediate offline DNS error) can set `isSaving`
    // true and then false again within the same StateFlow emission window --
    // StateFlow only guarantees delivery of the latest value to a collector,
    // so a UI effect keyed on `isSaving` transitioning true->false can miss
    // that intermediate frame entirely and never notice a save happened at
    // all. Comparing this id (bumped once per `save()` invocation) instead of
    // watching for an `isSaving` transition lets the UI reliably detect "a new
    // attempt just failed" from the final state alone, regardless of whether
    // any intermediate frame was actually observed.
    val saveAttemptId: Int = 0,
    /**
     * The [saveAttemptId] of the last save that SUCCEEDED. Monotonic, and never cleared.
     *
     * The sheet used to dismiss on `saveMessage != null`, which does not survive
     * conflation: the success path sets the message and then calls `loadEntry`, which clears
     * it again. While `loadEntry` read from the network there was a real gap and Compose
     * observed the message in between. Once that read became local it stopped being
     * observable at all — both writes landed in one `MutableStateFlow` window, the collector
     * only ever saw `null`, and the sheet stayed open over a save that had actually worked.
     *
     * A value that gets cleared is the wrong shape for "this happened". This one only ever
     * moves forward, so no amount of conflation can hide it.
     */
    val savedAttemptId: Int = 0,
    // Matches iOS's real `LoggingViewModel.isSavingYearSelection` -- Calendar's
    // year-view multi-select "Edit Period Dates" bar reads this for its own
    // save-button spinner, independent of the single-date `isSaving` flag above.
    val isSavingYearSelection: Boolean = false,
) {
    /**
     * Matches iOS's real `LoggingViewModel.hasAnyData` (checks every real logged
     * field, not just flow) -- used by Calendar's quick-log bar to decide between
     * a "+" (nothing logged for this date yet) and a pencil (something already
     * logged) icon, the same distinction Home's own quick-log bar already makes
     * via `HomeUiState.hasLoggedForSelectedDate`.
     */
    val hasAnyData: Boolean
        get() = selectedFlow != null ||
            selectedMoods.isNotEmpty() ||
            selectedSymptoms.isNotEmpty() ||
            notes.isNotBlank() ||
            weightKg != null ||
            bbtCelsius != null ||
            dischargeColor != null ||
            painkillerTaken ||
            doctorVisited
}

/**
 * Thin logging adapter over shared logging policy plus `PeriodLogRepository`.
 * The shared layer does not yet expose a single high-level "save daily log" API,
 * so this ViewModel only orchestrates the existing KMM validation, mutation, and
 * stable-id helpers without re-implementing cycle or merge rules on Android.
 */
/** Log-save trace: `adb logcat -s SakhiLogSave`. No health values are logged, only outcome and counts. */
private val logSaveLog = Logger.withTag("SakhiLogSave")

class LoggingViewModel(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val periodLogRepository: PeriodLogRepository,
    private val hapticManager: AndroidHapticManager,
    private val widgetSnapshotManager: AndroidWidgetSnapshotManager,
    private val cycleDetectionCoordinator: CycleDetectionCoordinator,
    // Needed only for the header's phase line: iOS's logging sheet shows
    // `cyclePhase.name` under the date, and the phase cannot be derived from logs alone.
    private val cycleDataRepository: CycleDataRepository,
    /** Used to tell Home that a background cycle rebuild has landed. See `save()`. */
    private val syncStore: SyncStore,
    /** Wakes an active care partner's device after a save. See [notifyCarePartnerOfLogChange]. */
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(DateConverter.today())
    private val _uiState = MutableStateFlow(LoggingUiState())
    val uiState: StateFlow<LoggingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.session,
                selectedDate,
            ) { session, date ->
                session to date
            }.collectLatest { (session, date) ->
                loadEntry(session, date)
                loadPhaseName(session, date)
            }
        }
    }

    fun showPreviousDay() {
        selectedDate.update { DateConverter.subtractDays(it, 1) }
    }

    fun showNextDay() {
        selectedDate.update { DateConverter.addDays(it, 1) }
    }

    /**
     * Jumps directly to an arbitrary date, matching iOS's real
     * `calendarLogVM.currentDate = date` assignment (`HomeCalendarSheet.swift`'s
     * `.onChange(of: selectedDate)`) -- needed so Calendar's own quick-log bar and
     * "Log" button operate on whichever date is currently selected there, not
     * always today. The existing `combine(session, selectedDate) { ... }
     * .collectLatest { loadEntry(...) }` pipeline already reacts to this the same
     * way it reacts to `showPreviousDay`/`showNextDay`, so no separate reload call
     * is needed the way iOS's manual `reloadLog()` requires.
     */
    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun onFlowSelected(flow: FlowIntensity?) {
        _uiState.update {
            it.copy(
                selectedFlow = flow,
                error = null,
                saveMessage = null,
            )
        }
    }

    fun toggleMood(mood: Mood) {
        // Real bug found writing this ViewModel's own test coverage: unlike every
        // sibling toggle here (toggleSymptom/togglePainkillerTaken/
        // toggleDoctorVisited), this used to fire the haptic unconditionally,
        // even when `canEditMoods` blocked the edit -- giving a partner without
        // mood-edit access false tactile feedback that something happened.
        // iOS's real `toggleMood(_:)` (LoggingViewModel.swift) `guard`-returns
        // before ever reaching `HapticManager.shared.selection()`, so a blocked
        // edit is silent there too.
        //
        // Second, more serious bug found in this session's own critical self-
        // review: this was still missing the `isViewingOwnData == false` block
        // every sibling toggle below has. iOS's real `toggleMood(_:)` guards on
        // `!isPartnerView` UNCONDITIONALLY -- mood editing is user-only there,
        // full stop, regardless of any granted view permission. `canEditMoods`
        // being computed as `isViewingOwnData || can(VIEW_MOODS)` meant a
        // partner granted only *view* access to moods could still actually
        // mutate (and, via `mergedMoods` at save time, persist) the primary
        // user's mood entries -- the exact class of privacy bug already found
        // and fixed once this session for the coarse-permission-flag issue.
        var didToggle = false
        _uiState.update { state ->
            if (!state.canEditMoods || state.session?.isViewingOwnData == false) return@update state
            didToggle = true
            state.copy(
                selectedMoods = state.selectedMoods.toggle(mood),
                error = null,
                saveMessage = null,
            )
        }
        if (didToggle) hapticManager.selection()
    }

    fun toggleSymptom(symptom: Symptom) {
        var didToggle = false
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            didToggle = true
            state.copy(
                selectedSymptoms = state.selectedSymptoms.toggle(symptom),
                error = null,
                saveMessage = null,
            )
        }
        if (didToggle) hapticManager.selection()
    }

    fun onWeightChanged(kg: Double?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            state.copy(weightKg = kg, error = null, saveMessage = null)
        }
    }

    fun onBbtChanged(celsius: Double?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            state.copy(bbtCelsius = celsius, error = null, saveMessage = null)
        }
    }

    fun onDischargeColorSelected(color: DischargeColor?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            state.copy(
                dischargeColor = if (state.dischargeColor == color) null else color,
                error = null,
                saveMessage = null,
            )
        }
    }

    fun togglePainkillerTaken() {
        var didToggle = false
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            didToggle = true
            state.copy(painkillerTaken = !state.painkillerTaken, error = null, saveMessage = null)
        }
        if (didToggle) hapticManager.selection()
    }

    fun toggleDoctorVisited() {
        var didToggle = false
        _uiState.update { state ->
            if (!state.canEditSymptoms || state.session?.isViewingOwnData == false) return@update state
            didToggle = true
            state.copy(doctorVisited = !state.doctorVisited, error = null, saveMessage = null)
        }
        if (didToggle) hapticManager.selection()
    }

    fun onNotesChanged(notes: String) {
        // Same real bug as `toggleMood` above, found in this session's critical
        // self-review: iOS's real `updateNotes(_:)` guards on `!isPartnerView`
        // unconditionally -- notes editing is user-only there regardless of any
        // granted `VIEW_NOTES` permission. `canEditNotes` alone (computed as
        // `isViewingOwnData || can(VIEW_NOTES)`) let a partner with just view
        // access actually edit and persist the primary user's notes text.
        _uiState.update { state ->
            if (!state.canEditNotes || state.session?.isViewingOwnData == false) return@update state
            state.copy(
                notes = notes,
                error = null,
                saveMessage = null,
            )
        }
    }

    fun save() {
        val session = sessionManager.current ?: run {
            _uiState.update {
                it.copy(
                    error = appContext.getString(R.string.logging_error_session_not_ready),
                    saveAttemptId = it.saveAttemptId + 1,
                )
            }
            return
        }
        val state = _uiState.value
        val attemptId = state.saveAttemptId + 1

        if (!state.canLogPeriod) {
            _uiState.update {
                it.copy(
                    error = appContext.getString(R.string.logging_error_care_role_cannot_save),
                    saveAttemptId = attemptId,
                )
            }
            return
        }
        if (!state.canMutateSelectedDate) {
            _uiState.update {
                it.copy(
                    error = appContext.getString(R.string.logging_error_primary_latest_log),
                    saveAttemptId = attemptId,
                )
            }
            return
        }
        if (state.isSaving) return

        val dateValidation = LogTokenEncoder.validateLogDate(state.selectedDate)
        if (dateValidation != LogTokenEncoder.LogValidationResult.Valid) {
            _uiState.update { it.copy(error = validationMessage(dateValidation), saveAttemptId = attemptId) }
            return
        }

        val flowValidation = LogTokenEncoder.validateFlowConsistency(
            periodPresent = state.selectedFlow != null,
            flow = state.selectedFlow,
        )
        if (flowValidation != LogTokenEncoder.LogValidationResult.Valid) {
            _uiState.update { it.copy(error = validationMessage(flowValidation), saveAttemptId = attemptId) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, saveMessage = null, saveAttemptId = attemptId) }

        viewModelScope.launch {
            val requestedTargetUserId = session.targetUserId
            val actorUserId = session.userId
            val logSource = session.activeRole.toLogSource()
            val attributedUserId = attributedSourceUserId(session)

            periodLogRepository.getForDateRange(
                userId = requestedTargetUserId,
                from = state.selectedDate,
                to = state.selectedDate,
            ).onSuccess { sameDayLogs ->
                if (!isStillCurrent(session, state.selectedDate)) return@onSuccess

                if (!session.isViewingOwnData &&
                    !PeriodLogPolicy.canCareViewerMutate(sameDayLogs, actorUserId)
                ) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                canMutateSelectedDate = false,
                                error = appContext.getString(R.string.logging_error_primary_latest_log),
                                saveAttemptId = attemptId,
                            )
                        }
                        return@onSuccess
                }

                val sourceLogs = logsForSource(
                    logs = sameDayLogs,
                    targetUserId = requestedTargetUserId,
                    sourceUserId = attributedUserId,
                )
                val canonical = canonicalLog(sourceLogs, logSource)
                val nowIso = Clock.System.now().toString()
                val nextLog = buildPeriodLog(
                    session = session,
                    state = _uiState.value,
                    canonical = canonical,
                    logSource = logSource,
                    attributedUserId = attributedUserId,
                    timestampIso = nowIso,
                )

                logSaveLog.i {
                    "upsert -> date=${state.selectedDate}, targetUserId=${attributedUserId.takeLast(4)}, " +
                        "existingSameDayLogs=${sameDayLogs.size}"
                }
                periodLogRepository.upsert(nextLog)
                    .onSuccess {
                        logSaveLog.i { "upsert OK for ${state.selectedDate}" }

                        notifyCarePartnerOfLogChange(
                            session = session,
                            subjectUserId = requestedTargetUserId,
                            logSource = logSource,
                        )

                        // Rebuild this user's cycles from the full log history, the
                        // same step iOS runs after every log change
                        // (`CycleDetectionEngine.processLogChange`).
                        //
                        // NOT awaited. It used to be, so that cycles were persisted before
                        // the sheet reported success — but measured on a real device it took
                        // ~2.6s against ~0.4s for the write itself, so it WAS the wait she
                        // felt after tapping save. The log row is already safe by this point;
                        // this step only recomputes derived cycles from it.
                        //
                        // The race that awaiting protected against is handled instead by
                        // telling Home when the rebuild lands: `markSuccess` publishes a new
                        // `lastSyncedAt`, which is exactly the signal Home already treats as
                        // "there may be new data" and reloads on. So Home shows the log
                        // immediately and corrects to the new cycles a moment later, rather
                        // than showing nothing until both are done.
                        //
                        // A detection failure must not fail the save: the log row is
                        // already safely written, and losing what she logged is far
                        // worse than a briefly stale cycle, which the next log or app
                        // start recomputes anyway.
                        viewModelScope.launch {
                            cycleDetectionCoordinator.processLogChange(attributedUserId)
                            .onSuccess { cycles ->
                                logSaveLog.i { "cycle detection OK -> ${cycles.size} cycle(s)" }
                                // Tell Home the rebuilt cycles have landed, so it reloads
                                // now that there is genuinely new derived data to show.
                                syncStore.markSuccess(Clock.System.now().toEpochMilliseconds())
                            }
                            .onFailure { throwable ->
                                // Only the exception TYPE and first line. supabase-kt
                                // puts the full request dump in `message`, including
                                // the `Authorization: Bearer <jwt>` header and the
                                // apikey — a real leak observed in logcat on
                                // 2026-08-01. A logcat capture is routinely pasted
                                // into bug reports, so a live session token must
                                // never reach it.
                                logSaveLog.e {
                                    "cycle detection FAILED (log row is saved): " +
                                        "${throwable::class.simpleName}: " +
                                        throwable.message.orEmpty().substringBefore('\n').take(160)
                                }
                            }
                        }

                        if (isStillCurrent(session, state.selectedDate)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    canMutateSelectedDate = true,
                                    saveMessage = appContext.getString(
                                        R.string.logging_save_success,
                                        formatSelectedDate(state.selectedDate),
                                    ),
                                    error = null,
                                    saveAttemptId = attemptId,
                                    savedAttemptId = attemptId,
                                )
                            }
                            widgetSnapshotManager.refreshAsync()
                            hapticManager.success()

                            // Re-read what was actually persisted.
                            //
                            // `loadEntry` otherwise runs only when the session or the
                            // selected date changes, and a save changes neither -- so the
                            // view model kept whatever the user last tapped, regardless of
                            // what the write produced. Clearing a period left
                            // `selectedFlow` sitting on the old value, so the sheet still
                            // showed it selected and the action button still showed the
                            // pencil (`hasAnyData` reads this same state), while the
                            // calendar and Home -- which read the repository -- correctly
                            // showed no period. That disagreement is what made a cleared
                            // day look logged.
                            loadEntry(session, state.selectedDate)
                        }
                    }
                    .onFailure { throwable ->
                        logSaveLog.e { "upsert FAILED for ${state.selectedDate}: ${throwable.message}" }
                        if (isStillCurrent(session, state.selectedDate)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = throwable.toSafeUserMessage(appContext, R.string.logging_error_failed_to_save),
                                    saveAttemptId = attemptId,
                                )
                            }
                            hapticManager.error()
                        }
                    }
            }.onFailure { throwable ->
                if (!isStillCurrent(session, state.selectedDate)) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = throwable.toSafeUserMessage(appContext, R.string.logging_error_failed_to_load_existing),
                        saveAttemptId = attemptId,
                    )
                }
                hapticManager.error()
            }
        }
    }

    /**
     * Real port of iOS's `LoggingViewModel.saveYearSelection(dates:)` -- called by
     * Calendar's year-view multi-select "Edit Period Dates" bar after the user
     * confirms a batch of staged dates. For each date, toggles `periodPresent`:
     * if a real period is already logged there, this removes it (`flowIntensity =
     * null`); otherwise it adds one at `.LIGHT` (iOS's exact default). Every other
     * field (notes/symptoms/moods/medications) is carried over from that date's
     * own canonical log untouched -- there's no open logging form for 12 arbitrary
     * dates to merge from, unlike the single-date `save()` above. Each date is a
     * fresh upsert (a new audit-trail row via `LogDiffer.buildHistoryEntry`,
     * matching iOS's doc comment "writes a newer owner action for each date,
     * preserving prior audit rows"), and one date's repository failure doesn't
     * abort the rest of the batch -- best-effort, matching iOS's per-date
     * do/catch that only logs and continues.
     *
     * Restricted to the signed-in user's own data (`session.isViewingOwnData`),
     * exactly like iOS's `!isPartnerView` guard -- bulk-editing another person's
     * period history is never allowed here, unlike the single-date quick-log bar
     * which care viewers with the right permission can still use.
     *
     * A genuine `suspend fun`, not a fire-and-forget `viewModelScope.launch` like
     * `save()` above -- the caller (Calendar's own multi-select bar) needs to
     * `await`/join the real completion before clearing its own local selection
     * UI state, exactly like iOS's `await calendarLogVM.saveYearSelection(...)`
     * blocking `saveAndClearSelection()` before it clears `yearSelection`.
     */
    suspend fun saveYearSelection(dates: List<LocalDate>) {
        val session = sessionManager.current ?: return
        if (!session.isViewingOwnData || dates.isEmpty() || _uiState.value.isSavingYearSelection) return

        _uiState.update { it.copy(isSavingYearSelection = true) }

        val targetUserId = session.targetUserId
        val actorUserId = session.userId
        val logSource = session.activeRole.toLogSource()
        val attributedUserId = attributedSourceUserId(session)

        for (date in dates) {
            periodLogRepository.getForDateRange(userId = targetUserId, from = date, to = date)
                .onSuccess { sameDayLogs ->
                    val sourceLogs = logsForSource(
                        logs = sameDayLogs,
                        targetUserId = targetUserId,
                        sourceUserId = attributedUserId,
                    )
                    val canonical = canonicalLog(sourceLogs, logSource)
                    val isCurrentlyPresent = canonical?.periodPresent == true
                    val nextPresent = !isCurrentlyPresent
                    val nextFlow = if (nextPresent) FlowIntensity.LIGHT else null
                    val nowIso = Clock.System.now().toString()

                    val nextLog = PeriodLog(
                        id = canonical?.id ?: DataMigration.stablePeriodLogId(
                            userId = targetUserId,
                            logDate = date.toString(),
                            sourceUserId = attributedUserId,
                        ),
                        userId = targetUserId,
                        logDate = date,
                        periodPresent = nextPresent,
                        flowIntensity = nextFlow,
                        loggedBy = logSource,
                        createdByUserId = canonical?.createdByUserId ?: actorUserId,
                        sourceUserId = canonical?.sourceUserId ?: attributedUserId,
                        partnerLogId = canonical?.partnerLogId,
                        isOverridden = canonical?.isOverridden ?: false,
                        overriddenPartnerLogId = canonical?.overriddenPartnerLogId,
                        notes = canonical?.notes,
                        symptoms = canonical?.symptoms ?: emptyList(),
                        moods = canonical?.moods ?: emptyList(),
                        sexualActivity = canonical?.sexualActivity ?: "none",
                        medications = canonical?.medications ?: emptyList(),
                        medicationDosages = canonical?.medicationDosages ?: emptyList(),
                        createdAt = canonical?.createdAt?.takeIf { it.isNotBlank() } ?: nowIso,
                        updatedAt = nowIso,
                        history = canonical?.let { old ->
                            old.history + LogDiffer.buildHistoryEntry(
                                old = old,
                                new = old.copy(periodPresent = nextPresent, flowIntensity = nextFlow),
                                changedBy = actorUserId,
                            )
                        } ?: emptyList(),
                    )
                    periodLogRepository.upsert(nextLog)
                }
        }

        if (session.isSameSubjectAs(sessionManager.current)) {
            _uiState.update { it.copy(isSavingYearSelection = false) }
        }
        widgetSnapshotManager.refreshAsync()
        hapticManager.success()
    }

    /**
     * Best-effort push so an active care partner's device wakes up even while
     * backgrounded, where Supabase Realtime websockets are suspended and cannot reach it.
     *
     * Port of iOS's `PeriodLog.notifyCarePartnerOfLogChange`. Android had no equivalent at
     * all: `NotificationRepository` was called from exactly one place in the whole
     * project, and that place was Swift. So an Android user could log her period and her
     * partner's phone would simply never hear about it until he next opened the app.
     *
     * Deliberately not awaited and deliberately never surfaced as an error. The log row is
     * already written by this point, and a failed push must not fail, roll back, or delay
     * a save she has already been told succeeded.
     */
    private fun notifyCarePartnerOfLogChange(
        session: SessionContext,
        subjectUserId: String,
        logSource: LogSource,
    ) {
        // A system-generated import is not a "she just logged" event, so it must not
        // notify anyone. Same guard iOS applies.
        if (logSource == LogSource.SYSTEM) return
        if (subjectUserId.isBlank()) return

        // `session.userName` is the signed-in user's name, which is the subject's name
        // only when she is looking at her own data. When a care partner logs on her
        // behalf it is HIS name, and sending that as `partner_name` would label the push
        // with the wrong person. Empty is honest, and nothing renders it: the receiving
        // notification copy is generic on both platforms now.
        val subjectName = if (session.userId == subjectUserId) session.userName else ""

        viewModelScope.launch {
            notificationRepository.notifyCarePartner(
                subjectUserId = subjectUserId,
                type = "partner_logged_period",
                payload = mapOf("user_id" to subjectUserId, "partner_name" to subjectName),
            ).onFailure { throwable ->
                // Type and first line only, never `message` in full: supabase-kt puts the
                // whole request dump in there, Authorization header included. Same rule
                // as the cycle-detection failure log above.
                logSaveLog.w {
                    "care partner notify FAILED (log row is saved): " +
                        "${throwable::class.simpleName}: " +
                        throwable.message.orEmpty().substringBefore('\n').take(160)
                }
            }
        }
    }

    /**
     * Resolves the header's phase line through the SAME shared engine Home uses
     * (`CycleInsightAdapter` -> `CyclePhaseInsight`), so the sheet can never disagree
     * with the phase Home is showing for the same date.
     *
     * Failures leave `phaseName` null rather than guessing: iOS shows a real phase or
     * nothing, and inventing one on a health screen would be worse than omitting it.
     */
    private suspend fun loadPhaseName(session: SessionContext?, date: LocalDate) {
        val targetUserId = session?.targetUserId ?: return
        // Every read here is best-effort. The phase line is decoration on a screen whose
        // real job is saving a log, so a failed or slow cycle read must never disturb the
        // entry load that runs alongside it -- it just leaves the header showing the date.
        val cycles = runCatching { cycleDataRepository.getAll(targetUserId).getOrNull() }
            .getOrNull().orEmpty()
        val logDates = runCatching { periodLogRepository.getAll(targetUserId).getOrNull() }
            .getOrNull().orEmpty()
            .filter { it.periodPresent }
            .mapTo(mutableSetOf()) { it.logDate }
        if (sessionManager.current?.targetUserId != targetUserId) return
        val insight = runCatching {
            CycleInsightAdapter.insightFor(
                date = date,
                cycles = cycles,
                periodLogDates = logDates,
                stats = null,
                userId = targetUserId,
            )
        }.getOrNull() ?: return
        _uiState.update { it.copy(phaseName = phaseDisplayName(insight.phase.kind)) }
    }

    /**
     * Maps the engine's `PhaseKind` straight to a name rather than going through
     * `CyclePhase`. That matters: `CyclePhase` has no PMS case, so Home's
     * `toCyclePhase()` folds PMS into LUTEAL — but iOS names it **"PMS Phase"** in its
     * own right. Routing through the domain enum here would silently rename it.
     */
    private fun phaseDisplayName(kind: CyclePhaseInsight.PhaseKind): String = appContext.getString(
        when (kind) {
            CyclePhaseInsight.PhaseKind.MENSTRUAL -> R.string.logging_phase_menstrual
            CyclePhaseInsight.PhaseKind.FOLLICULAR -> R.string.logging_phase_follicular
            CyclePhaseInsight.PhaseKind.OVULATION -> R.string.logging_phase_ovulation
            CyclePhaseInsight.PhaseKind.LUTEAL -> R.string.logging_phase_luteal
            CyclePhaseInsight.PhaseKind.PMS -> R.string.logging_phase_pms
            CyclePhaseInsight.PhaseKind.DELAYED -> R.string.logging_phase_delayed
            CyclePhaseInsight.PhaseKind.UNKNOWN -> R.string.logging_phase_unknown
        },
    )

    private suspend fun loadEntry(
        session: SessionContext?,
        date: LocalDate,
    ) {
        if (session == null) {
            _uiState.value = LoggingUiState(selectedDate = date)
            return
        }

        val canEditNotes = session.isViewingOwnData || session.can(Permission.VIEW_NOTES)
        val canEditSymptoms = session.isViewingOwnData || session.can(Permission.VIEW_SYMPTOMS)
        val canEditMoods = session.isViewingOwnData || session.can(Permission.VIEW_MOODS)
        val canViewWeight = session.isViewingOwnData || session.can(Permission.VIEW_WEIGHT)
        val canViewTemperature = session.isViewingOwnData || session.can(Permission.VIEW_TEMPERATURE)
        val canViewDailyLogs = session.isViewingOwnData || session.can(Permission.VIEW_DAILY_LOGS)
        val canViewDischarge = session.isViewingOwnData || session.can(Permission.VIEW_DISCHARGE)
        val canViewMedications = session.isViewingOwnData || session.can(Permission.VIEW_MEDICATIONS)

        _uiState.value = _uiState.value.copy(
            session = session,
            selectedDate = date,
            canLogPeriod = session.can(Permission.LOG_PERIOD),
            canEditMoods = canEditMoods,
            canEditSymptoms = canEditSymptoms,
            canEditNotes = canEditNotes,
            canViewWeight = canViewWeight,
            canViewTemperature = canViewTemperature,
            canViewDailyLogs = canViewDailyLogs,
            canViewDischarge = canViewDischarge,
            canViewMedications = canViewMedications,
            isLoadingEntry = true,
            isSaving = false,
            saveMessage = null,
            error = null,
        )

        val requestedTargetUserId = session.targetUserId
        periodLogRepository.getForDateRange(
            userId = requestedTargetUserId,
            from = date,
            to = date,
        ).onSuccess { sameDayLogs ->
            if (!isStillCurrent(session, date)) return@onSuccess

            val attributedUserId = attributedSourceUserId(session)
            val sourceLogs = logsForSource(
                logs = sameDayLogs,
                targetUserId = requestedTargetUserId,
                sourceUserId = attributedUserId,
            )
            val existing = PeriodLogPolicy.latestAction(sourceLogs)
            val canMutate = session.isViewingOwnData ||
                PeriodLogPolicy.canCareViewerMutate(sameDayLogs, session.userId)

            // Weight/BBT/discharge-color/painkiller/doctor-visited aren't separate
            // PeriodLog fields -- like iOS, they're packed as special tokens into the
            // shared `symptoms` list (see `LogTokenEncoder`, a real KMM port of iOS
            // LoggingViewModel's identical token scheme) and decoded back out here.
            // Every value below is independently gated by its own granular
            // permission (matching iOS's per-section `Feature(.xxx)` gates), not
            // the coarse `canEditSymptoms` -- a viewer denied `VIEW_WEIGHT` must
            // never see a real weight value even if `VIEW_SYMPTOMS` is granted.
            val joinedSymptoms = existing?.symptoms?.joinToString(" ").orEmpty()
            val fullSymptoms = existing.symptomsAsSet()

            _uiState.value = LoggingUiState(
                session = session,
                selectedDate = date,
                selectedFlow = existing?.flowIntensity,
                selectedMoods = if (canEditMoods) existing.moodsAsSet() else emptySet(),
                selectedSymptoms = fullSymptoms.filterTo(mutableSetOf()) { symptom ->
                    when (symptom.category) {
                        SymptomCategory.MOOD -> canEditMoods
                        SymptomCategory.SLEEP -> canViewDailyLogs
                        SymptomCategory.DISCHARGE -> canViewDischarge
                        SymptomCategory.PAIN, SymptomCategory.DIGESTIVE,
                        SymptomCategory.BODY, SymptomCategory.ADVANCED -> canEditSymptoms
                    }
                },
                weightKg = if (canViewWeight) LogTokenEncoder.decodeWeight(joinedSymptoms) else null,
                bbtCelsius = if (canViewTemperature) LogTokenEncoder.decodeBbt(joinedSymptoms) else null,
                dischargeColor = if (canViewDischarge) {
                    LogTokenEncoder.decodeDischargeColor(joinedSymptoms)?.let(DischargeColor::from)
                } else {
                    null
                },
                painkillerTaken = canViewMedications && LogTokenEncoder.hasPainkiller(joinedSymptoms),
                doctorVisited = canViewMedications && LogTokenEncoder.hasDoctorVisit(joinedSymptoms),
                notes = if (canEditNotes) existing?.notes.orEmpty() else "",
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                canEditMoods = canEditMoods,
                canEditSymptoms = canEditSymptoms,
                canEditNotes = canEditNotes,
                canViewWeight = canViewWeight,
                canViewTemperature = canViewTemperature,
                canViewDailyLogs = canViewDailyLogs,
                canViewDischarge = canViewDischarge,
                canViewMedications = canViewMedications,
                canMutateSelectedDate = canMutate,
                isLoadingEntry = false,
                isSaving = false,
                saveMessage = null,
                error = null,
            )
        }.onFailure { throwable ->
            if (!isStillCurrent(session, date)) return@onFailure

            _uiState.value = LoggingUiState(
                session = session,
                selectedDate = date,
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                canEditMoods = canEditMoods,
                canEditSymptoms = canEditSymptoms,
                canEditNotes = canEditNotes,
                canViewWeight = canViewWeight,
                canViewTemperature = canViewTemperature,
                canViewDailyLogs = canViewDailyLogs,
                canViewDischarge = canViewDischarge,
                canViewMedications = canViewMedications,
                canMutateSelectedDate = session.isViewingOwnData,
                isLoadingEntry = false,
                isSaving = false,
                saveMessage = null,
                error = throwable.toSafeUserMessage(appContext, R.string.logging_error_failed_to_load_daily),
            )
        }
    }

    private fun buildPeriodLog(
        session: SessionContext,
        state: LoggingUiState,
        canonical: PeriodLog?,
        logSource: LogSource,
        attributedUserId: String,
        timestampIso: String,
    ): PeriodLog {
        val targetUserId = session.targetUserId
        val actorUserId = session.userId
        val periodPresent = state.selectedFlow != null

        val nextLog = PeriodLog(
            id = canonical?.id ?: DataMigration.stablePeriodLogId(
                userId = targetUserId,
                logDate = state.selectedDate.toString(),
                sourceUserId = attributedUserId,
            ),
            userId = targetUserId,
            logDate = state.selectedDate,
            periodPresent = periodPresent,
            flowIntensity = state.selectedFlow,
            loggedBy = logSource,
            createdByUserId = canonical?.createdByUserId ?: actorUserId,
            sourceUserId = canonical?.sourceUserId ?: attributedUserId,
            partnerLogId = canonical?.partnerLogId,
            isOverridden = canonical?.isOverridden ?: false,
            overriddenPartnerLogId = canonical?.overriddenPartnerLogId,
            notes = mergedNotes(state, canonical),
            symptoms = mergedSymptoms(state, canonical),
            moods = mergedMoods(state, canonical),
            sexualActivity = canonical?.sexualActivity ?: "none",
            medications = canonical?.medications ?: emptyList(),
            medicationDosages = canonical?.medicationDosages ?: emptyList(),
            createdAt = canonical?.createdAt?.takeIf { it.isNotBlank() } ?: timestampIso,
            updatedAt = timestampIso,
            history = canonical?.let { old ->
                old.history + LogDiffer.buildHistoryEntry(
                    old = old,
                    new = old.copy(
                        periodPresent = periodPresent,
                        flowIntensity = state.selectedFlow,
                        notes = mergedNotes(state, canonical),
                        symptoms = mergedSymptoms(state, canonical),
                        moods = mergedMoods(state, canonical),
                    ),
                    changedBy = actorUserId,
                )
            } ?: emptyList(),
        )

        return nextLog
    }

    private fun mergedNotes(
        state: LoggingUiState,
        canonical: PeriodLog?,
    ): String? {
        if (!state.canEditNotes) return canonical?.notes
        return state.notes.trim().ifBlank { null }
    }

    private fun mergedSymptoms(
        state: LoggingUiState,
        canonical: PeriodLog?,
    ): List<String> {
        // Each category/token is merged independently by its own granular
        // permission, matching `loadEntry`'s decode gating -- a viewer who can't
        // see (e.g.) discharge data never had a real value loaded into state for
        // it, so blindly rebuilding from `state` here would silently wipe out
        // the canonical value on save. Categories/tokens the viewer can't see
        // are carried over from `canonical` untouched instead.
        val canonicalJoined = canonical?.symptoms?.joinToString(" ").orEmpty()

        val preservedSymptoms = canonical.symptomsAsSet().filterTo(mutableSetOf()) { symptom ->
            when (symptom.category) {
                SymptomCategory.MOOD -> !state.canEditMoods
                SymptomCategory.SLEEP -> !state.canViewDailyLogs
                SymptomCategory.DISCHARGE -> !state.canViewDischarge
                SymptomCategory.PAIN, SymptomCategory.DIGESTIVE,
                SymptomCategory.BODY, SymptomCategory.ADVANCED -> !state.canEditSymptoms
            }
        }
        val editableSymptoms = state.selectedSymptoms.filter { symptom ->
            when (symptom.category) {
                SymptomCategory.MOOD -> state.canEditMoods
                SymptomCategory.SLEEP -> state.canViewDailyLogs
                SymptomCategory.DISCHARGE -> state.canViewDischarge
                SymptomCategory.PAIN, SymptomCategory.DIGESTIVE,
                SymptomCategory.BODY, SymptomCategory.ADVANCED -> state.canEditSymptoms
            }
        }

        val tokens = buildList {
            if (state.canViewMedications) {
                if (state.painkillerTaken) add(LogTokenEncoder.TOKEN_PAINKILLER)
                if (state.doctorVisited) add(LogTokenEncoder.TOKEN_DOCTOR)
            } else {
                if (LogTokenEncoder.hasPainkiller(canonicalJoined)) add(LogTokenEncoder.TOKEN_PAINKILLER)
                if (LogTokenEncoder.hasDoctorVisit(canonicalJoined)) add(LogTokenEncoder.TOKEN_DOCTOR)
            }
            if (state.canViewWeight) {
                state.weightKg?.let { add(LogTokenEncoder.encodeWeight(it)) }
            } else {
                LogTokenEncoder.decodeWeight(canonicalJoined)?.let { add(LogTokenEncoder.encodeWeight(it)) }
            }
            if (state.canViewTemperature) {
                state.bbtCelsius?.let { add(LogTokenEncoder.encodeBbt(it)) }
            } else {
                LogTokenEncoder.decodeBbt(canonicalJoined)?.let { add(LogTokenEncoder.encodeBbt(it)) }
            }
            if (state.canViewDischarge) {
                state.dischargeColor?.let { add(LogTokenEncoder.encodeDischargeColor(it.value)) }
            } else {
                LogTokenEncoder.decodeDischargeColor(canonicalJoined)?.let { add(LogTokenEncoder.encodeDischargeColor(it)) }
            }
        }
        return ((preservedSymptoms + editableSymptoms).map { it.value } + tokens).distinct()
    }

    private fun mergedMoods(
        state: LoggingUiState,
        canonical: PeriodLog?,
    ): List<String> {
        if (!state.canEditMoods) return canonical?.moods ?: emptyList()
        return state.selectedMoods.map { it.value }.distinct()
    }

    private fun logsForSource(
        logs: List<PeriodLog>,
        targetUserId: String,
        sourceUserId: String,
    ): List<PeriodLog> {
        return if (sourceUserId.equals(targetUserId, ignoreCase = true)) {
            logs.filter {
                it.sourceUserId.equals(sourceUserId, ignoreCase = true) ||
                    it.sourceUserId.isBlank()
            }
        } else {
            logs.filter { it.sourceUserId.equals(sourceUserId, ignoreCase = true) }
        }
    }

    private fun canonicalLog(
        logs: List<PeriodLog>,
        incomingSource: LogSource,
    ): PeriodLog? {
        fun latest(predicate: (PeriodLog) -> Boolean): PeriodLog? =
            PeriodLogPolicy.latestAction(logs.filter(predicate))

        return if (incomingSource == LogSource.USER) {
            latest { it.loggedBy == LogSource.USER }
                ?: latest { it.loggedBy == LogSource.SYSTEM }
                ?: PeriodLogPolicy.latestAction(logs)
        } else {
            latest { it.loggedBy.isCareViewerLog }
                ?: latest { it.loggedBy == LogSource.SYSTEM }
                ?: PeriodLogPolicy.latestAction(logs)
        }
    }

    private fun attributedSourceUserId(session: SessionContext): String {
        val logSource = session.activeRole.toLogSource()
        return if (logSource.isCareViewerLog) session.userId else session.targetUserId
    }

    private fun isStillCurrent(
        session: SessionContext,
        date: LocalDate,
    ): Boolean {
        // isSameSubjectAs, not ==: the session object is republished once its name and
        // invitations load, and comparing the whole data class made a save decide its own
        // result was stale — which is what stopped the sheet dismissing.
        return session.isSameSubjectAs(sessionManager.current) &&
            selectedDate.value == date
    }

    private fun validationMessage(result: LogTokenEncoder.LogValidationResult): String = when (result) {
        LogTokenEncoder.LogValidationResult.Valid -> ""
        LogTokenEncoder.LogValidationResult.FutureDate -> appContext.getString(R.string.logging_error_future_date)
        LogTokenEncoder.LogValidationResult.MissingFlow -> appContext.getString(R.string.logging_error_missing_flow)
        LogTokenEncoder.LogValidationResult.FlowWithoutPeriod -> appContext.getString(R.string.logging_error_flow_without_period)
    }

    private fun formatSelectedDate(date: LocalDate): String {
        val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        val formatter = java.time.format.DateTimeFormatter.ofPattern(
            appContext.getString(R.string.logging_header_date_format),
            java.util.Locale.getDefault(),
        )
        return javaDate.format(formatter)
    }
}

private fun Set<Mood>.toggle(mood: Mood): Set<Mood> =
    if (mood in this) this - mood else this + mood

private fun Set<Symptom>.toggle(symptom: Symptom): Set<Symptom> =
    if (symptom in this) this - symptom else this + symptom

private fun PeriodLog?.moodsAsSet(): Set<Mood> =
    this?.moods?.mapNotNull(Mood::from)?.toSet() ?: emptySet()

private fun PeriodLog?.symptomsAsSet(): Set<Symptom> =
    this?.symptoms?.mapNotNull(Symptom::from)?.toSet() ?: emptySet()

private fun UserCareRole.toLogSource(): LogSource = when (this) {
    UserCareRole.PRIMARY_USER -> LogSource.USER
    UserCareRole.PARTNER -> LogSource.PARTNER
    UserCareRole.MOTHER -> LogSource.MOTHER
    UserCareRole.FATHER -> LogSource.FATHER
    UserCareRole.DAUGHTER -> LogSource.USER
}
