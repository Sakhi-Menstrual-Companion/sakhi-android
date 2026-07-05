package team.sakhi.android.feature.logging

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
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserCareRole
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.android.platform.AndroidHapticManager
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
    val canMutateSelectedDate: Boolean = false,
    val isLoadingEntry: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val error: String? = null,
)

/**
 * Thin logging adapter over shared logging policy plus `PeriodLogRepository`.
 * The shared layer does not yet expose a single high-level "save daily log" API,
 * so this ViewModel only orchestrates the existing KMM validation, mutation, and
 * stable-id helpers without re-implementing cycle or merge rules on Android.
 */
class LoggingViewModel(
    private val sessionManager: SessionManager,
    private val periodLogRepository: PeriodLogRepository,
    private val hapticManager: AndroidHapticManager,
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
        _uiState.update { state ->
            if (!state.canEditMoods) return@update state
            state.copy(
                selectedMoods = state.selectedMoods.toggle(mood),
                error = null,
                saveMessage = null,
            )
        }
        hapticManager.selection()
    }

    fun toggleSymptom(symptom: Symptom) {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(
                selectedSymptoms = state.selectedSymptoms.toggle(symptom),
                error = null,
                saveMessage = null,
            )
        }
        hapticManager.selection()
    }

    fun onWeightChanged(kg: Double?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(weightKg = kg, error = null, saveMessage = null)
        }
    }

    fun onBbtChanged(celsius: Double?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(bbtCelsius = celsius, error = null, saveMessage = null)
        }
    }

    fun onDischargeColorSelected(color: DischargeColor?) {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(
                dischargeColor = if (state.dischargeColor == color) null else color,
                error = null,
                saveMessage = null,
            )
        }
    }

    fun togglePainkillerTaken() {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(painkillerTaken = !state.painkillerTaken, error = null, saveMessage = null)
        }
        hapticManager.selection()
    }

    fun toggleDoctorVisited() {
        _uiState.update { state ->
            if (!state.canEditSymptoms) return@update state
            state.copy(doctorVisited = !state.doctorVisited, error = null, saveMessage = null)
        }
        hapticManager.selection()
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { state ->
            if (!state.canEditNotes) return@update state
            state.copy(
                notes = notes,
                error = null,
                saveMessage = null,
            )
        }
    }

    fun save() {
        val session = sessionManager.current ?: run {
            _uiState.update { it.copy(error = "Session is not ready yet.") }
            return
        }
        val state = _uiState.value

        if (!state.canLogPeriod) {
            _uiState.update { it.copy(error = "This care role cannot save period logs.") }
            return
        }
        if (!state.canMutateSelectedDate) {
            _uiState.update { it.copy(error = "This day belongs to the primary user's latest log.") }
            return
        }
        if (state.isSaving) return

        val dateValidation = LogTokenEncoder.validateLogDate(state.selectedDate)
        if (dateValidation != LogTokenEncoder.LogValidationResult.Valid) {
            _uiState.update { it.copy(error = validationMessage(dateValidation)) }
            return
        }

        val flowValidation = LogTokenEncoder.validateFlowConsistency(
            periodPresent = state.selectedFlow != null,
            flow = state.selectedFlow,
        )
        if (flowValidation != LogTokenEncoder.LogValidationResult.Valid) {
            _uiState.update { it.copy(error = validationMessage(flowValidation)) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, saveMessage = null) }

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
                if (!isStillOn(requestedTargetUserId, state.selectedDate)) return@onSuccess

                if (!session.isViewingOwnData &&
                    !PeriodLogPolicy.canCareViewerMutate(sameDayLogs, actorUserId)
                ) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            canMutateSelectedDate = false,
                            error = "This day belongs to the primary user's latest log.",
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
                        if (isStillOn(requestedTargetUserId, state.selectedDate)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    canMutateSelectedDate = true,
                                    saveMessage = "Saved for ${DateConverter.formatForDisplay(state.selectedDate)}",
                                    error = null,
                                )
                            }
                            hapticManager.success()
                        }
                    }
                    .onFailure { throwable ->
                        if (isStillOn(requestedTargetUserId, state.selectedDate)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = throwable.message ?: "Failed to save log",
                                )
                            }
                            hapticManager.error()
                        }
                    }
            }.onFailure { throwable ->
                if (!isStillOn(requestedTargetUserId, state.selectedDate)) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = throwable.message ?: "Failed to load existing logs",
                    )
                }
                hapticManager.error()
            }
        }
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

        _uiState.value = _uiState.value.copy(
            session = session,
            selectedDate = date,
            canLogPeriod = session.can(Permission.LOG_PERIOD),
            canEditMoods = canEditMoods,
            canEditSymptoms = canEditSymptoms,
            canEditNotes = canEditNotes,
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
            if (!isStillOn(requestedTargetUserId, date)) return@onSuccess

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
            val joinedSymptoms = if (canEditSymptoms) existing?.symptoms?.joinToString(" ").orEmpty() else ""

            _uiState.value = LoggingUiState(
                session = session,
                selectedDate = date,
                selectedFlow = existing?.flowIntensity,
                selectedMoods = if (canEditMoods) existing.moodsAsSet() else emptySet(),
                selectedSymptoms = if (canEditSymptoms) existing.symptomsAsSet() else emptySet(),
                weightKg = LogTokenEncoder.decodeWeight(joinedSymptoms),
                bbtCelsius = LogTokenEncoder.decodeBbt(joinedSymptoms),
                dischargeColor = LogTokenEncoder.decodeDischargeColor(joinedSymptoms)?.let(DischargeColor::from),
                painkillerTaken = LogTokenEncoder.hasPainkiller(joinedSymptoms),
                doctorVisited = LogTokenEncoder.hasDoctorVisit(joinedSymptoms),
                notes = if (canEditNotes) existing?.notes.orEmpty() else "",
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                canEditMoods = canEditMoods,
                canEditSymptoms = canEditSymptoms,
                canEditNotes = canEditNotes,
                canMutateSelectedDate = canMutate,
                isLoadingEntry = false,
                isSaving = false,
                saveMessage = null,
                error = null,
            )
        }.onFailure { throwable ->
            if (!isStillOn(requestedTargetUserId, date)) return@onFailure

            _uiState.value = LoggingUiState(
                session = session,
                selectedDate = date,
                canLogPeriod = session.can(Permission.LOG_PERIOD),
                canEditMoods = canEditMoods,
                canEditSymptoms = canEditSymptoms,
                canEditNotes = canEditNotes,
                canMutateSelectedDate = session.isViewingOwnData,
                isLoadingEntry = false,
                isSaving = false,
                saveMessage = null,
                error = throwable.message ?: "Failed to load daily log",
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
        if (!state.canEditSymptoms) return canonical?.symptoms ?: emptyList()
        val tokens = buildList {
            if (state.painkillerTaken) add(LogTokenEncoder.TOKEN_PAINKILLER)
            if (state.doctorVisited) add(LogTokenEncoder.TOKEN_DOCTOR)
            state.weightKg?.let { add(LogTokenEncoder.encodeWeight(it)) }
            state.bbtCelsius?.let { add(LogTokenEncoder.encodeBbt(it)) }
            state.dischargeColor?.let { add(LogTokenEncoder.encodeDischargeColor(it.value)) }
        }
        return (state.selectedSymptoms.map { it.value } + tokens).distinct()
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

    private fun isStillOn(
        targetUserId: String,
        date: LocalDate,
    ): Boolean {
        return sessionManager.current?.targetUserId == targetUserId &&
            selectedDate.value == date
    }

    private fun validationMessage(result: LogTokenEncoder.LogValidationResult): String = when (result) {
        LogTokenEncoder.LogValidationResult.Valid -> ""
        LogTokenEncoder.LogValidationResult.FutureDate -> "You can only log today or earlier."
        LogTokenEncoder.LogValidationResult.MissingFlow -> "Pick a flow intensity before saving."
        LogTokenEncoder.LogValidationResult.FlowWithoutPeriod -> "Flow cannot be saved without a period."
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
