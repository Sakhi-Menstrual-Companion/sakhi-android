package team.sakhi.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCountryPickerScroll() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        val device = this.device
        device.wait(Until.hasObject(By.desc(PHONE_FIELD_LABEL)), UI_WAIT_TIMEOUT_MS)
        device.findObject(By.desc(COUNTRY_CODE_LABEL))?.click()
            ?: error("Country code button not found")
        device.wait(Until.hasObject(By.desc(COUNTRY_PICKER_DISMISS)), UI_WAIT_TIMEOUT_MS)
        device.wait(Until.hasObject(By.desc(COUNTRY_PICKER_SEARCH_LABEL)), UI_WAIT_TIMEOUT_MS)

        repeat(3) { swipeCountryPicker(device, towardListEnd = true) }
        repeat(2) { swipeCountryPicker(device, towardListEnd = false) }
        device.pressBack()
        device.wait(Until.hasObject(By.desc(PHONE_FIELD_LABEL)), UI_WAIT_TIMEOUT_MS)
    }

    private fun swipeCountryPicker(
        device: UiDevice,
        towardListEnd: Boolean,
    ) {
        val centerX = device.displayWidth / 2
        val swipeStartYRatio = if (towardListEnd) 0.58 else 0.32
        val swipeEndYRatio = if (towardListEnd) 0.32 else 0.58
        val swipeSucceeded = device.swipe(
            centerX,
            (device.displayHeight * swipeStartYRatio).toInt(),
            centerX,
            (device.displayHeight * swipeEndYRatio).toInt(),
            SWIPE_STEPS,
        )
        check(swipeSucceeded) { "Country picker swipe failed" }
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "team.sakhi.android"
        const val UI_WAIT_TIMEOUT_MS = 7_500L
        const val SWIPE_STEPS = 24
        const val PHONE_FIELD_LABEL = "Phone number"
        const val COUNTRY_CODE_LABEL = "Country code"
        const val COUNTRY_PICKER_DISMISS = "Dismiss"
        const val COUNTRY_PICKER_SEARCH_LABEL = "Search for a country..."
    }
}
