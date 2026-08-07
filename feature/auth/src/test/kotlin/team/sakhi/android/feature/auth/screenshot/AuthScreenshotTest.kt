package team.sakhi.android.feature.auth.screenshot

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import team.sakhi.android.designsystem.SakhiTheme
import team.sakhi.android.feature.auth.AuthViewModel
import team.sakhi.android.feature.auth.OtpScreen
import team.sakhi.android.feature.auth.PhoneScreen
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AccountClassifier
import team.sakhi.auth.AuthRepository

/**
 * First slice of the screenshot-test durability lane (plan's "Compose UI / screenshot
 * tests" section, OPTIONAL, on top of the manual visual + flow parity gate -- a
 * durability improvement, not a substitute for that real release gate). Renders the
 * two real Auth screens through a real `AuthViewModel` instance (mocked KMM/platform
 * dependencies, same construction pattern as `AuthViewModelTest`) with zero Koin/DI
 * wiring, since both screens take their ViewModel as a plain default-arg parameter.
 * Kept intentionally small for this first pass: two screens, both themes, no
 * interaction simulated -- just proving the whole toolchain (Robolectric + Compose +
 * Roborazzi + light/dark theming) actually records and verifies real screenshots
 * before spreading this to more screens/modules in a later pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel 5 rather than Robolectric's 320x470 default. At the default width the OTP row is
// squeezed and the baseline showed only **five** boxes, while the code (and iOS) use six --
// a capture that misrepresents the screen it exists to pin is worse than no capture.
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AuthScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun newViewModel(): AuthViewModel = AuthViewModel(
        authRepository = mockk<AuthRepository>(relaxed = true),
        accountClassifier = mockk<AccountClassifier>(relaxed = true),
        appStateInputBridge = mockk<AppStateInputBridge>(relaxed = true),
        hapticManager = mockk<AndroidHapticManager>(relaxed = true),
        appContext = ApplicationProvider.getApplicationContext<Context>(),
    )

    @Test
    fun phoneScreen_light() {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) {
                PhoneScreen(onOtpSent = {}, viewModel = newViewModel())
            }
        }

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/PhoneScreen_light.png")
    }

    @Test
    fun phoneScreen_dark() {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = true) {
                PhoneScreen(onOtpSent = {}, viewModel = newViewModel())
            }
        }

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/PhoneScreen_dark.png")
    }

    @Test
    fun otpScreen_light() {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) {
                OtpScreen(phone = "+919990421555", viewModel = newViewModel())
            }
        }

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/OtpScreen_light.png")
    }

    @Test
    fun otpScreen_dark() {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = true) {
                OtpScreen(phone = "+919990421555", viewModel = newViewModel())
            }
        }

        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/OtpScreen_dark.png")
    }
}
