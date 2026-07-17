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
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.DataMigration

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
class LoggingViewModel(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val periodLogRepository: PeriodLogRepository,
    private val hapticManager: AndroidHapticManager,
    private val widgetSnapshotManager: AndroidWidgetSnapshotManager,
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

                periodLogRepository.upsert(nextLog)
                    .onSuccess {
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
                                )
                            }
                            widgetSnapshotManager.refreshAsync()
                            hapticManager.success()
                        }
                    }
                    .onFailure { throwable ->
                        if (isStillCurrent(session, state.selectedDate)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = throwable.message ?: appContext.getString(R.string.logging_error_failed_to_save),
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
                        error = throwable.message ?: appContext.getString(R.string.logging_error_failed_to_load_existing),
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

        if (sessionManager.current == session) {
            _uiState.update { it.copy(isSavingYearSelection = false) }
        }
        widgetSnapshotManager.refreshAsync()
        hapticManager.success()
    }

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
                error = throwable.message ?: appContext.getString(R.string.logging_error_failed_to_load_daily),
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
        return sessionManager.current == session &&
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
