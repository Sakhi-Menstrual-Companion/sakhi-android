package team.sakhi.android.feature.home

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.date.DateConverter
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.models.CarePartnership
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.LogHistoryEntry
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PartnerHealthSnapshot
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.sync.SyncRuntimeState
import team.sakhi.sync.SyncStore

/**
 * First state-machine test for `HomeViewModel`, the app's central Home-screen
 * adapter. `SessionManager`/`SyncStore`/`CycleDataRepository`/`PeriodLogRepository`
 * are all concrete, non-open KMM classes (same situation as
 * `PartnerChecklistViewModelTest`), so this uses mockk for those boundaries and
 * real `SessionContext`/`CycleData` KMM data classes directly. `Context` is mocked
 * only for the single `getString` fallback call the ViewModel actually makes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        canViewPredictions: Boolean = true,
        canViewCycleHistory: Boolean = true,
        canLogPeriod: Boolean = true,
        canViewDailyLogs: Boolean = true,
        canViewSymptoms: Boolean = true,
        canViewMoods: Boolean = true,
        canViewTemperature: Boolean = true,
        canViewWeight: Boolean = true,
    ) = SessionPermissions(
        canViewPeriodDates = true,
        canLogPeriod = canLogPeriod,
        canViewSymptoms = canViewSymptoms,
        canViewMoods = canViewMoods,
        canViewMedications = true,
        canViewPredictions = canViewPredictions,
        canViewCycleHistory = canViewCycleHistory,
        canViewDailyLogs = canViewDailyLogs,
        canViewOvulationTests = true,
        canViewTemperature = canViewTemperature,
        canViewWeight = canViewWeight,
        canViewNotes = true,
        canViewDischarge = true,
        canViewSexualActivity = true,
    )

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = "user-1",
        canViewPredictions: Boolean = true,
        canViewCycleHistory: Boolean = true,
        canLogPeriod: Boolean = true,
        canViewDailyLogs: Boolean = true,
        canViewSymptoms: Boolean = true,
        canViewMoods: Boolean = true,
        canViewTemperature: Boolean = true,
        canViewWeight: Boolean = true,
    ): SessionContext = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = targetUserId,
        permissions = permissions(
            canViewPredictions = canViewPredictions,
            canViewCycleHistory = canViewCycleHistory,
            canLogPeriod = canLogPeriod,
            canViewDailyLogs = canViewDailyLogs,
            canViewSymptoms = canViewSymptoms,
            canViewMoods = canViewMoods,
            canViewTemperature = canViewTemperature,
            canViewWeight = canViewWeight,
        ),
        activePartnership = if (targetUserId != userId) {
            CarePartnership(id = "partnership-1", userId = targetUserId, partnerId = userId, partnerName = "Partner")
        } else {
            null
        },
    )

    // periodLength=5 always keeps day-of-cycle < 5 unambiguously MENSTRUAL regardless
    // of AppConfig's ovulation-window constants.
    private fun menstrualCycle(userId: String = "user-1", daysAgo: Int = 2): CycleData = CycleData(
        id = "cycle-1",
        userId = userId,
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodLength = 5,
        cycleLength = 28,
    )

    // daysAgo=7 with periodLength=5/cycleLength=28/LUTEAL_PHASE_DAYS=14/
    // BEFORE_OVULATION_DAYS=5 lands well inside the real FOLLICULAR window (7 < 9).
    private fun follicularCycle(userId: String = "user-1", daysAgo: Int = 7): CycleData = CycleData(
        id = "cycle-2",
        userId = userId,
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodLength = 5,
        cycleLength = 28,
    )

    private fun newViewModel(
        sessionManager: SessionManager,
        syncStore: SyncStore,
        cycleDataRepository: CycleDataRepository = mockk(),
        periodLogRepository: PeriodLogRepository = mockk {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
        },
        appContext: Context = mockk {
            every { getString(R.string.home_load_cycle_failed) } returns "Failed to load cycle data"
        },
    ) = HomeViewModel(sessionManager, syncStore, cycleDataRepository, periodLogRepository, appContext)

    @Test
    fun `no session resets to a default state without touching repositories`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        assertEquals(HomeUiState(syncState = SyncRuntimeState.Idle), viewModel.uiState.value)
    }

    @Test
    fun `real session loads real cycle data through the actual CycleMath phase calculation`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycle = menstrualCycle()
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(cycle)
            coEvery { getAll("user-1") } returns Result.success(listOf(cycle))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.MENSTRUAL, state.phase)
        assertEquals(3, state.dayInCycle)
        assertEquals(28, state.cycleLength)
        assertTrue(state.hasCycleData)
        assertEquals(cycle, state.currentCycle)
        assertFalse(state.isLoadingCycle)
        assertNull(state.error)
        assertTrue(state.canViewPredictions)
        assertTrue(state.canLogPeriod)
    }

    @Test
    fun `partner with no granted cycle or log access does not read partner repositories at all`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = false,
            canViewCycleHistory = false,
            canLogPeriod = false,
            canViewDailyLogs = false,
            canViewSymptoms = false,
            canViewMoods = false,
            canViewTemperature = false,
            canViewWeight = false,
        )
        val sessionFlow = MutableStateFlow(testSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val periodLogRepository = mockk<PeriodLogRepository>()
        val viewModel = newViewModel(
            sessionManager = sessionManager,
            syncStore = syncStore,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.UNKNOWN, state.phase)
        assertFalse(state.hasCycleData)
        assertFalse(state.hasLoggedForSelectedDate)
        assertNull(state.selectedLog)
        coVerify(exactly = 0) { cycleDataRepository.getLatest(any()) }
        coVerify(exactly = 0) { cycleDataRepository.getAll(any()) }
        coVerify(exactly = 0) { periodLogRepository.getForDateRange(any(), any(), any()) }
    }

    @Test
    fun `partner home log is redacted down to only the granted fields`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = true,
            canViewCycleHistory = false,
            canLogPeriod = false,
            canViewDailyLogs = false,
            canViewSymptoms = false,
            canViewMoods = true,
            canViewTemperature = false,
            canViewWeight = true,
        )
        val sessionFlow = MutableStateFlow(testSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycle = menstrualCycle(userId = "primary-1")
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(cycle)
            coEvery { getAll("primary-1") } returns Result.success(listOf(cycle))
        }
        val rawLog = PeriodLog(
            id = "log-1",
            userId = "primary-1",
            logDate = DateConverter.today(),
            periodPresent = true,
            flowIntensity = team.sakhi.models.FlowIntensity.HEAVY,
            createdByUserId = "primary-1",
            sourceUserId = "primary-1",
            notes = "private notes",
            symptoms = listOf(
                "cramps",
                LogTokenEncoder.encodeWeight(65.5),
                LogTokenEncoder.encodeBbt(36.8),
            ),
            moods = listOf("calm"),
            sexualActivity = "protected",
            medications = listOf("ibuprofen"),
            medicationDosages = listOf("200mg"),
            createdAt = "2026-07-15T08:00:00Z",
            updatedAt = "2026-07-15T09:00:00Z",
            history = listOf(
                LogHistoryEntry(
                    timestamp = "2026-07-15T09:00:00Z",
                    changedBy = "primary-1",
                    changes = mapOf("notes" to "private notes"),
                ),
            ),
        )
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange("primary-1", any(), any()) } returns Result.success(listOf(rawLog))
        }
        val viewModel = newViewModel(
            sessionManager = sessionManager,
            syncStore = syncStore,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        advanceUntilIdle()

        val visibleLog = requireNotNull(viewModel.uiState.value.selectedLog)
        assertNull(visibleLog.flowIntensity)
        assertTrue(visibleLog.symptoms.contains(LogTokenEncoder.encodeWeight(65.5)))
        assertFalse(visibleLog.symptoms.contains("cramps"))
        assertFalse(visibleLog.symptoms.contains(LogTokenEncoder.encodeBbt(36.8)))
        assertEquals(listOf("calm"), visibleLog.moods)
        assertFalse(visibleLog.periodPresent)
        assertEquals("", visibleLog.createdByUserId)
        assertEquals("", visibleLog.sourceUserId)
        assertNull(visibleLog.notes)
        assertEquals("none", visibleLog.sexualActivity)
        assertTrue(visibleLog.medications.isEmpty())
        assertTrue(visibleLog.medicationDosages.isEmpty())
        assertEquals("", visibleLog.createdAt)
        assertEquals("", visibleLog.updatedAt)
        assertTrue(visibleLog.history.isEmpty())
    }

    @Test
    fun `cycle load failure with a real message surfaces that message`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.failure(RuntimeException("network down"))
            coEvery { getAll("user-1") } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.UNKNOWN, state.phase)
        assertFalse(state.hasCycleData)
        assertFalse(state.isLoadingCycle)
        assertEquals("network down", state.error)
    }

    @Test
    fun `cycle load failure with no message falls back to the real string resource`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.failure(RuntimeException())
            coEvery { getAll("user-1") } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        assertEquals("Failed to load cycle data", viewModel.uiState.value.error)
    }

    @Test
    fun `switching target user resets derived cycle state instead of showing stale data`() = runTest {
        val ownSession = sessionContext(userId = "user-1", targetUserId = "user-1")
        val partnerSession = sessionContext(userId = "user-1", targetUserId = "partner-1")
        val sessionFlow = MutableStateFlow(ownSession)
        val currentSlot = arrayOf(ownSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val ownCycle = menstrualCycle(userId = "user-1")
        val partnerCycle = follicularCycle(userId = "partner-1")
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(ownCycle)
            coEvery { getLatest("partner-1") } returns Result.success(partnerCycle)
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)
        advanceUntilIdle()
        assertEquals(CyclePhase.MENSTRUAL, viewModel.uiState.value.phase)

        currentSlot[0] = partnerSession
        sessionFlow.value = partnerSession
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.FOLLICULAR, state.phase)
        assertEquals(partnerCycle, state.currentCycle)
    }

    @Test
    fun `a stale cycle response for an already-abandoned target is discarded`() = runTest {
        val sessionA = sessionContext(userId = "user-1", targetUserId = "user-1")
        val sessionB = sessionContext(userId = "user-1", targetUserId = "partner-1")
        val sessionFlow = MutableStateFlow(sessionA)
        val currentSlot = arrayOf(sessionA)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        // getLatest("user-1") never completes during this test -- simulates a slow
        // in-flight request for the target the user has since navigated away from.
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } coAnswers {
                kotlinx.coroutines.awaitCancellation()
            }
            coEvery { getLatest("partner-1") } returns Result.success(follicularCycle(userId = "partner-1"))
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)
        advanceUntilIdle()

        // Session changes to partner-1 while the user-1 request is still (forever) pending.
        currentSlot[0] = sessionB
        sessionFlow.value = sessionB
        advanceUntilIdle()

        // Only the real, current target's data should ever land in state.
        assertEquals(CyclePhase.FOLLICULAR, viewModel.uiState.value.phase)
        assertEquals("partner-1", viewModel.uiState.value.currentCycle?.userId)
    }

    @Test
    fun `refreshSelectedDate re-checks only the selected days log without touching cycle state`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
            coEvery { getAll("user-1") } returns Result.success(emptyList())
        }
        val today = DateConverter.today()
        val log = PeriodLog(
            id = "log-1",
            userId = "user-1",
            logDate = today,
            periodPresent = true,
            createdByUserId = "user-1",
            sourceUserId = "user-1",
        )
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange("user-1", today, today) } returns Result.success(listOf(log))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository, periodLogRepository)
        advanceUntilIdle()

        viewModel.refreshSelectedDate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasLoggedForSelectedDate)
        assertEquals(log, state.selectedLog)
        // Cycle-derived fields from the initial refresh() are untouched by refreshSelectedDate().
        assertEquals(CyclePhase.MENSTRUAL, state.phase)
    }

    // Real feature build (2026-07-16): the Calendar day-tap/arbitrary-date-view
    // prerequisite -- verifies `selectDate` recomputes phase/dayInCycle/
    // daysUntilNextPeriod for the new date via the already-loaded `currentCycle`
    // (a pure, synchronous `CycleMath` computation, no new network read for
    // those fields) and separately re-checks log presence for that date.
    @Test
    fun `selectDate recomputes cycle-derived fields for the new date and refreshes its log`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        // daysAgo=2 keeps "today" solidly MENSTRUAL (periodLength=5, dayOfCycle=3).
        // +5 days from today lands at dayOfCycle=8, which -- with
        // cycleLength=28/LUTEAL_PHASE_DAYS=14/BEFORE_OVULATION_DAYS=5 (ovulationDay=
        // max(28-14,5+1)=14, ovulationStart=max(14-5,5)=9) -- is a real, distinctly
        // different FOLLICULAR day (8 < 9), not just a same-phase no-op.
        val cycle = menstrualCycle(daysAgo = 2)
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(cycle)
            coEvery { getAll("user-1") } returns Result.success(emptyList())
        }
        val futureDate = DateConverter.addDays(DateConverter.today(), 5)
        val futureLog = PeriodLog(
            id = "log-future",
            userId = "user-1",
            logDate = futureDate,
            periodPresent = true,
            createdByUserId = "user-1",
            sourceUserId = "user-1",
        )
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { getForDateRange("user-1", futureDate, futureDate) } returns Result.success(listOf(futureLog))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository, periodLogRepository)
        advanceUntilIdle()
        assertEquals(CyclePhase.MENSTRUAL, viewModel.uiState.value.phase)

        viewModel.selectDate(futureDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(futureDate, state.selectedDate)
        assertEquals(CyclePhase.FOLLICULAR, state.phase)
        assertEquals(8, state.dayInCycle)
        assertTrue(state.hasLoggedForSelectedDate)
        assertEquals(futureLog, state.selectedLog)
        // Selecting the same date twice is a no-op -- doesn't re-trigger a fetch.
        coVerify(exactly = 1) { periodLogRepository.getForDateRange("user-1", futureDate, futureDate) }
        viewModel.selectDate(futureDate)
        advanceUntilIdle()
        coVerify(exactly = 1) { periodLogRepository.getForDateRange("user-1", futureDate, futureDate) }
    }

    // Real gap found (2026-07-16) cross-checking the arbitrary-date-view
    // feature against the earlier privacy sweep: `LOG_PERIOD` alone was only
    // ever a safe stand-in for "can see *today's* log presence" (so a partner
    // who logs on someone's behalf doesn't create a duplicate entry) back
    // when this read was hardcoded to today. Once Calendar's day-tap could
    // drive Home to an arbitrary date, that same clause would let a partner
    // with ONLY `LOG_PERIOD` (no view permission at all) discover whether
    // period was logged on ANY day, not just today -- since Calendar's own
    // day-tap grid isn't gated by view permissions either. Verifies the log
    // presence read is un-gated for today but blocked for another date.
    @Test
    fun `a partner with only LOG_PERIOD sees log presence for today but not for another date`() = runTest {
        val partnerSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = false,
            canViewCycleHistory = false,
            canLogPeriod = true,
            canViewDailyLogs = false,
            canViewSymptoms = false,
            canViewMoods = false,
            canViewTemperature = false,
            canViewWeight = false,
        )
        val sessionFlow = MutableStateFlow(partnerSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val today = DateConverter.today()
        val otherDate = DateConverter.addDays(today, 3)
        val todayLog = PeriodLog(
            id = "log-today",
            userId = "primary-1",
            logDate = today,
            periodPresent = true,
            createdByUserId = "primary-1",
            sourceUserId = "primary-1",
        )
        val otherLog = PeriodLog(
            id = "log-other",
            userId = "primary-1",
            logDate = otherDate,
            periodPresent = true,
            createdByUserId = "primary-1",
            sourceUserId = "primary-1",
        )
        val cycleDataRepository = mockk<CycleDataRepository>()
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange("primary-1", today, today) } returns Result.success(listOf(todayLog))
            coEvery { getForDateRange("primary-1", otherDate, otherDate) } returns Result.success(listOf(otherLog))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository, periodLogRepository)
        advanceUntilIdle()

        // No cycle-view permission at all, so refresh() never reads cycle data --
        // only the log-presence path (LOG_PERIOD's narrow carve-out) is exercised.
        coVerify(exactly = 0) { cycleDataRepository.getLatest(any()) }
        // Presence leaks through (the LOG_PERIOD carve-out), but the actual
        // logged details stay redacted since this partner has no detail-view
        // permission at all -- `sanitizeForHome` nulls it out regardless.
        assertTrue(viewModel.uiState.value.hasLoggedForSelectedDate)
        assertNull(viewModel.uiState.value.selectedLog)

        viewModel.selectDate(otherDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(otherDate, state.selectedDate)
        // Real fix: LOG_PERIOD alone must not reveal log presence for a date
        // other than today -- only a genuine view permission (or self) should.
        assertFalse(state.hasLoggedForSelectedDate)
        assertNull(state.selectedLog)
        coVerify(exactly = 0) { periodLogRepository.getForDateRange("primary-1", otherDate, otherDate) }
    }

    // Backs iOS `HomeDayDetailGlassView.cycleStatusTile`'s "Regular"/"Irregular"
    // badge -- a real gap this parity pass found: Android's Current Cycle card
    // rendered no equivalent tile at all. Uses the actual shared
    // `CycleMath.computeStatistics` through the real `getAll()` read, exactly
    // as `refreshCycleStatistics` calls it, rather than asserting on a stubbed
    // pre-computed value.
    @Test
    fun `cycle statistics are computed from the full completed-cycle history via the real CycleMath`() = runTest {
        val sessionFlow = MutableStateFlow(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } returns sessionFlow.value
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val latest = menstrualCycle()
        val completedCycles = listOf(
            latest.copy(id = "c1", cycleLength = 28, isComplete = true),
            latest.copy(id = "c2", cycleLength = 30, isComplete = true),
            latest.copy(id = "c3", cycleLength = 21, isComplete = true),
            latest.copy(id = "c4", cycleLength = 28, isComplete = false), // incomplete: excluded
        )
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(latest)
            coEvery { getAll("user-1") } returns Result.success(completedCycles)
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Only the 3 isComplete=true cycles count; the incomplete one is excluded.
        assertEquals(3, state.cyclesAnalyzed)
        assertEquals(21, state.shortestCycle)
        assertEquals(30, state.longestCycle)
        // variance (30-21=9) > 7, so this real history should read as Irregular
        // when the composable applies iOS's exact judging rule.
        assertTrue(state.longestCycle - state.shortestCycle > 7)
    }

    @Test
    fun `partner snapshot fields are only populated when not viewing own data`() = runTest {
        val partnerSession = sessionContext(userId = "user-1", targetUserId = "partner-1")
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(partnerSession)
            every { current } returns partnerSession
        }
        val snapshot = PartnerHealthSnapshot(
            schemaVersion = 1,
            revision = 42L,
            subjectUserId = "partner-1",
            canViewPeriodDates = true,
            canViewCycleHistory = true,
            canViewPredictions = true,
            periodLogs = emptyList(),
            cycles = emptyList(),
            refreshedAt = "2026-07-11T00:00:00Z",
        )
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(snapshot)
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("partner-1") } returns Result.success(null)
            coEvery { getAll("partner-1") } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42L, state.partnerSnapshotRevision)
        assertEquals("2026-07-11T00:00:00Z", state.partnerSnapshotRefreshedAt)
    }

    @Test
    fun `partner snapshot fields stay hidden without home cycle permission`() = runTest {
        val partnerSession = sessionContext(
            userId = "user-1",
            targetUserId = "partner-1",
            canViewPredictions = false,
            canViewCycleHistory = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(partnerSession)
            every { current } returns partnerSession
        }
        val snapshot = PartnerHealthSnapshot(
            schemaVersion = 1,
            revision = 42L,
            subjectUserId = "partner-1",
            canViewPeriodDates = true,
            canViewCycleHistory = true,
            canViewPredictions = true,
            periodLogs = emptyList(),
            cycles = emptyList(),
            refreshedAt = "2026-07-11T00:00:00Z",
        )
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(snapshot)
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.partnerSnapshotRevision)
        assertNull(state.partnerSnapshotRefreshedAt)
        coVerify(exactly = 0) { cycleDataRepository.getLatest(any()) }
    }

    @Test
    fun `partner snapshot fields stay hidden when the snapshot belongs to another target`() = runTest {
        val partnerSession = sessionContext(userId = "user-1", targetUserId = "partner-1")
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(partnerSession)
            every { current } returns partnerSession
        }
        val snapshot = PartnerHealthSnapshot(
            schemaVersion = 1,
            revision = 42L,
            subjectUserId = "partner-2",
            canViewPeriodDates = true,
            canViewCycleHistory = true,
            canViewPredictions = true,
            periodLogs = emptyList(),
            cycles = emptyList(),
            refreshedAt = "2026-07-11T00:00:00Z",
        )
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(snapshot)
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("partner-1") } returns Result.success(null)
            coEvery { getAll("partner-1") } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.partnerSnapshotRevision)
        assertNull(state.partnerSnapshotRefreshedAt)
    }
}
