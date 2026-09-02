package team.sakhi.android.feature.home.screenshot

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
import team.sakhi.android.feature.home.HomeScreen
import team.sakhi.android.feature.home.HomeViewModel
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.feature.recommendations.RecommendationsViewModel
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.android.common.CycleDetectionCoordinator
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.RecommendationInsightService
import team.sakhi.repositories.RecommendationRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager
import team.sakhi.sync.SyncRuntimeState
import team.sakhi.sync.SyncStore
import team.sakhi.date.DateConverter
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionPermissions
import team.sakhi.session.SessionContext
import team.sakhi.models.CycleData

/**
 * Third slice of the screenshot-test durability lane (first was `feature:auth`'s
 * `AuthScreenshotTest`, second was `feature:calendar`'s `CalendarScreenshotTest`).
 * Home chosen as the app's central hub screen -- a real top bar / hero / card-stack
 * / bottom action bar layout, meaningfully different from both Auth's simple forms
 * and Calendar's grid. `HomeScreen` composes three real ViewModels
 * (`HomeViewModel`/`RecommendationsViewModel`/`LoggingViewModel`, all plain
 * default-arg params, same pattern as Auth) plus one internal
 * `koinInject<AndroidHapticManager>()` call, so a minimal Koin instance with just
 * that one mocked dependency is started/stopped per test (same approach as
 * `CalendarScreenshotTest`).
 *
 * Renders the real "no session" state deliberately, not a populated-data state:
 * every ViewModel here already has an existing, passing "no session" unit test
 * (`HomeViewModelTest`/`RecommendationsViewModelTest`/`LoggingViewModelTest`), so
 * this reuses a known-safe, already-covered state rather than hand-rolling a new
 * "populated own-data" fixture across three ViewModels' repositories in this first
 * pass -- deepening that to a populated-data render is real follow-up work, not
 * part of this contained slice. `HomeScreen`'s own branching sends a null session
 * down the non-partner, no-cycle-data path (`EmptyStateCard` + `LearningPhaseCards`),
 * which is a real, distinct, always-reachable Home layout (first-run / signed-out
 * users see exactly this).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel 5 rather than Robolectric's 320x470 default: at the default size Home's cards are
// clipped almost immediately, so a capture cannot show the card-text ladder it exists to
// pin. Same reasoning as the onboarding lane.
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeScreenshotTest {

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

    private fun mockSessionManager() = mockk<SessionManager> {
        every { session } returns MutableStateFlow(null)
        every { current } returns null
    }

    private fun newHomeViewModel(): HomeViewModel {
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val appContext: Context = mockk(relaxed = true)
        val cycleDataRepository = mockk<CycleDataRepository>()
        return HomeViewModel(
            mockSessionManager(),
            syncStore,
            cycleDataRepository,
            periodLogRepository,
            CycleDetectionCoordinator(periodLogRepository, cycleDataRepository),
            mockk<RecommendationRepository>(relaxed = true),
            appContext,
        )
    }

    private fun newRecommendationsViewModel(): RecommendationsViewModel {
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get(any()) } returns Result.success(null)
        }
        val recommendationRepository = mockk<RecommendationRepository>(relaxed = true)
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDate(any(), any()) } returns Result.success(null)
        }
        val recommendationInsightService = mockk<RecommendationInsightService>(relaxed = true)
        return RecommendationsViewModel(
            sessionManager = mockSessionManager(),
            cycleDataRepository = mockk(),
            userProfileRepository = userProfileRepository,
            recommendationRepository = recommendationRepository,
            periodLogRepository = periodLogRepository,
            recommendationInsightService = recommendationInsightService,
            appContext = mockk(relaxed = true),
        )
    }

    private fun newLoggingViewModel(): LoggingViewModel {
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        return LoggingViewModel(
            appContext = mockk(relaxed = true),
            sessionManager = mockSessionManager(),
            periodLogRepository = periodLogRepository,
            hapticManager = mockk(relaxed = true),
            widgetSnapshotManager = mockk<AndroidWidgetSnapshotManager>(relaxed = true),
            cycleDetectionCoordinator = CycleDetectionCoordinator(periodLogRepository, mockk()),
            // Added when the logging sheet started showing the cycle phase under the
            // date, which cannot be derived from logs alone.
            cycleDataRepository = mockk(relaxed = true),
            syncStore = mockk(relaxed = true),
        )
    }

    // ── Follicular (non-period) fixtures ─────────────────────────────────────
    //
    // Every on-device check of Home's card-text ladder so far has been a period day,
    // where all four levels resolve to white. The ladder's other live branch tints text
    // with the **phase primary** (1.0 / 0.72 / 0.56 / 0.45) and had never been seen
    // rendered — reaching it on the emulator needs a day-selection tap, which iteration 48
    // could not land reliably. Driving the state directly is both cheaper and repeatable.

    private fun ownDataSession(): SessionContext = SessionContext(
        userId = "user-1",
        userName = "Asha",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = "user-1",
        permissions = SessionPermissions.primaryUser,
        activePartnership = null,
    )

    /** daysAgo = 7 with periodLength 5 lands inside the real FOLLICULAR window. */
    private fun follicularCycle(): CycleData = CycleData(
        id = "cycle-follicular",
        userId = "user-1",
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -7),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -7),
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

    private fun follicularHomeViewModel(): HomeViewModel {
        val session = ownDataSession()
        val sessionManager = mockk<SessionManager> {
            every { this@mockk.session } returns MutableStateFlow(session)
            every { current } returns session
        }
        val syncStore = mockk<SyncStore> {
            every { syncState } returns MutableStateFlow(SyncRuntimeState.Idle)
            every { partnerHealthSnapshot } returns MutableStateFlow(null)
        }
        val cycle = follicularCycle()
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll(any()) } returns Result.success(listOf(cycle))
            coEvery { getLatest(any()) } returns Result.success(cycle)
            coEvery { upsert(any()) } answers { Result.success(firstArg()) }
        }
        val logs = periodLogsFor(cycle)
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getAll(any()) } returns Result.success(logs)
            coEvery { getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
        }
        val appContext: Context = mockk(relaxed = true)
        return HomeViewModel(
            sessionManager,
            syncStore,
            cycleDataRepository,
            periodLogRepository,
            CycleDetectionCoordinator(periodLogRepository, cycleDataRepository),
            mockk<RecommendationRepository>(relaxed = true),
            appContext,
        )
    }

    @Test
    fun homeScreen_follicularPhase_light() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) {
                HomeScreen(
                    viewModel = follicularHomeViewModel(),
                    recommendationsViewModel = newRecommendationsViewModel(),
                    quickLogViewModel = newLoggingViewModel(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/HomeScreen_follicular_light.png")
    }

    /**
     * The fourth cell of the matrix. Period-day light and dark were checked on device, and
     * non-period light by the test above; this is the combination nothing had covered.
     * It matters because the non-period branch tints from `phaseText`, which resolves
     * through `SakhiColors.resolved(isDark)` — so dark is a genuinely different colour
     * here, not the same one on a darker background.
     */
    @Test
    fun homeScreen_follicularPhase_dark() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = true) {
                HomeScreen(
                    viewModel = follicularHomeViewModel(),
                    recommendationsViewModel = newRecommendationsViewModel(),
                    quickLogViewModel = newLoggingViewModel(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/HomeScreen_follicular_dark.png")
    }

    @Test
    fun homeScreen_noSession_light() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) {
                HomeScreen(
                    viewModel = newHomeViewModel(),
                    recommendationsViewModel = newRecommendationsViewModel(),
                    quickLogViewModel = newLoggingViewModel(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/HomeScreen_light.png")
    }

    @Test
    fun homeScreen_noSession_dark() = runTest {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = true) {
                HomeScreen(
                    viewModel = newHomeViewModel(),
                    recommendationsViewModel = newRecommendationsViewModel(),
                    quickLogViewModel = newLoggingViewModel(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/HomeScreen_dark.png")
    }
}
