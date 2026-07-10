package team.sakhi.android.feature.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import team.sakhi.ai.AICardClassifier
import team.sakhi.ai.AIQueryClassifier
import team.sakhi.ai.SafePlaceRanker
import team.sakhi.android.feature.reports.ReportDateRangePreset
import team.sakhi.android.feature.reports.ReportPdfExporter
import team.sakhi.android.feature.reports.ReportSection
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidLocationProvider
import team.sakhi.android.platform.HapticImpact
import team.sakhi.models.AICardType
import team.sakhi.models.AIQueryIntent
import team.sakhi.models.ConversationMessage
import team.sakhi.models.SafePlace
import team.sakhi.models.SakhiAIContext
import team.sakhi.repositories.AIRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.report.ReportDataBuilder
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager

data class ChatReportSession(
    val selectedRange: ReportDateRangePreset = ReportDateRangePreset.ThreeMonths,
    val isGenerating: Boolean = false,
)

data class ChatUiState(
    val session: SessionContext? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val inputText: String = "",
    val suggestionChips: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val reportSession: ChatReportSession? = null,
    val sharePdfUri: Uri? = null,
    // Real, unblocked Nearby Places (feeds `AICardClassifier`'s `hasPlaces` flag
    // and `ConversationMessage.places` -- both already existed in shared KMM,
    // unused by Android until this pass). True when the last message needed
    // location but the runtime permission isn't granted yet; `ChatScreen`
    // observes this to launch the system permission dialog.
    val needsLocationPermission: Boolean = false,
)

/**
 * Thin AI chat adapter over shared `AIRepository`, `AIQueryClassifier`, and
 * `AICardClassifier`. Android owns only UI state and never re-implements intent
 * detection or card selection locally.
 */
