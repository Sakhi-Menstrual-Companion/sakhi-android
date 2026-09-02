package team.sakhi.android.feature.calendar.screenshot

import team.sakhi.android.common.CycleDetectionCoordinator
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import team.sakhi.android.designsystem.SakhiTheme
import team.sakhi.android.feature.calendar.CalendarScreen
import team.sakhi.android.feature.calendar.CalendarViewModel
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.date.DateConverter
import team.sakhi.models.CycleData
import team.sakhi.models.UserCareRole
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.models.PeriodLog

/**
 * Second slice of the screenshot-test durability lane (first slice was
 * `feature:auth`'s `AuthScreenshotTest`; see that file's doc comment for the
 * overall rationale). Calendar chosen as a meaningfully different layout from
 * Auth's simple forms -- a real populated month grid with real phase/period
 * marks. `CalendarScreen`'s own internal `koinInject<AndroidHapticManager>()`
 * call (unlike Auth's screens, which take everything as a plain default-arg
 * parameter) is the one piece of real Koin wiring this test needs, so a
 * minimal Koin instance with just that one mocked dependency is started/
 * stopped per test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel 5 rather than Robolectric's 320x470 default: the grid and the bottom bar do not
// both fit at the default size, so a capture cannot show what it exists to pin.
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CalendarScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<AndroidHapticManager> { mockk(relaxed = true) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun permissions() = SessionPermissions(
        canViewPeriodDates = true,
        canLogPeriod = true,
        canViewSymptoms = true,
        canViewMoods = true,
        canViewMedications = true,
        canViewPredictions = true,
        canViewCycleHistory = true,
        canViewDailyLogs = true,
        canViewOvulationTests = true,
        canViewTemperature = true,
        canViewWeight = true,
        canViewNotes = true,
        canViewDischarge = true,
        canViewSexualActivity = true,
    )

    private fun sessionContext(): SessionContext = SessionContext(
        userId = "user-1",
        userName = "Test User",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = "user-1",
        permissions = permissions(),
        activePartnership = null,
    )

    // Same daysAgo=2/periodLength=5/cycleLength=28 fixture as CalendarViewModelTest --
    // keeps "today" unambiguously inside the real MENSTRUAL window.
    private fun menstrualCycle(): CycleData = CycleData(
        id = "cycle-1",
        userId = "user-1",
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -2),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -2),
        periodLength = 5,
        cycleLength = 28,
    )

    /** A cycle only exists because logs produced it; keep the two doubles consistent. */
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

    private fun newViewModel(): CalendarViewModel {
        val session = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { this@mockk.session } returns MutableStateFlow(session)
            every { current } returns session
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll("user-1") } returns Result.success(listOf(menstrualCycle()))
        }
        val appContext: Context = mockk {
            every { getString(any()) } returns "Failed to load calendar data"
        }
        return CalendarViewModel(
            sessionManager,
            cycleDataRepository,
            // Day marks come from the shared engine, which reads **logged period days**.
            // This previously returned an empty list, so the captured calendar had no
            // period / predicted / fertile / ovulation markers at all -- the lane looked
            // like it covered the grid while being blind to every marker colour in it.
            // Expanding the fixture cycle into its own run of logs is what makes the
            // markers render, and is the same consistency rule HomeViewModelTest documents.
            mockk<PeriodLogRepository> {
                coEvery { getAll(any()) } returns Result.success(periodLogsFor(menstrualCycle()))
            },
            mockk(relaxed = true),
            appContext,
        )
    }

    // `CalendarScreen` now also drives its own dedicated `LoggingViewModel` (the
    // bottom action bar's quick-log state, see the second parity sweep's Calendar
    // bottom-bar fix) -- constructed directly and passed as a parameter, the same
    // way `newViewModel()` above bypasses Koin for `CalendarViewModel`, rather than
    // registering it in this test's minimal Koin module.
    private fun newLoggingViewModel(): LoggingViewModel {
        val session = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { this@mockk.session } returns MutableStateFlow(session)
            every { current } returns session
        }
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            // `getAll` became reachable from the logging sheet's own load path; without
            // this the mock throws rather than returning an empty result, which failed the
            // whole render.
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val appContext: Context = mockk {
            every { getString(any()) } returns ""
        }
        return LoggingViewModel(
            appContext = appContext,
            sessionManager = sessionManager,
            periodLogRepository = periodLogRepository,
            hapticManager = mockk(relaxed = true),
            widgetSnapshotManager = mockk(relaxed = true),
            // Real coordinator over the same mocked repositories -- a thin orchestrator
            // around the shared detector, so a mock would only assert against itself.
            cycleDetectionCoordinator = CycleDetectionCoordinator(
                periodLogRepository = periodLogRepository,
                cycleDataRepository = mockk(relaxed = true),
            ),
            // Added when the logging sheet started showing the phase under the date --
            // it cannot be derived from logs alone. Returns the same cycle the calendar
            // fixture uses rather than a relaxed default, so the sheet's phase line and
            // the grid it sits under cannot disagree.
            cycleDataRepository = mockk {
                coEvery { getAll(any()) } returns Result.success(listOf(menstrualCycle()))
                coEvery { getLatest(any()) } returns Result.success(menstrualCycle())
            },
            syncStore = mockk(relaxed = true),
        )
    }

    @Test
    fun calendarScreen_light() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) {
                CalendarScreen(viewModel = newViewModel(), logViewModel = newLoggingViewModel())
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/CalendarScreen_light.png")
    }

    @Test
    fun calendarScreen_dark() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = true) {
                CalendarScreen(viewModel = newViewModel(), logViewModel = newLoggingViewModel())
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/CalendarScreen_dark.png")
    }
}
