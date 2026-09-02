package team.sakhi.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke

/**
 * Emulators declared in Gradle instead of created by hand in Android Studio.
 *
 * Gradle downloads the system image, boots it, runs the tests and tears it down. That matters
 * here beyond CI convenience: generating a baseline profile currently requires somebody to
 * have booted the right emulator first, and getting that wrong is not obvious. The last
 * generation run failed twice for environment reasons alone (a stale package record on the
 * phone, then MIUI refusing the install) before anything about the app was even exercised.
 *
 * With these declared, profile generation and instrumented tests become one command on a
 * machine with no emulator set up at all.
 */
private data class DeviceConfig(
    val device: String,
    val apiLevel: Int,
    val systemImageSource: String,
) {
    /**
     * The Gradle task prefix, e.g. `pixel6Api33GoogleApis`. Separators in the image source
     * (`google_apis`, `aosp-atd`) become camel case rather than being dropped, so the name
     * stays readable and is a legal task name either way.
     */
    val taskName: String = buildString {
        append(device.lowercase().replace(" ", ""))
        append("Api")
        append(apiLevel)
        systemImageSource.split('-', '_').forEach { part ->
            append(part.replaceFirstChar(Char::uppercase))
        }
    }
}

internal fun configureGradleManagedDevices(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    // google_apis, NOT aosp-atd. ATD images are smaller and headless, which is what a CI
    // device normally wants, but they ship WITHOUT Google Play services — and this app wires
    // Firebase Messaging, Google Maps and Play Services Location at startup. Tests on an ATD
    // image would fail for reasons that have nothing to do with the code under test.
    val pixel6Api33 = DeviceConfig("Pixel 6", 33, "google_apis")

    // A second, older device on the same image family. API 28 is the floor Macrobenchmark
    // supports (see :baseline-profile's minSdk note); the app itself goes down to 26, so this
    // is the oldest API that both can share.
    val pixel4Api28 = DeviceConfig("Pixel 4", 28, "google_apis")

    val allDevices = listOf(pixel6Api33, pixel4Api28)

    commonExtension.testOptions {
        managedDevices {
            @Suppress("UnstableApiUsage")
            devices {
                allDevices.forEach { config ->
                    maybeCreate(config.taskName, ManagedVirtualDevice::class.java).apply {
                        device = config.device
                        apiLevel = config.apiLevel
                        systemImageSource = config.systemImageSource
                    }
                }
            }
        }
    }
}
