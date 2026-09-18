package team.sakhi.android.feature.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import team.sakhi.ai.AICardClassifier
import team.sakhi.ai.AIQueryClassifier
import team.sakhi.ai.ChatMoodDetector
import team.sakhi.ai.ChatSymptomDetector
import team.sakhi.ai.SafePlaceRanker
import team.sakhi.ai.ChatTopicMemory
import team.sakhi.android.feature.reports.ReportDateRangePreset
import team.sakhi.android.feature.reports.ReportDocument
import team.sakhi.android.feature.reports.ReportPdfExporter
import team.sakhi.android.feature.reports.ReportSection
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidLocationProvider
import team.sakhi.android.platform.DeviceLocation
import team.sakhi.emergency.EmergencyStore
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.date.DateConverter
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.localdb.SharedLocalRecordCodec
import team.sakhi.logging.LogDiffer
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.models.AICardType
import team.sakhi.models.AIQueryIntent
import team.sakhi.models.ConversationMessage
import team.sakhi.models.LogSource
import team.sakhi.models.SafePlace
import team.sakhi.models.SakhiAIContext
import team.sakhi.models.UserCareRole
import team.sakhi.network.NetworkException
import team.sakhi.repositories.AIRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.report.ReportDataBuilder
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.DataMigration
import team.sakhi.sync.DeterministicIds
import team.sakhi.sync.OfflineUpgradeDataset
import team.sakhi.android.common.toSafeUserMessage

data class ChatReportSession(
    val selectedRange: ReportDateRangePreset = ReportDateRangePreset.ThreeMonths,
    val isGenerating: Boolean = false,
)

private data class PendingChatRetry(
    val messageId: String,
    val text: String,
    val sessionKey: String,
    val history: List<ConversationMessage>,
)

