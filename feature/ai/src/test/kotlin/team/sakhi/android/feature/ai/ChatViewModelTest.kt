package team.sakhi.android.feature.ai

import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.ai.AICardClassifier
import team.sakhi.android.feature.reports.ReportDateRangePreset
import team.sakhi.android.feature.reports.ReportPdfExporter
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidLocationProvider
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.platform.DeviceLocation
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.localdb.SharedLocalRecordCodec
import team.sakhi.models.AICardType
import team.sakhi.models.AIQueryIntent
import team.sakhi.models.CarePartnership
import team.sakhi.models.ConversationMessage
import team.sakhi.models.SafePlace
import team.sakhi.models.SakhiAIContext
import team.sakhi.models.UserCareRole
import team.sakhi.network.NetworkException
import team.sakhi.repositories.AIRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.ai.SafePlaceRanker
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.sync.OfflineUpgradeDataset

/**
 * State-machine test for `ChatViewModel`. `SessionManager`/`AIRepository`/
 * `CycleDataRepository`/`PeriodLogRepository`/`ReportPdfExporter`/`SafePlaceRanker`/
 * `AndroidLocationProvider`/`AndroidHapticManager` are concrete, non-open KMM/platform
 * classes (same situation as `HomeViewModelTest`), so this uses mockk for those
 * boundaries. Intent detection and card-type selection run through the *actual*
 * `AIQueryClassifier`/`AICardClassifier` -- a real regression in either shared rule
 * would fail this test too, not just a stubbed value. `generateReport()`'s
 * `withContext(Dispatchers.IO)` needs the same real, off-scheduler wait technique
 * `ReportsViewModelTest` established, since `advanceUntilIdle()` alone can't wait for
 * a real IO-dispatched coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<ChatViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        while (ToastManager.current.value != null) {
            ToastManager.dismiss()
        }
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { viewModel ->
            viewModel.javaClass.getMethod("clear\$lifecycle_viewmodel_release").invoke(viewModel)
        }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.awaitUiState(
        viewModel: ChatViewModel,
        timeoutMs: Long = 5_000,
        predicate: (ChatUiState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(viewModel.uiState.value)) return
            withContext(Dispatchers.Default) { delay(5) }
            advanceUntilIdle()
            if (predicate(viewModel.uiState.value)) return
        }
        error("Timed out waiting for UI state: ${viewModel.uiState.value}")
    }

    private suspend fun TestScope.awaitUiStateWithoutAdvancingTime(
        viewModel: ChatViewModel,
        timeoutMs: Long = 5_000,
        predicate: (ChatUiState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(viewModel.uiState.value)) return
            withContext(Dispatchers.Default) { delay(5) }
            runCurrent()
            if (predicate(viewModel.uiState.value)) return
        }
        error("Timed out waiting for UI state without advancing time: ${viewModel.uiState.value}")
    }

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = userId,
        permissions: SessionPermissions = SessionPermissions.primaryUser,
    ) = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = if (userId == targetUserId) UserCareRole.PRIMARY_USER else UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = permissions,
        activePartnership = if (targetUserId != userId) {
            CarePartnership(
                id = "partnership-1",
                userId = targetUserId,
                partnerId = userId,
                partnerName = "Partner",
            )
        } else {
            null
        },
    )

    // Real fix (2026-07-16): `ChatViewModel`'s own `sessionId(session)` is now
    // the same composite `userId|targetUserId|isViewingOwnData` key as
    // `activeSessionKey` (previously just `session.userId`, which is why a
    // single device user's self-mode and partner-mode conversations used to
    // bleed into each other -- `session.userId` never varies by viewing
    // mode). Tests that exercise the *real* local-cache/session-scoping
    // logic (not just an `any()`-mocked repository return value) need their
    // fixture `sessionId` to match this real production key.
    private fun SessionContext.testSessionId(): String = "$userId|$targetUserId|$isViewingOwnData"

    /** Stubs both `session` and `current` -- the init block always subscribes to `session`. */
    private fun mockSessionManager(session: SessionContext?) = mockk<SessionManager> {
        every { this@mockk.session } returns MutableStateFlow(session)
        every { current } returns session
    }

    private fun mockContext(): Context = mockk {
        every { getString(R.string.chat_error_location_permission) } returns "I need location access to find nearby places."
        every { getString(R.string.chat_error_places_unavailable) } returns "I couldn't load nearby places right now. Try again in a moment."
        every { getString(R.string.chat_error_no_nearby_places) } returns "I couldn't find any nearby places from your current location."
        every { getString(R.string.chat_error_report_retry) } returns "I couldn't generate the report this time."
        every { getString(R.string.chat_error_report_permission_denied) } returns "She hasn't given you permission to generate or export reports."
        every { getString(R.string.chat_report_ready) } returns "Your report is ready. Sharing it now."
        every { getString(R.string.chat_error_load_history) } returns "Unable to load chat history right now."
        every { getString(R.string.chat_error_send_message) } returns "Unable to send that message right now."
        every { getString(R.string.chat_auto_log_title, any()) } answers {
            val names = (invocation.args[1] as Array<*>).first()
            "Logged $names"
        }
        every { getString(R.string.chat_auto_log_undo) } returns "Undo"
        every { getString(R.string.chat_welcome_intro_named, any()) } answers {
            val firstName = (invocation.args[1] as Array<*>).first()
            "Hey, $firstName. I'm Sakhi. I've been waiting to meet you."
        }
        every { getString(R.string.chat_welcome_intro_unnamed) } returns "Hey. I'm Sakhi. I've been waiting to meet you."
        every { getString(R.string.chat_welcome_follow_up_named) } returns "How has your body been feeling lately? Or if something is on your mind today, just say it."
        every { getString(R.string.chat_welcome_follow_up_unnamed) } returns "I don't know your name yet. What should I call you?"
        every { getString(R.string.chat_welcome_partner_intro) } returns "I'm here."
        every { getString(R.string.chat_welcome_partner_follow_up) } returns "Tell me how you would like to support her today."
        every { getString(R.string.chat_context_subject_default) } returns "her"
        every { getString(R.string.chat_context_user_default) } returns "you"
    }

    private fun newViewModel(
        sessionManager: SessionManager,
        aiRepository: AIRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi there")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        },
        cycleDataRepository: CycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll(any()) } returns Result.success(emptyList())
        },
        periodLogRepository: PeriodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.upsert(any()) } answers { Result.success(firstArg()) }
        },
        reportPdfExporter: ReportPdfExporter = mockk(),
        safePlaceRanker: SafePlaceRanker = mockk(),
        locationProvider: AndroidLocationProvider = mockk {
            every { hasPermission() } returns false
        },
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        widgetSnapshotManager: AndroidWidgetSnapshotManager = mockk(relaxed = true),
        localStore: SakhiPhaseALocalStore = mockk {
            coEvery { exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns emptyList()
            coEvery { upsertConversationMessage(any()) } returns Unit
        },
        appContext: Context = mockContext(),
        nearbyPlacesFetcher: NearbyPlacesFetcher = mockk(),
    ) = ChatViewModel(
        sessionManager,
        aiRepository,
        cycleDataRepository,
        periodLogRepository,
        reportPdfExporter,
        safePlaceRanker,
        locationProvider,
        hapticManager,
        widgetSnapshotManager,
        localStore,
        appContext,
        nearbyPlacesFetcher,
    ).also(createdViewModels::add)

    @Test
    fun `no session resets to a stopped-loading empty state`() = runTest {
        val sessionManager = mockSessionManager(null)
        val viewModel = newViewModel(sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `own session loads real conversation history and clears loading`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val existing = ConversationMessage(
            id = "m1",
            role = "user",
            content = "hello",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = "user-1",
            userId = "user-1",
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(listOf(existing))
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(listOf("chip1"))
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)

        awaitUiState(viewModel) { !it.isLoading }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(existing), state.messages)
        assertEquals(listOf("chip1"), state.suggestionChips)
    }

    @Test
    fun `assistant general history reopens through displayMessages as one sentence per bubble`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val existing = ConversationMessage(
            id = "m1",
            role = "assistant",
            content = "First sentence. Second sentence?",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = "user-1",
            userId = "user-1",
            isSynced = true,
            cardType = AICardType.GENERAL,
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(listOf(existing))
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)

        awaitUiState(viewModel) { !it.isLoading }

        val state = viewModel.uiState.value
        assertEquals(listOf(existing), state.messages)
        assertEquals(listOf("First sentence.", "Second sentence?"), state.displayMessages.map { it.content })
        assertEquals(listOf("m1#0", "m1#1"), state.displayMessages.map { it.id })
    }

    @Test
    fun `local conversation history preloads immediately before the cloud fetch completes`() = runTest {
        val testSession = sessionContext()
        val gate = CompletableDeferred<Unit>()
        val sessionManager = mockSessionManager(testSession)
        val localMessage = ConversationMessage(
            id = "local-1",
            role = "assistant",
            content = "From local Room cache",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = testSession.testSessionId(),
            userId = "user-1",
            isSynced = true,
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns listOf(
                SharedLocalRecordCodec.conversationMessageEnvelope(localMessage),
            )
            coEvery { it.upsertConversationMessage(any()) } returns Unit
        }

        val viewModel = newViewModel(
            sessionManager = sessionManager,
            aiRepository = aiRepository,
            localStore = localStore,
        )
        awaitUiState(viewModel) { it.isLoading && it.messages == listOf(localMessage) }

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertEquals(listOf(localMessage), loadingState.messages)

        gate.complete(Unit)
        awaitUiState(viewModel) { !it.isLoading }
    }

    @Test
    fun `empty history keeps the deterministic two bubble warm open instead of a generated empty state message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)

        awaitUiState(viewModel) { !it.isLoading }

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("Hey, Test. I'm Sakhi. I've been waiting to meet you.", state.messages[0].content)
        assertEquals(
            "How has your body been feeling lately? Or if something is on your mind today, just say it.",
            state.messages[1].content,
        )
        assertEquals(
            listOf(
                "Hey, Test. I'm Sakhi. I've been waiting to meet you.",
                "How has your body been feeling lately? Or if something is on your mind today, just say it.",
            ),
            state.displayMessages.map { it.content },
        )
        assertTrue(state.messages.first().isAssistant)
        coVerify(exactly = 0) { aiRepository.generateWelcomeMessage(any()) }
    }

    @Test
    fun `history load failure still keeps both local welcome bubbles`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.failure(RuntimeException("network down"))
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)

        awaitUiState(viewModel) { !it.isLoading && it.messages.size == 2 }

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(2, state.displayMessages.size)
    }

    @Test
    fun `history fetch completion merges instead of clobbering a user message sent while loading`() = runTest {
        val testSession = sessionContext()
        val gate = CompletableDeferred<Unit>()
        val sessionManager = mockSessionManager(testSession)
        val localMessage = ConversationMessage(
            id = "local-1",
            role = "assistant",
            content = "Local preload",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = testSession.testSessionId(),
            userId = "user-1",
            isSynced = true,
        )
        val remoteMessage = ConversationMessage(
            id = "remote-1",
            role = "assistant",
            content = "Remote history",
            timestamp = "2026-07-14T00:01:00Z",
            sessionId = testSession.testSessionId(),
            userId = "user-1",
            isSynced = true,
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } coAnswers {
                gate.await()
                Result.success(listOf(remoteMessage))
            }
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.success("Assistant reply")
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns listOf(
                SharedLocalRecordCodec.conversationMessageEnvelope(localMessage),
            )
            coEvery { it.upsertConversationMessage(any()) } returns Unit
        }
        val viewModel = newViewModel(
            sessionManager = sessionManager,
            aiRepository = aiRepository,
            localStore = localStore,
        )
        awaitUiState(viewModel) { it.isLoading && it.messages == listOf(localMessage) }

        viewModel.onInputChanged("hello while loading")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.messages.any { message -> message.content == "Assistant reply" } }
        gate.complete(Unit)
        awaitUiState(viewModel) { !it.isLoading && it.messages.any { message -> message.content == "Remote history" } }

        val contents = viewModel.uiState.value.messages.map { it.content }
        assertTrue("hello while loading" in contents)
        assertTrue("Remote history" in contents)
        assertTrue("Assistant reply" in contents)
    }

    @Test
    fun `the error toast auto-dismisses after a real 3 second window, matching iOS's showError timer`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading && it.messages.size == 2 }

        viewModel.onInputChanged("hello there")
        viewModel.sendCurrentMessage()
        runCurrent()
        // Was asserting the RAW exception message. That assertion pinned a real
        // defect: Supabase/Ktor messages embed the request URL, the
        // `Authorization: Bearer ...` header and the apikey, and they rendered
        // verbatim as user-visible error text (seen on a real device). The UI must
        // show app copy; the raw cause is logged only.
        assertEquals("Unable to send that message right now.", viewModel.uiState.value.error)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `sendCurrentMessage with blank input is a no-op`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading && it.messages.size == 2 }

        viewModel.onInputChanged("   ")
        viewModel.sendCurrentMessage()
        advanceUntilIdle()

        coVerify(exactly = 0) { aiRepository.sendMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage detects a real HEALTH intent and still splits the assistant text bubbles`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), AIQueryIntent.HEALTH, any()) } returns
                Result.success("Rest is good for cramps. A warm compress can help too.")
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("I have really bad cramps today")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.messages.count { message -> message.sessionId != "welcome" } == 3 }

        coVerify(exactly = 1) { aiRepository.sendMessage(any(), any(), AIQueryIntent.HEALTH, any()) }
        val assistantReplies = viewModel.uiState.value.messages.filter { it.isAssistant && it.sessionId != "welcome" }
        assertEquals(listOf("Rest is good for cramps.", "A warm compress can help too."), assistantReplies.map { it.content })
        assertTrue(assistantReplies.all { it.cardType == AICardType.CRAMP_RELIEF })
    }

    @Test
    fun `sendMessage splits a general assistant reply into multiple assistant bubbles and saves each chunk`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val savedMessages = mutableListOf<ConversationMessage>()
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(capture(savedMessages)) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), "How should I think about this?", AIQueryIntent.GENERAL, any()) } returns
                Result.success("First sentence. Second sentence! Third sentence?")
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("How should I think about this?")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.messages.count { message -> message.sessionId != "welcome" } == 4 }

        val state = viewModel.uiState.value
        val assistantReplies = state.messages.filter { it.isAssistant && it.sessionId != "welcome" }
        assertEquals(listOf("First sentence.", "Second sentence!", "Third sentence?"), assistantReplies.map { it.content })
        assertEquals(assistantReplies.size, assistantReplies.map { it.id }.distinct().size)
        assertEquals(assistantReplies.map { it.id }, state.displayMessages.filter { it.isAssistant && it.sessionId != "welcome" }.map { it.id })
        assertEquals(
            listOf(
                "How should I think about this?",
                "First sentence.",
                "Second sentence!",
                "Third sentence?",
            ),
            savedMessages.takeLast(4).map { it.content },
        )
    }

    @Test
    fun `sendMessage auto logs newly detected symptoms and moods for self chat`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), AIQueryIntent.HEALTH, any()) } returns Result.success("Take rest.")
        }
        val savedLog = slot<team.sakhi.models.PeriodLog>()
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.upsert(capture(savedLog)) } answers { Result.success(savedLog.captured) }
        }
        val viewModel = newViewModel(
            sessionManager = sessionManager,
            aiRepository = aiRepository,
            periodLogRepository = periodLogRepository,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("I have cramps and I feel anxious today")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && ToastManager.current.value?.title == "Logged Cramps, Anxious" }

        assertTrue("cramps" in savedLog.captured.symptoms)
        assertTrue("anxious" in savedLog.captured.moods)
        assertEquals("Logged Cramps, Anxious", ToastManager.current.value?.title)
    }

    @Test
    fun `sendMessage detects a real report request and opens a report session instead of calling the repository`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.reportSession != null && !it.isSending }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reportSession != null)
        coVerify(exactly = 0) { aiRepository.sendMessage(any(), any(), any(), any()) }
        coVerify(exactly = 1) { aiRepository.saveMessage(match { it.role == "user" }) }
    }

    @Test
    fun `sendMessage keeps report-like prompts in the normal repository flow while viewing a partner session`() = runTest {
        val partnerSession = sessionContext(userId = "user-1", targetUserId = "user-2")
        val sessionManager = mockSessionManager(partnerSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), "please generate report for me", any(), any()) } returns
                Result.success("I can summarize this here, but exports stay in your own profile.")
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.messages.lastOrNull()?.isAssistant == true }

        val state = viewModel.uiState.value
        assertNull(state.reportSession)
        assertTrue(state.messages.last().isAssistant)
        coVerify(exactly = 1) { aiRepository.sendMessage(any(), "please generate report for me", any(), any()) }
    }

    @Test
    fun `sendMessage non retryable failure marks the user message failed and surfaces a real message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.failure(RuntimeException("bad request"))
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading && it.messages.size == 2 }

        viewModel.onInputChanged("hello there")
        viewModel.sendCurrentMessage()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        // Was asserting the RAW exception message. That assertion pinned a real
        // defect: Supabase/Ktor messages embed the request URL, the
        // `Authorization: Bearer ...` header and the apikey, and they rendered
        // verbatim as user-visible error text (seen on a real device). The UI must
        // show app copy; the raw cause is logged only.
        assertEquals("Unable to send that message right now.", state.error)
        assertTrue(state.messages.first { it.isUser }.isFailed)
        advanceUntilIdle()
    }

    @Test
    fun `sendMessage retryable failure keeps the user message pending and retries on resume`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returnsMany listOf(
                Result.failure(NetworkException(NetworkException.NO_CONNECTION, "No internet connection")),
                Result.success("Retried reply"),
            )
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("hello there")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.error == null && it.messages.any { message -> message.isUser } }

        val pendingState = viewModel.uiState.value
        val pendingUser = pendingState.messages.first { it.isUser }
        assertFalse(pendingState.isSending)
        assertNull(pendingState.error)
        assertFalse(pendingUser.isSynced)
        assertFalse(pendingUser.isFailed)

        viewModel.retryPendingMessages()
        advanceUntilIdle()

        val retriedState = viewModel.uiState.value
        val retriedUser = retriedState.messages.first { it.isUser }
        assertTrue(retriedUser.isSynced)
        assertFalse(retriedUser.isFailed)
        assertEquals("Retried reply", retriedState.messages.last().content)
    }

    @Test
    fun `legacy cloud history falls back to the user wide fetch and seeds the local cache with the stable session id`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val legacyMessage = ConversationMessage(
            id = "legacy-1",
            role = "assistant",
            content = "From a pre-fix session",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = "random-session-id",
            userId = "user-1",
            isSynced = true,
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.getMessages("user-1", 0, 100) } returns Result.success(listOf(legacyMessage))
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
        }
        val cachedMessage = slot<ConversationMessage>()
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns emptyList()
            coEvery { it.upsertConversationMessage(capture(cachedMessage)) } returns Unit
        }

        val viewModel = newViewModel(
            sessionManager = sessionManager,
            aiRepository = aiRepository,
            localStore = localStore,
        )
        awaitUiState(viewModel) { !it.isLoading && it.messages.size == 1 }

        val state = viewModel.uiState.value
        assertEquals("From a pre-fix session", state.messages.single().content)
        assertEquals(testSession.testSessionId(), state.messages.single().sessionId)
        assertEquals(testSession.testSessionId(), cachedMessage.captured.sessionId)
    }

    @Test
    fun `sendMessage with a LOCATION intent and no permission sets needsLocationPermission without querying places`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.success("Here's a place")
        }
        val safePlaceRanker = mockk<SafePlaceRanker>()
        val locationProvider = mockk<AndroidLocationProvider> { every { hasPermission() } returns false }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.needsLocationPermission }

        assertTrue(viewModel.uiState.value.needsLocationPermission)
        coVerify(exactly = 0) { safePlaceRanker.findNearby(any(), any(), any()) }
    }

    @Test
    fun `granting location permission resumes the original location send and marks that same user message delivered`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val places = listOf(SafePlace("p1", "City Hospital", 200.0, latitude = 12.0, longitude = 77.0))
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.success("Found a hospital nearby")
        }
        var permissionGranted = false
        val safePlaceRanker = mockk<SafePlaceRanker> {
            coEvery { findNearby(12.0, 77.0, "hospital") } returns Result.success(places)
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } answers { permissionGranted }
            coEvery { currentLocation() } returns DeviceLocation(12.0, 77.0)
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.needsLocationPermission && !it.isSending && it.messages.any(ConversationMessage::isUser) }

        val pendingUserId = viewModel.uiState.value.messages.first(ConversationMessage::isUser).id
        assertFalse(viewModel.uiState.value.messages.first(ConversationMessage::isUser).isSynced)

        permissionGranted = true
        viewModel.onLocationPermissionResult(true)
        awaitUiState(viewModel) {
            !it.needsLocationPermission &&
                !it.isSending &&
                it.messages.lastOrNull()?.places == places &&
                it.messages.firstOrNull { message -> message.id == pendingUserId }?.isSynced == true
        }

        val state = viewModel.uiState.value
        val resumedUser = state.messages.first { it.id == pendingUserId }
        assertTrue(resumedUser.isSynced)
        assertFalse(resumedUser.isFailed)
        assertEquals("Found a hospital nearby", state.messages.last().content)
        assertEquals(places, state.messages.last().places)
        coVerify(exactly = 1) { aiRepository.sendMessage(any(), "where is the nearest hospital", any(), any()) }
        coVerify(exactly = 1) { safePlaceRanker.findNearby(12.0, 77.0, "hospital") }
    }

    @Test
    fun `a second location message before permission resolution does not overwrite the first pending send`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } coAnswers {
                Result.success("Reply for ${secondArg<String>()}")
            }
        }
        var permissionGranted = false
        val safePlaceRanker = mockk<SafePlaceRanker> {
            coEvery { findNearby(12.0, 77.0, "hospital") } returns Result.success(
                listOf(SafePlace("p1", "City Hospital", 200.0, latitude = 12.0, longitude = 77.0)),
            )
            coEvery { findNearby(12.0, 77.0, "police_station") } returns Result.success(
                listOf(SafePlace("p2", "Police Station", 300.0, latitude = 12.0, longitude = 77.0)),
            )
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } answers { permissionGranted }
            coEvery { currentLocation() } returns DeviceLocation(12.0, 77.0)
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.needsLocationPermission && it.messages.count(ConversationMessage::isUser) == 1 }

        viewModel.onInputChanged("where is the nearest police station")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) {
            it.needsLocationPermission &&
                !it.isSending &&
                it.messages.count(ConversationMessage::isUser) == 2 &&
                it.messages.filter(ConversationMessage::isUser).all { message -> !message.isSynced }
        }

        permissionGranted = true
        viewModel.onLocationPermissionResult(true)
        awaitUiState(viewModel) {
            !it.needsLocationPermission &&
                !it.isSending &&
                it.messages.filter(ConversationMessage::isUser).size == 2 &&
                it.messages.filter(ConversationMessage::isUser).all { message -> message.isSynced } &&
                it.messages.count { message -> message.content.startsWith("Reply for ") } == 2
        }

        val state = viewModel.uiState.value
        val assistantContents = state.messages.filter(ConversationMessage::isAssistant).map(ConversationMessage::content)
        assertTrue("Reply for where is the nearest hospital" in assistantContents)
        assertTrue("Reply for where is the nearest police station" in assistantContents)
        coVerify(exactly = 1) { aiRepository.sendMessage(any(), "where is the nearest hospital", any(), any()) }
        coVerify(exactly = 1) { aiRepository.sendMessage(any(), "where is the nearest police station", any(), any()) }
        coVerify(exactly = 1) { safePlaceRanker.findNearby(12.0, 77.0, "hospital") }
        coVerify(exactly = 1) { safePlaceRanker.findNearby(12.0, 77.0, "police_station") }
    }

    @Test
    fun `sendMessage with a LOCATION intent and permission queries real nearby places via the real place-type classifier`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val places = listOf(SafePlace("p1", "City Hospital", 200.0, latitude = 12.0, longitude = 77.0))
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
            coEvery { it.sendMessage(any(), any(), any(), any()) } returns Result.success("Found a hospital nearby")
        }
        val safePlaceRanker = mockk<SafePlaceRanker> {
            coEvery { findNearby(12.0, 77.0, "hospital") } returns Result.success(places)
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } returns true
            coEvery { currentLocation() } returns DeviceLocation(12.0, 77.0)
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { !it.isSending && it.messages.lastOrNull()?.places == places }

        assertFalse(viewModel.uiState.value.needsLocationPermission)
        val lastMessage = viewModel.uiState.value.messages.last()
        assertEquals(places, lastMessage.places)
        assertEquals(AICardType.PLACES, lastMessage.cardType)
    }

    @Test
    fun `sendMessage with a LOCATION intent surfaces diagnostic lookup failures instead of flattening them into no results`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val safePlaceRanker = mockk<SafePlaceRanker> {
            coEvery { findNearby(12.0, 77.0, "hospital") } returns Result.failure(IllegalStateException("REQUEST_DENIED"))
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } returns true
            coEvery { currentLocation() } returns DeviceLocation(12.0, 77.0)
        }
        val nearbyPlacesFetcher = mockk<NearbyPlacesFetcher> {
            coEvery {
                inspectNearby(latitude = 12.0, longitude = 77.0, placeType = "hospital")
            } returns NearbyPlacesFetchResult.LookupError(
                status = "REQUEST_DENIED",
                errorMessage = "This API project is not authorized to use this API.",
                httpCode = 200,
            )
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
            nearbyPlacesFetcher = nearbyPlacesFetcher,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) {
            !it.isSending &&
                it.messages.lastOrNull()?.content == "I couldn't load nearby places right now. Try again in a moment."
        }

        val state = viewModel.uiState.value
        assertEquals("I couldn't load nearby places right now. Try again in a moment.", state.messages.last().content)
        assertTrue(state.messages.none { it.places.isNotEmpty() })
        coVerify(exactly = 1) { nearbyPlacesFetcher.inspectNearby(12.0, 77.0, "hospital") }
    }

    @Test
    fun `sendMessage with a LOCATION intent surfaces real zero-results copy when both fetchers find nothing`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val safePlaceRanker = mockk<SafePlaceRanker> {
            coEvery { findNearby(12.0, 77.0, "hospital") } returns Result.success(emptyList())
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } returns true
            coEvery { currentLocation() } returns DeviceLocation(12.0, 77.0)
        }
        val nearbyPlacesFetcher = mockk<NearbyPlacesFetcher> {
            coEvery {
                inspectNearby(latitude = 12.0, longitude = 77.0, placeType = "hospital")
            } returns NearbyPlacesFetchResult.ZeroResults
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            safePlaceRanker = safePlaceRanker,
            locationProvider = locationProvider,
            nearbyPlacesFetcher = nearbyPlacesFetcher,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) {
            !it.isSending &&
                it.messages.lastOrNull()?.content == "I couldn't find any nearby places from your current location."
        }

        val state = viewModel.uiState.value
        assertEquals("I couldn't find any nearby places from your current location.", state.messages.last().content)
        assertTrue(state.messages.none { it.places.isNotEmpty() })
        coVerify(exactly = 1) { nearbyPlacesFetcher.inspectNearby(12.0, 77.0, "hospital") }
    }

    @Test
    fun `denying location permission resolves the original location message and appends a real local denial reply`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val locationProvider = mockk<AndroidLocationProvider> { every { hasPermission() } returns false }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            locationProvider = locationProvider,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("where is the nearest hospital")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.needsLocationPermission && it.messages.any(ConversationMessage::isUser) }

        val pendingUserId = viewModel.uiState.value.messages.first(ConversationMessage::isUser).id
        viewModel.onLocationPermissionResult(false)
        awaitUiState(viewModel) {
            !it.needsLocationPermission &&
                !it.isSending &&
                it.messages.lastOrNull()?.content == "I need location access to find nearby places." &&
                it.messages.firstOrNull { message -> message.id == pendingUserId }?.isSynced == true
        }

        val state = viewModel.uiState.value
        val deniedUser = state.messages.first { it.id == pendingUserId }
        assertFalse(state.needsLocationPermission)
        assertTrue(deniedUser.isSynced)
        assertFalse(deniedUser.isFailed)
        assertEquals("I need location access to find nearby places.", state.messages.last().content)
    }

    @Test
    fun `reload restores a cached location-intent send back into permission-pending state instead of orphaning it`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val pendingLocationMessage = ConversationMessage(
            id = "pending-location-1",
            role = "user",
            content = "where is the nearest hospital",
            timestamp = "2026-07-16T05:30:00Z",
            sessionId = testSession.testSessionId(),
            userId = "user-1",
            isSynced = false,
            isFailed = false,
            cardType = AICardType.GENERAL,
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val localStore = mockk<SakhiPhaseALocalStore>().also {
            coEvery { it.exportRecords(OfflineUpgradeDataset.AI_MESSAGES) } returns listOf(
                SharedLocalRecordCodec.conversationMessageEnvelope(pendingLocationMessage),
            )
            coEvery { it.upsertConversationMessage(any()) } returns Unit
        }
        val locationProvider = mockk<AndroidLocationProvider> {
            every { hasPermission() } returns false
        }
        val viewModel = newViewModel(
            sessionManager = sessionManager,
            aiRepository = aiRepository,
            localStore = localStore,
            locationProvider = locationProvider,
        )

        awaitUiState(viewModel) {
            !it.isLoading &&
                it.needsLocationPermission &&
                it.messages.any { message -> message.id == "pending-location-1" && !message.isSynced }
        }

        val state = viewModel.uiState.value
        assertTrue(state.needsLocationPermission)
        assertFalse(state.isSending)
        assertEquals(listOf("pending-location-1"), state.messages.filter(ConversationMessage::isUser).map(ConversationMessage::id))
    }

    @Test
    fun `confirmClearConversation clears messages optimistically then calls the real delete and reload`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val existing = ConversationMessage(
            id = "m1",
            role = "user",
            content = "hello",
            timestamp = "2026-07-14T00:00:00Z",
            sessionId = "user-1",
            userId = "user-1",
        )
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(listOf(existing))
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.deleteConversation(any(), any()) } returns Result.success(Unit)
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }
        assertTrue(viewModel.uiState.value.messages.isNotEmpty())

        viewModel.confirmClearConversation()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.showClearConfirm)
        advanceUntilIdle()
        coVerify(exactly = 1) { aiRepository.deleteConversation("user-1", testSession.testSessionId()) }
    }

    // Real fix (2026-07-16): the previous `confirmClearConversation` used
    // `session.targetUserId` for the delete's `userId` param, which only
    // happens to equal `session.userId` in the self-mode case the test above
    // covers -- meaning that test could never have caught the real bug.
    // Uses a genuinely partner-mode session (`userId != targetUserId`) to
    // prove the delete call now uses the actual authenticated `userId`
    // (matching what save/load key conversations under), not the
    // partner-viewed `targetUserId`.
    @Test
    fun `confirmClearConversation in partner mode deletes under the real userId, not targetUserId`() = runTest {
        val testSession = sessionContext(userId = "partner-1", targetUserId = "primary-1")
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.deleteConversation(any(), any()) } returns Result.success(Unit)
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository)
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.confirmClearConversation()
        advanceUntilIdle()

        coVerify(exactly = 1) { aiRepository.deleteConversation("partner-1", testSession.testSessionId()) }
        coVerify(exactly = 0) { aiRepository.deleteConversation("primary-1", any()) }
    }

    // Real security fix (2026-07-16), defense-in-depth: `sendMessage()`'s own
    // report-detection is self-mode-only (`!context.isPartnerMode`), so a
    // partner session can never organically reach `generateReport()` through
    // the normal chip/keyword flow -- that upstream branch was previously
    // the *only* thing stopping a partner without the grant. Drives
    // `reportSession` into existence via `selectReportRange` (which has no
    // partner-mode gate of its own, matching how a future/alternate call
    // site could reach `generateReport()`) to prove the gate inside
    // `generateReport()` itself, matching `ReportsViewModel.generate()`'s
    // exact pattern, actually blocks it independently.
    @Test
    fun `generateReport is blocked for a partner session without GENERATE_REPORTS and never touches the repositories`() = runTest {
        val restrictedPermissions = SessionPermissions.primaryUser.copy(canGenerateReports = false)
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            permissions = restrictedPermissions,
        )
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val periodLogRepository = mockk<PeriodLogRepository>()
        val reportPdfExporter = mockk<ReportPdfExporter>()
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.selectReportRange(ReportDateRangePreset.ThreeMonths)
        assertTrue(viewModel.uiState.value.reportSession != null)

        viewModel.generateReport()
        awaitUiState(viewModel) {
            it.reportSession == null &&
                it.messages.lastOrNull()?.content == "She hasn't given you permission to generate or export reports."
        }

        assertNull(viewModel.uiState.value.reportSession)
        coVerify(exactly = 0) { cycleDataRepository.getAll(any()) }
        coVerify(exactly = 0) { periodLogRepository.getForDateRange(any(), any(), any()) }
        coVerify(exactly = 0) { reportPdfExporter.export(any(), any()) }
    }

    // Same session shape as the blocked test above, but with the permission
    // actually granted -- proves the gate is independence-checked (blocks
    // without it, allows with it), not just "always blocks partner mode".
    @Test
    fun `generateReport succeeds for a partner session explicitly granted GENERATE_REPORTS`() = runTest {
        val grantedPermissions = SessionPermissions.primaryUser.copy(
            canViewPredictions = false,
            canViewCycleHistory = false,
            canLogPeriod = false,
            canViewDailyLogs = false,
            canGenerateReports = true,
        )
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            permissions = grantedPermissions,
        )
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("primary-1") } returns Result.success(emptyList())
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
        }
        val fakeFile = java.io.File("/tmp/SakhiReport_partner_test.pdf")
        val fakeUri = mockk<Uri>()
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } returns fakeFile
            every { buildShareUri(fakeFile) } returns fakeUri
        }
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.selectReportRange(ReportDateRangePreset.ThreeMonths)
        viewModel.generateReport()
        awaitUiState(viewModel) { it.reportSession == null && it.sharePdfUri == fakeUri }

        assertEquals(fakeUri, viewModel.uiState.value.sharePdfUri)
    }

    @Test
    fun `generateReport success builds a real report and stores a real share uri`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val fakeFile = java.io.File("/tmp/SakhiReport_test.pdf")
        val fakeUri = mockk<Uri>()
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } returns fakeFile
            every { buildShareUri(fakeFile) } returns fakeUri
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository, reportPdfExporter = reportPdfExporter)
        awaitUiState(viewModel) { !it.isLoading }
        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.reportSession != null }

        viewModel.generateReport()
        awaitUiState(viewModel) { it.reportSession == null && it.sharePdfUri == fakeUri }

        val state = viewModel.uiState.value
        assertNull(state.reportSession)
        assertEquals(fakeUri, state.sharePdfUri)
        assertEquals("Your report is ready. Sharing it now.", state.messages.last().content)
    }

    @Test
    fun `generateReport failure appends a real retry message and clears the report session`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } throws RuntimeException("disk full")
        }
        val viewModel = newViewModel(sessionManager, aiRepository = aiRepository, reportPdfExporter = reportPdfExporter)
        awaitUiState(viewModel) { !it.isLoading }
        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.reportSession != null }

        viewModel.generateReport()
        awaitUiState(viewModel) {
            it.reportSession == null &&
                it.messages.lastOrNull()?.content == "I couldn't generate the report this time."
        }

        val state = viewModel.uiState.value
        assertNull(state.reportSession)
        assertEquals("I couldn't generate the report this time.", state.messages.last().content)
    }

    @Test
    fun `generateReport with a lost current session clears the report sheet and never starts export`() = runTest {
        val testSession = sessionContext()
        val currentSession = arrayOf<SessionContext?>(testSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(testSession)
            every { current } answers { currentSession[0] }
        }
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val reportPdfExporter = mockk<ReportPdfExporter>()
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            reportPdfExporter = reportPdfExporter,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.reportSession != null }

        currentSession[0] = null
        viewModel.generateReport()
        awaitUiState(viewModel) { it.reportSession == null }

        val state = viewModel.uiState.value
        assertNull(state.reportSession)
        coVerify(exactly = 0) { reportPdfExporter.export(any(), any()) }
    }

    @Test
    fun `generateReport completion after a session swap is discarded instead of reviving the abandoned session state`() = runTest {
        val sessionA = sessionContext(userId = "user-1", targetUserId = "user-1")
        val sessionB = sessionContext(userId = "user-2", targetUserId = "user-2")
        val sessionFlow = MutableStateFlow(sessionA)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { sessionFlow.value }
        }
        val gate = CompletableDeferred<Unit>()
        val aiRepository = mockk<AIRepository>().also {
            coEvery { it.getConversationHistory(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.generateWelcomeMessage(any()) } returns Result.success("Hi")
            coEvery { it.generateSuggestionChips(any()) } returns Result.success(emptyList())
            coEvery { it.saveMessage(any()) } returns Result.success(Unit)
        }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("user-1", any(), any()) } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
        }
        val reportPdfExporter = mockk<ReportPdfExporter>()
        val viewModel = newViewModel(
            sessionManager,
            aiRepository = aiRepository,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.onInputChanged("please generate report for me")
        viewModel.sendCurrentMessage()
        awaitUiState(viewModel) { it.reportSession != null }

        viewModel.generateReport()
        awaitUiState(viewModel) { it.reportSession?.isGenerating == true }

        sessionFlow.value = sessionB
        gate.complete(Unit)
        advanceUntilIdle()
        awaitUiState(viewModel) { !it.isLoading && it.session?.userId == "user-2" }

        val state = viewModel.uiState.value
        assertEquals("user-2", state.session?.userId)
        assertNull(state.reportSession)
        assertNull(state.sharePdfUri)
        assertTrue(state.messages.none { it.content == "Your report is ready. Sharing it now." })
        verify(exactly = 0) { reportPdfExporter.export(any(), any()) }
    }
}
