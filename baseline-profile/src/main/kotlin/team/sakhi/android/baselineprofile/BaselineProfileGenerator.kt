package team.sakhi.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the ART baseline profile that ships inside the release bundle.
 *
 * WHY THE PREVIOUS VERSION NEVER PRODUCED ANYTHING
 *
 * A profile run always installs the app FRESH, so it opens on the onboarding carousel, never
 * on the phone screen. The old test waited on a "Phone number" label immediately after
 * startup, a screen only reachable after onboarding, so it timed out on every clean run. That
 * plus the wrong package name (fixed in `TargetApp.kt`) is why `app/src/release/` stayed empty
 * and every release so far shipped a profile containing zero `team/sakhi` entries.
 *
 * WHAT THIS COVERS NOW
 *
 * The full real journey: cold start, the onboarding carousel, the role step, phone entry, OTP
 * sign-in, and then Home — including scrolling it, which is the screen users actually spend
 * their time on and the one that felt slowest. Those composables are the whole point of
 * having a profile at all, and they are only reachable behind a login.
 *
 * ── SIGN-IN DEPENDENCY, READ BEFORE CHANGING ANYTHING BELOW ──────────────────────────────
 *
 * This test signs in as [TEST_PHONE] with the fixed code [TEST_OTP]. That works ONLY while the
 * matching Test OTP entry exists in Supabase Auth (Authentication -> Sign In / Providers ->
 * Phone -> Test OTP). With that entry present, no SMS is sent and the run is free.
 *
 * If that entry is ever removed again, this test does NOT fail safely: it falls back to the
 * live Twilio Verify path and every single profile generation sends a real billable SMS on the
 * international route (roughly Rs 7 each) to a real number, and then still fails, because
 * nothing here can read the delivered code. The entry was in fact removed once before, on
 * 2026-08-20, which is what broke the Apple reviewer sign-in and caused the Guideline 2.1(a)
 * rejection on 2026-08-28. It was verified present again on 2026-08-30 by running this
 * journey against a clean install.
 *
 * So: if this test starts failing at the OTP step, check that Supabase setting FIRST, and stop
 * re-running it in the meantime.
 *
 * Every step past the required cold start is best-effort and never throws. A profile covering
 * less of the journey is still a correct, useful profile, whereas a thrown exception produces
 * no profile at all, which is the failure mode this file was stuck in for months.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupThroughHome() = baselineProfileRule.collect(
        packageName = targetPackageName(),
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        val device = this.device
        // The only hard requirement: the app's own window came up.
        device.wait(Until.hasObject(By.pkg(targetPackageName()).depth(0)), UI_WAIT_TIMEOUT_MS)
        device.waitForIdle()

        device.advanceToPhoneScreen()
        if (device.signIn()) {
            device.exerciseHome()
        }
    }

    /**
     * Taps through the onboarding carousel and the "Who Are You Here For?" role step until the
     * phone screen shows. Bounded, so a flow change turns into a shorter profile rather than a
     * hang.
     */
    private fun UiDevice.advanceToPhoneScreen() {
        repeat(MAX_ONBOARDING_STEPS) {
            if (has(COUNTRY_CODE_LABEL)) return
            // Only present on the role step; ignored everywhere else.
            tapIfPresent(ROLE_SELF_LABEL)
            if (!tapIfPresent(CONTINUE_LABEL)) return
            waitForIdle()
            wait(Until.hasObject(By.pkg(currentPackageName).depth(0)), STEP_TIMEOUT_MS)
        }
    }

    /** Returns true if Home was reached. */
    private fun UiDevice.signIn(): Boolean {
        if (!has(COUNTRY_CODE_LABEL)) return false

        tapIfPresent(PHONE_FIELD_LABEL)
        typeText(TEST_PHONE)
        tapIfPresent(CONTINUE_LABEL)

        // OTP screen. The field takes focus on arrival and the form submits itself once six
        // digits are in, so there is no button to press here.
        wait(Until.hasObject(By.textContains(OTP_SCREEN_MARKER)), UI_WAIT_TIMEOUT_MS)
        typeText(TEST_OTP)

        return wait(Until.hasObject(By.desc(HOME_MARKER)), SIGN_IN_TIMEOUT_MS) != null
    }

    /**
     * Scrolls Home. This is the surface the profile exists for: the hero, the phase card and
     * every card below it, plus the shared design-system components they are built from.
     */
    private fun UiDevice.exerciseHome() {
        repeat(HOME_SCROLL_PASSES) {
            scrollFirstScrollable(Direction.DOWN)
        }
        scrollFirstScrollable(Direction.UP)
        // The date label opens the calendar overlay when Home is showing today.
        if (tapIfPresent(By.clickable(true))) {
            waitForIdle()
            scrollFirstScrollable(Direction.DOWN)
            pressBack()
            waitForIdle()
        }
    }

    private fun UiDevice.has(label: String): Boolean =
        findObject(By.desc(label)) != null || findObject(By.text(label)) != null

    private fun UiDevice.tapIfPresent(label: String): Boolean {
        val target = findObject(By.text(label)) ?: findObject(By.desc(label)) ?: return false
        return runCatching { target.click() }.isSuccess
    }

    private fun UiDevice.tapIfPresent(selector: androidx.test.uiautomator.BySelector): Boolean {
        val target = findObject(selector) ?: return false
        return runCatching { target.click() }.isSuccess
    }

    /**
     * Types through the shell rather than `UiObject2.setText`. These fields are Compose
     * `BasicTextField`s that filter and reformat input as it arrives (the phone field groups
     * digits, the OTP field splits them across boxes); `setText` replaces the buffer wholesale
     * and those filters do not see a normal keystroke sequence.
     */
    private fun UiDevice.typeText(text: String) {
        runCatching { executeShellCommand("input text $text") }
        waitForIdle()
    }

    private fun UiDevice.scrollFirstScrollable(direction: Direction) {
        val scrollable = findObject(By.scrollable(true)) ?: return
        runCatching {
            scrollable.setGestureMargin(displayWidth / GESTURE_MARGIN_DIVISOR)
            scrollable.scroll(direction, SCROLL_FRACTION)
        }
        waitForIdle()
    }

    private val UiDevice.currentPackageName: String
        get() = targetPackageName()

    private companion object {
        // Test account. Free only while the Supabase Test OTP entry exists, see the class note.
        const val TEST_PHONE = "9990421555"
        const val TEST_OTP = "123456"

        const val UI_WAIT_TIMEOUT_MS = 15_000L
        const val SIGN_IN_TIMEOUT_MS = 30_000L
        const val STEP_TIMEOUT_MS = 5_000L
        const val MAX_ONBOARDING_STEPS = 12
        const val HOME_SCROLL_PASSES = 3
        const val SCROLL_FRACTION = 0.8f
        const val GESTURE_MARGIN_DIVISOR = 5

        const val CONTINUE_LABEL = "Continue"
        const val ROLE_SELF_LABEL = "Myself"
        const val COUNTRY_CODE_LABEL = "Country code"
        const val PHONE_FIELD_LABEL = "Phone number"
        const val OTP_SCREEN_MARKER = "Secret Code"
        const val HOME_MARKER = "Open profile"
    }
}
