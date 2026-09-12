package team.sakhi.android.feature.home

import team.sakhi.repositories.RecommendationRepository
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import team.sakhi.android.testing.MainDispatcherRule
import org.junit.Test
import team.sakhi.date.DateConverter
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.models.CarePartnership
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.LogHistoryEntry
import team.sakhi.models.PeriodLog
import team.sakhi.android.common.CycleDetectionCoordinator
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PartnerHealthSnapshot
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.sync.SyncRuntimeState
import team.sakhi.sync.SyncStore
import kotlinx.coroutines.CompletableDeferred

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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val testDispatcher get() = mainDispatcherRule.testDispatcher



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
    /** The logged period days a cycle implies: `periodStartDate` for `periodLength` days. */
    private fun periodLogsFor(cycle: CycleData): List<PeriodLog> =
        (0 until (cycle.periodLength ?: 5)).map { offset ->
            val date = DateConverter.addDays(cycle.periodStartDate, offset)
            PeriodLog(
                id = "log-${cycle.id}-$offset",
                userId = cycle.userId,
                logDate = date,
                periodPresent = true,
                createdByUserId = cycle.userId,
                sourceUserId = cycle.userId,
            )
        }

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
        // Derived from `cycleDataRepository` on purpose. Home's phase now comes from
        // the shared `CyclePhaseInsight` engine, which reads the *logged period days*
        // rather than inferring a phase from a cycle record alone. A fake that returns
        // a cycle but no logs is therefore an impossible state -- in production a cycle
        // only exists because logs produced it -- and asserting against it would test
        // behaviour the real app can never reach. Expanding each cycle into its own
        // period-length run of logs keeps the two doubles consistent.
        periodLogRepository: PeriodLogRepository = mockk {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { getAll(any()) } coAnswers {
                val userId = firstArg<String>()
                // Guarded: not every test stubs `getAll` on the cycle fake, and an
                // unstubbed mockk call throws rather than returning a failed Result.
                val cycles = runCatching {
                    cycleDataRepository.getAll(userId).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { cycle -> periodLogsFor(cycle) })
            }
        },
        appContext: Context = mockk {
            every { getString(R.string.home_load_cycle_failed) } returns "Failed to load cycle data"
            // Home's hero tip reaches for this whenever she is inside her period window
            // but has not logged today. Leaving it unstubbed made mockk throw from
            // inside `_uiState.update {}`, which aborted the whole state write -- every
            // phase assertion then saw UNKNOWN and every error assertion saw null, from
            // one missing stub rather than nine real failures.
            every { getString(R.string.home_hero_tip_log_reminder) } returns "Please remember to log"
        },
        // Real coordinator over the same mocked repositories rather than a mock of it:
        // it is a thin orchestrator, so a fake would only assert against itself. Home's
        // forecast call is driven by whatever `getAll` these mocks return.
        cycleDetectionCoordinator: CycleDetectionCoordinator = CycleDetectionCoordinator(
            periodLogRepository = periodLogRepository,
            cycleDataRepository = cycleDataRepository,
        ),
    ) = HomeViewModel(
        sessionManager,
        syncStore,
        cycleDataRepository,
        periodLogRepository,
        cycleDetectionCoordinator,
        // Relaxed mock: the real repository needs a Ktor HttpClient that is not on the
        // unit-test classpath, and these tests assert cycle/prediction state, not tips.
        mockk<RecommendationRepository>(relaxed = true),
        appContext,
        // The cycle engine now runs off the main thread. Handing it the test dispatcher
        // keeps that work inside the scheduler `advanceUntilIdle()` drives; on
        // `Dispatchers.Default` it lands after the assertions and every derived field
        // reads as its default.
        computeDispatcher = testDispatcher,
    )

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
            // Home reads the full log history for the shared phase engine, which
            // decides phase from logged period DAYS. Returning an empty list here
            // would make the engine correctly answer UNKNOWN and defeat the phase
            // assertions below, so derive the logs from this test's own cycles --
            // the same consistency the default factory keeps.
            coEvery { getAll(any()) } coAnswers {
                val requested = firstArg<String>()
                val cycles = runCatching {
                    cycleDataRepository.getAll(requested).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { periodLogsFor(it) })
            }
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
    fun `cycle load failure surfaces app copy, never the raw exception message`() = runTest {
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
            coEvery { getAll("user-1") } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.UNKNOWN, state.phase)
        assertFalse(state.hasCycleData)
        assertFalse(state.isLoadingCycle)
        // This used to assert the raw `throwable.message` was shown. That assertion was
        // itself pinning a real defect: Supabase/Ktor messages carry the request URL, the
        // `Authorization: Bearer …` header and the apikey, and they were rendering
        // verbatim as red text on Home (seen on a real device). The user-facing string
        // must be the app's own copy; the raw cause goes to the log only.
        assertEquals("Failed to load cycle data", state.error)
        assertFalse(state.error.orEmpty().contains("network down"))
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
            coEvery { getAll("user-1") } returns Result.failure(RuntimeException())
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
            // Her data reaches a partner's Home through the snapshot now, never the local
            // store, so the partner target needs one to have anything to draw.
            every { partnerHealthSnapshot } returns MutableStateFlow(
                partnerSnapshot("partner-1", cycles = listOf(follicularCycle(userId = "partner-1"))),
            )
        }
        val ownCycle = menstrualCycle(userId = "user-1")
        val partnerCycle = follicularCycle(userId = "partner-1")
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(ownCycle)
            coEvery { getLatest("partner-1") } returns Result.success(partnerCycle)
            // Per target, not a blanket empty stub: Home reads the whole history via
            // getAll now, and returning empty for both users made the shared engine
            // correctly answer UNKNOWN for each — which would have hidden exactly the
            // stale-data leak this test exists to catch.
            coEvery { getAll("user-1") } returns Result.success(listOf(ownCycle))
            coEvery { getAll("partner-1") } returns Result.success(listOf(partnerCycle))
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
            // Her data reaches a partner's Home through the snapshot now, never the local
            // store, so the partner target needs one to have anything to draw.
            every { partnerHealthSnapshot } returns MutableStateFlow(
                partnerSnapshot("partner-1", cycles = listOf(follicularCycle(userId = "partner-1"))),
            )
        }
        // getLatest("user-1") never completes during this test -- simulates a slow
        // in-flight request for the target the user has since navigated away from.
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } coAnswers {
                kotlinx.coroutines.awaitCancellation()
            }
            coEvery { getLatest("partner-1") } returns Result.success(follicularCycle(userId = "partner-1"))
            // The hang has to sit on getAll, because that is the call Home actually
            // makes now. Left on getLatest it no longer simulates anything, and the
            // test would pass without ever exercising a stale in-flight response.
            coEvery { getAll("user-1") } coAnswers {
                kotlinx.coroutines.awaitCancellation()
            }
            coEvery { getAll("partner-1") } returns
                Result.success(listOf(follicularCycle(userId = "partner-1")))
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

    /**
     * Late response for an abandoned target must not overwrite the current target's data.
     *
     * Stronger than the sibling test above, which hangs the abandoned request forever
     * (`awaitCancellation`) and so only proves that a request which *never completes*
     * cannot corrupt state. This one lets the response actually land after the session
     * has moved on.
     *
     * **What actually protects this, established by mutation testing (2026-08-02):** not
     * the `if (sessionManager.current?.targetUserId != targetUserId) return` checks. All
     * five of those can be deleted and both this test and the whole `:feature:home` suite
     * stay green. The real mechanism is `collectLatest` in `init`: a session change
     * cancels the in-flight `refresh`, so the abandoned repository call never resumes.
     * The explicit checks are defence-in-depth that this path cannot reach.
     *
     * So do not read a pass here as "the guard works" — it means the cancellation
     * behaviour works. If `collectLatest` is ever changed to `collect`, or the reads are
     * moved outside the cancelled scope, this test is what should catch it, and the
     * guards become load-bearing for real.
     *
     * (The permission gates are a different story and *are* load-bearing: forcing
     * `canViewHomeCycle` to true fails three partner-isolation tests.)
     */
    @Test
    fun `a late cycle response for an abandoned target does not overwrite the current targets data`() = runTest {
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
            // Her data reaches a partner's Home through the snapshot now, never the local
            // store, so the partner target needs one to have anything to draw.
            every { partnerHealthSnapshot } returns MutableStateFlow(
                partnerSnapshot("partner-1", cycles = listOf(follicularCycle(userId = "partner-1"))),
            )
        }
        // user-1's read is held open, then released *after* the switch to partner-1, so
        // the response is genuinely in-flight-then-late rather than never arriving.
        val abandonedRead = CompletableDeferred<Result<List<CycleData>>>()
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } coAnswers { abandonedRead.await().map { it.firstOrNull() } }
            coEvery { getAll("user-1") } coAnswers { abandonedRead.await() }
            coEvery { getLatest("partner-1") } returns Result.success(follicularCycle(userId = "partner-1"))
            coEvery { getAll("partner-1") } returns
                Result.success(listOf(follicularCycle(userId = "partner-1")))
        }
        val viewModel = newViewModel(sessionManager, syncStore, cycleDataRepository)
        advanceUntilIdle()

        currentSlot[0] = sessionB
        sessionFlow.value = sessionB
        advanceUntilIdle()
        // partner-1 is what the user is looking at before the late reply arrives.
        assertEquals("partner-1", viewModel.uiState.value.currentCycle?.userId)

        // The abandoned target's response finally lands, carrying a different phase so an
        // overwrite would be unmistakable.
        abandonedRead.complete(Result.success(listOf(menstrualCycle(userId = "user-1"))))
        advanceUntilIdle()

        assertEquals("partner-1", viewModel.uiState.value.currentCycle?.userId)
        assertEquals(CyclePhase.FOLLICULAR, viewModel.uiState.value.phase)
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
            coEvery { getAll("user-1") } returns Result.success(listOf(menstrualCycle()))
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
            // Home reads the full log history for the shared phase engine, which
            // decides phase from logged period DAYS. Returning an empty list here
            // would make the engine correctly answer UNKNOWN and defeat the phase
            // assertions below, so derive the logs from this test's own cycles --
            // the same consistency the default factory keeps.
            coEvery { getAll(any()) } coAnswers {
                val requested = firstArg<String>()
                val cycles = runCatching {
                    cycleDataRepository.getAll(requested).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { periodLogsFor(it) })
            }
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
            coEvery { getAll("user-1") } returns Result.success(listOf(cycle))
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
            // Home reads the full log history for the shared phase engine, which
            // decides phase from logged period DAYS. Returning an empty list here
            // would make the engine correctly answer UNKNOWN and defeat the phase
            // assertions below, so derive the logs from this test's own cycles --
            // the same consistency the default factory keeps.
            coEvery { getAll(any()) } coAnswers {
                val requested = firstArg<String>()
                val cycles = runCatching {
                    cycleDataRepository.getAll(requested).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { periodLogsFor(it) })
            }
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
            // Home reads the full log history for the shared phase engine, which
            // decides phase from logged period DAYS. Returning an empty list here
            // would make the engine correctly answer UNKNOWN and defeat the phase
            // assertions below, so derive the logs from this test's own cycles --
            // the same consistency the default factory keeps.
            coEvery { getAll(any()) } coAnswers {
                val requested = firstArg<String>()
                val cycles = runCatching {
                    cycleDataRepository.getAll(requested).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { periodLogsFor(it) })
            }
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

    /**
     * Her data reaches a partner's Home through `SyncStore.partnerHealthSnapshot`, which the
     * server filters by what she has shared. Home stopped reading a partner's data from this
     * phone's own store on 2026-09-13, because a cached copy there was kept for good.
     */
    private fun partnerSnapshot(
        subjectUserId: String,
        cycles: List<team.sakhi.models.CycleData> = emptyList(),
        logs: List<team.sakhi.models.PeriodLog> = emptyList(),
        revision: Long = 1L,
    ) = PartnerHealthSnapshot(
        schemaVersion = 1,
        revision = revision,
        subjectUserId = subjectUserId,
        canViewPeriodDates = true,
        canViewCycleHistory = true,
        canViewPredictions = true,
        periodLogs = logs,
        cycles = cycles,
        refreshedAt = "2026-09-13T00:00:00Z",
    )

}
