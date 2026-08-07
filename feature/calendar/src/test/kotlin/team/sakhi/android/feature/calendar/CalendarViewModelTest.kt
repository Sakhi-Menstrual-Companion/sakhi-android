package team.sakhi.android.feature.calendar

import team.sakhi.models.PeriodLog
import team.sakhi.repositories.PeriodLogRepository
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.UserCareRole
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * State-machine test for `CalendarViewModel`. `SessionManager`/`CycleDataRepository`/
 * `AndroidHapticManager` are concrete, non-open KMM/platform classes (same situation
 * as `HomeViewModelTest`/`PartnerChecklistViewModelTest`), so this uses mockk for
 * those boundaries only. Marks are built through the *real* shared
 * `CalendarMarker.buildMarks`/`CycleMath` -- a real regression in the shared
 * calendar/phase logic would fail this test too, not just a stubbed value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

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
        canViewPeriodDates: Boolean = true,
        canViewCycleHistory: Boolean = true,
        canViewPredictions: Boolean = true,
    ) = SessionPermissions(
        canViewPeriodDates = canViewPeriodDates,
        canLogPeriod = true,
        canViewSymptoms = true,
        canViewMoods = true,
        canViewMedications = true,
        canViewPredictions = canViewPredictions,
        canViewCycleHistory = canViewCycleHistory,
        canViewDailyLogs = true,
        canViewOvulationTests = true,
        canViewTemperature = true,
        canViewWeight = true,
        canViewNotes = true,
        canViewDischarge = true,
        canViewSexualActivity = true,
    )

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = "user-1",
        canViewPeriodDates: Boolean = true,
        canViewCycleHistory: Boolean = true,
        canViewPredictions: Boolean = true,
    ): SessionContext = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = targetUserId,
        permissions = permissions(canViewPeriodDates, canViewCycleHistory, canViewPredictions),
        activePartnership = if (targetUserId != userId) {
            CarePartnership(id = "partnership-1", userId = targetUserId, partnerId = userId, partnerName = "Partner")
        } else {
            null
        },
    )

    // daysAgo=2/periodLength=5/cycleLength=28 keeps "today" unambiguously inside the
    // real MENSTRUAL window regardless of AppConfig's ovulation-window constants.
    /** The logged period days a cycle implies: `periodStartDate` for `periodLength` days. */
    private fun periodLogsFor(cycle: CycleData): List<PeriodLog> =
        (0 until (cycle.periodLength ?: 5)).map { offset ->
            PeriodLog(
                id = "log-${cycle.id}-$offset",
                userId = cycle.userId,
                logDate = DateConverter.addDays(cycle.periodStartDate, offset),
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

    private fun newViewModel(
        sessionManager: SessionManager,
        cycleDataRepository: CycleDataRepository = mockk(),
        // Calendar now reads the logged period days too: day marks come from the
        // shared engine's buildCalendarState, not from CycleData alone.
        periodLogRepository: PeriodLogRepository = mockk {
            // Derived from the cycle fake, not empty: day marks now come from the
            // shared engine, which reads logged period DAYS. A cycle with no logs is
            // an impossible state in production -- cycles are built from logs -- so an
            // empty stub would make the engine correctly emit no marks and defeat the
            // assertions below.
            coEvery { getAll(any()) } coAnswers {
                val requested = firstArg<String>()
                val cycles = runCatching {
                    cycleDataRepository.getAll(requested).getOrDefault(emptyList())
                }.getOrDefault(emptyList())
                Result.success(cycles.flatMap { periodLogsFor(it) })
            }
        },
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockk {
            every { getString(R.string.calendar_load_failed) } returns "Failed to load calendar data"
        },
    ) = CalendarViewModel(sessionManager, cycleDataRepository, periodLogRepository, hapticManager, appContext)

    private fun todayCell(viewModel: CalendarViewModel) =
        viewModel.uiState.value.days.first { it.date == DateConverter.today() }

    @Test
    fun `no session loads an empty month cache without touching the repository`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasAnyCalendarAccess)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.days.isNotEmpty())
        assertTrue(state.days.all { it.mark == null })
    }

    @Test
    fun `own data session builds real marks through the actual CalendarMarker and CycleMath`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.success(listOf(menstrualCycle()))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasAnyCalendarAccess)
        assertFalse(state.isLoading)
        val mark = todayCell(viewModel).mark
        requireNotNull(mark)
        assertTrue(mark.isPeriod)
        assertEquals(CyclePhase.MENSTRUAL, mark.phase)
        assertEquals(3, mark.cycleDay)
    }

    @Test
    fun `partner with zero calendar permissions sees no marks and no access`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPeriodDates = false,
            canViewCycleHistory = false,
            canViewPredictions = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("primary-1") } returns Result.success(listOf(menstrualCycle(userId = "primary-1")))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasAnyCalendarAccess)
        assertNull(todayCell(viewModel).mark)
        coVerify(exactly = 0) { cycleDataRepository.getAll(any()) }
    }

    @Test
    fun `partner who can only view period dates sees the period flag but not the phase or cycle day`() = runTest {
        // Real product rule: "can view period dates" and "can view cycle progress/
        // predictions" are independent axes -- a partner limited to period-only
        // visibility must see isPeriod=true but phase/cycleDay hidden.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPeriodDates = true,
            canViewCycleHistory = false,
            canViewPredictions = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("primary-1") } returns Result.success(listOf(menstrualCycle(userId = "primary-1")))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasAnyCalendarAccess)
        val mark = todayCell(viewModel).mark
        requireNotNull(mark)
        assertTrue(mark.isPeriod)
        assertEquals(CyclePhase.UNKNOWN, mark.phase)
        assertEquals(0, mark.cycleDay)
    }

    @Test
    fun `cycle load failure with a real message surfaces that message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Was asserting the RAW exception message, which pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible error copy. UI shows app copy; cause is logged.
                assertEquals("Failed to load calendar data", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `cycle load failure with no message falls back to the real string resource`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.failure(RuntimeException())
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)

        advanceUntilIdle()

        assertEquals("Failed to load calendar data", viewModel.uiState.value.error)
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
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } coAnswers { kotlinx.coroutines.awaitCancellation() }
            coEvery { getAll("partner-1") } returns Result.success(listOf(menstrualCycle(userId = "partner-1")))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)
        advanceUntilIdle()

        currentSlot[0] = sessionB
        sessionFlow.value = sessionB
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasAnyCalendarAccess)
        assertTrue(todayCell(viewModel).mark?.isPeriod == true)
    }

    @Test
    fun `showNextMonth and showPreviousMonth navigate by one real month and trigger haptics`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()
        val startMonth = viewModel.uiState.value.visibleMonth

        viewModel.showNextMonth()
        advanceUntilIdle()
        val nextMonth = viewModel.uiState.value.visibleMonth
        assertEquals(LocalDate(startMonth.year, startMonth.month, 1), startMonth)
        assertTrue(nextMonth > startMonth)

        viewModel.showPreviousMonth()
        viewModel.showPreviousMonth()
        advanceUntilIdle()
        val backTwoMonths = viewModel.uiState.value.visibleMonth
        assertTrue(backTwoMonths < startMonth)

        verify(exactly = 3) { hapticManager.selection() }
    }

    @Test
    fun `jumpToToday resets both the visible month and the selected date to real today`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()
        viewModel.showNextMonth()
        viewModel.showNextMonth()
        viewModel.selectDate(DateConverter.addDays(DateConverter.today(), 40))
        advanceUntilIdle()

        viewModel.jumpToToday()
        advanceUntilIdle()

        val today = DateConverter.today()
        assertEquals(LocalDate(today.year, today.month, 1), viewModel.uiState.value.visibleMonth)
        assertEquals(today, viewModel.uiState.value.selectedDate)
        verify(atLeast = 1) { hapticManager.selection() }
    }

    @Test
    fun `jumpToMonth selects the 1st for a non-current month but real today for the current month`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        val today = DateConverter.today()
        val farMonth = LocalDate(today.year + 1, 6, 15)

        viewModel.jumpToMonth(farMonth)
        advanceUntilIdle()
        assertEquals(LocalDate(farMonth.year, farMonth.month, 1), viewModel.uiState.value.selectedDate)

        viewModel.jumpToMonth(today)
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate updates only the selected date`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        val monthBefore = viewModel.uiState.value.visibleMonth
        val newDate = DateConverter.addDays(DateConverter.today(), 3)

        viewModel.selectDate(newDate)

        assertEquals(newDate, viewModel.uiState.value.selectedDate)
        assertEquals(monthBefore, viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun `ensureYearLoaded populates the month cache for the whole real year using cached session data`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.success(listOf(menstrualCycle()))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)
        advanceUntilIdle()
        val today = DateConverter.today()
        val distantMonth = LocalDate(
            today.year,
            if (today.monthNumber <= 6) today.monthNumber + 6 else today.monthNumber - 6,
            1,
        )
        // Not preloaded by the initial load (which only preloads prev/current/next
        // month plus the current year) unless it happens to already be in range.
        val wasAlreadyCached = viewModel.uiState.value.monthCache[distantMonth].isNotEmpty()

        viewModel.ensureYearLoaded(today.year)
        advanceUntilIdle()

        val cachedMonth = viewModel.uiState.value.monthCache[distantMonth]
        assertEquals(42, cachedMonth.size)
        if (!wasAlreadyCached) {
            // The real cached cycle (menstrual, started 2 days ago) should not
            // produce a period mark on a month 6 real months away.
            assertTrue(cachedMonth.none { it.mark?.isPeriod == true })
        }
    }

    @Test
    fun `ensureYearLoaded does not reuse the previous targets cached cycles while the new target is still loading`() = runTest {
        val sessionA = sessionContext(userId = "user-1", targetUserId = "user-1")
        val sessionB = sessionContext(userId = "user-1", targetUserId = "partner-2")
        val sessionFlow = MutableStateFlow<SessionContext?>(sessionA)
        val gate = CompletableDeferred<Unit>()
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { sessionFlow.value }
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.success(listOf(menstrualCycle()))
            coEvery { getAll("partner-2") } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository)
        advanceUntilIdle()
        assertTrue(todayCell(viewModel).mark?.isPeriod == true)

        sessionFlow.value = sessionB
        advanceUntilIdle()

        viewModel.ensureYearLoaded(DateConverter.today().year)
        advanceUntilIdle()

        val today = DateConverter.today()
        val currentMonth = LocalDate(today.year, today.month, 1)
        val todayMark = viewModel.uiState.value.monthCache[currentMonth]
            .first { it.date == today }
            .mark
        assertNull(todayMark)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `the 42-cell month grid is real Monday-first, matching iOS's year-view header, for every visible month`() = runTest {
        // Real regression lock for a bug this session's own dedicated iOS-parity
        // pass found and fixed by hand (see `CalendarScreen.kt`'s doc comment on
        // `sundayFirstMonthCells`): the shared `monthCache`/year-view grid this
        // ViewModel builds is Monday-first by design (`isoDayNumber - 1`), matching
        // iOS's real `HomeCalendarSheet.swift` hardcoded `["M","T","W","T","F","S","S"]`
        // header -- a *different* convention from the compact pager's own
        // Sunday-first grid, which is deliberately re-derived only in the UI layer.
        // Nothing in this ViewModel's own test suite ever asserted the actual grid
        // *ordering*, only that cells existed -- a future refactor could silently
        // reintroduce Sunday-first math here (exactly the manually-found bug) and
        // every existing test would still pass. Checked across several distinct
        // months (not just "today"'s) so it isn't accidentally proving something
        // true only for one lucky first-weekday.
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()

        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).forEach { monthNumber ->
            val month = LocalDate(2026, monthNumber, 1)
            viewModel.showMonth(month)
            advanceUntilIdle()

            val days = viewModel.uiState.value.days
            assertEquals(42, days.size)
            assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, days.last().date.dayOfWeek)
            assertTrue(days.any { it.date == month && it.isInVisibleMonth })
            // The grid's own first cell is never itself in the visible month
            // unless the month genuinely starts on a Monday.
            if (month.dayOfWeek != DayOfWeek.MONDAY) {
                assertFalse(days.first().isInVisibleMonth)
            }
        }
    }

    @Test
    fun `ensureYearLoaded before any session has ever loaded still builds a real markless grid`() = runTest {
        // `cachedSession`/`cachedCycles` default to null/empty until the very first
        // `loadMonth` completes -- calling `ensureYearLoaded` before that (e.g. a
        // fast year-picker tap right as the screen opens) takes the `session == null`
        // branch inside the method itself, completely untested until now.
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val viewModel = CalendarViewModel(
            sessionManager,
            cycleDataRepository,
            mockk<PeriodLogRepository> { coEvery { getAll(any()) } returns Result.success(emptyList()) },
            mockk<AndroidHapticManager>(relaxed = true),
            mockk { every { getString(R.string.calendar_load_failed) } returns "Failed to load calendar data" },
        )
        // Deliberately no advanceUntilIdle() -- calling this immediately, before the
        // init block's own collectLatest has ever resolved a session.
        val year = DateConverter.today().year

        viewModel.ensureYearLoaded(year)
        advanceUntilIdle()

        val someMonth = LocalDate(year, 6, 1)
        val cachedMonth = viewModel.uiState.value.monthCache[someMonth]
        assertEquals(42, cachedMonth.size)
        assertTrue(cachedMonth.all { it.mark == null })
        coVerify(exactly = 0) { cycleDataRepository.getAll(any()) }
    }

    @Test
    fun `showMonth jumps the visible month without touching the selected date`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val viewModel = newViewModel(sessionManager)
        advanceUntilIdle()
        val selectedBefore = viewModel.uiState.value.selectedDate

        val target = LocalDate(2027, 3, 17)
        viewModel.showMonth(target)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate(2027, 3, 1), state.visibleMonth)
        assertEquals(selectedBefore, state.selectedDate)
    }
}
