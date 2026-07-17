package team.sakhi.android.feature.home.screenshot

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
import team.sakhi.android.feature.home.HomeScreen
import team.sakhi.android.feature.home.HomeViewModel
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.feature.recommendations.RecommendationsViewModel
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.RecommendationInsightService
import team.sakhi.repositories.RecommendationRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager
import team.sakhi.sync.SyncRuntimeState
import team.sakhi.sync.SyncStore

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
@Config(sdk = [34])
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
        }
        val appContext: Context = mockk(relaxed = true)
        return HomeViewModel(mockSessionManager(), syncStore, mockk(), periodLogRepository, appContext)
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
        }
        return LoggingViewModel(
            appContext = mockk(relaxed = true),
            sessionManager = mockSessionManager(),
            periodLogRepository = periodLogRepository,
            hapticManager = mockk(relaxed = true),
            widgetSnapshotManager = mockk<AndroidWidgetSnapshotManager>(relaxed = true),
        )
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
