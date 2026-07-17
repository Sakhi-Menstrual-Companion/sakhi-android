package team.sakhi.android.baselineprofile

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartToPhoneScreen() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc(PHONE_FIELD_LABEL)), UI_WAIT_TIMEOUT_MS)
    }

    private companion object {
        const val PACKAGE_NAME = "team.sakhi.android"
        const val ITERATIONS = 5
        const val UI_WAIT_TIMEOUT_MS = 7_500L
        const val PHONE_FIELD_LABEL = "Phone number"
    }
}
