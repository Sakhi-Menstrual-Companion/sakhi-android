package team.sakhi.android.feature.calendar.screenshot

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
@Config(sdk = [34])
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
        return CalendarViewModel(sessionManager, cycleDataRepository, mockk(relaxed = true), appContext)
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