private data class PendingLocationPermissionSend(
    val messageId: String,
    val text: String,
    val sessionKey: String,
    val history: List<ConversationMessage>,
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
    // Real iOS parity gap closed: `SakhiAIInfoView.swift`'s `dangerCard` /
    // `showClearConfirm` -- a destructive "Clear conversation" action wired to the
    // same shared `AIRepository.deleteConversation`, was missing from Android's
    // `ChatInfoScreen` entirely (found doing a genuine side-by-side iOS comparison,
    // not just a functional check).
    val showClearConfirm: Boolean = false,
) {
    /**
     * Real port of iOS `SakhiAIViewModel.displayMessages`: assistant general-text
     * messages render one sentence per bubble so reopened history looks the same
     * as the live chat thread. `messages` stays canonical for search/starring/info.
     */
    val displayMessages: List<ConversationMessage>
        get() = messages.expandAssistantGeneralMessages()
}

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
    private val emergencyStore: EmergencyStore,
    private val hapticManager: AndroidHapticManager,
    private val widgetSnapshotManager: AndroidWidgetSnapshotManager,
    private val localStore: SakhiPhaseALocalStore,
    private val appContext: Context,
    private val topicMemory: ChatTopicMemory,
    private val nearbyPlacesFetcher: NearbyPlacesFetcher = NearbyPlacesFetcher(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val pendingRetries = mutableListOf<PendingChatRetry>()
    private val pendingLocationPermissionSends = mutableListOf<PendingLocationPermissionSend>()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                loadConversation(session)
            }
        }

        // Real port of iOS `SakhiAIViewModel.showError(_:)`'s 3-second auto-dismiss
        // timer. `distinctUntilChanged` + `collectLatest` only restarts the delay when
        // the error value itself actually changes (not on every unrelated state
        // mutation), the same cancel-and-reschedule semantics as iOS's
        // `errorDismissTask?.cancel()`.
        viewModelScope.launch {
            _uiState.map { it.error }.distinctUntilChanged().collectLatest { currentError ->
                if (currentError == null) return@collectLatest
                delay(3_000)
                _uiState.update { if (it.error == currentError) it.copy(error = null) else it }
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

    fun retryPendingMessages() {
        val session = sessionManager.current ?: return
        val requestedSessionKey = activeSessionKey(session) ?: return
        val queued = pendingRetries.filter { retry ->
            retry.sessionKey == requestedSessionKey &&
                _uiState.value.messages.any { message -> message.id == retry.messageId && !message.isFailed }
        }
        if (queued.isEmpty()) return
        pendingRetries.removeAll(queued.toSet())
        viewModelScope.launch {
            queued.forEach { retry ->
                retryPendingMessage(session, retry)
            }
        }
    }

    fun dismissReportCard() {
        hapticManager.selection()
        _uiState.update { it.copy(reportSession = null) }
    }

    fun requestClearConversation() {
        hapticManager.selection()
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    /**
     * Real port of iOS `SakhiAIViewModel.clearConversation(userId:)` -- optimistic,
     * same as iOS: `messages` clears immediately (iOS's own `messages.removeAll()`
     * is synchronous, not awaited on the network delete), the actual server delete
     * happens in the background.
     */
    fun confirmClearConversation() {
        val session = sessionManager.current ?: return
        // Real fix (2026-07-16): this must match the key save/load actually use
        // (`session.userId`, via `sessionId(session)` below) -- using
        // `targetUserId` here diverged from that in partner mode, so "Clear
        // conversation" was deleting under a key nothing was ever saved under.
        val userId = session.userId
        val requestedSessionKey = activeSessionKey(session)
        hapticManager.impact(HapticImpact.MEDIUM)
        _uiState.update {
            it.copy(
                messages = emptyList(),
                showClearConfirm = false,
            )
        }
        viewModelScope.launch {
            aiRepository.deleteConversation(userId = userId, sessionId = sessionId(session))
            if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@launch
            loadConversation(sessionManager.current)
        }
    }

    /**
     * Called once the system location-permission dialog resolves. If a
     * LOCATION-intent send was paused behind the permission prompt, grant
     * resumes that exact pending send and denial resolves it locally with the
     * assistant's permission-needed reply, so the original user bubble does
     * not stay stuck in its optimistic sending state.
     */
    /**
     * How many Sakhis are nearby, for the chat header's Nearby button.
     *
     * Read straight off the shared `EmergencyStore` rather than through an
     * `EmergencyViewModel`: that object subscribes to six flows, owns the request state
     * machine and starts polling, and a second one alive alongside the real one inside the
     * Emergency flow would be two owners of the same shared state. This watches the one
     * value and nothing else, mirroring iOS's `NearbySakhiCountProbe`.
     *
     * `null` means not known yet, which is not the same as zero — the button draws its
     * fallback glyph rather than "0".
     */
    val nearbyAvailableCount: StateFlow<Int?> = emergencyStore.nearbyAvailableCount

    /**
     * Refreshes that count, but **only if location was already granted**.
     *
     * Returns immediately otherwise, so opening the AI chat can never raise a location
     * prompt on a screen that has no reason to want one. iOS says the same thing on its own
     * copy of this: location is asked for in the Emergency introduction, where the reason is
     * on screen; a count in a header is not a good enough reason to ask, and being asked out
     * of nowhere is how a health app loses trust.
     */
    private val _nearbyCoordinate = MutableStateFlow<DeviceLocation?>(null)

    /**
     * Where she is, once a fix has landed, for the map behind the Nearby capsule.
     *
     * `null` until then, and forever if location was never granted — the capsule falls back
     * to a plain fill, which is also what anyone who never granted it sees.
     */
    val nearbyCoordinate: StateFlow<DeviceLocation?> = _nearbyCoordinate.asStateFlow()

    fun refreshNearbyCountIfAlreadyAllowed() {
        if (!locationProvider.hasPermission()) return
        viewModelScope.launch {
            val fix = runCatching { locationProvider.currentLocation() }.getOrNull() ?: return@launch
            _nearbyCoordinate.value = fix
            emergencyStore.updateDeviceLocation(fix.latitude, fix.longitude)
            runCatching { emergencyStore.refreshNearbyAvailableCount() }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val session = sessionManager.current
        val requestedSessionKey = activeSessionKey(session)
        val pendingSends = requestedSessionKey?.let(::takePendingLocationPermissionSends).orEmpty()
        _uiState.update { it.copy(needsLocationPermission = false) }
        if (pendingSends.isEmpty()) {
            if (!granted) {
                appendLocalAssistantMessage(appContext.getString(R.string.chat_error_location_permission))
            }
            return
        }
        session ?: return

        if (!granted) {
            viewModelScope.launch {
                denyPendingLocationPermissionSends(
                    session = session,
                    pendingSends = pendingSends,
                )
            }
            return
        }

        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            resumePendingLocationPermissionSends(
                session = session,
                pendingSends = pendingSends,
            )
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

        // Real security fix (2026-07-16), defense-in-depth: this path was only
        // ever protected by the upstream `!context.isPartnerMode` branch in
        // `sendMessage()` that decides whether to even offer the report card in
        // the first place -- there was no check at the actual generation
        // boundary itself. Matches `ReportsViewModel.generate()`'s own gate
        // exactly (`!isViewingOwnData && !can(GENERATE_REPORTS)`), so a partner
        // session reaching this function through any other path than the
        // normal chip/keyword flow still can't produce a report without the
        // primary user's explicit grant.
        if (!session.isViewingOwnData && !session.can(Permission.GENERATE_REPORTS)) {
            appendLocalAssistantMessage(appContext.getString(R.string.chat_error_report_permission_denied))
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
                val cyclesForReport = cycles.filteredForReportWindow(from = startDate, to = endDate)
                val report = ReportDataBuilder.build(
                    userId = userId,
                    cycles = cyclesForReport,
                    logs = logs,
                    from = startDate,
                    to = endDate,
                )
                val document = ReportDocument(
                    report = report,
                    subjectName = session.userName.ifBlank { appContext.getString(R.string.chat_context_user_default) },
                    generatedOn = DateConverter.today(),
                    nextPredictedPeriod = cyclesForReport.maxByOrNull { it.cycleStartDate }?.cycleEndDate?.let { cycleEndDate ->
                        DateConverter.addDays(cycleEndDate, 1)
                    },
                    trackedCyclesCount = cyclesForReport.count { it.isComplete && it.cycleLength != null },
                )
                withContext(Dispatchers.IO) {
                    val file = reportPdfExporter.export(
                        document = document,
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
            replacePendingLocationPermissionSends(emptyList())
            _uiState.value = ChatUiState(isLoading = false)
            return
        }
        replacePendingLocationPermissionSends(emptyList())

        val requestedUserId = session.userId
        val requestedSessionKey = activeSessionKey(session) ?: return
        val stableSessionId = sessionId(session)
        val preloadedMessages = preloadLocalConversation(
            userId = requestedUserId,
            stableSessionId = stableSessionId,
        )
        val initialMessages = preloadedMessages.ifEmpty {
            localWelcomeMessages(session)
        }
        val preloadedCount = initialMessages.size

        _uiState.value = ChatUiState(
            session = session,
            messages = initialMessages,
            isLoading = true,
        )

        val context = buildContext(session)

        val historyResult = aiRepository.getConversationHistory(
            userId = requestedUserId,
            sessionId = stableSessionId,
            limit = 100,
        )

        if (activeSessionKey(sessionManager.current) != requestedSessionKey) return

        val loadedMessages = historyResult.getOrElse { throwable ->
            // A history fetch that fails is NOT a blocking error. The chat is perfectly
            // usable without it -- the welcome messages are already on screen and the
            // user can still send -- so raising a red banner over a working screen was
            // alarming for nothing, and it fired on every open. The cause is logged so a
            // genuine backend problem is still diagnosable.
            android.util.Log.w(
                "SakhiChat",
                "conversation history load failed (chat still usable): " +
                    "${throwable::class.simpleName}: " +
                    throwable.message.orEmpty().substringBefore('\n').take(160),
            )
            emptyList()
        }

        val remoteMessages = when {
            loadedMessages.isNotEmpty() -> loadedMessages
            else -> {
                runCatching {
                    aiRepository.getMessages(
                        userId = requestedUserId,
                        page = 0,
                        pageSize = 100,
                    ).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                    .map { message ->
                        if (message.sessionId == stableSessionId) {
                            message
                        } else {
                            message.copy(
                                sessionId = stableSessionId,
                                userId = requestedUserId,
                            )
                        }
                    }
            }
        }

        if (remoteMessages.isNotEmpty()) {
            cacheConversation(remoteMessages)
        }

        val visibleMessages = if (remoteMessages.isNotEmpty()) remoteMessages else initialMessages

        _uiState.update { state ->
            val resolvedMessages = if (state.messages.size == preloadedCount) {
                visibleMessages
            } else {
                state.messages.mergeRemoteMessages(visibleMessages)
            }
            state.copy(
                session = session,
                messages = resolvedMessages,
                isLoading = false,
                error = state.error,
                reportSession = null,
                sharePdfUri = null,
            )
        }
        reconcilePendingLocationPermissionSends(
            session = session,
            messages = _uiState.value.messages,
            requestedSessionKey = requestedSessionKey,
        )

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
        val requestedSessionKey = activeSessionKey(session) ?: return
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
        cacheMessage(userMessage)

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
            completeSend(
                session = session,
                context = context,
                text = text,
                intent = intent,
                priorHistory = priorHistory,
                userMessage = userMessage,
                requestedSessionKey = requestedSessionKey,
                stableSessionId = stableSessionId,
            )
        }
    }

    private suspend fun retryPendingMessage(
        session: SessionContext,
        retry: PendingChatRetry,
    ) {
        val userMessage = _uiState.value.messages.firstOrNull { it.id == retry.messageId } ?: return
        val context = buildContext(session)
        _uiState.update { it.copy(isSending = true, error = null) }
        aiRepository.saveMessage(userMessage.copy(isFailed = false, isSynced = false))
        completeSend(
            session = session,
            context = context,
            text = retry.text,
            intent = AIQueryClassifier.detectIntent(retry.text),
            priorHistory = retry.history,
            userMessage = userMessage.copy(isFailed = false),
            requestedSessionKey = retry.sessionKey,
            stableSessionId = sessionId(session),
        )
    }

    private suspend fun completeSend(
        session: SessionContext,
        context: SakhiAIContext,
        text: String,
        intent: AIQueryIntent,
        priorHistory: List<ConversationMessage>,
        userMessage: ConversationMessage,
        requestedSessionKey: String,
        stableSessionId: String,
    ) {
        // Nearby Places: `SafePlaceRanker`/`AIQueryClassifier.resolveSafePlaceType`
        // and `ConversationMessage.places` all already existed in shared KMM,
        // completely unused by Android until this pass -- only the location
        // permission + fetch + card rendering were actually missing.
        var fetchedPlaces: List<SafePlace> = emptyList()
        if (intent == AIQueryIntent.LOCATION && !context.isPartnerMode) {
            if (!locationProvider.hasPermission()) {
                enqueuePendingLocationPermissionSend(
                    messageId = userMessage.id,
                    text = text,
                    sessionKey = requestedSessionKey,
                    history = priorHistory,
                )
                _uiState.update {
                    it.copy(
                        needsLocationPermission = true,
                        isSending = false,
                    )
                }
                return
            } else {
                hapticManager.impact(HapticImpact.LIGHT)
                val placeType = AIQueryClassifier.resolveSafePlaceType(text).value
                val location = locationProvider.currentLocation()
                if (location == null) {
                    finishWithLocalAssistantMessage(
                        session = session,
                        requestedSessionKey = requestedSessionKey,
                        userMessage = userMessage,
                        content = appContext.getString(R.string.chat_error_places_unavailable),
                    )
                    return
                }
                fetchedPlaces = safePlaceRanker.findNearby(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    placeType = placeType,
                ).getOrDefault(emptyList())
                if (fetchedPlaces.isEmpty()) {
                    when (val diagnosticResult = nearbyPlacesFetcher.inspectNearby(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        placeType = placeType,
                    )) {
                        is NearbyPlacesFetchResult.Success -> {
                            fetchedPlaces = diagnosticResult.places
                        }

                        NearbyPlacesFetchResult.ZeroResults -> {
                            finishWithLocalAssistantMessage(
                                session = session,
                                requestedSessionKey = requestedSessionKey,
                                userMessage = userMessage,
                                content = appContext.getString(R.string.chat_error_no_nearby_places),
                            )
                            return
                        }

                        is NearbyPlacesFetchResult.LookupError -> {
                            finishWithLocalAssistantMessage(
                                session = session,
                                requestedSessionKey = requestedSessionKey,
                                userMessage = userMessage,
                                content = appContext.getString(R.string.chat_error_places_unavailable),
                            )
                            return
                        }
                    }
                }
            }
        }

        // What Sakhi has learned about this girl's own topics, in the phase she is in now.
        // Her own chat only: a care partner never sees what she talks about. Counts and
        // topic names, on this phone, never her words. Same class iOS calls, so the Sakhi
        // that knows her here is the one that knows her there.
        val herContext = if (context.isPartnerMode) {
            null
        } else {
            val today = DateConverter.today().toEpochDays().toLong()
            topicMemory.note(
                userId = session.userId,
                phase = context.currentPhase,
                message = text,
                todayEpochDay = today,
            )
            buildList {
                topicMemory.contextSummary(session.userId, context.currentPhase, today)?.let { add(it) }
                if (topicMemory.isOutOfWords(text)) {
                    add(
                        "She does not have anything particular to say right now. Do not ask her what is " +
                            "wrong again. Stay with her, or gently open something that is hers.",
                    )
                }
            }.joinToString("\n").takeIf { it.isNotBlank() }
        }

        aiRepository.sendMessage(
            context = context,
            userMessage = text,
            intent = intent,
            history = priorHistory,
            herContext = herContext,
        ).onSuccess { response ->
            if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onSuccess

            val assistantMessages = buildAssistantMessages(
                session = session,
                stableSessionId = stableSessionId,
                intent = intent,
                query = text,
                context = context,
                response = response,
                fetchedPlaces = fetchedPlaces,
            )
            assistantMessages.forEach { assistantMessage ->
                aiRepository.saveMessage(assistantMessage)
                cacheMessage(assistantMessage)
            }

            val deliveredUserMessage = userMessage.copy(isSynced = true, isFailed = false)
            cacheMessage(deliveredUserMessage)
            _uiState.update {
                it.copy(
                    messages = it.messages
                        .replaceMessage(deliveredUserMessage)
                        .plus(assistantMessages),
                    isSending = false,
                    error = null,
                )
            }

            aiRepository.generateSuggestionChips(context)
                .onSuccess { chips ->
                    if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onSuccess
                    _uiState.update { it.copy(suggestionChips = chips) }
                }

            autoLogFromChat(
                session = session,
                intent = intent,
                text = text,
            )
        }.onFailure { throwable ->
            logSendFailure(throwable, willRetry = throwable.isRetryableChatFailure())
            if (activeSessionKey(sessionManager.current) != requestedSessionKey) return@onFailure
            if (throwable.isRetryableChatFailure()) {
                enqueuePendingRetry(
                    messageId = userMessage.id,
                    text = text,
                    sessionKey = requestedSessionKey,
                    history = priorHistory,
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages.clearFailedState(userMessage.id),
                        isSending = false,
                        error = null,
                    )
                }
                return@onFailure
            }

            val failedUserMessage = userMessage.copy(isSynced = true, isFailed = true)
            cacheMessage(failedUserMessage)
            _uiState.update {
                it.copy(
                    messages = it.messages.replaceMessage(failedUserMessage),
                    isSending = false,
                    error = throwable.toSafeUserMessage(appContext, R.string.chat_error_send_message),
                )
            }
        }
    }

    // The girl only ever sees "not sent". Without this line nothing says whether the server
    // was reached, which is how a phone clock six hours behind hid as a silent chat failure.
    // Class and first line only: Ktor messages can carry the request URL and headers.
    private fun logSendFailure(throwable: Throwable, willRetry: Boolean) {
        android.util.Log.w(
            "SakhiChat",
            "send failed (${if (willRetry) "will retry" else "marked not sent"}): " +
                "${throwable::class.simpleName}: " +
                throwable.message.orEmpty().substringBefore('\n').take(160),
        )
    }

    private fun localWelcomeMessages(session: SessionContext): List<ConversationMessage> {
        val now = Clock.System.now()
        return if (session.isViewingOwnData) {
            val firstName = session.userName
                .trim()
                .split(" ")
                .firstOrNull()
                .orEmpty()
            val greeting = if (firstName.isEmpty()) {
                appContext.getString(R.string.chat_welcome_intro_unnamed)
            } else {
                appContext.getString(R.string.chat_welcome_intro_named, firstName)
            }
            val followUp = if (firstName.isEmpty()) {
                appContext.getString(R.string.chat_welcome_follow_up_unnamed)
            } else {
                appContext.getString(R.string.chat_welcome_follow_up_named)
            }
            listOf(
                welcomeMessage(
                    id = "welcome_1_${sessionId(session)}",
                    content = greeting,
                    timestamp = now.minus(6.seconds).toString(),
                    session = session,
                ),
                welcomeMessage(
                    id = "welcome_2_${sessionId(session)}",
                    content = followUp,
                    timestamp = now.minus(2.seconds).toString(),
                    session = session,
                ),
            )
        } else {
            listOf(
                welcomeMessage(
                    id = "welcome_1_${sessionId(session)}",
                    content = appContext.getString(R.string.chat_welcome_partner_intro),
                    timestamp = now.minus(6.seconds).toString(),
                    session = session,
                ),
                welcomeMessage(
                    id = "welcome_2_${sessionId(session)}",
                    content = appContext.getString(R.string.chat_welcome_partner_follow_up),
                    timestamp = now.minus(2.seconds).toString(),
                    session = session,
                ),
            )
        }
    }

    private fun welcomeMessage(
        id: String,
        content: String,
        timestamp: String,
        session: SessionContext,
    ): ConversationMessage = ConversationMessage(
        id = id,
        role = "assistant",
        content = content,
        timestamp = timestamp,
        sessionId = "welcome",
        userId = session.userId,
        isSynced = false,
        cardType = AICardType.GENERAL,
    )

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

    // Real fix (2026-07-16): was `session.userId` alone, so a single device
    // user's own self-mode conversation and their partner-mode conversation
    // (while viewing someone else) persisted under the exact same key and
    // bled into each other on reload -- `session.userId` never varies by
    // viewing mode. Reusing `activeSessionKey`'s own composite scoping
    // (userId|targetUserId|isViewingOwnData) here means every persistence
    // call already routed through this function (save, load, delete via
    // `confirmClearConversation`) is now scoped consistently, with no new
    // storage path.
    /**
     * The chat session id as the DATABASE needs it: a real UUID.
     *
     * `activeSessionKey` is a composite ("user|target|isOwn") and is exactly right for
     * deciding in-memory whether a response still belongs to the session on screen. It
     * was also being sent straight to Postgres as `session_id`, which is a `uuid`
     * column, so every history fetch failed with
     * `invalid input syntax for type uuid: "…|…|true"` and the chat raised a red error
     * on open. Hashing the same composite into a deterministic UUID keeps one stable
     * session per (viewer, subject, own-data) triple while giving the column the type it
     * expects.
     */
    private fun sessionId(session: SessionContext): String =
        DeterministicIds.uuidV5(
            namespace = AI_SESSION_NAMESPACE,
            name = activeSessionKey(session).orEmpty(),
        )

    private fun activeSessionKey(session: SessionContext?): String? =
        session?.let { "${it.userId}|${it.targetUserId}|${it.isViewingOwnData}" }

    private fun messageId(
        prefix: String,
        userId: String,
    ): String = "${prefix}_${userId}_${Clock.System.now().toEpochMilliseconds()}"

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
        val assistantMessage = assistantMessage(
            userId = session.userId,
            content = content,
            sessionId = sessionId(session),
            cardType = AICardType.GENERAL,
        )
        viewModelScope.launch {
            aiRepository.saveMessage(assistantMessage)
        }
        cacheMessage(assistantMessage)
        _uiState.update { it.copy(messages = it.messages + assistantMessage) }
    }

    private suspend fun finishWithLocalAssistantMessage(
        session: SessionContext,
        requestedSessionKey: String,
        userMessage: ConversationMessage,
        content: String,
    ) {
        if (activeSessionKey(sessionManager.current) != requestedSessionKey) return
        val deliveredUserMessage = userMessage.copy(isSynced = true, isFailed = false)
        val assistantMessage = assistantMessage(
            userId = session.userId,
            content = content,
            sessionId = sessionId(session),
            cardType = AICardType.GENERAL,
        )
        aiRepository.saveMessage(deliveredUserMessage)
        aiRepository.saveMessage(assistantMessage)
        cacheMessage(deliveredUserMessage)
        cacheMessage(assistantMessage)
        _uiState.update {
            it.copy(
                messages = it.messages
                    .replaceMessage(deliveredUserMessage)
                    .plus(assistantMessage),
                isSending = false,
                error = null,
            )
        }
    }

    private suspend fun preloadLocalConversation(
        userId: String,
        stableSessionId: String,
    ): List<ConversationMessage> = withContext(Dispatchers.IO) {
        localStore.exportRecords(OfflineUpgradeDataset.AI_MESSAGES)
            .map(SharedLocalRecordCodec::decodeConversationMessage)
            .filter { message ->
                message.userId.equals(userId, ignoreCase = true) &&
                    message.sessionId.equals(stableSessionId, ignoreCase = true)
            }
            .sortedBy { it.timestamp }
            .takeLast(100)
    }

    private suspend fun cacheConversation(messages: List<ConversationMessage>) = withContext(Dispatchers.IO) {
        messages.forEach { message ->
            localStore.upsertConversationMessage(message)
        }
    }

    private fun cacheMessage(message: ConversationMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertConversationMessage(message)
        }
    }

    private fun enqueuePendingRetry(
        messageId: String,
        text: String,
        sessionKey: String,
        history: List<ConversationMessage>,
    ) {
        pendingRetries.removeAll { it.messageId == messageId }
        pendingRetries += PendingChatRetry(
            messageId = messageId,
            text = text,
            sessionKey = sessionKey,
            history = history,
        )
    }

    private fun enqueuePendingLocationPermissionSend(
        messageId: String,
        text: String,
        sessionKey: String,
        history: List<ConversationMessage>,
    ) {
        pendingLocationPermissionSends.removeAll { it.messageId == messageId }
        pendingLocationPermissionSends += PendingLocationPermissionSend(
            messageId = messageId,
            text = text,
            sessionKey = sessionKey,
            history = history,
        )
    }

    private fun replacePendingLocationPermissionSends(
        pendingSends: List<PendingLocationPermissionSend>,
    ) {
        pendingLocationPermissionSends.clear()
        pendingLocationPermissionSends += pendingSends.distinctBy(PendingLocationPermissionSend::messageId)
    }

    private fun takePendingLocationPermissionSends(sessionKey: String): List<PendingLocationPermissionSend> {
        val matched = pendingLocationPermissionSends.filter { it.sessionKey == sessionKey }
        if (matched.isEmpty()) return emptyList()
        pendingLocationPermissionSends.removeAll(matched.toSet())
        return matched
    }

    private fun reconcilePendingLocationPermissionSends(
        session: SessionContext,
        messages: List<ConversationMessage>,
        requestedSessionKey: String,
    ) {
        val recoveredPendingSends = recoverPendingLocationPermissionSends(
            session = session,
            messages = messages,
            requestedSessionKey = requestedSessionKey,
        )
        replacePendingLocationPermissionSends(recoveredPendingSends)
        if (recoveredPendingSends.isEmpty()) {
            _uiState.update { it.copy(needsLocationPermission = false) }
            return
        }

        if (!locationProvider.hasPermission()) {
            _uiState.update {
                it.copy(
                    needsLocationPermission = true,
                    isSending = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                needsLocationPermission = false,
                isSending = true,
                error = null,
            )
        }
        viewModelScope.launch {
            resumePendingLocationPermissionSends(
                session = session,
                pendingSends = recoveredPendingSends,
            )
        }
    }

    private fun recoverPendingLocationPermissionSends(
        session: SessionContext,
        messages: List<ConversationMessage>,
        requestedSessionKey: String,
    ): List<PendingLocationPermissionSend> {
        if (!session.isViewingOwnData) return emptyList()
        return messages.mapIndexedNotNull { index, message ->
            if (!message.isUser || message.isSynced || message.isFailed) return@mapIndexedNotNull null
            if (AIQueryClassifier.detectIntent(message.content) != AIQueryIntent.LOCATION) return@mapIndexedNotNull null
            PendingLocationPermissionSend(
                messageId = message.id,
                text = message.content,
                sessionKey = requestedSessionKey,
                history = messages.take(index),
            )
        }
    }

    private suspend fun denyPendingLocationPermissionSends(
        session: SessionContext,
        pendingSends: List<PendingLocationPermissionSend>,
    ) {
        pendingSends.forEach { pendingSend ->
            if (activeSessionKey(sessionManager.current) != pendingSend.sessionKey) return@forEach
            val userMessage = _uiState.value.messages.firstOrNull { it.id == pendingSend.messageId } ?: return@forEach
            finishWithLocalAssistantMessage(
                session = session,
                requestedSessionKey = pendingSend.sessionKey,
                userMessage = userMessage,
                content = appContext.getString(R.string.chat_error_location_permission),
            )
        }
    }

    private suspend fun resumePendingLocationPermissionSends(
        session: SessionContext,
        pendingSends: List<PendingLocationPermissionSend>,
    ) {
        pendingSends.forEach { pendingSend ->
            if (activeSessionKey(sessionManager.current) != pendingSend.sessionKey) return@forEach
            val userMessage = _uiState.value.messages.firstOrNull { it.id == pendingSend.messageId } ?: return@forEach
            _uiState.update { it.copy(isSending = true, error = null) }
            completeSend(
                session = session,
                context = buildContext(session),
                text = pendingSend.text,
                intent = AIQueryClassifier.detectIntent(pendingSend.text),
                priorHistory = pendingSend.history,
                userMessage = userMessage.copy(isFailed = false),
                requestedSessionKey = pendingSend.sessionKey,
                stableSessionId = sessionId(session),
            )
        }
    }

    private fun buildAssistantMessages(
        session: SessionContext,
        stableSessionId: String,
        intent: AIQueryIntent,
        query: String,
        context: SakhiAIContext,
        response: String,
        fetchedPlaces: List<SafePlace>,
    ): List<ConversationMessage> {
        val cardType = AICardClassifier.detectCardType(
            intent = intent,
            response = response,
            query = query,
            context = context,
            isPartnerMode = context.isPartnerMode,
            hasPlaces = fetchedPlaces.isNotEmpty(),
        )
        if (fetchedPlaces.isNotEmpty()) {
            return listOf(
                assistantMessage(
                    userId = session.userId,
                    content = response,
                    sessionId = stableSessionId,
                    cardType = cardType,
                    places = fetchedPlaces,
                ),
            )
        }
        val replyGroupId = messageId(prefix = "assistant", userId = session.userId)
        return splitIntoChunks(response).mapIndexed { index, chunk ->
            assistantMessage(
                id = "$replyGroupId#$index",
                userId = session.userId,
                content = chunk,
                sessionId = stableSessionId,
                cardType = cardType,
            )
        }
    }

    private fun assistantMessage(
        userId: String,
        id: String = messageId(prefix = "assistant", userId = userId),
        content: String,
        sessionId: String,
        cardType: String,
        places: List<SafePlace> = emptyList(),
    ): ConversationMessage = ConversationMessage(
        id = id,
        role = "assistant",
        content = content,
        timestamp = Clock.System.now().toString(),
        sessionId = sessionId,
        userId = userId,
        isSynced = false,
        cardType = if (places.isNotEmpty()) AICardType.PLACES else cardType,
        places = places,
    )

    private suspend fun autoLogFromChat(
        session: SessionContext,
        intent: AIQueryIntent,
        text: String,
    ) {
        if (!session.isViewingOwnData || intent == AIQueryIntent.SAFETY) return

        val detectedSymptoms = ChatSymptomDetector.detect(text)
        val detectedMoods = ChatMoodDetector.detect(text)
        if (detectedSymptoms.isEmpty() && detectedMoods.isEmpty()) return

        val targetDate = DateConverter.today()
        val requestedTargetUserId = session.targetUserId
        val actorUserId = session.userId
        val logSource = session.activeRole.toLogSource()
        val attributedUserId = attributedSourceUserId(session)

        val existingLogs = periodLogRepository.getForDateRange(
            userId = requestedTargetUserId,
            from = targetDate,
            to = targetDate,
        ).getOrDefault(emptyList())

        val sourceLogs = logsForSource(
            logs = existingLogs,
            targetUserId = requestedTargetUserId,
            sourceUserId = attributedUserId,
        )
        val canonical = canonicalLog(sourceLogs, logSource)
        val knownSymptoms = canonical.symptomsAsSet()
        val knownMoods = canonical.moodsAsSet()
        val newSymptoms = detectedSymptoms.filterNot(knownSymptoms::contains)
        val newMoods = detectedMoods.filterNot(knownMoods::contains)
        if (newSymptoms.isEmpty() && newMoods.isEmpty()) return

        val updated = buildAutoLoggedPeriodLog(
            session = session,
            date = targetDate,
            canonical = canonical,
            addedSymptoms = newSymptoms,
            removedSymptoms = emptyList(),
            addedMoods = newMoods,
            removedMoods = emptyList(),
            actorUserId = actorUserId,
            attributedUserId = attributedUserId,
            logSource = logSource,
        )

        periodLogRepository.upsert(updated).onSuccess {
            widgetSnapshotManager.refreshAsync()
            val names = (newSymptoms.map(Symptom::displayName) + newMoods.map(Mood::displayName))
                .joinToString(", ")
            ToastManager.show(
                title = appContext.getString(R.string.chat_auto_log_title, names),
                message = "",
                type = ToastType.SUCCESS,
                durationMs = 3_000L,
                actionLabel = appContext.getString(R.string.chat_auto_log_undo),
                onAction = {
                    viewModelScope.launch {
                        undoAutoLogged(
                            session = session,
                            date = targetDate,
                            symptoms = newSymptoms,
                            moods = newMoods,
                        )
                    }
                },
            )
        }
    }

    private suspend fun undoAutoLogged(
        session: SessionContext,
        date: LocalDate,
        symptoms: List<Symptom>,
        moods: List<Mood>,
    ) {
        val requestedTargetUserId = session.targetUserId
        val actorUserId = session.userId
        val logSource = session.activeRole.toLogSource()
        val attributedUserId = attributedSourceUserId(session)

        val existingLogs = periodLogRepository.getForDateRange(
            userId = requestedTargetUserId,
            from = date,
            to = date,
        ).getOrDefault(emptyList())
        val sourceLogs = logsForSource(
            logs = existingLogs,
            targetUserId = requestedTargetUserId,
            sourceUserId = attributedUserId,
        )
        val canonical = canonicalLog(sourceLogs, logSource) ?: return
        val updated = buildAutoLoggedPeriodLog(
            session = session,
            date = date,
            canonical = canonical,
            addedSymptoms = emptyList(),
            removedSymptoms = symptoms,
            addedMoods = emptyList(),
            removedMoods = moods,
            actorUserId = actorUserId,
            attributedUserId = attributedUserId,
            logSource = logSource,
        )
        periodLogRepository.upsert(updated).onSuccess {
            widgetSnapshotManager.refreshAsync()
        }
    }

    private fun buildAutoLoggedPeriodLog(
        session: SessionContext,
        date: LocalDate,
        canonical: team.sakhi.models.PeriodLog?,
        addedSymptoms: List<Symptom>,
        removedSymptoms: List<Symptom>,
        addedMoods: List<Mood>,
        removedMoods: List<Mood>,
        actorUserId: String,
        attributedUserId: String,
        logSource: LogSource,
    ): team.sakhi.models.PeriodLog {
        val targetUserId = session.targetUserId
        val timestampIso = Clock.System.now().toString()
        val nextSymptoms = canonical?.symptoms.orEmpty()
            .filterNot { value -> removedSymptoms.any { it.value == value } }
            .toMutableList()
            .apply {
                addedSymptoms.forEach { symptom ->
                    if (symptom.value !in this) add(symptom.value)
                }
            }
        val nextMoods = canonical?.moods.orEmpty()
            .filterNot { value -> removedMoods.any { it.value == value } }
            .toMutableList()
            .apply {
                addedMoods.forEach { mood ->
                    if (mood.value !in this) add(mood.value)
                }
            }

        val updated = team.sakhi.models.PeriodLog(
            id = canonical?.id ?: DataMigration.stablePeriodLogId(
                userId = targetUserId,
                logDate = date.toString(),
                sourceUserId = attributedUserId,
            ),
            userId = targetUserId,
            logDate = date,
            periodPresent = canonical?.periodPresent ?: false,
            flowIntensity = canonical?.flowIntensity,
            loggedBy = logSource,
            createdByUserId = canonical?.createdByUserId ?: actorUserId,
            sourceUserId = canonical?.sourceUserId ?: attributedUserId,
            partnerLogId = canonical?.partnerLogId,
            isOverridden = canonical?.isOverridden ?: false,
            overriddenPartnerLogId = canonical?.overriddenPartnerLogId,
            notes = canonical?.notes,
            symptoms = nextSymptoms,
            moods = nextMoods,
            sexualActivity = canonical?.sexualActivity ?: "none",
            medications = canonical?.medications ?: emptyList(),
            medicationDosages = canonical?.medicationDosages ?: emptyList(),
            createdAt = canonical?.createdAt?.takeIf { it.isNotBlank() } ?: timestampIso,
            updatedAt = timestampIso,
            history = canonical?.let { old ->
                old.history + LogDiffer.buildHistoryEntry(
                    old = old,
                    new = old.copy(
                        symptoms = nextSymptoms,
                        moods = nextMoods,
                    ),
                    changedBy = actorUserId,
                )
            } ?: emptyList(),
        )
        return updated
    }

    private fun logsForSource(
        logs: List<team.sakhi.models.PeriodLog>,
        targetUserId: String,
        sourceUserId: String,
    ): List<team.sakhi.models.PeriodLog> {
        return if (sourceUserId.equals(targetUserId, ignoreCase = true)) {
            logs.filter {
                it.sourceUserId.equals(sourceUserId, ignoreCase = true) || it.sourceUserId.isBlank()
            }
        } else {
            logs.filter { it.sourceUserId.equals(sourceUserId, ignoreCase = true) }
        }
    }

    private fun canonicalLog(
        logs: List<team.sakhi.models.PeriodLog>,
        incomingSource: LogSource,
    ): team.sakhi.models.PeriodLog? {
        fun latest(predicate: (team.sakhi.models.PeriodLog) -> Boolean): team.sakhi.models.PeriodLog? =
            team.sakhi.logging.PeriodLogPolicy.latestAction(logs.filter(predicate))

        return if (incomingSource == LogSource.USER) {
            latest { it.loggedBy == LogSource.USER }
                ?: latest { it.loggedBy == LogSource.SYSTEM }
                ?: team.sakhi.logging.PeriodLogPolicy.latestAction(logs)
        } else {
            latest { it.loggedBy.isCareViewerLog }
                ?: latest { it.loggedBy == LogSource.SYSTEM }
                ?: team.sakhi.logging.PeriodLogPolicy.latestAction(logs)
        }
    }

    private fun attributedSourceUserId(session: SessionContext): String {
        val logSource = session.activeRole.toLogSource()
        return if (logSource.isCareViewerLog) session.userId else session.targetUserId
    }
}

private fun List<ConversationMessage>.markFailed(messageId: String): List<ConversationMessage> {
    return map { message ->
        if (message.id == messageId) message.copy(isSynced = true, isFailed = true) else message
    }
}

private fun List<ConversationMessage>.expandAssistantGeneralMessages(): List<ConversationMessage> {
    return flatMap { message ->
        if (!message.isAssistant || message.sessionId == "welcome" || message.places.isNotEmpty()) {
            listOf(message)
        } else {
            val chunks = splitIntoChunks(message.content)
            if (chunks.size <= 1) {
                listOf(message)
            } else {
                chunks.mapIndexed { index, chunk ->
                    message.copy(
                        id = "${message.id}#$index",
                        content = chunk,
                        places = emptyList(),
                        cardType = AICardType.GENERAL,
                    )
                }
            }
        }
    }
}

private fun List<ConversationMessage>.replaceMessage(updated: ConversationMessage): List<ConversationMessage> {
    return map { message -> if (message.id == updated.id) updated else message }
}

private fun List<ConversationMessage>.clearFailedState(messageId: String): List<ConversationMessage> {
    return map { message ->
        if (message.id == messageId) {
            message.copy(isSynced = false, isFailed = false)
        } else {
            message
        }
    }
}

private fun List<ConversationMessage>.mergeRemoteMessages(
    remoteMessages: List<ConversationMessage>,
): List<ConversationMessage> {
    val liveMessages = filter { it.sessionId != "welcome" }
    val existingIds = liveMessages.mapTo(linkedSetOf(), ConversationMessage::id)
    val incoming = remoteMessages.filterNot { it.id in existingIds }
    return (liveMessages + incoming).sortedBy { it.timestamp }
}

private fun team.sakhi.models.PeriodLog?.moodsAsSet(): Set<Mood> =
    this?.moods?.mapNotNull(Mood::from)?.toSet() ?: emptySet()

private fun team.sakhi.models.PeriodLog?.symptomsAsSet(): Set<Symptom> =
    this?.symptoms?.mapNotNull(Symptom::from)?.toSet() ?: emptySet()

private fun UserCareRole.toLogSource(): LogSource = when (this) {
    UserCareRole.PRIMARY_USER -> LogSource.USER
    UserCareRole.PARTNER -> LogSource.PARTNER
    UserCareRole.MOTHER -> LogSource.MOTHER
    UserCareRole.FATHER -> LogSource.FATHER
    UserCareRole.DAUGHTER -> LogSource.USER
}

private fun Throwable.isRetryableChatFailure(): Boolean {
    val network = this as? NetworkException
    if (network?.canRetry == true) return true
    val normalized = message?.lowercase().orEmpty()
    return normalized.contains("unable to resolve host") ||
        normalized.contains("no internet") ||
        normalized.contains("offline") ||
        normalized.contains("timed out") ||
        normalized.contains("timeout") ||
        normalized.contains("connection")
}

private fun splitIntoChunks(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val paragraphs = trimmed.split("\n\n")
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (paragraphs.size > 1) {
        return paragraphs.flatMap(::splitIntoChunks)
    }

    val sentences = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < trimmed.length) {
        val ch = trimmed[index]
        current.append(ch)
        val next = index + 1
        if (ch == '.' || ch == '?' || ch == '!') {
            when {
                next == trimmed.length -> {
                    sentences += current.toString().trim()
                    current.clear()
                    index = next
                    continue
                }

                trimmed[next] == ' ' -> {
                    sentences += current.toString().trim()
                    current.clear()
                    index = next + 1
                    continue
                }
            }
        }
        index = next
    }

    val tail = current.toString().trim()
    if (tail.isNotEmpty()) {
        sentences += tail
    }

    if (sentences.size <= 1) {
        return if (trimmed.length <= 80) listOf(trimmed) else breakAtComma(trimmed)
    }

    return sentences.flatMap { sentence ->
        if (sentence.length <= 80) listOf(sentence) else breakAtComma(sentence)
    }
}

private fun breakAtComma(text: String): List<String> {
    val midpoint = text.length / 2
    var bestIndex: Int? = null
    var bestDistance = Int.MAX_VALUE

    fun check(substring: String) {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val rangeStart = text.indexOf(substring, startIndex = searchFrom, ignoreCase = true)
            if (rangeStart < 0) break
            val rangeEnd = rangeStart + substring.length
            val distance = abs(rangeStart - midpoint)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = rangeEnd
            }
            searchFrom = rangeEnd
        }
    }

    check(",")
    check(", ")
    check(" and ")
    check(" but ")
    check(" so ")
    check(" because ")

    return bestIndex?.let { splitIndex ->
        val first = text.substring(0, splitIndex).trim()
        val second = text.substring(splitIndex).trim()
        if (first.isNotEmpty() && second.isNotEmpty()) {
            listOf(first, second)
        } else {
            listOf(text)
        }
    } ?: listOf(text)
}

private fun List<team.sakhi.models.CycleData>.filteredForReportWindow(
    from: kotlinx.datetime.LocalDate,
    to: kotlinx.datetime.LocalDate,
): List<team.sakhi.models.CycleData> {
    val earliestCycleStart = from.minus(2, DateTimeUnit.YEAR)
    return filter { cycle ->
        cycle.cycleStartDate >= earliestCycleStart && cycle.cycleStartDate <= to
    }
}

/** Namespace for deriving a stable chat `session_id` UUID. */
private const val AI_SESSION_NAMESPACE = "3f1c8a2e-5d47-4b93-9c26-8a71d0e4b5c9"
