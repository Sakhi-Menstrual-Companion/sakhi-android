package team.sakhi.android.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry

/**
 * The package these tests drive on the device.
 *
 * This is the app's `applicationId` (`com.rachna.mysakhi`), NOT this project's Kotlin
 * namespace (`team.sakhi.android`). Those two deliberately differ, see the `applicationId`
 * comment in `app/build.gradle.kts`.
 *
 * Both files here used to hardcode the *namespace*, so `startActivityAndWait()` was
 * launching a package that does not exist on the device and profile generation could never
 * succeed. That is why `app/src/release/` stayed empty, and why every release so far shipped
 * an ART profile containing only the androidx library entries and zero `team/sakhi` ones.
 *
 * Reading the injected argument rather than hardcoding keeps this correct across an
 * applicationId change and across the `nonMinifiedRelease` variant's suffixing, neither of
 * which a string literal can track.
 *
 * The argument name is version-dependent, so both spellings are tried. This plugin
 * (androidx.baselineprofile 1.4.1) injects `androidx.benchmark.targetPackageName` — verified
 * by reading the runner arguments back off a real instrumentation run, after assuming the
 * newer `targetAppId` name and watching the run fail. Newer versions use `targetAppId`, so
 * that stays as a fallback for whenever the plugin is bumped.
 */
internal fun targetPackageName(): String {
    val args = InstrumentationRegistry.getArguments()
    return args.getString("androidx.benchmark.targetPackageName")
        ?: args.getString("targetAppId")
        ?: error(
            "No target package argument was passed to the instrumentation runner. Tried " +
                "androidx.benchmark.targetPackageName and targetAppId. Received: " +
                args.keySet().sorted().joinToString(),
        )
}
