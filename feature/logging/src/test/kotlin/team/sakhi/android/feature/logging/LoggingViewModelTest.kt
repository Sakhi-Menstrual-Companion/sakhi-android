package team.sakhi.android.feature.logging

import android.content.Context
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.date.DateConverter
import team.sakhi.logging.DischargeColor
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserCareRole
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.sync.DataMigration

/**
 * State-machine test for `LoggingViewModel` -- the richest ViewModel tested in this
 * thread so far. `SessionManager`/`PeriodLogRepository`/`AndroidHapticManager`/
 * `AndroidWidgetSnapshotManager` are concrete, non-open KMM/platform classes (same
 * situation as `HomeViewModelTest`), so this uses mockk for those boundaries. Token
 * decoding/encoding, date/flow validation, and the care-viewer mutation rule all run
 * through the *actual* `LogTokenEncoder`/`PeriodLogPolicy`/`DataMigration` functions --
 * a real regression in any of that shared logic would fail this test too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoggingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun permissions(
        logPeriod: Boolean = true,
        viewNotes: Boolean = true,
        viewSymptoms: Boolean = true,
        viewMoods: Boolean = true,
        viewWeight: Boolean = true,
        viewTemperature: Boolean = true,
        viewDailyLogs: Boolean = true,
        viewDischarge: Boolean = true,
        viewMedications: Boolean = true,
    ) = SessionPermissions(
        canViewPeriodDates = true,
        canLogPeriod = logPeriod,
        canViewSymptoms = viewSymptoms,
        canViewMoods = viewMoods,
        canViewMedications = viewMedications,
        canViewPredictions = true,
        canViewCycleHistory = true,
        canViewDailyLogs = viewDailyLogs,
        canViewOvulationTests = true,
        canViewTemperature = viewTemperature,
        canViewWeight = viewWeight,
        canViewNotes = viewNotes,
        canViewDischarge = viewDischarge,
        canViewSexualActivity = true,
    )

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = "user-1",
        logPeriod: Boolean = true,
        viewNotes: Boolean = true,
        viewSymptoms: Boolean = true,
        viewMoods: Boolean = true,
        viewWeight: Boolean = true,
        viewTemperature: Boolean = true,
        viewDailyLogs: Boolean = true,
        viewDischarge: Boolean = true,
        viewMedications: Boolean = true,
    ) = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = if (userId == targetUserId) UserCareRole.PRIMARY_USER else UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = permissions(
            logPeriod, viewNotes, viewSymptoms, viewMoods,
            viewWeight, viewTemperature, viewDailyLogs, viewDischarge, viewMedications,
        ),
    )

    /** Stubs both `session` and `current` -- the init block always subscribes to `session`. */
    private fun mockSessionManager(session: SessionContext?) = mockk<SessionManager> {
        every { this@mockk.session } returns MutableStateFlow(session)
        every { current } returns session
    }

    private fun mockContext(): Context = mockk {
        every { getString(R.string.logging_error_session_not_ready) } returns "Session is not ready yet."
        every { getString(R.string.logging_error_care_role_cannot_save) } returns "cannot save"
        every { getString(R.string.logging_error_primary_latest_log) } returns "She made the latest change on this date, so you can no longer edit or remove it."
        every { getString(R.string.logging_error_future_date) } returns "You can only log today or earlier."
        every { getString(R.string.logging_error_missing_flow) } returns "Pick a flow intensity before saving."
        every { getString(R.string.logging_error_flow_without_period) } returns "Flow cannot be saved without a period."
        every { getString(R.string.logging_save_success, any()) } returns "Saved"
        every { getString(R.string.logging_header_date_format) } returns "d MMMM"
        every { getString(R.string.logging_error_failed_to_save) } returns "Failed to save log"
        every { getString(R.string.logging_error_failed_to_load_existing) } returns "Failed to load existing logs"
        every { getString(R.string.logging_error_failed_to_load_daily) } returns "Failed to load daily log"
    }

    private fun newViewModel(
        sessionManager: SessionManager,
        periodLogRepository: PeriodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
        },
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        widgetSnapshotManager: AndroidWidgetSnapshotManager = mockk(relaxed = true),
        appContext: Context = mockContext(),
    ) = LoggingViewModel(appContext, sessionManager, periodLogRepository, hapticManager, widgetSnapshotManager)

    @Test
    fun `no session resets to a fresh state for the selected date`() = runTest {
        val sessionManager = mockSessionManager(null)
        val viewModel = newViewModel(sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertFalse(state.canLogPeriod)
        assertEquals(DateConverter.today(), state.selectedDate)
    }

    @Test
    fun `own-data session sets every edit flag true regardless of the underlying permission flags`() = runTest {
        val testSession = sessionContext(
            viewNotes = false,
            viewSymptoms = false,
            viewMoods = false,
            viewWeight = false,
            viewTemperature = false,
            viewDailyLogs = false,
            viewDischarge = false,
            viewMedications = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditNotes)
        assertTrue(state.canEditSymptoms)
        assertTrue(state.canEditMoods)
        assertTrue(state.canViewWeight)
        assertTrue(state.canViewTemperature)
        assertTrue(state.canViewDailyLogs)
        assertTrue(state.canViewDischarge)
        assertTrue(state.canViewMedications)
        assertTrue(state.canMutateSelectedDate)
    }

    @Test
    fun `partner session gates each edit flag independently by its own permission`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewNotes = true,
            viewSymptoms = false,
            viewMoods = false,
            viewWeight = false,
            viewTemperature = true,
            viewDailyLogs = false,
            viewDischarge = true,
            viewMedications = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditNotes)
        assertFalse(state.canEditSymptoms)
        assertFalse(state.canEditMoods)
        assertFalse(state.canViewWeight)
        assertTrue(state.canViewTemperature)
        assertFalse(state.canViewDailyLogs)
        assertTrue(state.canViewDischarge)
        assertFalse(state.canViewMedications)
    }

    @Test
    fun `loadEntry decodes real weight, BBT, discharge, painkiller, and doctor tokens through the actual LogTokenEncoder`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val existing = PeriodLog(
            id = "log-1",
            userId = "user-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            flowIntensity = FlowIntensity.MEDIUM,
            createdByUserId = "user-1",
            sourceUserId = "user-1",
            symptoms = listOf(
                Symptom.CRAMPS.value,
                LogTokenEncoder.encodeWeight(65.5),
                LogTokenEncoder.encodeBbt(36.7),
                LogTokenEncoder.encodeDischargeColor(DischargeColor.BROWN.value),
                LogTokenEncoder.TOKEN_PAINKILLER,
                LogTokenEncoder.TOKEN_DOCTOR,
            ),
            moods = listOf(Mood.HAPPY.value),
            notes = "felt tired",
        )
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(existing))
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FlowIntensity.MEDIUM, state.selectedFlow)
        assertTrue(Symptom.CRAMPS in state.selectedSymptoms)
        assertEquals(65.5, state.weightKg)
        assertEquals(36.7, state.bbtCelsius)
        assertEquals(DischargeColor.BROWN, state.dischargeColor)
        assertTrue(state.painkillerTaken)
        assertTrue(state.doctorVisited)
        assertTrue(Mood.HAPPY in state.selectedMoods)
        assertEquals("felt tired", state.notes)
    }

    @Test
    fun `loadEntry redacts weight, BBT, discharge, and medication tokens independently of the general symptoms permission`() = runTest {
        // Real bug this test guards against: a partner with VIEW_SYMPTOMS granted
        // but VIEW_WEIGHT/VIEW_TEMPERATURE/VIEW_DISCHARGE/VIEW_MEDICATIONS denied
        // used to still see the real weight/BBT/discharge/painkiller/doctor values,
        // because decoding was gated by the single coarse `canEditSymptoms` flag
        // instead of each value's own granular permission (iOS gates each of these
        // behind its own `Feature(.weight/.temperature/.discharge/.medications)`).
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewSymptoms = true,
            viewWeight = false,
            viewTemperature = false,
            viewDischarge = false,
            viewMedications = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val existing = PeriodLog(
            id = "log-1",
            userId = "primary-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            loggedBy = LogSource.PARTNER,
            createdByUserId = "partner-1",
            sourceUserId = "partner-1",
            symptoms = listOf(
                Symptom.CRAMPS.value,
                LogTokenEncoder.encodeWeight(65.5),
                LogTokenEncoder.encodeBbt(36.7),
                LogTokenEncoder.encodeDischargeColor(DischargeColor.BROWN.value),
                LogTokenEncoder.TOKEN_PAINKILLER,
                LogTokenEncoder.TOKEN_DOCTOR,
            ),
        )
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(existing))
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditSymptoms)
        assertTrue(Symptom.CRAMPS in state.selectedSymptoms)
        assertNull(state.weightKg)
        assertNull(state.bbtCelsius)
        assertNull(state.dischargeColor)
        assertFalse(state.painkillerTaken)
        assertFalse(state.doctorVisited)
    }

    @Test
    fun `loadEntry gates the Mood symptom sub-category by canEditMoods, not canEditSymptoms`() = runTest {
        // Real bug this test guards against: the Mood section's symptoms
        // (MOOD_SWINGS/IRRITABILITY/ANXIETY/SADNESS_LOW_MOOD/BRAIN_FOG) were
        // decoded under the general symptoms flag, so a partner denied VIEW_MOODS
        // but granted VIEW_SYMPTOMS could still see real mood-symptom data.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewSymptoms = true,
            viewMoods = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val existing = PeriodLog(
            id = "log-1",
            userId = "primary-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            loggedBy = LogSource.PARTNER,
            createdByUserId = "partner-1",
            sourceUserId = "partner-1",
            symptoms = listOf(Symptom.CRAMPS.value, Symptom.MOOD_SWINGS.value, Symptom.BRAIN_FOG.value),
        )
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(existing))
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(Symptom.CRAMPS in state.selectedSymptoms)
        assertFalse(Symptom.MOOD_SWINGS in state.selectedSymptoms)
        assertFalse(Symptom.BRAIN_FOG in state.selectedSymptoms)
    }

    @Test
    fun `save preserves a real canonical discharge color the current viewer cannot see instead of wiping it`() = runTest {
        // Real data-loss risk introduced by hiding sections per-permission: since a
        // restricted viewer never has the real value loaded into state, naively
        // rebuilding all tokens from `state` on save would silently erase the
        // canonical discharge color. It must round-trip untouched instead.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewDischarge = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val canonical = PeriodLog(
            id = "log-1",
            userId = "primary-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            flowIntensity = FlowIntensity.LIGHT,
            loggedBy = LogSource.PARTNER,
            createdByUserId = "partner-1",
            sourceUserId = "partner-1",
            symptoms = listOf(LogTokenEncoder.encodeDischargeColor(DischargeColor.BROWN.value)),
        )
        val upsertSlot: CapturingSlot<PeriodLog> = slot()
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(canonical))
            coEvery { it.upsert(capture(upsertSlot)) } answers { Result.success(upsertSlot.captured) }
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.dischargeColor)

        viewModel.onFlowSelected(FlowIntensity.HEAVY)
        viewModel.save()
        advanceUntilIdle()

        val savedLog = upsertSlot.captured
        assertTrue(savedLog.symptoms.contains(LogTokenEncoder.encodeDischargeColor(DischargeColor.BROWN.value)))
        assertEquals(FlowIntensity.HEAVY, savedLog.flowIntensity)
    }

    @Test
    fun `toggleSymptom is a no-op and fires no haptic when the session cannot edit symptoms`() = runTest {
        val testSession = sessionContext(userId = "partner-1", targetUserId = "primary-1", viewSymptoms = false)
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.toggleSymptom(Symptom.CRAMPS)

        assertTrue(viewModel.uiState.value.selectedSymptoms.isEmpty())
        verify(exactly = 0) { hapticManager.selection() }
    }

    @Test
    fun `togglePainkillerTaken flips the flag and fires a haptic when editing is allowed`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.togglePainkillerTaken()

        assertTrue(viewModel.uiState.value.painkillerTaken)
        verify(exactly = 1) { hapticManager.selection() }
    }

    @Test
    fun `save blocked when no current session is ready surfaces the real message`() = runTest {
        val sessionManager = mockSessionManager(null)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals("Session is not ready yet.", viewModel.uiState.value.error)
    }

    @Test
    fun `save blocked when the session lacks LOG_PERIOD permission surfaces the real message`() = runTest {
        val testSession = sessionContext(logPeriod = false)
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals("cannot save", viewModel.uiState.value.error)
    }

    @Test
    fun `save blocked on a future date via the real LogTokenEncoder validation`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        viewModel.showNextDay()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals("You can only log today or earlier.", viewModel.uiState.value.error)
    }

    @Test
    fun `save blocked by the real PeriodLogPolicy care-viewer-mutate rule when someone else logged last`() = runTest {
        val testSession = sessionContext(userId = "partner-1", targetUserId = "primary-1")
        val sessionManager = mockSessionManager(testSession)
        val existing = PeriodLog(
            id = "log-1",
            userId = "primary-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            flowIntensity = FlowIntensity.LIGHT,
            loggedBy = LogSource.PARTNER,
            createdByUserId = "other-partner",
            sourceUserId = "other-partner",
        )
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(existing))
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.canMutateSelectedDate)
        assertEquals(
            "She made the latest change on this date, so you can no longer edit or remove it.",
            state.error,
        )
        coVerify(exactly = 0) { periodLogRepository.upsert(any()) }
    }

    @Test
    fun `save success builds a real PeriodLog through DataMigration and LogTokenEncoder, then upserts and refreshes the widget`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val upsertSlot: CapturingSlot<PeriodLog> = slot()
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.upsert(capture(upsertSlot)) } answers { Result.success(upsertSlot.captured) }
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val widgetSnapshotManager = mockk<AndroidWidgetSnapshotManager>(relaxed = true)
        val viewModel = newViewModel(
            sessionManager,
            periodLogRepository = periodLogRepository,
            hapticManager = hapticManager,
            widgetSnapshotManager = widgetSnapshotManager,
        )
        advanceUntilIdle()

        viewModel.onFlowSelected(FlowIntensity.HEAVY)
        viewModel.toggleSymptom(Symptom.CRAMPS)
        viewModel.onWeightChanged(60.0)
        viewModel.togglePainkillerTaken()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNull(state.error)
        val savedLog = upsertSlot.captured
        val expectedId = DataMigration.stablePeriodLogId(
            userId = "user-1",
            logDate = DateConverter.today().toString(),
            sourceUserId = "user-1",
        )
        assertEquals(expectedId, savedLog.id)
        assertTrue(savedLog.symptoms.contains(Symptom.CRAMPS.value))
        assertTrue(savedLog.symptoms.contains(LogTokenEncoder.encodeWeight(60.0)))
        assertTrue(savedLog.symptoms.contains(LogTokenEncoder.TOKEN_PAINKILLER))
        assertEquals(FlowIntensity.HEAVY, savedLog.flowIntensity)
        verify(exactly = 1) { widgetSnapshotManager.refreshAsync() }
        verify(exactly = 1) { hapticManager.success() }
    }

    @Test
    fun `save failure surfaces a real message and fires a haptic error`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.upsert(any()) } returns Result.failure(RuntimeException("disk full"))
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertEquals("disk full", state.error)
        assertEquals(1, state.saveAttemptId)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `each save call bumps saveAttemptId even when isSaving flips true and false within the same virtual-time step`() = runTest {
        // Real bug found via a real on-device offline test: a fast failure (e.g. an
        // immediate DNS resolution error while offline) can set isSaving true then
        // false+error again before the UI's StateFlow collector ever observes the
        // intermediate true frame -- StateFlow only guarantees delivery of the latest
        // value. `runTest`'s virtual time collapses every suspension point the same
        // way, so this test proves saveAttemptId is what lets the UI tell two
        // failures apart even though it can't rely on ever seeing `isSaving == true`.
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { it.upsert(any()) } returns Result.failure(RuntimeException("offline"))
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.saveAttemptId)
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.save()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.saveAttemptId)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `save is a no-op re-entry guard while a save is already in flight`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val gate = CompletableDeferred<Unit>()
        // The very first `getForDateRange` call is the init block's own `loadEntry`, not
        // `save()`'s -- it must resolve immediately so the ViewModel's permission/edit
        // flags actually populate. Only calls after that (i.e. `save()`'s own fetch)
        // should gate, otherwise `save()` never even gets past its own guard clauses.
        var callCount = 0
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } coAnswers {
                callCount += 1
                if (callCount > 1) gate.await()
                Result.success(emptyList())
            }
            coEvery { it.upsert(any()) } answers { Result.success(firstArg()) }
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.save()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 2) { periodLogRepository.getForDateRange(any(), any(), any()) }
    }

    @Test
    fun `a save resolving after the selected date has since changed is discarded`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } coAnswers {
                callCount += 1
                if (callCount > 1) gate.await()
                Result.success(emptyList())
            }
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.showNextDay()
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.saveMessage)
    }

    @Test
    fun `a same-target session swap discards a stale save completion`() = runTest {
        val ownSession = sessionContext(userId = "user-1", targetUserId = "user-1")
        val restrictedPartnerSession = sessionContext(
            userId = "partner-1",
            targetUserId = "user-1",
            logPeriod = false,
            viewNotes = false,
            viewSymptoms = false,
            viewMoods = false,
        )
        val sessionFlow = MutableStateFlow<SessionContext?>(ownSession)
        val currentSlot = arrayOf<SessionContext?>(ownSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("user-1", DateConverter.today(), DateConverter.today()) } coAnswers {
                callCount += 1
                Result.success(emptyList())
            }
            coEvery { it.upsert(any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                Result.success(firstArg())
            }
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.onFlowSelected(FlowIntensity.HEAVY)
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaving)

        currentSlot[0] = restrictedPartnerSession
        sessionFlow.value = restrictedPartnerSession
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canEditNotes)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.canLogPeriod)
        assertFalse(state.canEditNotes)
        assertFalse(state.canEditSymptoms)
        assertFalse(state.canEditMoods)
        assertFalse(state.isSaving)
        assertNull(state.saveMessage)
    }

    @Test
    fun `showNextDay and showPreviousDay navigate the real selected date and trigger a real reload`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val today = DateConverter.today()
        val tomorrow = DateConverter.addDays(today, 1)
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("user-1", today, today) } returns Result.success(emptyList())
            coEvery { it.getForDateRange("user-1", tomorrow, tomorrow) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.showNextDay()
        advanceUntilIdle()

        assertEquals(tomorrow, viewModel.uiState.value.selectedDate)
        coVerify(exactly = 1) { periodLogRepository.getForDateRange("user-1", tomorrow, tomorrow) }

        viewModel.showPreviousDay()
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        coVerify(exactly = 2) { periodLogRepository.getForDateRange("user-1", today, today) }
    }

    @Test
    fun `onDischargeColorSelected selects a real color, then deselects it back to null on a second tap of the same color`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onDischargeColorSelected(DischargeColor.WHITE)
        assertEquals(DischargeColor.WHITE, viewModel.uiState.value.dischargeColor)

        viewModel.onDischargeColorSelected(DischargeColor.WHITE)
        assertNull(viewModel.uiState.value.dischargeColor)

        viewModel.onDischargeColorSelected(DischargeColor.YELLOW)
        assertEquals(DischargeColor.YELLOW, viewModel.uiState.value.dischargeColor)
    }

    @Test
    fun `onDischargeColorSelected is a no-op for a partner session without symptom-edit access`() = runTest {
        val testSession = sessionContext(userId = "partner-1", targetUserId = "primary-1", viewSymptoms = false)
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onDischargeColorSelected(DischargeColor.WHITE)

        assertNull(viewModel.uiState.value.dischargeColor)
    }

    @Test
    fun `toggleMood is gated by its own real canEditMoods flag, independent of symptom-edit access`() = runTest {
        // Real, easy-to-copy-paste-wrong shape: this session's own established
        // gating pattern for the symptom-adjacent setters checks `canEditSymptoms`,
        // but moods are governed by a *separate* permission (`VIEW_MOODS`) --
        // proving `toggleMood` checks its own flag, not a copy-pasted one.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewSymptoms = true,
            viewMoods = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canEditSymptoms)
        assertFalse(viewModel.uiState.value.canEditMoods)

        viewModel.toggleMood(Mood.HAPPY)

        assertTrue(viewModel.uiState.value.selectedMoods.isEmpty())
        verify(exactly = 0) { hapticManager.selection() }
    }

    @Test
    fun `onNotesChanged is gated by its own real canEditNotes flag, independent of symptom-edit access`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewSymptoms = true,
            viewNotes = false,
        )
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canEditSymptoms)
        assertFalse(viewModel.uiState.value.canEditNotes)

        viewModel.onNotesChanged("felt great today")

        assertEquals("", viewModel.uiState.value.notes)
    }

    @Test
    fun `toggleMood stays blocked for a partner even when granted the VIEW_MOODS permission`() = runTest {
        // Real bug found in this session's own critical self-review: unlike every
        // sibling toggle (toggleSymptom/onWeightChanged/onBbtChanged/
        // onDischargeColorSelected/togglePainkillerTaken/toggleDoctorVisited, all of
        // which additionally check `session?.isViewingOwnData == false`), this used
        // to check ONLY `canEditMoods` -- which is `true` for a partner granted
        // `VIEW_MOODS`, since that flag is computed as `isViewingOwnData ||
        // can(VIEW_MOODS)`. iOS's real `toggleMood(_:)` guards on `!isPartnerView`
        // unconditionally: mood editing is user-only there, full stop, no permission
        // can grant a partner edit access. `viewMoods = true` here is the crucial
        // difference from the sibling test above (which used `viewMoods = false`
        // and would have passed even under the old, buggy gating).
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewMoods = true,
        )
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canEditMoods)

        viewModel.toggleMood(Mood.HAPPY)

        assertTrue(viewModel.uiState.value.selectedMoods.isEmpty())
        verify(exactly = 0) { hapticManager.selection() }
    }

    @Test
    fun `onNotesChanged stays blocked for a partner even when granted the VIEW_NOTES permission`() = runTest {
        // Same real bug, same session-review pass, as the `toggleMood` test above --
        // `viewNotes = true` here (vs. `viewNotes = false` in the sibling test) is
        // exactly the scenario the old `if (!state.canEditNotes)`-only guard let
        // through: a partner granted just *view* access to notes could still edit
        // and persist the primary user's notes text, which iOS's unconditional
        // `guard !isPartnerView` in `updateNotes(_:)` never allows.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            viewNotes = true,
        )
        val sessionManager = mockSessionManager(testSession)
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canEditNotes)

        viewModel.onNotesChanged("felt great today")

        assertEquals("", viewModel.uiState.value.notes)
    }

    @Test
    fun `save with an existing real SYSTEM log but no prior USER log falls back through the real canonicalLog chain`() = runTest {
        // Real merge-precedence rule in `canonicalLog()`: a USER-sourced save prefers
        // the latest USER log, then falls back to the latest SYSTEM log (e.g. a
        // Health Connect import) before falling back to any log at all. Every
        // existing save test starts from a completely empty log list, so this
        // fallback chain itself was never actually exercised.
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val systemLog = PeriodLog(
            id = "system-log-1",
            userId = "user-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            flowIntensity = FlowIntensity.LIGHT,
            loggedBy = LogSource.SYSTEM,
            createdByUserId = "user-1",
            sourceUserId = "user-1",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val upsertSlot: CapturingSlot<PeriodLog> = slot()
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(systemLog))
            coEvery { it.upsert(capture(upsertSlot)) } answers { Result.success(upsertSlot.captured) }
        }
        val viewModel = newViewModel(sessionManager, periodLogRepository = periodLogRepository)
        advanceUntilIdle()

        viewModel.onFlowSelected(FlowIntensity.HEAVY)
        viewModel.save()
        advanceUntilIdle()

        val savedLog = upsertSlot.captured
        // Reused the real SYSTEM log's identity/creation time rather than minting a
        // brand-new stable id -- proof the SYSTEM fallback branch actually ran.
        assertEquals("system-log-1", savedLog.id)
        assertEquals("2026-01-01T00:00:00Z", savedLog.createdAt)
        assertEquals(FlowIntensity.HEAVY, savedLog.flowIntensity)
        assertEquals(LogSource.USER, savedLog.loggedBy)
        assertNull(viewModel.uiState.value.error)
    }
}
