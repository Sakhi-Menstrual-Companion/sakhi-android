package team.sakhi.android.feature.onboarding.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.mockk
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
import team.sakhi.android.feature.onboarding.OnboardingCareInviteUiState
import team.sakhi.android.feature.onboarding.OnboardingContentStepScreen
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.onboarding.OnboardingFlowStep

/**
 * Fourth slice of the screenshot-test durability lane (after `feature:auth`,
 * `feature:calendar` and `feature:home`).
 *
 * Onboarding is the one area with no emulator path at all. Reaching any of these steps
 * needs app data cleared, and re-login would kick the QA account off Karan's own device
 * under the single-device rule, so the earlier onboarding sweeps had to be done by
 * reading code. That is exactly how the 2026-08-02 `PartnerInvitePrompt` defect survived
 * a clean coverage sweep: every step *had* a render branch, so nothing looked wrong,
 * while three subtitle strings were missing from the build entirely.
 *
 * These two steps are covered first because they are the two consumers of the shared
 * `FeatureBulletRow`, the component that defect was traced to. A screenshot pins the
 * rendered text, so a silently dropped subtitle fails here instead of shipping.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel 5 rather than Robolectric's 320x470 default (which the earlier slices use): at
// the default size the footer clips the second bullet, so a dropped subtitle further
// down would not appear in the image at all — defeating the point of these captures.
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class OnboardingScreenshotTest {

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

    private fun capture(step: OnboardingFlowStep, darkTheme: Boolean, name: String) {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = darkTheme) {
                OnboardingContentStepScreen(
                    step = step,
                    canGoBack = false,
                    careInviteUiState = OnboardingCareInviteUiState(),
                    fieldError = null,
                    onContinue = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun universalIntro_light() = runTest {
        capture(OnboardingFlowStep.UniversalIntro, darkTheme = false, name = "UniversalIntro_light")
    }

    @Test
    fun universalIntro_dark() = runTest {
        capture(OnboardingFlowStep.UniversalIntro, darkTheme = true, name = "UniversalIntro_dark")
    }

    // The intro carousel was showing the UniversalIntro bullet copy instead of iOS's
    // onboarding.carousel.slide* set. Both screens are captured, so a future edit that
    // re-collapses them onto one copy set shows up as two identical-reading screenshots.
    @Test
    fun myselfIntroCarousel_light() = runTest {
        capture(
            OnboardingFlowStep.MyselfIntroCarousel,
            darkTheme = false,
            name = "MyselfIntroCarousel_light",
        )
    }

    @Test
    fun joinFamilyIntroCarousel_light() = runTest {
        capture(
            OnboardingFlowStep.JoinFamilyIntroCarousel,
            darkTheme = false,
            name = "JoinFamilyIntroCarousel_light",
        )
    }

    @Test
    fun partnerInvitePrompt_light() = runTest {
        capture(
            OnboardingFlowStep.PartnerInvitePrompt,
            darkTheme = false,
            name = "PartnerInvitePrompt_light",
        )
    }

    @Test
    fun partnerInvitePrompt_dark() = runTest {
        capture(
            OnboardingFlowStep.PartnerInvitePrompt,
            darkTheme = true,
            name = "PartnerInvitePrompt_dark",
        )
    }
}