class ChatViewModel(
    private val sessionManager: SessionManager,
    private val aiRepository: AIRepository,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val reportPdfExporter: ReportPdfExporter,
    private val safePlaceRanker: SafePlaceRanker,
    private val locationProvider: AndroidLocationProvider,
    private val hapticManager: AndroidHapticManager,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                loadConversation(session)
            }
        }
    }

    fun onInputChanged(value: String) {
        _uiState.update {
            it.copy(
                inputText = value,
                error = null,
            )
        }
    }

    fun sendCurrentMessage() {
        sendMessage(_uiState.value.inputText)
    }

    fun sendSuggestedChip(chip: String) {
        hapticManager.impact(HapticImpact.LIGHT)
        sendMessage(chip)
    }

    fun dismissReportCard() {
        hapticManager.selection()
        _uiState.update { it.copy(reportSession = null) }
    }

    /**
     * Called once the system location-permission dialog resolves. Doesn't
     * retry the previous places lookup automatically (asking again is simple
     * and avoids re-running a stale query) -- just clears the pending flag so
     * `ChatScreen` stops showing the permission prompt.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(needsLocationPermission = false) }
        if (!granted) {
            appendLocalAssistantMessage(appContext.getString(R.string.chat_error_location_permission))
        }
    }

    fun selectReportRange(preset: ReportDateRangePreset) {
        hapticManager.selection()
        _uiState.update { state ->
            val session = state.reportSession ?: ChatReportSession()
            state.copy(reportSession = session.copy(selectedRange = preset))
        }
    }

    fun consumeSharePdf() {
        _uiState.update { it.copy(sharePdfUri = null) }
    }

    fun generateReport() {
        hapticManager.impact(HapticImpact.MEDIUM)
        val reportSession = _uiState.value.reportSession ?: return
        val session = sessionManager.current
        val userId = session?.targetUserId.orEmpty()
        val requestedSessionKey = activeSessionKey(session)

        if (session == null || userId.isBlank()) {
            appendLocalAssistantMessage(appContext.getString(R.string.chat_error_report_retry))
            _uiState.update { it.copy(reportSession = null) }
            return
        }

        _uiState.update {
            it.copy(
                reportSession = reportSession.copy(isGenerating = true),
                error = null,
            )
        }

        viewModelScope.launch {
            val (startDate, endDate) = reportSession.selectedRange.dateRange()
            val cyclesDeferred = async { cycleDataRepository.getAll(userId) }
            val logsDeferred = async {
                periodLogRepository.getForDateRange(
                    userId = userId,
                    from = startDate,
                    to = endDate,
                )
            }

            val cyclesResult = cyclesDeferred.await()
            val logsResult = logsDeferred.await()

            if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@launch

            val exportResult = runCatching {
                val cycles = cyclesResult.getOrThrow()
                val logs = logsResult.getOrThrow()
                val report = ReportDataBuilder.build(
                    userId = userId,
                    cycles = cycles,
                    logs = logs,
                    from = startDate,
                    to = endDate,
                )
                withContext(Dispatchers.IO) {
                    val file = reportPdfExporter.export(
                        report = report,
                        selectedSections = ReportSection.entries.toSet(),
                    )
                    reportPdfExporter.buildShareUri(file)
                }
            }

            if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@launch

            val shareUri = exportResult.getOrElse {
                appendLocalAssistantMessage(appContext.getString(R.string.chat_error_report_retry))
                _uiState.update { state -> state.copy(reportSession = null) }
                return@launch
            }

            appendLocalAssistantMessage(appContext.getString(R.string.chat_report_ready))
            _uiState.update { state ->
                state.copy(
                    reportSession = null,
                    sharePdfUri = shareUri,
                )
            }
        }
    }

    private suspend fun loadConversation(session: SessionContext?) {
        if (session == null) {
            _uiState.value = ChatUiState(isLoading = false)
            return
        }

        _uiState.value = ChatUiState(
            session = session,
            isLoading = true,
        )

        val context = buildContext(session)
        val requestedUserId = session.userId
        val requestedSessionKey = activeSessionKey(session)
        val stableSessionId = sessionId(session)

        val historyResult = aiRepository.getConversationHistory(
            userId = requestedUserId,
            sessionId = stableSessionId,
            limit = 100,
        )

        if (activeSessionKey(sessionManager.current) != requestedSessionKey) return

        val loadedMessages = historyResult.getOrElse { throwable ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = throwable.message ?: appContext.getString(R.string.chat_error_load_history),
                )
            }
            emptyList()
        }

        val visibleMessages = if (loadedMessages.isNotEmpty()) {
            loadedMessages
        } else {
            listOf(welcomeMessage(session, context))
        }

        _uiState.update {
            it.copy(
                session = session,
                messages = visibleMessages,
                isLoading = false,
                error = historyResult.exceptionOrNull()?.message,
                reportSession = null,
                sharePdfUri = null,
            )
        }

        aiRepository.generateSuggestionChips(context)
            .onSuccess { chips ->
                if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onSuccess
                _uiState.update { it.copy(suggestionChips = chips) }
            }
            .onFailure { throwable ->
                if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onFailure
                _uiState.update {
                    it.copy(
                        suggestionChips = emptyList(),
                        error = it.error ?: throwable.message,
                    )
                }
            }
    }

    private fun sendMessage(rawText: String) {
        val session = sessionManager.current ?: return
        val text = rawText.trim()
        if (text.isBlank()) return
        if (_uiState.value.isSending) return
        if (_uiState.value.reportSession != null) return
        hapticManager.impact(HapticImpact.LIGHT)
        val priorHistory = _uiState.value.messages

        val context = buildContext(session)
        val intent = AIQueryClassifier.detectIntent(text)
        val nowIso = Clock.System.now().toString()
        val stableSessionId = sessionId(session)
        val requestedSessionKey = activeSessionKey(session)
        val userMessage = ConversationMessage(
            id = messageId(prefix = "user", userId = session.userId),
            role = "user",
            content = text,
            timestamp = nowIso,
            sessionId = stableSessionId,
            userId = session.userId,
            isSynced = false,
            cardType = AICardType.GENERAL,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isSending = true,
                error = null,
            )
        }

        if (!context.isPartnerMode && isReportRequest(text)) {
            viewModelScope.launch {
                aiRepository.saveMessage(userMessage)
            }
            _uiState.update {
                it.copy(
                    isSending = false,
                    reportSession = ChatReportSession(),
                )
            }
            return
        }

        viewModelScope.launch {
            aiRepository.saveMessage(userMessage)

            // Nearby Places: `SafePlaceRanker`/`AIQueryClassifier.resolveSafePlaceType`
            // and `ConversationMessage.places` all already existed in shared KMM,
            // completely unused by Android until this pass -- only the location
            // permission + fetch + card rendering were actually missing.
            var fetchedPlaces: List<SafePlace> = emptyList()
            if (intent == AIQueryIntent.LOCATION && !context.isPartnerMode) {
                if (!locationProvider.hasPermission()) {
                    _uiState.update { it.copy(needsLocationPermission = true) }
                } else {
                    hapticManager.impact(HapticImpact.LIGHT)
                    locationProvider.currentLocation()?.let { location ->
                        fetchedPlaces = safePlaceRanker.findNearby(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            placeType = AIQueryClassifier.resolveSafePlaceType(text).value,
                        ).getOrDefault(emptyList())
                    }
                }
            }

            aiRepository.sendMessage(
                context = context,
                userMessage = text,
                intent = intent,
                history = priorHistory,
            ).onSuccess { response ->
                if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onSuccess

                val assistantMessage = ConversationMessage(
                    id = messageId(prefix = "assistant", userId = session.userId),
                    role = "assistant",
                    content = response,
                    timestamp = Clock.System.now().toString(),
                    sessionId = stableSessionId,
                    userId = session.userId,
                    isSynced = false,
                    cardType = AICardClassifier.detectCardType(
                        intent = intent,
                        response = response,
                        query = text,
                        context = context,
                        isPartnerMode = context.isPartnerMode,
                        hasPlaces = fetchedPlaces.isNotEmpty(),
                    ),
                    places = fetchedPlaces,
                )

                aiRepository.saveMessage(assistantMessage)

                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isSending = false,
                        error = null,
                    )
                }

                aiRepository.generateSuggestionChips(context)
                    .onSuccess { chips ->
                        if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onSuccess
                        _uiState.update { it.copy(suggestionChips = chips) }
                    }
            }.onFailure { throwable ->
                if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onFailure
                _uiState.update {
                    it.copy(
                        messages = it.messages.markFailed(userMessage.id),
                        isSending = false,
                        error = throwable.message ?: appContext.getString(R.string.chat_error_send_message),
                    )
                }
            }
        }
    }

    private suspend fun welcomeMessage(
        session: SessionContext,
        context: SakhiAIContext,
    ): ConversationMessage {
        val welcomeText = aiRepository.generateWelcomeMessage(context)
            .getOrElse {
                fallbackWelcome(session, context)
            }

        return ConversationMessage(
            id = "welcome_${sessionId(session)}",
            role = "assistant",
            content = welcomeText,
            timestamp = Clock.System.now().toString(),
            sessionId = "welcome",
            userId = session.userId,
            isSynced = false,
            cardType = AICardType.GENERAL,
        )
    }

    private fun buildContext(session: SessionContext): SakhiAIContext {
        val isPartnerMode = !session.isViewingOwnData
        val subjectName = session.activePartnership?.partnerName
            ?: appContext.getString(R.string.chat_context_subject_default)

        return SakhiAIContext(
            userName = if (isPartnerMode) {
                appContext.getString(R.string.chat_context_subject_default)
            } else {
                session.userName.ifBlank { appContext.getString(R.string.chat_context_user_default) }
            },
            hasCycleData = false,
            hasRecentPeriodLogs = false,
            hasPredictionData = false,
            isPartnerMode = isPartnerMode,
            subjectUserId = if (isPartnerMode) session.targetUserId else null,
            subjectDisplayName = if (isPartnerMode) subjectName else null,
        )
    }

    private fun sessionId(session: SessionContext): String = session.userId

    private fun activeSessionKey(session: SessionContext?): String? =
        session?.let { "${it.userId}|${it.targetUserId}|${it.isViewingOwnData}" }

    private fun messageId(
        prefix: String,
        userId: String,
    ): String = "${prefix}_${userId}_${Clock.System.now().toEpochMilliseconds()}"

    private fun fallbackWelcome(
        session: SessionContext,
        context: SakhiAIContext,
    ): String {
        return if (context.isPartnerMode) {
            appContext.getString(R.string.chat_fallback_welcome_partner)
        } else {
            val name = session.userName.ifBlank { appContext.getString(R.string.chat_fallback_name_default) }
            appContext.getString(R.string.chat_fallback_welcome_self, name)
        }
    }

    private fun isReportRequest(text: String): Boolean {
        val query = text.lowercase()
        val triggers = listOf(
            "report",
            "pdf",
            "generate report",
            "health report",
            "cycle report",
            "doctor report",
            "report banao",
            "report chahiye",
            "report do",
            "apni report",
            "meri report",
            "health summary",
            "export",
        )
        return triggers.any(query::contains)
    }

    private fun appendLocalAssistantMessage(content: String) {
        val session = sessionManager.current ?: return
        val assistantMessage = ConversationMessage(
            id = messageId(prefix = "assistant", userId = session.userId),
            role = "assistant",
            content = content,
            timestamp = Clock.System.now().toString(),
            sessionId = sessionId(session),
            userId = session.userId,
            isSynced = false,
            cardType = AICardType.GENERAL,
        )
        viewModelScope.launch {
            aiRepository.saveMessage(assistantMessage)
        }
        _uiState.update { it.copy(messages = it.messages + assistantMessage) }
    }
}

private fun List<ConversationMessage>.markFailed(messageId: String): List<ConversationMessage> {
    return map { message ->
        if (message.id == messageId) message.copy(isFailed = true) else message
    }
}
