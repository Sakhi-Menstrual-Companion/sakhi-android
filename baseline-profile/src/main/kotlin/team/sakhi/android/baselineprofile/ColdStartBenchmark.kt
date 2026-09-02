package team.sakhi.android.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold start, twice, so the baseline profile's value is a number rather than a claim.
 *
 * Run it with:
 *
 *   ./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest -PbenchmarkRules=Macrobenchmark
 *
 * The rule filter matters: `baseline-profile/build.gradle.kts` defaults
 * `androidx.benchmark.enabledRules` to `BaselineProfile`, so that a profile *generation* run
 * does not also pay for benchmark iterations. That default is why this class produced no
 * timings at all before — it was silently filtered out of every run.
 *
 * [coldStartNoCompilation] is the floor: everything interpreted, no AOT. [coldStartWithProfile]
 * is what a user installing from Play actually gets on first launch, because Play ships the
 * profile in the bundle and ART compiles against it at install time. The gap between the two
 * IS the profile's contribution.
 *
 * Read the medians, not the mins. And treat the absolute numbers as specific to whichever
 * device this ran on: a Redmi Note 10 Pro is not a flagship, which makes it a more honest
 * proxy for the phones Sakhi's users actually carry than an emulator on an M-series Mac.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** No AOT at all. The worst case, and what the app shipped as before the profile worked. */
    @Test
    fun coldStartNoCompilation() = measureColdStart(CompilationMode.None())

    /** Compiled against the shipped baseline profile, which is the real first-launch case. */
    @Test
    fun coldStartWithProfile() = measureColdStart(
        CompilationMode.Partial(baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require),
    )

    private fun measureColdStart(mode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = targetPackageName(),
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
        // Waits for the app's own window, not a specific screen. This used to wait on a
        // "Phone number" label, which a freshly installed app never shows — it opens on the
        // onboarding intro — so the wait burned its full timeout on every iteration and the
        // numbers included that stall. See the note in `BaselineProfileGenerator`.
        device.wait(Until.hasObject(By.pkg(targetPackageName()).depth(0)), UI_WAIT_TIMEOUT_MS)
    }

    private companion object {
        const val ITERATIONS = 10
        const val UI_WAIT_TIMEOUT_MS = 15_000L
    }
}
