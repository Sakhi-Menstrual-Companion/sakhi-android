package team.sakhi.android.feature.emergency.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import team.sakhi.android.designsystem.SakhiTheme
import team.sakhi.android.feature.emergency.EmergencyOnboarding
import team.sakhi.android.feature.emergency.R
import team.sakhi.android.ui.FeatureBulletRow
import team.sakhi.android.ui.OnboardingIntroScaffold
import team.sakhi.android.ui.OnboardingShell

/**
 * Emergency Assistance's intro is meant to BE the account-onboarding page, not a
 * lookalike, and it has drifted from it once already: it re-declared the shell's Column by
 * hand and lost the top safe-area inset and the page's pink ground on the way, which Karan
 * spotted on a device ("jo app onboarding view mai structure and view use ho rha hai, same
 * emergency assistance ke onboarding mai ho").
 *
 * Reading the two files side by side is what missed it the first time, so this asserts it
 * instead. [reference] is the account-onboarding page shape written out directly --
 * `OnboardingShell` around `OnboardingIntroScaffold` around three `FeatureBulletRow`s,
 * which is exactly what `UniversalIntroScreen` in `feature:onboarding` renders. The real
 * screen is rendered beside it and every pixel of the two must agree. Any padding, colour
 * or inset that goes back into `EmergencyOnboarding` alone fails here.
 *
 * The strings are Emergency's own, so this pins the page SHAPE, not the copy.
 *
 * Two test methods rather than one because a `ComposeTestRule` accepts `setContent` once
 * per test and each JUnit method gets its own rule, so the reference is captured to disk
 * first and read back in the second. `@FixMethodOrder` is what guarantees that order.
 *
 * Roborazzi does the rendering, because `captureToImage()` cannot force a redraw under
 * Robolectric and times out. Its captures are written only under
 * `-Proborazzi.test.record=true` (i.e. `recordRoborazziDebug`), so the comparison is
 * skipped rather than silently passed when the images were never produced.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmergencyOnboardingParityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(target: File, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            SakhiTheme(darkTheme = false) { content() }
        }
        composeTestRule.waitForIdle()
        target.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(target.path)
    }

    private fun Bitmap.pixels(): IntArray =
        IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    /** The shared page shape, exactly as `UniversalIntroScreen` assembles it. */
    @Composable
    private fun reference() {
        OnboardingShell(onClose = {}, paintsPageBackground = true) {
            OnboardingIntroScaffold(
                title = stringResource(R.string.emergency_intro_title),
                subtitle = stringResource(R.string.emergency_intro_subtitle),
                primaryLabel = stringResource(R.string.emergency_continue),
                onPrimaryClick = {},
            ) {
                FeatureBulletRow(
                    icon = Icons.Filled.ErrorOutline,
                    title = stringResource(R.string.emergency_intro_row1_title),
                    subtitle = stringResource(R.string.emergency_intro_row1_body),
                )
                FeatureBulletRow(
                    icon = Icons.Filled.Group,
                    title = stringResource(R.string.emergency_intro_row2_title),
                    subtitle = stringResource(R.string.emergency_intro_row2_body),
                )
                FeatureBulletRow(
                    icon = Icons.Filled.NearMe,
                    title = stringResource(R.string.emergency_intro_row3_title),
                    subtitle = stringResource(R.string.emergency_intro_row3_body),
                )
            }
        }
    }

    @Test
    fun aRendersTheAccountOnboardingPageShape() {
        render(REFERENCE) { reference() }
    }

    @Test
    fun bIntroPageIsPixelIdenticalToTheAccountOnboardingPageShape() {
        render(ACTUAL) {
            EmergencyOnboarding(
                locationGranted = false,
                notificationsGranted = false,
                onRequestLocation = {},
                onRequestNotifications = {},
                onFinished = {},
                onCancel = {},
            )
        }
        assumeTrue(
            "Run `./gradlew :feature:emergency:recordRoborazziDebug` to actually compare " +
                "these; Roborazzi writes nothing without it.",
            REFERENCE.exists() && ACTUAL.exists(),
        )

        val expected = BitmapFactory.decodeFile(REFERENCE.path)
        val actual = BitmapFactory.decodeFile(ACTUAL.path)
        assertEquals("page width", expected.width, actual.width)
        assertEquals("page height", expected.height, actual.height)

        val expectedPixels = expected.pixels()
        val actualPixels = actual.pixels()
        val differing = expectedPixels.indices.count { expectedPixels[it] != actualPixels[it] }
        val first = expectedPixels.indices.firstOrNull { expectedPixels[it] != actualPixels[it] }
        assertEquals(
            "Emergency Assistance's intro no longer renders as an account-onboarding page: " +
                "$differing of ${expectedPixels.size} pixels differ, first at " +
                "(${first?.rem(actual.width)}, ${first?.div(actual.width)}). Compare " +
                "${REFERENCE.absolutePath} against ${ACTUAL.absolutePath}.",
            0,
            differing,
        )
    }

    private companion object {
        val REFERENCE = File("build/screenshots/OnboardingPageShape_reference_light.png")
        val ACTUAL = File("build/screenshots/EmergencyOnboarding_intro_light.png")
    }
}
