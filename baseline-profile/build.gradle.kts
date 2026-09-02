plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "team.sakhi.android.baselineprofile"
    // Tracks :app's compileSdk/targetSdk (both 36). A profile is collected against the
    // installed app, so a test module targeting an older API can exercise different
    // framework paths than the shipping build actually takes.
    compileSdk = 36
    targetProjectPath = ":app"

    defaultConfig {
        // 28, not :app's 26 — Macrobenchmark itself requires API 28+. This only bounds
        // where a profile can be *collected*, not where it can be *used*: the generated
        // profile still ships to, and benefits, every device from minSdk 26 up.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Defaults to BaselineProfile so a profile *generation* run does not also pay for
        // benchmark iterations. Override to measure instead:
        //
        //   ./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest \
        //       -PbenchmarkRules=Macrobenchmark
        //
        // This default is why `ColdStartBenchmark` silently produced no timings: it was
        // filtered out of every run, so nothing ever measured whether the profile helped.
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] =
            providers.gradleProperty("benchmarkRules").getOrElse("BaselineProfile")
        // This module exists only to collect a profile on the local emulator/device.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true

    // Declared here rather than inherited: this module is `com.android.test` and does not
    // apply the sakhi.android.* convention plugins, because it deliberately differs on minSdk
    // (28, the Macrobenchmark floor). Kept in step with GradleManagedDevices.kt by hand.
    testOptions {
        managedDevices {
            @Suppress("UnstableApiUsage")
            devices {
                maybeCreate("pixel6Api33GoogleApis", com.android.build.api.dsl.ManagedVirtualDevice::class.java).apply {
                    device = "Pixel 6"
                    apiLevel = 33
                    // google_apis, not aosp-atd: the app initialises Firebase, Maps and Play
                    // Services Location at startup, none of which exist on an ATD image.
                    systemImageSource = "google_apis"
                }
            }
        }
    }
}

baselineProfile {
    // Gradle boots the emulator itself. Generation previously required somebody to have the
    // right AVD already running, and getting that wrong failed in ways that looked like app
    // bugs: a stale package record, then MIUI refusing the install, before any app code ran.
    //
    // Flip back with `-PuseConnectedDevice` to generate against whatever is plugged in, which
    // is still the better profile when a real target phone is available.
    val useConnected = providers.gradleProperty("useConnectedDevice").isPresent
    useConnectedDevices = useConnected
    if (!useConnected) {
        managedDevices += "pixel6Api33GoogleApis"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
